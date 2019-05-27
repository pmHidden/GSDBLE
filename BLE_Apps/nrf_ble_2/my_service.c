// https://github.com/bjornspockeli/custom_ble_service_example
#include "my_service.h"
#include "ble_srv_common.h"
#include "boards.h"
#include "chara_conf.h"
#include "chara_data.h"
#include "imu.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "sdk_common.h"
#include <string.h>

my_service_t *my_service_cus;

static bool send_my_data(imu_data data) {
  return chara_data_update(my_service_cus, data) == NRF_SUCCESS;
}

void my_service_loop() {
  imu_loop();
}

uint32_t my_service_init(my_service_t *p_cus, const my_service_init_t *p_cus_init) {
  if (p_cus == NULL || p_cus_init == NULL) {
    return NRF_ERROR_NULL;
  }
  my_service_cus = p_cus;

  uint32_t err_code;
  ble_uuid_t ble_uuid;

  // Initialize service structure
  p_cus->conn_handle = BLE_CONN_HANDLE_INVALID;

  // Add Custom Service UUID
  ble_uuid128_t base_uuid = {CUSTOM_SERVICE_UUID_BASE};
  err_code = sd_ble_uuid_vs_add(&base_uuid, &p_cus->uuid_type);
  VERIFY_SUCCESS(err_code);

  ble_uuid.type = p_cus->uuid_type;
  ble_uuid.uuid = CUSTOM_SERVICE_UUID;

  // Add the Custom Service
  err_code = sd_ble_gatts_service_add(BLE_GATTS_SRVC_TYPE_PRIMARY, &ble_uuid, &p_cus->service_handle);
  if (err_code != NRF_SUCCESS)
    return err_code;

  // Add Characteristics to Service
  err_code = chara_data_add(p_cus, p_cus_init);
  if (err_code != NRF_SUCCESS)
    return err_code;

  //err_code = chara_conf_add(p_cus, p_cus_init);
  if (err_code != NRF_SUCCESS)
    return err_code;

  // Activate Sensor
  imu_init(send_my_data);
  imu_set_speed(IMU_ODR_800Hz);
  imu_stop();

  return NRF_SUCCESS;
}

/**@brief Function for handling the Connect event.
 *
 * @param[in]   p_cus       Custom Service structure.
 * @param[in]   p_ble_evt   Event received from the BLE stack.
 */
static void on_connect(my_service_t *p_cus, ble_evt_t const *p_ble_evt) {
  p_cus->conn_handle = p_ble_evt->evt.gap_evt.conn_handle;
  imu_restart();
}

/**@brief Function for handling the Write event.
 *
 * @param[in]   p_cus       Custom Service structure.
 * @param[in]   p_ble_evt   Event received from the BLE stack.
 */
static void on_write(my_service_t *p_cus, ble_evt_t const *p_ble_evt) {
  const ble_gatts_evt_write_t *p_evt_write = &p_ble_evt->evt.gatts_evt.params.write;

  // Check if the handle passed with the event matches the Custom Value Characteristic handle.
  if (p_evt_write->handle == p_cus->custom_value_handles.value_handle) {
    NRF_LOG_INFO("yes!");
  }

  // Check if the Custom value CCCD is written to and that the value is the appropriate length, i.e 2 bytes.
  if ((p_evt_write->handle == p_cus->custom_value_handles.cccd_handle) && (p_evt_write->len == 2)) {
    if (ble_srv_is_notification_enabled(p_evt_write->data)) {
      //evt.evt_type = BLE_CUS_EVT_NOTIFICATION_ENABLED;
      NRF_LOG_INFO("notificated!");
    } else {
      //evt.evt_type = BLE_CUS_EVT_NOTIFICATION_DISABLED;
      NRF_LOG_INFO("unnotificated!");
    }
  }
}

/**@brief Function for handling the Disconnect event.
 *
 * @param[in]   p_cus       Custom Service structure.
 * @param[in]   p_ble_evt   Event received from the BLE stack.
 */
static void on_disconnect(my_service_t *p_cus, ble_evt_t const *p_ble_evt) {
  UNUSED_PARAMETER(p_ble_evt);
  p_cus->conn_handle = BLE_CONN_HANDLE_INVALID;
  imu_stop();
}

void ble_cus_on_ble_evt(ble_evt_t const *p_ble_evt, void *p_context) {
  my_service_t *p_cus = (my_service_t *)p_context;

  if (p_cus == NULL || p_ble_evt == NULL) {
    return;
  }

  switch (p_ble_evt->header.evt_id) {
  case BLE_GAP_EVT_CONNECTED:
    on_connect(p_cus, p_ble_evt);
    break;

  case BLE_GAP_EVT_DISCONNECTED:
    on_disconnect(p_cus, p_ble_evt);
    break;

  case BLE_GATTS_EVT_WRITE:
    on_write(p_cus, p_ble_evt);
    break;
  case BLE_GATTS_EVT_HVN_TX_COMPLETE:
    //NRF_LOG_INFO("tx complete");
    break;
  default:
    NRF_LOG_INFO("other: %d", p_ble_evt->header.evt_id);
    // No implementation needed.
    break;
  }
}