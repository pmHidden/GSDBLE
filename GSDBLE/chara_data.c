// https://github.com/bjornspockeli/custom_ble_service_example
// characteristic for sensor data. only has the configuration, not the function.
#include "chara_data.h"
#include "ble_srv_common.h"
#include "boards.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "sdk_common.h"
#include <string.h>

uint32_t chara_data_add(my_service_t *p_cus) {
  // properties that will be displayed to the central during service discovery
  ble_gatts_char_md_t char_md;
  memset(&char_md, 0, sizeof(char_md));
  char_md.char_props.read = 1;
  char_md.char_props.write = 0;
  char_md.char_props.notify = 1;
  char_md.p_char_user_desc = NULL;
  char_md.p_char_pf = NULL;
  char_md.p_user_desc_md = NULL;
  char_md.p_cccd_md = NULL;
  char_md.p_sccd_md = NULL;

  //  Read  operation on Cccd should be possible without authentication.
  ble_gatts_attr_md_t cccd_md;
  memset(&cccd_md, 0, sizeof(cccd_md));
  BLE_GAP_CONN_SEC_MODE_SET_OPEN(&cccd_md.read_perm);
  BLE_GAP_CONN_SEC_MODE_SET_OPEN(&cccd_md.write_perm);
  cccd_md.vloc = BLE_GATTS_VLOC_STACK;
  char_md.p_cccd_md = &cccd_md;

  // set the UUID, point to the attribute metadata and set the size of the characteristic
  ble_gatts_attr_t attr_char_value;
  memset(&attr_char_value, 0, sizeof(attr_char_value));
  attr_char_value.init_len = sizeof(imu_data_t);
  attr_char_value.init_offs = 0;
  attr_char_value.max_len = sizeof(imu_data_t);
  NRF_LOG_INFO("Size of chara_data is %d bytes", sizeof(imu_data_t));

  // set the properties
  ble_gatts_attr_md_t attr_md;
  memset(&attr_md, 0, sizeof(attr_md));
  attr_md.vloc = BLE_GATTS_VLOC_STACK;
  attr_md.rd_auth = 0;
  attr_md.wr_auth = 0;
  attr_md.vlen = 0;

  // open access (read)
  ble_srv_cccd_security_mode_t secu;
  memset(&secu, 0, sizeof(ble_srv_cccd_security_mode_t));
  BLE_GAP_CONN_SEC_MODE_SET_OPEN(&secu.read_perm);
  attr_md.read_perm = secu.read_perm;
  attr_md.write_perm = secu.write_perm;
  attr_char_value.p_attr_md = &attr_md;

  // uuid setting
  ble_uuid_t ble_uuid;
  memset(&ble_uuid, 0, sizeof(ble_uuid_t));
  ble_uuid.type = p_cus->uuid_type;
  ble_uuid.uuid = CHARA_DATA_UUID;
  attr_char_value.p_uuid = &ble_uuid;

  // add our characteristic
  return sd_ble_gatts_characteristic_add(p_cus->service_handle, &char_md, &attr_char_value, &p_cus->chara_data_handles);
}

uint32_t chara_data_update(my_service_t *p_cus, imu_data_t new_value) {
  if (p_cus == NULL)
    return NRF_ERROR_NULL;

  // Initialize value struct.
  ble_gatts_value_t gatts_value;
  memset(&gatts_value, 0, sizeof(ble_gatts_value_t));
  gatts_value.len = sizeof(imu_data_t);
  gatts_value.offset = 0;
  gatts_value.p_value = (uint8_t *)&new_value;

  // Update database.
  uint32_t err_code = sd_ble_gatts_value_set(p_cus->conn_handle, p_cus->chara_data_handles.value_handle, &gatts_value);
  if (err_code != NRF_SUCCESS)
    return err_code;

  // Send value if connected and notifying.
  if ((p_cus->conn_handle == BLE_CONN_HANDLE_INVALID))
    return NRF_ERROR_INVALID_STATE;

  ble_gatts_hvx_params_t hvx_params;
  memset(&hvx_params, 0, sizeof(hvx_params));
  hvx_params.handle = p_cus->chara_data_handles.value_handle;
  hvx_params.type = BLE_GATT_HVX_NOTIFICATION;
  hvx_params.offset = gatts_value.offset;
  hvx_params.p_len = &gatts_value.len;
  hvx_params.p_data = gatts_value.p_value;

  return sd_ble_gatts_hvx(p_cus->conn_handle, &hvx_params);
}