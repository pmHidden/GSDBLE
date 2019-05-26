#include "lsm_protocol.h"
#include "nrf_log.h"
#include "nrf_drv_twi.h"

/**
implementation of lsm_protocol.h. uses i2c as protocol

hookup on 'steval-mk178v2' -> 'nrf dk52':
miso -> gnd
mosi -> P0.28
sck -> P0.29
*/
#define TWI_INSTANCE_ID 0
static const nrf_drv_twi_t m_twi = NRF_DRV_TWI_INSTANCE(TWI_INSTANCE_ID);
static uint8_t twi_chip_id = LSM6DSL_I2C_ADD_L;

void lsm_protocol_init() {
  nrf_drv_twi_config_t twi_config = NRF_DRV_TWI_DEFAULT_CONFIG;
  twi_config.scl = 29;
  twi_config.sda = 28;
  twi_config.frequency = NRF_DRV_TWI_FREQ_400K;
  APP_ERROR_CHECK(nrf_drv_twi_init(&m_twi, &twi_config, NULL, NULL));
  nrf_drv_twi_enable(&m_twi);
}

int32_t lsm_protocol_read(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  //NRF_LOG_INFO("i2c read %u on %02x", len, reg_addr);
  uint8_t writeId = twi_chip_id >> 1;
  uint8_t readId = twi_chip_id >> 1;
  APP_ERROR_CHECK(nrf_drv_twi_tx(&m_twi, writeId, &reg_addr, 1, true));
  APP_ERROR_CHECK(nrf_drv_twi_rx(&m_twi, readId, data, len));
  return 0;
}
int32_t lsm_protocol_write(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  //NRF_LOG_INFO("i2c write %u on %02x", len, reg_addr);
  uint8_t tx_ptr[len + 1];
  tx_ptr[0] = reg_addr;
  memcpy(tx_ptr + 1, data, len);
  uint8_t writeId = twi_chip_id >> 1;
  APP_ERROR_CHECK(nrf_drv_twi_tx(&m_twi, writeId, tx_ptr, len + 1, false));
  return 0;
}