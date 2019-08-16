#include "app_error.h"
#include "app_timer.h"
#include "nrf_ble_gatt.h"
#include "nrf_ble_qwr.h"
#include "nrf_pwr_mgmt.h"
#include "nrf_sdh.h"
#include "nrf_sdh_ble.h"
#include "nrf_sdh_soc.h"

#include "board_manager.h"
#include "peer_advertise.h"

#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"

#include "service/my_service.h"

#define DEVICE_NAME "GSDBLE"
#define APP_BLE_CONN_CFG_TAG 1                           // A tag identifying the SoftDevice BLE configuration
#define APP_TIMER_MAX_TIMERS (1 + BSP_APP_TIMERS_NUMBER) // We use one timer for LED
#define TX_QUEUE_SIZE 101                                  // size of ble tx buffer
#define ALGORITHM_ENABLED 1                             // enable algorithm for tx buffer
#define DEAD_BEEF 0xDEADBEEF                             // Value used as error code on stack dump, can be used to identify stack location on stack unwind

NRF_BLE_GATT_DEF(m_gatt);           // GATT module instance
NRF_BLE_QWR_DEF(m_qwr);             // Context for the Queued Write module
BLE_ADVERTISING_DEF(m_advertising); // Advertising module instance
MY_SERVICE_DEF(m_myservice);        // our sensor service

static uint16_t m_conn_handle = BLE_CONN_HANDLE_INVALID; // Handle of the current connection
static bool errorLedOnceDone = false;                    // on error: ignore, if we cant light up error-leds

/**@brief Callback function for asserts in the SoftDevice.
 *
 * @details This function will be called in case of an assert in the SoftDevice.
 *
 * @warning This handler is an example only and does not fit a final product. You need to analyze
 *          how your product is supposed to react in case of Assert.
 * @warning On assert from the SoftDevice, the system can only recover on reset.
 *
 * @param[in] line_num   Line number of the failing ASSERT call.
 * @param[in] file_name  File name of the failing ASSERT call.
 */
void assert_nrf_callback(uint16_t line_num, const uint8_t *p_file_name) {
  app_error_handler(DEAD_BEEF, line_num, p_file_name);
}

void app_error_fault_handler(uint32_t id, uint32_t pc, uint32_t info) {
  if (!errorLedOnceDone) {
    bsp_ind_error();
    errorLedOnceDone = true;
  }
  app_error_save_and_stop(id, pc, info);
}

/**@brief Function for the GAP initialization.
 *
 * @details This function sets up all the necessary GAP (Generic Access Profile) parameters of the
 *          device including the device name, appearance, and the preferred connection parameters.
 */
static void gap_params_init(void) {
  ret_code_t err_code;
  ble_gap_conn_params_t gap_conn_params;
  ble_gap_conn_sec_mode_t sec_mode;

  BLE_GAP_CONN_SEC_MODE_SET_OPEN(&sec_mode);

  err_code = sd_ble_gap_device_name_set(&sec_mode, (const uint8_t *)DEVICE_NAME, strlen(DEVICE_NAME));
  APP_ERROR_CHECK(err_code);

  /* YOUR_JOB: Use an appearance value matching the application's use case.
       err_code = sd_ble_gap_appearance_set(BLE_APPEARANCE_);
       APP_ERROR_CHECK(err_code); */

  memset(&gap_conn_params, 0, sizeof(gap_conn_params));

  // slave_latency 4 would be best, but it can be maximum 2, because of this constraint:
  // conn_sup_timeout * 4 > (1 + slave_latency) * max_conn_interval
  // this is because we want to allow every conn_interval.

  gap_conn_params.min_conn_interval = BLE_GAP_CP_MIN_CONN_INTVL_MIN;
  gap_conn_params.max_conn_interval = BLE_GAP_CP_MAX_CONN_INTVL_MAX;
  gap_conn_params.slave_latency = 2;
  gap_conn_params.conn_sup_timeout = BLE_GAP_CP_CONN_SUP_TIMEOUT_NONE;

  err_code = sd_ble_gap_ppcp_set(&gap_conn_params);
  APP_ERROR_CHECK(err_code);
}

/**@brief Function for handling Queued Write Module errors.
 *
 * @details A pointer to this function will be passed to each service which may need to inform the
 *          application about an error.
 *
 * @param[in]   nrf_error   Error code containing information about what went wrong.
 */
static void nrf_qwr_error_handler(uint32_t nrf_error) {
  APP_ERROR_HANDLER(nrf_error);
}

/**@brief Function for initializing services that will be used by the application.
 */
static void services_init(void) {
  ret_code_t err_code;
  nrf_ble_qwr_init_t qwr_init = {0};

  // Initialize Queued Write Module.
  qwr_init.error_handler = nrf_qwr_error_handler;

  err_code = nrf_ble_qwr_init(&m_qwr, &qwr_init);
  APP_ERROR_CHECK(err_code);

  // Initialize CUS Service init structure to zero.
#if ALGORITHM_ENABLED
  err_code = my_service_init(&m_myservice, TX_QUEUE_SIZE);
#else
  err_code = my_service_init(&m_myservice, 0xFFFFu);
#endif
  APP_ERROR_CHECK(err_code);
}

/**@brief Function for putting the chip into sleep mode.
 *
 * @note This function will not return.
 */
static void sleep_mode_enter(void) {
  bsp_ind_idle();
  bsp_ready_for_sleep();

  // Go to system-off mode (this function will not return; wakeup will cause a reset).
  ret_code_t err_code;
  err_code = sd_power_system_off();
  APP_ERROR_CHECK(err_code);
}

/**@brief Function for handling BLE events.
 *
 * @param[in]   p_ble_evt   Bluetooth stack event.
 * @param[in]   p_context   Unused.
 */
static void ble_evt_handler(ble_evt_t const *p_ble_evt, void *p_context) {
  ret_code_t err_code = NRF_SUCCESS;

  switch (p_ble_evt->header.evt_id) {
  case BLE_GAP_EVT_CONN_PARAM_UPDATE:
    NRF_LOG_INFO("BLE_GAP_EVT_CONN_PARAM_UPDATE: interval %d ms", 1.25 * p_ble_evt->evt.gap_evt.params.conn_param_update.conn_params.min_conn_interval);
    break;

  case BLE_GAP_EVT_DISCONNECTED:
    NRF_LOG_INFO("Disconnected.");
    break;

  case BLE_GAP_EVT_CONNECTED:
    NRF_LOG_INFO("Connected: interval %d ms", 1.25 * p_ble_evt->evt.gap_evt.params.connected.conn_params.min_conn_interval);
    bsp_ind_conn();
    m_conn_handle = p_ble_evt->evt.gap_evt.conn_handle;
    err_code = nrf_ble_qwr_conn_handle_assign(&m_qwr, m_conn_handle);
    APP_ERROR_CHECK(err_code);
    break;

  case BLE_GAP_EVT_PHY_UPDATE_REQUEST: {
    NRF_LOG_DEBUG("PHY update request.");
    ble_gap_phys_t const phys =
        {
            .rx_phys = BLE_GAP_PHY_AUTO,
            .tx_phys = BLE_GAP_PHY_AUTO,
        };
    err_code = sd_ble_gap_phy_update(p_ble_evt->evt.gap_evt.conn_handle, &phys);
    APP_ERROR_CHECK(err_code);
  } break;

  case BLE_GATTC_EVT_TIMEOUT:
    // Disconnect on GATT Client timeout event.
    NRF_LOG_DEBUG("GATT Client Timeout.");
    err_code = sd_ble_gap_disconnect(p_ble_evt->evt.gattc_evt.conn_handle, BLE_HCI_REMOTE_USER_TERMINATED_CONNECTION);
    APP_ERROR_CHECK(err_code);
    break;

  case BLE_GATTS_EVT_TIMEOUT:
    // Disconnect on GATT Server timeout event.
    NRF_LOG_DEBUG("GATT Server Timeout.");
    err_code = sd_ble_gap_disconnect(p_ble_evt->evt.gatts_evt.conn_handle, BLE_HCI_REMOTE_USER_TERMINATED_CONNECTION);
    APP_ERROR_CHECK(err_code);
    break;

  default:
    // No implementation needed.
    break;
  }
}

/**@brief Function for initializing the BLE stack.
 *
 * @details Initializes the SoftDevice and the BLE event interrupt.
 */
static void ble_stack_init(void) {
  ret_code_t err_code;

  err_code = nrf_sdh_enable_request();
  APP_ERROR_CHECK(err_code);

  // Configure the BLE stack using the default settings.
  // Fetch the start address of the application RAM.
  uint32_t ram_start = 0;
  err_code = nrf_sdh_ble_default_cfg_set(APP_BLE_CONN_CFG_TAG, &ram_start);
  APP_ERROR_CHECK(err_code);

  ble_cfg_t ble_cfg;
  memset(&ble_cfg, 0, sizeof ble_cfg);
  ble_cfg.conn_cfg.conn_cfg_tag = APP_BLE_CONN_CFG_TAG;
  ble_cfg.conn_cfg.params.gatts_conn_cfg.hvn_tx_queue_size = TX_QUEUE_SIZE;
  err_code = sd_ble_cfg_set(BLE_CONN_CFG_GATTS, &ble_cfg, ram_start);
  APP_ERROR_CHECK(err_code);

  // Enable BLE stack.
  err_code = nrf_sdh_ble_enable(&ram_start);
  APP_ERROR_CHECK(err_code);

  // Register a handler for BLE events.
  NRF_SDH_BLE_OBSERVER(m_ble_observer, 3, ble_evt_handler, NULL);
}

static uint16_t getConnHandle() {
  return m_conn_handle;
}

/**@brief Function for application main entry.
 */
int main(void) {
  ret_code_t err_code;
  // Initialize Log Module
  err_code = NRF_LOG_INIT(NULL);
  APP_ERROR_CHECK(err_code);
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  // Initialize Timer Module.
  err_code = app_timer_init();
  APP_ERROR_CHECK(err_code);

  bsp_module_init(sleep_mode_enter, getConnHandle);

  // Initialize Power Management Module.
  err_code = nrf_pwr_mgmt_init();
  APP_ERROR_CHECK(err_code);

  ble_stack_init();
  gap_params_init();

  // Initialize GATT Module.
  err_code = nrf_ble_gatt_init(&m_gatt, NULL);
  APP_ERROR_CHECK(err_code);

  services_init();
  advertising_init(&m_advertising, APP_BLE_CONN_CFG_TAG, bsp_ind_adv, sleep_mode_enter);
  peer_manager_init();

  NRF_LOG_INFO("Init was successful. GSDBLE started");
  advertising_start();

  // Enter main loop.
  for (;;) {
    //If there is no pending log operation, then sleep until next the next event occurs.
    if (NRF_LOG_PROCESS() == false) {
      nrf_pwr_mgmt_run();
      my_service_loop();
    }
  }
}