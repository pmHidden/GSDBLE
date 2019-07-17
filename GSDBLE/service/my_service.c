// https://github.com/bjornspockeli/custom_ble_service_example
// also has the function of the characteristics
#include "my_service.h"
#include "ble_srv_common.h"
#include "boards.h"
#include "chara_conf.h"
#include "chara_data.h"
#include "../imu.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "sdk_common.h"
#include <string.h>

my_service_t *my_service_cus;

static bool send_my_data(imu_data_t data) {
  return chara_data_update(my_service_cus, data) == NRF_SUCCESS;
}

void my_service_loop() {
  imu_loop();
}

static void writeCurrentConfig() {
  chara_conf_packet_t config;
  memset(&config, 0, sizeof(chara_conf_packet_t));
  config.speed = (uint8_t)imu_speed_get();
  config.pause = (uint8_t)imu_is_paused();
  chara_conf_update(my_service_cus, config);
}

uint32_t my_service_init(my_service_t *p_cus, uint16_t p_ble_tx_buffer_size) {
  if (p_cus == NULL) {
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
  VERIFY_SUCCESS(err_code);

  // Add Characteristics to Service
  err_code = chara_data_add(p_cus);
  VERIFY_SUCCESS(err_code);

  err_code = chara_conf_add(p_cus);
  VERIFY_SUCCESS(err_code);

  // Activate Sensor
  imu_init(send_my_data, p_ble_tx_buffer_size);

  // Write current config
  writeCurrentConfig();

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

  // user writes new configuration
  if (p_evt_write->handle == p_cus->chara_conf_handles.value_handle && p_evt_write->len >= sizeof(chara_conf_packet_u)) {
    // pull new config and apply
    chara_conf_packet_u config;
    memset(&config, 0, sizeof(chara_conf_packet_u));
    memcpy(config.bytes, p_evt_write->data, sizeof(chara_conf_packet_u));
    imu_speed_set((imu_speed_t)config.parsed.speed);
    bool want_to_pause = (bool)config.parsed.pause;
    if (want_to_pause != imu_is_paused()) {
      if (want_to_pause)
        imu_stop(false);
      else
        imu_restart();
    }
    // respond with update
    writeCurrentConfig();
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
  imu_stop(true);
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
  case BLE_GAP_EVT_CONN_PARAM_UPDATE:
    imu_on_new_interval(p_ble_evt->evt.gap_evt.params.conn_param_update.conn_params.max_conn_interval);
    chara_conf_packet_u config;
    memset(&config, 0, sizeof(chara_conf_packet_u));
    config.parsed.speed = (uint8_t)imu_speed_get();
    chara_conf_update(p_cus, config.parsed);
    break;
  case BLE_GATTS_EVT_HVN_TX_COMPLETE:
    break;
  default:
    NRF_LOG_INFO("other ble event: %d", p_ble_evt->header.evt_id);
    // No implementation needed.
    break;
  }
}