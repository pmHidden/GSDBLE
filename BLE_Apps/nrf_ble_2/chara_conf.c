// https://github.com/bjornspockeli/custom_ble_service_example
// characteristic for sensor config
#include "chara_conf.h"
#include "my_service.h"
#include "ble_srv_common.h"
#include "boards.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "sdk_common.h"
#include <string.h>

uint32_t chara_conf_add(my_service_t *p_cus, const my_service_init_t *p_cus_init) {
  uint32_t err_code;
  ble_gatts_char_md_t char_md;
  ble_gatts_attr_md_t cccd_md;
  ble_gatts_attr_t attr_char_value;
  ble_uuid_t ble_uuid;
  ble_gatts_attr_md_t attr_md;

  // properties that will be displayed to the central during service discovery
  memset(&char_md, 0, sizeof(char_md));
  char_md.char_props.read = 1;
  char_md.char_props.write = 1;
  char_md.char_props.notify = 1;
  char_md.p_char_user_desc = NULL;
  char_md.p_char_pf = NULL;
  char_md.p_user_desc_md = NULL;
  char_md.p_cccd_md = &cccd_md;
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
  ble_uuid.uuid = CHARA_CONF_UUID;

  // set the UUID, point to the attribute metadata and set the size of the characteristic
  memset(&attr_char_value, 0, sizeof(attr_char_value));
  attr_char_value.p_uuid = &ble_uuid;
  attr_char_value.p_attr_md = &attr_md;
  attr_char_value.init_len = sizeof(uint8_t);
  attr_char_value.init_offs = 0;
  attr_char_value.max_len = sizeof(uint8_t);

  //  Read  operation on Cccd should be possible without authentication.
  memset(&cccd_md, 0, sizeof(cccd_md));
  BLE_GAP_CONN_SEC_MODE_SET_OPEN(&cccd_md.read_perm);
  BLE_GAP_CONN_SEC_MODE_SET_OPEN(&cccd_md.write_perm);
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