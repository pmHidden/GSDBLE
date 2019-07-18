#include "lsm_protocol.h"
#include "nrf_drv_gpiote.h"
#include "nrf_drv_twi.h"
#include "nrf_log.h"

/**
implementation of lsm_protocol.h. uses i2c as protocol

hookup on 'steval-mk178v2' -> 'nrf dk52':
 sdo -> P0.11 miso
 sda -> P0.12 mosi
 scl -> P0.14 sck
 cs -> P0.16 ss
*/
#define TWI_INSTANCE_ID 0
static const nrf_drv_twi_t m_twi = NRF_DRV_TWI_INSTANCE(TWI_INSTANCE_ID);
static uint8_t twi_chip_id = LSM6DSL_I2C_ADD_L;

void lsm_protocol_init() {
  // set miso and ss to gnd
  nrf_gpio_cfg_output(11);
  nrf_gpio_pin_clear(11);
  nrf_gpio_cfg_output(16);
  nrf_gpio_pin_set(16);

  // init twi
  nrf_drv_twi_config_t twi_config = NRF_DRV_TWI_DEFAULT_CONFIG;
  twi_config.scl = 14;
  twi_config.sda = 12;
  twi_config.frequency = NRF_DRV_TWI_FREQ_400K;
  APP_ERROR_CHECK(nrf_drv_twi_init(&m_twi, &twi_config, NULL, NULL));
  nrf_drv_twi_enable(&m_twi);
}

int32_t lsm_protocol_read(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  if (len > 0xFEu)
    NRF_LOG_INFO("I2C-READ: YOU ARE READING TOO MUCH DATA! %u", len);

  uint8_t writeId = twi_chip_id >> 1;
  uint8_t readId = twi_chip_id >> 1;
  APP_ERROR_CHECK(nrf_drv_twi_tx(&m_twi, writeId, &reg_addr, 1, true));
  APP_ERROR_CHECK(nrf_drv_twi_rx(&m_twi, readId, data, len));
  return 0;
}
int32_t lsm_protocol_write(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  if (len > 0xFEu)
    NRF_LOG_INFO("I2C-WRITE: YOU ARE WRITING TOO MUCH DATA! %u", len);

  uint8_t tx_ptr[len + 1];
  tx_ptr[0] = reg_addr;
  memcpy(tx_ptr + 1, data, len);
  uint8_t writeId = twi_chip_id >> 1;
  APP_ERROR_CHECK(nrf_drv_twi_tx(&m_twi, writeId, tx_ptr, len + 1, false));
  return 0;
}