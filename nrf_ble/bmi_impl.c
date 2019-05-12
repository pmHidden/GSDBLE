#define USE_BMI
#ifdef USE_BMI

#include "chara_data.h"
#include "imu.h"

#include "app_error.h"
#include "bmi160.h"
#include "nrf_delay.h"
#include "nrf_drv_gpiote.h"
#include "nrf_drv_spi.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_mtx.h"
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
struct bmi160_dev sensor;

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
  uint8_t bigData[len + 1];

  ret_code_t err_code;
  err_code = nrf_drv_spi_transfer(&spi, &reg_addr, 1, bigData, len + 1);
  APP_ERROR_CHECK(err_code);

  memcpy(data, bigData + 1, len * sizeof(uint8_t));

  return BMI160_OK;
}

int8_t user_spi_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
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

void sensor_init() {
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
  }
}

void sensor_config() {
  sensor.accel_cfg.odr = BMI160_ACCEL_ODR_800HZ;
  sensor.accel_cfg.range = BMI160_ACCEL_RANGE_2G;
  sensor.accel_cfg.bw = BMI160_ACCEL_BW_NORMAL_AVG4;
  sensor.accel_cfg.power = BMI160_ACCEL_NORMAL_MODE;

  sensor.gyro_cfg.odr = BMI160_GYRO_ODR_800HZ;
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
  }
}

struct dataAndResult {
  imu_data data;
  int8_t result;
};

struct dataAndResult bmi_read() {
  struct dataAndResult dar;
  struct bmi160_sensor_data accel;
  struct bmi160_sensor_data gyro;
  dar.result = bmi160_get_sensor_data((BMI160_ACCEL_SEL | BMI160_GYRO_SEL | BMI160_TIME_SEL), &accel, &gyro, &sensor);
  if (BMI160_OK == dar.result) {
    imu_data data;
    data.accel_x = accel.x;
    data.accel_y = accel.y;
    data.accel_z = accel.z;
    data.gyro_x = gyro.x;
    data.gyro_y = gyro.y;
    data.gyro_z = gyro.z;
    // data.time = accel.sensortime;
    dar.data = data;
  }
  return dar;
}

void sensor_config_interrupt() {
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

my_service_t *service;
nrf_mtx_t bmi_mutex;

void in_pin_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  if (nrf_mtx_trylock(&bmi_mutex)) {
    struct dataAndResult sensor_result = bmi_read();
    if (sensor_result.result == BMI160_OK) {
      ret_code_t err_code;
      err_code = chara_data_update(service, sensor_result.data);
      if (err_code != NRF_SUCCESS) {
        NRF_LOG_INFO("couldnt send, code: %d, is queue full: %d", err_code, err_code == NRF_ERROR_RESOURCES);
      }
    } else {
      NRF_LOG_INFO("couldnt read sensor, code: %d", sensor_result.result);
    }
    nrf_mtx_unlock(&bmi_mutex);
  } else {
    NRF_LOG_INFO("couldnt lock");
  }
}

static void gpio_init(void) {
  ret_code_t err_code;

  if (!nrf_drv_gpiote_in_init) {
    err_code = nrf_drv_gpiote_init();
    APP_ERROR_CHECK(err_code);
  }

  nrf_drv_gpiote_in_config_t in_config = NRFX_GPIOTE_RAW_CONFIG_IN_SENSE_HITOLO(true);
  in_config.pull = NRF_GPIO_PIN_NOPULL;

  err_code = nrf_drv_gpiote_in_init(INT1_PIN, &in_config, in_pin_handler);
  APP_ERROR_CHECK(err_code);

  nrf_drv_gpiote_in_event_enable(INT1_PIN, true);
}

void imu_init(my_service_t *p_cus) {
  service = p_cus;
  spi_init();

  memset(&bmi_mutex, 0, sizeof(nrf_mtx_t));
  nrf_mtx_init(&bmi_mutex);

  sensor_init();
  sensor_config();
  sensor_config_interrupt();
  gpio_init();
}
#endif