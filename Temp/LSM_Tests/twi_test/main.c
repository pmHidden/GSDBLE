#include "app_error.h"
#include "app_util_platform.h"
#include "boards.h"
#include "nrf_drv_twi.h"
#include <stdio.h>

#include "nrf_delay.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"

#include "lsm6dsl_reg.h"
#include <string.h>

/**
hookup on 'steval-mk178v2' -> 'nrf dk52':
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
static uint8_t twi_chip_id = LSM6DSL_I2C_ADD_L;

ret_code_t twi_init(void) {
  ret_code_t err_code;

  nrf_drv_twi_config_t twi_config = NRF_DRV_TWI_DEFAULT_CONFIG;
  twi_config.scl = ARDUINO_SCL_PIN;
  twi_config.sda = ARDUINO_SDA_PIN;
  twi_config.frequency = NRF_DRV_TWI_FREQ_100K;

  err_code = nrf_drv_twi_init(&m_twi, &twi_config, NULL, NULL);
  if (err_code == NRF_SUCCESS) {
    nrf_drv_twi_enable(&m_twi);
    return NRF_SUCCESS;
  } else
    return err_code;
}

int32_t user_spi_read(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  //NRF_LOG_INFO("i2c read %hu", len);

  ret_code_t err_code;
  uint8_t writeId = twi_chip_id >> 1;
  uint8_t readId = twi_chip_id >> 1;
  err_code = nrf_drv_twi_tx(&m_twi, writeId, &reg_addr, 1, true);
  APP_ERROR_CHECK(err_code);

  err_code = nrf_drv_twi_rx(&m_twi, readId, data, len);
  APP_ERROR_CHECK(err_code);

  return 0;
}

int32_t user_spi_write(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  // NRF_LOG_INFO("i2c write %hu", len);

  uint8_t tx_ptr[len + 1];
  tx_ptr[0] = reg_addr;
  memcpy(tx_ptr + 1, data, len);
  ret_code_t err_code;
  uint8_t writeId = twi_chip_id >> 1;
  err_code = nrf_drv_twi_tx(&m_twi, writeId, &tx_ptr, len + 1, false);
  APP_ERROR_CHECK(err_code);

  return 0;
}

void example_main(void) {
  lsm6dsl_ctx_t dev_ctx;
  dev_ctx.write_reg = user_spi_write;
  dev_ctx.read_reg = user_spi_read;

  uint8_t whoamI = 0;
  lsm6dsl_device_id_get(&dev_ctx, &whoamI);
  if (whoamI != LSM6DSL_ID)
    while (1)
      ; /*manage here device not found */

  lsm6dsl_reset_set(&dev_ctx, PROPERTY_ENABLE);
  uint8_t rst;
  do {
    lsm6dsl_reset_get(&dev_ctx, &rst);
  } while (rst);

  /*
   *  Enable Block Data Update
   */
  lsm6dsl_block_data_update_set(&dev_ctx, PROPERTY_ENABLE);
  /*
   * Set Output Data Rate
   */
  lsm6dsl_xl_data_rate_set(&dev_ctx, LSM6DSL_XL_ODR_208Hz);
  lsm6dsl_gy_data_rate_set(&dev_ctx, LSM6DSL_GY_ODR_208Hz);
  /*
   * Set full scale
   */
  lsm6dsl_xl_full_scale_set(&dev_ctx, LSM6DSL_2g);
  lsm6dsl_gy_full_scale_set(&dev_ctx, LSM6DSL_2000dps);

  /*
   * Configure filtering chain(No aux interface)
   */
  /* Accelerometer - analog filter */
  lsm6dsl_xl_filter_analog_set(&dev_ctx, LSM6DSL_XL_ANA_BW_400Hz);

  /* Accelerometer - LPF1 path ( LPF2 not used )*/
  //lsm6dsl_xl_lp1_bandwidth_set(&dev_ctx, LSM6DSL_XL_LP1_ODR_DIV_4);

  /* Accelerometer - LPF1 + LPF2 path */
  lsm6dsl_xl_lp2_bandwidth_set(&dev_ctx, LSM6DSL_XL_LOW_LAT_LP_ODR_DIV_9);

  /* Gyroscope - filtering chain */
  lsm6dsl_gy_band_pass_set(&dev_ctx, LSM6DSL_HP_260mHz_LP1_STRONG);

  static axis3bit16_t data_raw_acceleration;
  static axis3bit16_t data_raw_angular_rate;
  static axis1bit16_t data_raw_temperature;

  while (1) {
    lsm6dsl_reg_t reg;
    lsm6dsl_status_reg_get(&dev_ctx, &reg.status_reg);

    if (reg.status_reg.xlda) {
      memset(data_raw_acceleration.u8bit, 0x00, sizeof(data_raw_acceleration));
      lsm6dsl_acceleration_raw_get(&dev_ctx, data_raw_acceleration.u8bit);
      NRF_LOG_INFO("Acceleration [mg]:%d, %d, %d", data_raw_acceleration.i16bit[0], data_raw_acceleration.i16bit[1], data_raw_acceleration.i16bit[2]);
    }
    if (reg.status_reg.gda) {
      memset(data_raw_angular_rate.u8bit, 0x00, sizeof(data_raw_angular_rate));
      lsm6dsl_angular_rate_raw_get(&dev_ctx, data_raw_angular_rate.u8bit);
      NRF_LOG_INFO("Angular rate [mdps]:%d, %d, %d", data_raw_angular_rate.i16bit[0], data_raw_angular_rate.i16bit[1], data_raw_angular_rate.i16bit[2]);
    }
  }
}

int main(void) {

  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  APP_ERROR_CHECK(twi_init());

  NRF_LOG_INFO("TWI scanner started.");
  NRF_LOG_FLUSH();
  example_main();
}