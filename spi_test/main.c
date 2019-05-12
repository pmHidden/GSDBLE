#include "app_error.h"
#include "app_util_platform.h"
#include "bmi160.h"
#include "boards.h"
#include "nrf_delay.h"
#include "nrf_drv_gpiote.h"
#include "nrf_drv_spi.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"
#include <string.h>

/**
hookup on 'bmi160 shuttle board' -> 'nrf dk52':
vdd -> vdd
vddio -> vdd
gnd -> gnd
miso -> P0.30
mosi -> P0.29
sck -> P0.27
cs -> P0.31
int1 -> P0.28
rest nc
*/
#define SPI_INSTANCE 0
static const nrf_drv_spi_t spi = NRF_DRV_SPI_INSTANCE(SPI_INSTANCE);
#define INT1_PIN 28
struct bmi160_dev bmi;
volatile bool newDataAvailable;

void spi_init() {
  nrf_drv_spi_config_t spi_config = NRF_DRV_SPI_DEFAULT_CONFIG;
  spi_config.ss_pin = 31;
  spi_config.miso_pin = 30;
  spi_config.mosi_pin = 29;
  spi_config.sck_pin = 27;
  spi_config.frequency = NRF_DRV_SPI_FREQ_8M;
  APP_ERROR_CHECK(nrf_drv_spi_init(&spi, &spi_config, NULL, NULL));
}

int8_t user_spi_read(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  NRF_LOG_INFO("spi read %hu", len);
  NRF_LOG_FLUSH();

  uint8_t bigData[len + 1];

  ret_code_t err_code;
  err_code = nrf_drv_spi_transfer(&spi, &reg_addr, 1, bigData, len + 1);
  APP_ERROR_CHECK(err_code);

  memcpy(data, bigData + 1, len * sizeof(uint8_t));

  return BMI160_OK;
}

int8_t user_spi_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  NRF_LOG_INFO("spi write %hu", len);
  NRF_LOG_FLUSH();

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

struct bmi160_dev sensor_init() {
  struct bmi160_dev sensor;

  sensor.id = 0;
  sensor.interface = BMI160_SPI_INTF;
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
  sensor.accel_cfg.odr = BMI160_ACCEL_ODR_200HZ;
  sensor.accel_cfg.range = BMI160_ACCEL_RANGE_2G;
  sensor.accel_cfg.bw = BMI160_ACCEL_BW_NORMAL_AVG4;
  sensor.accel_cfg.power = BMI160_ACCEL_LOWPOWER_MODE;

  sensor.gyro_cfg.odr = BMI160_GYRO_ODR_200HZ;
  sensor.gyro_cfg.range = BMI160_GYRO_RANGE_2000_DPS;
  sensor.gyro_cfg.bw = BMI160_GYRO_BW_NORMAL_MODE;
  sensor.gyro_cfg.power = BMI160_GYRO_NORMAL_MODE;

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

void sensor_config_interrupt(struct bmi160_dev sensor) {
  struct bmi160_int_pin_settg pin_config;
  pin_config.output_en = BMI160_ENABLE;
  pin_config.output_mode = BMI160_DISABLE;
  pin_config.output_type = BMI160_DISABLE;
  pin_config.edge_ctrl = BMI160_ENABLE;
  pin_config.input_en = BMI160_DISABLE;
  pin_config.latch_dur = BMI160_LATCH_DUR_NONE;

  struct bmi160_int_settg int_config;
  memset(&int_config, 0, sizeof(int_config));
  int_config.int_channel = BMI160_INT_CHANNEL_1;
  int_config.int_type = BMI160_ACC_GYRO_DATA_RDY_INT;
  int_config.int_pin_settg = pin_config;

  int8_t result = bmi160_set_int_config(&int_config, &sensor);
  if (BMI160_OK != result) {
    NRF_LOG_INFO("sensor_set_interrupt not ok: %hhd", result);
    NRF_LOG_FLUSH();
    APP_ERROR_CHECK_BOOL(false);
  }
}

void in_pin_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  newDataAvailable = true;
}

static void gpio_init(void) {
  ret_code_t err_code;

  err_code = nrf_drv_gpiote_init();
  APP_ERROR_CHECK(err_code);

  nrf_drv_gpiote_in_config_t in_config = NRFX_GPIOTE_RAW_CONFIG_IN_SENSE_HITOLO(true);
  in_config.pull = NRF_GPIO_PIN_NOPULL;

  err_code = nrf_drv_gpiote_in_init(INT1_PIN, &in_config, in_pin_handler);
  APP_ERROR_CHECK(err_code);

  nrf_drv_gpiote_in_event_enable(INT1_PIN, true);
}

int main(void) {
  newDataAvailable = false;
  bsp_board_init(BSP_INIT_LEDS);

  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  spi_init();

  NRF_LOG_INFO("SPI example started.");
  NRF_LOG_FLUSH();
  bmi = sensor_init();
  sensor_config(bmi);
  sensor_config_interrupt(bmi);
  gpio_init();

  // main
  while (true) {
    if (NRF_LOG_PROCESS() == false) {
      __SEV();
      __WFE();
      __WFE();
    }
    if (newDataAvailable) {
      sensor_read(bmi);
      newDataAvailable = false;
    }
  }
}