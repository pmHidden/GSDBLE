#include "../imu.h"
#include "app_error.h"
#include "bmi160.h"
#include "bmi_get_data.h"
#include "bmi_protocol.h"
#include "nrf_delay.h"
#include "nrf_log.h"

/**
implementation of imu.h. uses lsm3dsl as imu

hookup on 'steval-mk178v2' -> 'nrf dk52':
vdd -> vdd
vddio -> vdd
gnd -> gnd
*/
static struct bmi160_dev dev_ctx;
static bool (*send_data)(imu_data_t);
static imu_speed_t cur_speed;
static uint16_t cur_buffer_clear_interval;
volatile static bool isPause;

void imu_init(bool (*send_data_p)(imu_data_t), uint16_t buffer_size) {
  isPause = false;
  send_data = send_data_p;
  // init sensor
  memset(&dev_ctx, 0, sizeof(struct bmi160_dev));
  bmi_protocol_init(&dev_ctx);
  dev_ctx.read = bmi_protocol_read;
  dev_ctx.write = bmi_protocol_write;
  dev_ctx.delay_ms = nrf_delay_ms;
  BMI_ERROR_CHECK(bmi160_init(&dev_ctx));

  // config sensor
  dev_ctx.accel_cfg.range = BMI160_ACCEL_RANGE_2G;
  dev_ctx.accel_cfg.bw = BMI160_ACCEL_BW_NORMAL_AVG4;
  dev_ctx.accel_cfg.power = BMI160_ACCEL_NORMAL_MODE;
  dev_ctx.accel_cfg.odr = BMI160_ACCEL_ODR_25HZ;

  dev_ctx.gyro_cfg.range = BMI160_GYRO_RANGE_2000_DPS;
  dev_ctx.gyro_cfg.bw = BMI160_GYRO_BW_NORMAL_MODE;
  dev_ctx.gyro_cfg.power = BMI160_GYRO_NORMAL_MODE;
  dev_ctx.gyro_cfg.odr = BMI160_GYRO_ODR_25HZ;
  BMI_ERROR_CHECK(bmi160_set_sens_conf(&dev_ctx));

  bmi_get_data_init(&dev_ctx, buffer_size);
  imu_stop(true);
}

void imu_on_new_interval(uint16_t buffer_clear_interval) {
  imu_stop(false);
  cur_buffer_clear_interval = buffer_clear_interval;
  cur_speed = bmi_get_data_speed_set(&dev_ctx, cur_speed, buffer_clear_interval);
  imu_restart();
}

void imu_speed_set(imu_speed_t speed) {
  speed = MIN(speed, IMU_ODR_1600Hz);
  imu_stop(false);
  cur_speed = bmi_get_data_speed_set(&dev_ctx, speed, cur_buffer_clear_interval);
  imu_restart();
}

imu_speed_t imu_speed_get(void) {
  return cur_speed;
}

void imu_stop(bool reset) {
  isPause = true;
  if (reset) { // slowest setting
    imu_stop(false);
    cur_speed = IMU_ODR_25Hz;
    cur_buffer_clear_interval = 3200;
    cur_speed = bmi_get_data_speed_set(&dev_ctx, cur_speed, cur_buffer_clear_interval);
  }
  bmi_get_data_stop(&dev_ctx);
  dev_ctx.gyro_cfg.power = BMI160_GYRO_SUSPEND_MODE;
  dev_ctx.accel_cfg.power = BMI160_ACCEL_SUSPEND_MODE;
  BMI_ERROR_CHECK(bmi160_set_sens_conf(&dev_ctx));
  NRF_LOG_INFO("stopped sensor");
}

void imu_restart(void) {
  isPause = false;
  dev_ctx.gyro_cfg.power = BMI160_GYRO_NORMAL_MODE;
  dev_ctx.accel_cfg.power = BMI160_ACCEL_NORMAL_MODE;
  dev_ctx.accel_cfg.odr = cur_speed + BMI160_ACCEL_ODR_25HZ;
  dev_ctx.gyro_cfg.odr = cur_speed + BMI160_GYRO_ODR_25HZ;
  BMI_ERROR_CHECK(bmi160_set_sens_conf(&dev_ctx));
  bmi_get_data_restart(&dev_ctx, cur_speed);
}

bool imu_is_paused() {
  return isPause;
}

void imu_loop(void) {
  if (!isPause)
    bmi_get_data_loop(&dev_ctx, send_data, cur_speed);
  else
    NRF_LOG_INFO("paused");
}