#include "app_error.h"
#include "imu.h"
#include "lsm6dsl_reg.h"
#include "lsm_get_data.h"
#include "lsm_protocol.h"
#include "nrf_log.h"
/**
implementation of imu.h. uses lsm3dsl as imu

hookup on 'steval-mk178v2' -> 'nrf dk52':
vdd -> vdd
vddio -> vdd
gnd -> gnd
*/
static lsm6dsl_ctx_t dev_ctx;
static bool (*send_data)(imu_data_t);
static imu_speed_t cur_speed; 
static uint16_t cur_buffer_clear_interval;
volatile static bool isPause;

void imu_init(bool (*send_data_p)(imu_data_t), uint16_t buffer_size) {
  isPause = false;
  send_data = send_data_p;
  lsm_protocol_init();
  dev_ctx.write_reg = lsm_protocol_write;
  dev_ctx.read_reg = lsm_protocol_read;

  uint8_t whoamI = 0;
  LSM_ERROR_CHECK(lsm6dsl_device_id_get(&dev_ctx, &whoamI));
  APP_ERROR_CHECK_BOOL(whoamI == LSM6DSL_ID);
  LSM_ERROR_CHECK(lsm6dsl_reset_set(&dev_ctx, PROPERTY_ENABLE));
  uint8_t rst;
  do {
    LSM_ERROR_CHECK(lsm6dsl_reset_get(&dev_ctx, &rst));
  } while (rst);

  LSM_ERROR_CHECK(lsm6dsl_block_data_update_set(&dev_ctx, PROPERTY_ENABLE));
  LSM_ERROR_CHECK(lsm6dsl_xl_full_scale_set(&dev_ctx, LSM6DSL_2g));
  LSM_ERROR_CHECK(lsm6dsl_xl_filter_analog_set(&dev_ctx, LSM6DSL_XL_ANA_BW_400Hz));
  LSM_ERROR_CHECK(lsm6dsl_xl_lp2_bandwidth_set(&dev_ctx, LSM6DSL_XL_LOW_LAT_LP_ODR_DIV_9));
  LSM_ERROR_CHECK(lsm6dsl_gy_full_scale_set(&dev_ctx, LSM6DSL_2000dps));
  LSM_ERROR_CHECK(lsm6dsl_gy_band_pass_set(&dev_ctx, LSM6DSL_HP_260mHz_LP1_STRONG));

  lsm_get_data_init(&dev_ctx, buffer_size);
  imu_stop(true);
}

void imu_on_new_interval(uint16_t buffer_clear_interval) {
  imu_stop(false);
  cur_buffer_clear_interval = buffer_clear_interval;
  cur_speed = lsm_get_data_speed_set(&dev_ctx, cur_speed, buffer_clear_interval);
  imu_restart();
}

void imu_speed_set(imu_speed_t speed) {
  speed = MIN(speed, IMU_ODR_1600Hz);
  imu_stop(false);
  cur_speed = lsm_get_data_speed_set(&dev_ctx, speed, cur_buffer_clear_interval);
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
    cur_speed = lsm_get_data_speed_set(&dev_ctx, cur_speed, cur_buffer_clear_interval);
  }
  lsm_get_data_stop(&dev_ctx);
  LSM_ERROR_CHECK(lsm6dsl_xl_data_rate_set(&dev_ctx, LSM6DSL_XL_ODR_OFF));
  LSM_ERROR_CHECK(lsm6dsl_gy_data_rate_set(&dev_ctx, LSM6DSL_GY_ODR_OFF));
}

void imu_restart(void) {
  isPause = false;
  LSM_ERROR_CHECK(lsm6dsl_xl_data_rate_set(&dev_ctx, cur_speed + 2));
  LSM_ERROR_CHECK(lsm6dsl_gy_data_rate_set(&dev_ctx, cur_speed + 2));
  lsm_get_data_restart(&dev_ctx, cur_speed);
}

void imu_loop(void) {
  if (!isPause)
    lsm_get_data_loop(&dev_ctx, send_data);
    else NRF_LOG_INFO("paused");
}