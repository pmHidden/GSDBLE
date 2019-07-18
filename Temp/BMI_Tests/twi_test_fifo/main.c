#include "app_error.h"
#include "app_util_platform.h"
#include "boards.h"
#include "nrf_drv_twi.h"
#include <stdio.h>

#include "nrf_delay.h"
#include "nrf_drv_gpiote.h"
#include "nrf_gpio.h"
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
#define INT1_PIN 8
struct bmi160_dev bmi;

ret_code_t twi_init(void) {
  ret_code_t err_code;

  nrf_drv_twi_config_t twi_config = NRF_DRV_TWI_DEFAULT_CONFIG;
  twi_config.scl = 14;
  twi_config.sda = 12;
  twi_config.frequency = NRF_DRV_TWI_FREQ_100K;

  err_code = nrf_drv_twi_init(&m_twi, &twi_config, NULL, NULL);
  if (err_code == NRF_SUCCESS) {
    nrf_drv_twi_enable(&m_twi);
    return NRF_SUCCESS;
  } else
    return err_code;
}

int8_t user_spi_read(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  //NRF_LOG_INFO("i2c read %hu", len);

  if (len != 0) {
    ret_code_t err_code;
    err_code = nrf_drv_twi_tx(&m_twi, id, &reg_addr, 1, true);
    APP_ERROR_CHECK(err_code);

    err_code = nrf_drv_twi_rx(&m_twi, id, data, len);
    APP_ERROR_CHECK(err_code);
  }

  return BMI160_OK;
}

int8_t user_spi_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  //NRF_LOG_INFO("i2c write %hu", len);

  uint8_t tx_ptr[len + 1];
  tx_ptr[0] = reg_addr;
  memcpy(tx_ptr + 1, data, len);
  ret_code_t err_code;
  err_code = nrf_drv_twi_tx(&m_twi, id, &tx_ptr, len + 1, false);
  APP_ERROR_CHECK(err_code);

  return BMI160_OK;
}

int8_t sensor_init() {
  memset(&bmi, 0, sizeof(struct bmi160_dev));

  bmi.id = BMI160_I2C_ADDR;
  bmi.interface = BMI160_I2C_INTF;
  bmi.read = user_spi_read;
  bmi.write = user_spi_write;
  bmi.delay_ms = nrf_delay_ms;

  return bmi160_init(&bmi);
}

int8_t sensor_config() {
  bmi.accel_cfg.odr = BMI160_ACCEL_ODR_100HZ;
  bmi.accel_cfg.range = BMI160_ACCEL_RANGE_2G;
  bmi.accel_cfg.bw = BMI160_ACCEL_BW_NORMAL_AVG4;
  bmi.accel_cfg.power = BMI160_ACCEL_NORMAL_MODE;

  bmi.gyro_cfg.odr = BMI160_GYRO_ODR_100HZ;
  bmi.gyro_cfg.range = BMI160_GYRO_RANGE_2000_DPS;
  bmi.gyro_cfg.bw = BMI160_GYRO_BW_NORMAL_MODE;
  bmi.gyro_cfg.power = BMI160_GYRO_NORMAL_MODE;

  return bmi160_set_sens_conf(&bmi);
}

int8_t sensor_config_interrupt() {
  struct bmi160_int_pin_settg pin_config;
  memset(&pin_config, 0, sizeof(struct bmi160_int_pin_settg));
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
  int_config.int_type = BMI160_ACC_GYRO_FIFO_FULL_INT;
  int_config.fifo_full_int_en = BMI160_ENABLE;

  return bmi160_set_int_config(&int_config, &bmi);
}

void in_pin_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  NRF_LOG_INFO("Wakeup Event");
}

ret_code_t gpio_init(void) {
  ret_code_t err_code;

  err_code = nrf_drv_gpiote_init();
  APP_ERROR_CHECK(err_code);

  nrf_drv_gpiote_in_config_t in_config = NRFX_GPIOTE_RAW_CONFIG_IN_SENSE_HITOLO(true);
  in_config.pull = NRF_GPIO_PIN_NOPULL;

  err_code = nrf_drv_gpiote_in_init(INT1_PIN, &in_config, in_pin_handler);
  if (err_code == NRF_SUCCESS) {
    nrf_drv_gpiote_in_event_enable(INT1_PIN, true);
    return NRF_SUCCESS;
  } else
    return err_code;
}

/* An example to read the Gyro data in header mode along with sensor time (if available)
 * Configure the gyro sensor as prerequisite and follow the below example to read and
 * obtain the gyro data from FIFO */
int8_t fifo_gyro_header_time_data(struct bmi160_dev *dev) {
  int8_t rslt = 0;

  /* Declare memory to store the raw FIFO buffer information */
  uint8_t fifo_buff[254];

  /* Modify the FIFO buffer instance and link to the device instance */
  struct bmi160_fifo_frame fifo_frame;
  fifo_frame.data = fifo_buff;
  fifo_frame.length = 254;
  dev->fifo = &fifo_frame;
  uint16_t index = 0;

  /* Declare instances of the sensor data structure to store the parsed FIFO data */
  struct bmi160_sensor_data gyro_data[42]; // 300 bytes / ~7bytes per frame ~ 42 data frames
  uint8_t gyro_frames_req = 42;
  uint8_t gyro_index;

  /* Configure the sensor's FIFO settings */
  rslt = bmi160_set_fifo_config(BMI160_FIFO_GYRO, BMI160_ENABLE, dev);

  if (rslt == BMI160_OK) {
    dev->delay_ms(420);
    /* Read data from the sensor's FIFO and store it the FIFO buffer,"fifo_buff" */
    NRF_LOG_INFO("USER REQUESTED FIFO LENGTH : %d", dev->fifo->length);
    rslt = bmi160_get_fifo_data(dev);

    if (rslt == BMI160_OK) {
      NRF_LOG_INFO("AVAILABLE FIFO LENGTH : %d", dev->fifo->length);

      /* Parse the FIFO data to extract gyro data from the FIFO buffer */
      NRF_LOG_INFO("REQUESTED GYRO DATA FRAMES : %d", gyro_frames_req);
      rslt = bmi160_extract_gyro(gyro_data, &gyro_frames_req, dev);

      if (rslt == BMI160_OK) {
        NRF_LOG_INFO("AVAILABLE GYRO DATA FRAMES : %d", gyro_frames_req);

        /* Print the parsed gyro data from the FIFO buffer */
        for (gyro_index = 0; gyro_index < gyro_frames_req; gyro_index++) {
          NRF_LOG_INFO("GYRO[%d] X-DATA : %d,  Y-DATA : %d,  Z-DATA : %d", gyro_index, gyro_data[gyro_index].x, gyro_data[gyro_index].y, gyro_data[gyro_index].z);
          NRF_LOG_FLUSH(); // Log buffer overflow otherwise
        }
        /* Print the special FIFO frame data like sensortime */
        NRF_LOG_INFO("SKIPPED FRAME COUNT : %d", dev->fifo->skipped_frame_count);
      } else {
        NRF_LOG_INFO("Gyro data extraction failed");
      }
    } else {
      NRF_LOG_INFO("Reading FIFO data failed");
    }
  } else {
    NRF_LOG_INFO("Setting FIFO configuration failed");
  }

  return rslt;
}

int main(void) {
  bsp_board_init(BSP_INIT_LEDS);

  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  NRF_LOG_INFO("TWI example started.");

  nrf_gpio_cfg_output(11);
  nrf_gpio_pin_clear(11);

  APP_ERROR_CHECK(twi_init());
  APP_ERROR_CHECK_BOOL(BMI160_OK == sensor_init());
  APP_ERROR_CHECK_BOOL(BMI160_OK == sensor_config());
  APP_ERROR_CHECK_BOOL(BMI160_OK == bmi160_set_fifo_down(BMI160_GYRO_FIFO_DOWN_ONE, &bmi));
  APP_ERROR_CHECK_BOOL(BMI160_OK == sensor_config_interrupt());
  APP_ERROR_CHECK(gpio_init());

  NRF_LOG_INFO("Init finished");

  struct bmi160_fifo_frame fifo_frame;
  memset(&fifo_frame, 0, sizeof(struct bmi160_fifo_frame));
  bmi.fifo = &fifo_frame;

  uint8_t fifo_buff[1024];
  bmi.fifo->data = fifo_buff;
  bmi.fifo->length = 1024u;

  APP_ERROR_CHECK_BOOL(BMI160_OK == bmi160_set_fifo_config(BMI160_FIFO_GYRO | BMI160_FIFO_HEADER, BMI160_ENABLE, &bmi));

  for (;;) {
    if (!NRF_LOG_PROCESS()) {
      __SEV();
      __WFE();
      bmi160_set_fifo_flush(&bmi);
      __WFE();
      for (;;) {
        if (!NRF_LOG_PROCESS()) {
          fifo_gyro_header_time_data(&bmi);
          __SEV();
          __WFE();
          __WFE();
        }
      }
    }
  }
}