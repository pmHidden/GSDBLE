#include "bmi_protocol.h"
#include "nrf_drv_spi.h"
#include "nrf_log.h"

/**
implementation of lsm_protocol.h. uses spi as protocol

hookup on 'steval-mk178v2' -> 'nrf dk52':
 sdo -> P0.11 miso
 sda -> P0.12 mosi
 scl -> P0.14 sck
 cs -> P0.16 ss
*/
#define SPI_INSTANCE 0
static const nrf_drv_spi_t spi = NRF_DRV_SPI_INSTANCE(SPI_INSTANCE);
#define PROTOCOL_SPEED NRF_DRV_SPI_FREQ_4M

void bmi_protocol_init(struct bmi160_dev *dev_ctx) {
  nrf_drv_spi_config_t spi_config = NRF_DRV_SPI_DEFAULT_CONFIG;
  spi_config.miso_pin = 11;
  spi_config.mosi_pin = 12;
  spi_config.sck_pin = 14;
  spi_config.ss_pin = 16;
  spi_config.frequency = PROTOCOL_SPEED;
  APP_ERROR_CHECK(nrf_drv_spi_init(&spi, &spi_config, NULL, NULL));

  dev_ctx->id = 0;
  dev_ctx->interface = BMI160_SPI_INTF;
}

int8_t bmi_protocol_read(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  if (len > 0xFEu)
    NRF_LOG_INFO("SPI-READ: YOU ARE READING TOO MUCH DATA! %u", len);

  uint8_t bigData[len + 1];

  ret_code_t err_code;
  err_code = nrf_drv_spi_transfer(&spi, &reg_addr, 1, bigData, len + 1);
  APP_ERROR_CHECK(err_code);

  memcpy(data, bigData + 1, len * sizeof(uint8_t));

  return BMI160_OK;
}
int8_t bmi_protocol_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  if (len > 0xFEu)
    NRF_LOG_INFO("SPI-WRITE: YOU ARE WRITING TOO MUCH DATA! %u", len);
/*
  uint8_t bigData[len + 1];
  bigData[0] = reg_addr;
  memcpy(bigData + 1, data, len);

  ret_code_t err_code;
  err_code = nrf_drv_spi_transfer(&spi, bigData, len + 1, NULL, 0);
  APP_ERROR_CHECK(err_code);

  return BMI160_OK;
*/
    uint16_t index = 0;
  while (index < len) {
    uint8_t p_tx_buffer[2];
    p_tx_buffer[0] = reg_addr;
    p_tx_buffer[1] = data[index];

    ret_code_t err_code;
    err_code = nrf_drv_spi_transfer(&spi, p_tx_buffer, 2, NULL, 0);
    APP_ERROR_CHECK(err_code);

    index++;
  }
  return BMI160_OK;
}