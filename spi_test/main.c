/**
 * Copyright (c) 2015 - 2019, Nordic Semiconductor ASA
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form, except as embedded into a Nordic
 *    Semiconductor ASA integrated circuit in a product or a software update for
 *    such product, must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. Neither the name of Nordic Semiconductor ASA nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * 4. This software, with or without modification, must only be used with a
 *    Nordic Semiconductor ASA integrated circuit.
 *
 * 5. Any software provided in binary form under this license must not be reverse
 *    engineered, decompiled, modified and/or disassembled.
 *
 * THIS SOFTWARE IS PROVIDED BY NORDIC SEMICONDUCTOR ASA "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY, NONINFRINGEMENT, AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NORDIC SEMICONDUCTOR ASA OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
#include "app_error.h"
#include "app_util_platform.h"
#include "bmi160.h"
#include "boards.h"
#include "nrf_delay.h"
#include "nrf_drv_spi.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"
#include <string.h>

#define SPI_INSTANCE 0                                               /**< SPI instance index. */
static const nrf_drv_spi_t spi = NRF_DRV_SPI_INSTANCE(SPI_INSTANCE); /**< SPI instance. */

void spi_init() {
  nrf_drv_spi_config_t spi_config = NRF_DRV_SPI_DEFAULT_CONFIG;
  spi_config.ss_pin = 31;
  spi_config.miso_pin = 30;
  spi_config.mosi_pin = 29;
  spi_config.sck_pin = 28;
  spi_config.frequency = NRF_DRV_SPI_FREQ_125K;
  APP_ERROR_CHECK(nrf_drv_spi_init(&spi, &spi_config, NULL, NULL));
}

int8_t user_spi_read(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  NRF_LOG_INFO("read %hu", len);

  ret_code_t err_code;
  NRF_LOG_INFO("before: %hhd is %hhd", reg_addr, data[0]);

  uint8_t p_tx_buffer[1];
  p_tx_buffer[0] = reg_addr;

  err_code = nrf_drv_spi_transfer(&spi, p_tx_buffer, 1, data, len);
  APP_ERROR_CHECK(err_code);
  NRF_LOG_INFO("after: %hhd is %hhd", reg_addr, data[0]);

  return BMI160_OK;
}

int8_t user_spi_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  NRF_LOG_INFO("write %hu start", len);
  uint16_t index = 0;
  while (index < len) {

    uint8_t p_tx_buffer[2];
    p_tx_buffer[0] = reg_addr;
    p_tx_buffer[1] = data[index];

    NRF_LOG_INFO("%hhd is %hhd", p_tx_buffer[0], p_tx_buffer[1]);

    ret_code_t err_code;
    err_code = nrf_drv_spi_transfer(&spi, p_tx_buffer, 2, NULL, 0);

    APP_ERROR_CHECK(err_code);

    index++;
  }
  NRF_LOG_INFO("write %hu stop", len);

  return BMI160_OK;
}

void user_delay_ms(uint32_t period) {
  nrf_delay_ms(period);
}

struct bmi160_dev sensor_init() {
  struct bmi160_dev sensor;

  sensor.id = 0;
  sensor.interface = BMI160_SPI_INTF;
  sensor.read = user_spi_read;
  sensor.write = user_spi_write;
  sensor.delay_ms = user_delay_ms;

  int8_t rslt = BMI160_OK;
  rslt = bmi160_init(&sensor);
  if (BMI160_OK != rslt) {
    NRF_LOG_INFO("sensor_init not ok: %hhd", rslt);
    APP_ERROR_CHECK_BOOL(false);
  } else {
    NRF_LOG_INFO("sensor_init ok");
  }
  /* After the above function call, accel and gyro parameters in the device structure 
are set with default values, found in the datasheet of the sensor */
  return sensor;
}

int main(void) {
  bsp_board_init(BSP_INIT_LEDS);

  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  spi_init();

  NRF_LOG_INFO("SPI example started.");
  NRF_LOG_FLUSH();
  sensor_init();
}