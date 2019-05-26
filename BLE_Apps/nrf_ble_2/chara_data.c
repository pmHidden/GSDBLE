// https://github.com/bjornspockeli/custom_ble_service_example
// characteristic for sensor data
#include "chara_data.h"
#include "ble_srv_common.h"
#include "boards.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "sdk_common.h"
#include <string.h>

uint32_t chara_data_add(my_service_t *p_cus, const my_service_init_t *p_cus_init) {
  uint32_t err_code;
  ble_gatts_char_md_t char_md;
  ble_gatts_attr_md_t cccd_md;
  ble_gatts_attr_t attr_char_value;
  ble_uuid_t ble_uuid;
  ble_gatts_attr_md_t attr_md;

  // properties that will be displayed to the central during service discovery
  memset(&char_md, 0, sizeof(char_md));
  char_md.char_props.read = 1;
  char_md.char_props.write = 0;
  char_md.char_props.notify = 1;
  char_md.p_char_user_desc = NULL;
  char_md.p_char_pf = NULL;
  char_md.p_user_desc_md = NULL;
  char_md.p_cccd_md = NULL;
  char_md.p_sccd_md = NULL;

  // set the properties
  memset(&attr_md, 0, sizeof(attr_md));
  attr_md.read_perm = p_cus_init->custom_value_char_attr_md.read_perm;
  attr_md.write_perm = p_cus_init->custom_value_char_attr_md.write_perm;
  attr_md.vloc = BLE_GATTS_VLOC_STACK;
  attr_md.rd_auth = 0;
  attr_md.wr_auth = 0;
  attr_md.vlen = 0;

  // uuid setting
  ble_uuid.type = p_cus->uuid_type;
  ble_uuid.uuid = CHARA_DATA_UUID;

  // set the UUID, point to the attribute metadata and set the size of the characteristic
  memset(&attr_char_value, 0, sizeof(attr_char_value));
  attr_char_value.p_uuid = &ble_uuid;
  attr_char_value.p_attr_md = &attr_md;
  attr_char_value.init_len = sizeof(imu_data);
  attr_char_value.init_offs = 0;
  attr_char_value.max_len = sizeof(imu_data);

  //  Read  operation on Cccd should be possible without authentication.
  memset(&cccd_md, 0, sizeof(cccd_md));
  BLE_GAP_CONN_SEC_MODE_SET_OPEN(&cccd_md.read_perm);
  cccd_md.vloc = BLE_GATTS_VLOC_STACK;

  // add our characteristic
  err_code = sd_ble_gatts_characteristic_add(p_cus->service_handle, &char_md,
      &attr_char_value,
      &p_cus->custom_value_handles);
  if (err_code != NRF_SUCCESS) {
    return err_code;
  }

  return NRF_SUCCESS;
}

/**@brief Function for handling the Write event.
 *
 * @param[in]   p_cus       Custom Service structure.
 * @param[in]   p_ble_evt   Event received from the BLE stack.
 */
static void on_write(my_service_t *p_cus, ble_evt_t const *p_ble_evt) {
  ble_gatts_evt_write_t *p_evt_write = &p_ble_evt->evt.gatts_evt.params.write;

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

uint32_t chara_data_update(my_service_t *p_cus, imu_data custom_value) {
  //NRF_LOG_INFO("In ble_cus_custom_value_update. \r\n");
  if (p_cus == NULL) {
    NRF_LOG_INFO("A");
    return NRF_ERROR_NULL;
  }

  uint32_t err_code = NRF_SUCCESS;
  ble_gatts_value_t gatts_value;

  // Initialize value struct.
  memset(&gatts_value, 0, sizeof(gatts_value));

  gatts_value.len = sizeof(imu_data);
  gatts_value.offset = 0;
  gatts_value.p_value = &custom_value;

  // Update database.
  err_code = sd_ble_gatts_value_set(p_cus->conn_handle,
      p_cus->custom_value_handles.value_handle,
      &gatts_value);
  if (err_code != NRF_SUCCESS) {
    NRF_LOG_INFO("B");
    return err_code;
  }

  // Send value if connected and notifying.
  if ((p_cus->conn_handle != BLE_CONN_HANDLE_INVALID)) {
    ble_gatts_hvx_params_t hvx_params;

    memset(&hvx_params, 0, sizeof(hvx_params));

    hvx_params.handle = p_cus->custom_value_handles.value_handle;
    hvx_params.type = BLE_GATT_HVX_NOTIFICATION;
    hvx_params.offset = gatts_value.offset;
    hvx_params.p_len = &gatts_value.len;
    hvx_params.p_data = gatts_value.p_value;

    err_code = sd_ble_gatts_hvx(p_cus->conn_handle, &hvx_params);
  } else {
    err_code = NRF_ERROR_INVALID_STATE;
  }

  //NRF_LOG_INFO("C");
  return err_code;
}