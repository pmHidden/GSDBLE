#include "board_manager.h"

#include "ble.h"
#include "bsp.h"
#include "nrf_sdh_ble.h"
#include <stdint.h>

#define BTN_ID 0

static void (*disconnect)();
static uint16_t (*getConnHandle)();

/**@brief Function for handling events from the BSP module.
 *
 * @param[in]   event   Event generated when button is pressed.
 */
static void bsp_event_handler(bsp_event_t event) {
  //NRF_LOG_INFO("bsp_event_handler");
  ret_code_t err_code;

  switch (event) {
  case BSP_EVENT_SLEEP:

    disconnect();
    break; // BSP_EVENT_SLEEP

  case BSP_EVENT_DISCONNECT:
    err_code = sd_ble_gap_disconnect(getConnHandle(), BLE_HCI_REMOTE_USER_TERMINATED_CONNECTION);
    if (err_code != NRF_ERROR_INVALID_STATE) {
      APP_ERROR_CHECK(err_code);
    }
    break; // BSP_EVENT_DISCONNECT
  default:
    break;
  }
}

static uint32_t connection_buttons_configure() {
  uint32_t err_code;

  err_code = bsp_event_to_button_action_assign(BTN_ID, BSP_BUTTON_ACTION_LONG_PUSH, BSP_EVENT_DISCONNECT);
  if (err_code != NRF_SUCCESS)
    return err_code;

  err_code = bsp_event_to_button_action_assign(BTN_ID, BSP_BUTTON_ACTION_RELEASE, BSP_EVENT_DEFAULT);
  return err_code;
}

static uint32_t advertising_buttons_configure() {
  uint32_t err_code;

  err_code = bsp_event_to_button_action_assign(BTN_ID, BSP_BUTTON_ACTION_LONG_PUSH, BSP_EVENT_DEFAULT);
  if (err_code != NRF_SUCCESS)
    return err_code;

  err_code = bsp_event_to_button_action_assign(BTN_ID, BSP_BUTTON_ACTION_RELEASE, BSP_EVENT_SLEEP);
  return err_code;
}

static void ble_evt_handler(ble_evt_t const *p_ble_evt, void *p_context) {
  uint32_t err_code;

  switch (p_ble_evt->header.evt_id) {
  case BLE_GAP_EVT_CONNECTED:
    err_code = connection_buttons_configure();
    APP_ERROR_CHECK(err_code);
    break;

  case BLE_GAP_EVT_DISCONNECTED:
    err_code = advertising_buttons_configure();
    APP_ERROR_CHECK(err_code);
    break;

  default:
    break;
  }
}
NRF_SDH_BLE_OBSERVER(m_ble_observer, BSP_BTN_BLE_OBSERVER_PRIO, ble_evt_handler, NULL);

// publics

void bsp_module_init(void (*p_disconnect)(), uint16_t (*p_getConnHandle)()) {
  disconnect = p_disconnect;
  getConnHandle = p_getConnHandle;

  ret_code_t err_code;
  bsp_event_t startup_event;

  err_code = bsp_init(BSP_INIT_LEDS | BSP_INIT_BUTTONS, bsp_event_handler);
  APP_ERROR_CHECK(err_code);

  err_code = advertising_buttons_configure();
  APP_ERROR_CHECK(err_code);
}

void bsp_ind_adv(void) {
  ret_code_t err_code;
  err_code = bsp_indication_set(BSP_INDICATE_ADVERTISING);
  APP_ERROR_CHECK(err_code);
}

void bsp_ind_idle(void) {
  ret_code_t err_code;
  err_code = bsp_indication_set(BSP_INDICATE_IDLE);
  APP_ERROR_CHECK(err_code);
}

void bsp_ind_conn(void) {
  ret_code_t err_code;
  err_code = bsp_indication_set(BSP_INDICATE_CONNECTED);
  APP_ERROR_CHECK(err_code);
}

void bsp_ind_error(void) {
  ret_code_t err_code;
  err_code = bsp_indication_set(BSP_INDICATE_FATAL_ERROR);
  APP_ERROR_CHECK(err_code);
}

void bsp_ready_for_sleep(void) {
  ret_code_t err_code;
  err_code = bsp_wakeup_button_enable(BTN_ID);
  APP_ERROR_CHECK(err_code);
}