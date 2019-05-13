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
  //NRF_LOG_INFO("spi read %hu", len);

  uint8_t bigData[len + 1];

  ret_code_t err_code;
  err_code = nrf_drv_spi_transfer(&spi, &reg_addr, 1, bigData, len + 1);
  APP_ERROR_CHECK(err_code);

  memcpy(data, bigData + 1, len * sizeof(uint8_t));

  return BMI160_OK;
}

int8_t user_spi_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  // NRF_LOG_INFO("spi write %hu", len);

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
  }
  return sensor;
}

void sensor_config(struct bmi160_dev sensor) {
  sensor.accel_cfg.odr = BMI160_ACCEL_ODR_200HZ;
  sensor.accel_cfg.range = BMI160_ACCEL_RANGE_2G;
  sensor.accel_cfg.bw = BMI160_ACCEL_BW_NORMAL_AVG4;
  sensor.accel_cfg.power = BMI160_ACCEL_NORMAL_MODE;

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
  memset(&int_config, 0, sizeof(struct bmi160_int_settg));
  int_config.int_channel = BMI160_INT_CHANNEL_1;
  int_config.int_pin_settg = pin_config;
  int_config.int_type = BMI160_ACC_GYRO_FIFO_WATERMARK_INT;
  int_config.fifo_WTM_int_en = BMI160_ENABLE;

  //int_config.int_type = BMI160_ACC_GYRO_FIFO_FULL_INT;
  //int_config.fifo_full_int_en = BMI160_ENABLE;

  int8_t result = bmi160_set_int_config(&int_config, &sensor);
  if (BMI160_OK != result) {
    NRF_LOG_INFO("sensor_set_interrupt not ok: %hhd", result);
    NRF_LOG_FLUSH();
    APP_ERROR_CHECK_BOOL(false);
  }
}

void in_pin_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  NRF_LOG_INFO("Wakeup Event");
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
  bsp_board_init(BSP_INIT_LEDS);

  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  spi_init();

  NRF_LOG_INFO("SPI example started.");
  NRF_LOG_FLUSH();
  bmi = sensor_init();
  sensor_config(bmi);

  APP_ERROR_CHECK_BOOL(BMI160_OK == bmi160_set_fifo_wm(200u, &bmi));
  APP_ERROR_CHECK_BOOL(BMI160_OK == bmi160_set_fifo_down(BMI160_ACCEL_FIFO_DOWN_ONE | BMI160_GYRO_FIFO_DOWN_ONE | BMI160_ACCEL_FIFO_FILT_EN | BMI160_GYRO_FIFO_FILT_EN, &bmi));
  APP_ERROR_CHECK_BOOL(BMI160_OK == bmi160_set_fifo_config(BMI160_FIFO_GYRO | BMI160_FIFO_ACCEL | BMI160_FIFO_HEADER | BMI160_FIFO_TIME, BMI160_ENABLE, &bmi));

  sensor_config_interrupt(bmi);
  gpio_init();

  // main
  while (true) {
    NRF_LOG_INFO("Read");
    uint8_t fifo_buff[1024];
    memset(fifo_buff, 0, sizeof(uint8_t)*1024);
    struct bmi160_fifo_frame fifo_frame;
    memset(&fifo_frame, 0, sizeof(struct bmi160_fifo_frame));
    fifo_frame.data = fifo_buff;
    fifo_frame.length = 1024u;
    bmi.fifo = &fifo_frame;

    NRF_LOG_INFO("USER REQUESTED FIFO LENGTH : %u", bmi.fifo->length);
    int8_t rslt = 0;
    rslt = bmi160_get_fifo_data(&bmi);

    if (rslt == BMI160_OK) {
      NRF_LOG_INFO("AVAILABLE FIFO LENGTH : %u", bmi.fifo->length);

      struct bmi160_sensor_data gyro_data[170];
      uint8_t gyro_frames_req = 170u;
      rslt = bmi160_extract_gyro(gyro_data, &gyro_frames_req, &bmi);
      NRF_LOG_INFO("GYRO DATA : %d", gyro_frames_req);

      if (rslt == BMI160_OK) {
        NRF_LOG_INFO("SENSOR TIME DATA : %d", bmi.fifo->sensor_time);
        NRF_LOG_INFO("SKIPPED : %d.", bmi.fifo->skipped_frame_count);
      } else {
        NRF_LOG_INFO("Gyro data extraction failed");
      }

      struct bmi160_sensor_data accel_data[10];
      uint8_t accel_frames_req = 10u;
      rslt = bmi160_extract_accel(accel_data, &accel_frames_req, &bmi);
      NRF_LOG_INFO("ACCEL DATA : %d", accel_frames_req);

      if (rslt == BMI160_OK) {
        NRF_LOG_INFO("SENSOR TIME DATA : %d", bmi.fifo->sensor_time);
        NRF_LOG_INFO("SKIPPED : %d.", bmi.fifo->skipped_frame_count);
      } else {
        NRF_LOG_INFO("Gyro data extraction failed");
      }

    } else {
        NRF_LOG_INFO("Fifo data extraction failed");
      }
    NRF_LOG_FLUSH();
    __SEV();
    __WFE();
    union bmi160_int_status status;
    APP_ERROR_CHECK_BOOL(BMI160_OK == bmi160_get_int_status(BMI160_INT_STATUS_1, &status, &bmi));
    if (status.bit.ffull == 0u) {
      NRF_LOG_INFO("Sleep");
      __WFE();
    }
  }
}