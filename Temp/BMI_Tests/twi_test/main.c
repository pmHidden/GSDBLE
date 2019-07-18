#include "app_error.h"
#include "app_util_platform.h"
#include "boards.h"
#include "nrf_drv_twi.h"
#include <stdio.h>

#include "nrf_delay.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"

#include "bmi160.h"

/**
hookup on 'bmi160 shuttle board' -> 'nrf dk52':
vdd -> vdd
vddio -> vdd
gnd -> gnd
miso -> gnd
mosi -> P0.26
sck -> P0.27
rest nc
*/

#define TWI_INSTANCE_ID 0
static const nrf_drv_twi_t m_twi = NRF_DRV_TWI_INSTANCE(TWI_INSTANCE_ID);

void twi_init(void) {
  ret_code_t err_code;

  const nrf_drv_twi_config_t twi_config = {
      .scl = 14,
      .sda = 12,
      .frequency = NRF_DRV_TWI_FREQ_100K,
      .interrupt_priority = APP_IRQ_PRIORITY_HIGH,
      .clear_bus_init = false};

  err_code = nrf_drv_twi_init(&m_twi, &twi_config, NULL, NULL);
  APP_ERROR_CHECK(err_code);

  nrf_drv_twi_enable(&m_twi);
}

int8_t user_spi_read(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  NRF_LOG_INFO("i2c read %hu", len);
  NRF_LOG_FLUSH();

  ret_code_t err_code;
  err_code = nrf_drv_twi_tx(&m_twi, id, &reg_addr, 1, true);
  APP_ERROR_CHECK(err_code);

  //NRF_LOG_INFO("before: %hhd is %hhd", reg_addr, data[0]);
  err_code = nrf_drv_twi_rx(&m_twi, id, data, len);
  APP_ERROR_CHECK(err_code);
  //NRF_LOG_INFO("after: %hhd is %hhd", reg_addr, data[0]);

  return BMI160_OK;
}

int8_t user_spi_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  NRF_LOG_INFO("i2c write %hu", len);
  NRF_LOG_FLUSH();

  uint8_t tx_ptr[len + 1];
  tx_ptr[0] = reg_addr;
  memcpy(tx_ptr + 1, data, len);
  ret_code_t err_code;
  err_code = nrf_drv_twi_tx(&m_twi, id, &tx_ptr, len + 1, false);
  APP_ERROR_CHECK(err_code);

  return BMI160_OK;
}

struct bmi160_dev sensor_init() {
  struct bmi160_dev sensor;

  sensor.id = BMI160_I2C_ADDR;
  sensor.interface = BMI160_I2C_INTF;
  sensor.read = user_spi_read;
  sensor.write = user_spi_write;
  sensor.delay_ms = nrf_delay_ms;

  int8_t rslt = BMI160_OK;
  rslt = bmi160_init(&sensor);
  if (BMI160_OK != rslt) {
    NRF_LOG_INFO("sensor_init not ok: %hhd", rslt);
    NRF_LOG_FLUSH();
    APP_ERROR_CHECK_BOOL(false);
  } else {
    NRF_LOG_INFO("sensor_init ok");
    NRF_LOG_FLUSH();
  }
  return sensor;
}

void sensor_config(struct bmi160_dev sensor) {
  sensor.accel_cfg.odr = BMI160_ACCEL_ODR_100HZ;
  sensor.accel_cfg.range = BMI160_ACCEL_RANGE_2G;
  sensor.accel_cfg.bw = BMI160_ACCEL_BW_NORMAL_AVG4;
  sensor.accel_cfg.power = BMI160_ACCEL_LOWPOWER_MODE;

  sensor.gyro_cfg.odr = BMI160_GYRO_ODR_100HZ;
  sensor.gyro_cfg.range = BMI160_GYRO_RANGE_2000_DPS;
  sensor.gyro_cfg.bw = BMI160_GYRO_BW_NORMAL_MODE;
  sensor.gyro_cfg.power = BMI160_GYRO_FASTSTARTUP_MODE;

  /* Set the sensor configuration */
  int8_t rslt = BMI160_OK;
  rslt = bmi160_set_sens_conf(&sensor);
  if (BMI160_OK != rslt) {
    NRF_LOG_INFO("sensor_config not ok: %hhd", rslt);
    NRF_LOG_FLUSH();
    APP_ERROR_CHECK_BOOL(false);
  } else {
    NRF_LOG_INFO("sensor_config ok");
    NRF_LOG_FLUSH();
  }
}

void sensor_read(struct bmi160_dev sensor) {
  struct bmi160_sensor_data accel;
  struct bmi160_sensor_data gyro;
  int8_t result = bmi160_get_sensor_data((BMI160_ACCEL_SEL | BMI160_GYRO_SEL | BMI160_TIME_SEL), &accel, &gyro, &sensor);
  if (BMI160_OK != result) {
    NRF_LOG_INFO("sensor_read not ok: %hhd", result);
    NRF_LOG_FLUSH();
    APP_ERROR_CHECK_BOOL(false);
  } else {
    NRF_LOG_INFO("sensor_read ok:");
    NRF_LOG_INFO(" gyro %d %d %d %u", gyro.x, gyro.y, gyro.z, gyro.sensortime);
    NRF_LOG_INFO(" accel %d %d %d %u", accel.x, accel.y, accel.z, accel.sensortime);
    NRF_LOG_FLUSH();
  }
}

int main(void) {

  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  twi_init();

  NRF_LOG_INFO("TWI scanner started.");
  NRF_LOG_FLUSH();

  struct bmi160_dev bmi = sensor_init();
  sensor_config(bmi);

  while (true) {
    sensor_read(bmi);
    nrf_delay_ms(12);
  }
}