#include "bmi_protocol.h"
#include <hal/nrf_gpio.h>
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

void bmi_protocol_init(struct bmi160_dev *dev_ctx) {
  // set miso and ss to gnd
  nrf_gpio_cfg_output(11);
  nrf_gpio_pin_clear(11);
  //nrf_gpio_cfg_output(16);
  //nrf_gpio_pin_clear(16);

  // init twi
  nrf_drv_twi_config_t twi_config = NRF_DRV_TWI_DEFAULT_CONFIG;
  twi_config.scl = 14;
  twi_config.sda = 12;
  twi_config.frequency = NRF_TWI_FREQ_100K;
  APP_ERROR_CHECK(nrf_drv_twi_init(&m_twi, &twi_config, NULL, NULL));
  nrf_drv_twi_enable(&m_twi);

  dev_ctx->id = BMI160_I2C_ADDR;
  dev_ctx->interface = BMI160_I2C_INTF;
}

int8_t bmi_protocol_read(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  if (len > 0xFEu)
    NRF_LOG_INFO("I2C-READ: YOU ARE READING TOO MUCH DATA! %u", len);

  if (len != 0) {
    ret_code_t err_code;
    err_code = nrf_drv_twi_tx(&m_twi, id, &reg_addr, 1, true);
    APP_ERROR_CHECK(err_code);

    err_code = nrf_drv_twi_rx(&m_twi, id, data, len);
    APP_ERROR_CHECK(err_code);
  }
  return BMI160_OK;
}
int8_t bmi_protocol_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  if (len > 0xFEu)
    NRF_LOG_INFO("I2C-WRITE: YOU ARE WRITING TOO MUCH DATA! %u", len);

  uint8_t tx_ptr[len + 1];
  tx_ptr[0] = reg_addr;
  memcpy(tx_ptr + 1, data, len);
  ret_code_t err_code;
  err_code = nrf_drv_twi_tx(&m_twi, id, (const uint8_t *)&tx_ptr, len + 1, false);
  APP_ERROR_CHECK(err_code);
  return BMI160_OK;
}