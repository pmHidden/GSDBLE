#include "lsm_get_data.h"
#include "nrf_drv_gpiote.h"
#include "nrf_log.h"

/**
implementation of lsm_get_data.h. uses fifo for data retrieval

hookup on 'steval-mk178v2' -> 'nrf dk52':
int1 -> P0.31
*/
#define IMU_PIN_INT1 31
static volatile uint64_t interrupts = 0u;

static void interrupt_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  //NRF_LOG_INFO("interrupt!");
  interrupts++;
}

void lsm_get_data_init(lsm6dsl_ctx_t *dev_ctx) {
  // config gpiote
  if (!nrf_drv_gpiote_is_init())
    APP_ERROR_CHECK(nrf_drv_gpiote_init());
  nrf_drv_gpiote_in_config_t in_config = NRFX_GPIOTE_RAW_CONFIG_IN_SENSE_LOTOHI(true);
  in_config.pull = NRF_GPIO_PIN_NOPULL;
  APP_ERROR_CHECK(nrf_drv_gpiote_in_init(IMU_PIN_INT1, &in_config, interrupt_handler));
  nrf_drv_gpiote_in_event_enable(IMU_PIN_INT1, true);

  // config sensor
  lsm6dsl_int1_route_t intConfig;
  memset(&intConfig, 0, sizeof(lsm6dsl_int1_route_t));
  intConfig.int1_full_flag = 1;
  LSM_ERROR_CHECK(lsm6dsl_pin_int1_route_set(dev_ctx, intConfig));

  LSM_ERROR_CHECK(lsm6dsl_fifo_write_trigger_set(dev_ctx, LSM6DSL_TRG_XL_GY_DRDY));

  LSM_ERROR_CHECK(lsm6dsl_fifo_mode_set(dev_ctx, LSM6DSL_STREAM_MODE));
  LSM_ERROR_CHECK(lsm6dsl_fifo_pedo_and_timestamp_batch_set(dev_ctx, PROPERTY_ENABLE));
  LSM_ERROR_CHECK(lsm6dsl_fifo_xl_batch_set(dev_ctx, LSM6DSL_FIFO_XL_NO_DEC));
  LSM_ERROR_CHECK(lsm6dsl_fifo_gy_batch_set(dev_ctx, LSM6DSL_FIFO_GY_NO_DEC));

  lsm6dsl_reg_t timer;
  LSM_ERROR_CHECK(lsm6dsl_read_reg(dev_ctx, LSM6DSL_CTRL10_C, &timer.byte, 1));
  timer.ctrl10_c.timer_en = 0x1;
  LSM_ERROR_CHECK(lsm6dsl_write_reg(dev_ctx, LSM6DSL_CTRL10_C, &timer.byte, 1));

  lsm6dsl_reg_t interrupt_latch;
  LSM_ERROR_CHECK(lsm6dsl_read_reg(dev_ctx, LSM6DSL_DRDY_PULSE_CFG_G, &interrupt_latch.byte, 1));
  interrupt_latch.drdy_pulse_cfg_g.drdy_pulsed = PROPERTY_ENABLE;
  LSM_ERROR_CHECK(lsm6dsl_write_reg(dev_ctx, LSM6DSL_DRDY_PULSE_CFG_G, &interrupt_latch.byte, 1));

  LSM_ERROR_CHECK(lsm6dsl_timestamp_res_set(dev_ctx, LSM6DSL_LSB_25us));

  uint8_t resetTime[3];
  memset(resetTime, 0, 3 * sizeof(uint8_t));
  LSM_ERROR_CHECK(lsm6dsl_write_reg(dev_ctx, LSM6DSL_TIMESTAMP0_REG, resetTime, 3));

  LSM_ERROR_CHECK(lsm6dsl_fifo_dataset_4_batch_set(dev_ctx, LSM6DSL_FIFO_DS4_NO_DEC));
}

void lsm_get_data_stop(lsm6dsl_ctx_t *dev_ctx) {
  //LSM_ERROR_CHECK(lsm6dsl_fifo_data_rate_set(dev_ctx, LSM6DSL_FIFO_DISABLE));
  //LSM_ERROR_CHECK(lsm6dsl_fifo_xl_batch_set(dev_ctx, LSM6DSL_FIFO_XL_DISABLE));
  //LSM_ERROR_CHECK(lsm6dsl_fifo_gy_batch_set(dev_ctx, LSM6DSL_FIFO_GY_DISABLE));
  LSM_ERROR_CHECK(lsm6dsl_fifo_mode_set(dev_ctx, LSM6DSL_BYPASS_MODE));
}

void lsm_get_data_restart(lsm6dsl_ctx_t *dev_ctx) {
  LSM_ERROR_CHECK(lsm6dsl_fifo_mode_set(dev_ctx, LSM6DSL_STREAM_MODE));
}

void lsm_get_data_speed_set(lsm6dsl_ctx_t *dev_ctx, imu_speed_t speed) {
  LSM_ERROR_CHECK(lsm6dsl_fifo_data_rate_set(dev_ctx, speed + 2));
}

// ===========================================================================

void lsm_get_data_loop(lsm6dsl_ctx_t *dev_ctx, bool (*send_data)(imu_data)) {
  //NRF_LOG_INFO("new loop. interrupts: %u", interrupts);

  while (true) {
#define DATAS_PER_PACKET 3u                                                          // we have 2 sensor data and time data.
#define BYTES_PER_DATA 6u                                                            // every data is 6 byte
#define PACKETS_PER_TRANSACTION ((0xFFu - 1u) / (BYTES_PER_DATA * DATAS_PER_PACKET)) // each transaction should have nearly 255 byte. one byte is reserved for address. we only want complete packets

    axis1bit16_t available_sensor_data;
    memset(&available_sensor_data, 0, sizeof(axis1bit16_t));
    LSM_ERROR_CHECK(lsm6dsl_read_reg(dev_ctx, LSM6DSL_FIFO_STATUS1, available_sensor_data.u8bit, 2)); // actually read LSM6DSL_FIFO_STATUS1 and LSM6DSL_FIFO_STATUS2, but this combined is faster since we dont need to transmit the address two times
    available_sensor_data.u8bit[0] &= 0x07u;                                                          // only the upper 11 bits are valid  from the register
    available_sensor_data.i16bit /= BYTES_PER_DATA;                                                   // one sensor has 3 axes with 2 byte per axis (also round down to get full packets)
    if (available_sensor_data.i16bit < PACKETS_PER_TRANSACTION)
      break;

#define DATAS_PER_TRANSACTION (PACKETS_PER_TRANSACTION * DATAS_PER_PACKET)
    axis3bit16_t theData[DATAS_PER_TRANSACTION];
    memset(theData, 0, DATAS_PER_TRANSACTION * sizeof(axis3bit16_t));
    LSM_ERROR_CHECK(lsm6dsl_fifo_raw_data_get(dev_ctx, theData->u8bit, BYTES_PER_DATA * DATAS_PER_TRANSACTION));

    for (uint16_t i = 0; i < PACKETS_PER_TRANSACTION; i++) {
      imu_data data;
      data.gyro_x = theData[i * DATAS_PER_PACKET].i16bit[0];
      data.gyro_y = theData[i * DATAS_PER_PACKET].i16bit[1];
      data.gyro_z = theData[i * DATAS_PER_PACKET].i16bit[2];
      data.accel_x = theData[i * DATAS_PER_PACKET + 1].i16bit[0];
      data.accel_y = theData[i * DATAS_PER_PACKET + 1].i16bit[1];
      data.accel_z = theData[i * DATAS_PER_PACKET + 1].i16bit[2];
      memset(&data.time, 0, sizeof(uint32_t));
      data.time = (uint32_t)(theData[i * DATAS_PER_PACKET + 2].u8bit[1] << 8 | theData[i * DATAS_PER_PACKET + 2].u8bit[0]);
      if (!send_data(data)) {
        //NRF_LOG_INFO("%u packets dropped", PACKETS_PER_TRANSACTION - i);
        //break;
      }
    }
  }
}