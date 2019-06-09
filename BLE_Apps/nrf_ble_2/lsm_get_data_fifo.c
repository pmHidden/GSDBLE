#include "lsm_get_data.h"
#include "nrf_drv_gpiote.h"
#include "nrf_log.h"

/**
implementation of lsm_get_data.h. uses fifo for data retrieval

hookup on 'steval-mk178v2' -> 'nrf dk52':
int1 -> P0.31
*/
#define IMU_PIN_INT1 31

#define DATAS_PER_PACKET 3u // we have 2 sensor data and time data.
#define BYTES_PER_DATA 6u   // every data is 6 byte

#define FIFO_MAX_FILL_BYTES (4096u - ((4096u % (BYTES_PER_DATA * DATAS_PER_PACKET))))   // = 4086
#define FIFO_MAX_FILL_PACKETS (FIFO_MAX_FILL_BYTES / BYTES_PER_DATA / DATAS_PER_PACKET) // = 227

#define MAX_PACKETS_PER_TRANSACTION ((0xFFu - 1u) / (BYTES_PER_DATA * DATAS_PER_PACKET)) // = 14. each transaction can have nearly 255 byte. one byte is reserved for address.
static uint16_t packets_per_transaction = MAX_PACKETS_PER_TRANSACTION;
static volatile bool config_changed = false;

static uint16_t current_time_prefix = 0u;
static uint16_t last_time = 0u;

static void interrupt_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  // we just use the interrupt for the wakeup
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
  intConfig.int1_fth = 1;
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
  LSM_ERROR_CHECK(lsm6dsl_fifo_mode_set(dev_ctx, LSM6DSL_BYPASS_MODE)); // this will clear the fifo
  config_changed = true;
  LSM_ERROR_CHECK(lsm6dsl_fifo_data_rate_set(dev_ctx, LSM6DSL_FIFO_DISABLE));
  LSM_ERROR_CHECK(lsm6dsl_fifo_xl_batch_set(dev_ctx, LSM6DSL_FIFO_XL_DISABLE));
  LSM_ERROR_CHECK(lsm6dsl_fifo_gy_batch_set(dev_ctx, LSM6DSL_FIFO_GY_DISABLE));
}

void lsm_get_data_restart(lsm6dsl_ctx_t *dev_ctx, imu_speed_t speed) {
  uint8_t resetTime[3];
  memset(resetTime, 0, 3 * sizeof(uint8_t));
  LSM_ERROR_CHECK(lsm6dsl_write_reg(dev_ctx, LSM6DSL_TIMESTAMP0_REG, resetTime, 3));
  current_time_prefix = 0u;
  last_time = 0u;
  LSM_ERROR_CHECK(lsm6dsl_fifo_xl_batch_set(dev_ctx, LSM6DSL_FIFO_XL_NO_DEC));
  LSM_ERROR_CHECK(lsm6dsl_fifo_gy_batch_set(dev_ctx, LSM6DSL_FIFO_GY_NO_DEC));
  LSM_ERROR_CHECK(lsm6dsl_fifo_data_rate_set(dev_ctx, speed + 2));
  LSM_ERROR_CHECK(lsm6dsl_fifo_mode_set(dev_ctx, LSM6DSL_STREAM_MODE));
}

imu_speed_t lsm_get_data_speed_set(lsm6dsl_ctx_t *dev_ctx, imu_speed_t speed, uint16_t buffer_clear_interval, uint16_t buffer_size) {
  float buffer_clear_interval_ms = ((float)buffer_clear_interval) * 1.25f; // range: 7.5 - 4000

  uint16_t packets_generated_per_buffer_clear_interval;
#define packet_generation_speed_ms(speed_param) (1000.0f / ((float)(25 * (0b1 << speed_param)))) // range: 0.625 - 40
  if (speed < IMU_ODR_1600Hz) {
    do {
      speed++;
      packets_generated_per_buffer_clear_interval = (uint16_t)(buffer_clear_interval_ms / packet_generation_speed_ms(speed)); // range: 0 - 6400
      // actual minimum is 0.1875. 1 / 0.1875 = 5.3333. This is why we want slave latency 6
    } while (packets_generated_per_buffer_clear_interval > buffer_size && speed < IMU_ODR_NOT_DEF);
    speed--; // one lower so we stay in bounds and better be above the buffersize then under so it can be set lower manually if wanted.
  }
  packets_generated_per_buffer_clear_interval = (uint16_t)(buffer_clear_interval_ms / packet_generation_speed_ms(speed)); // range: 0 - 6400

  uint16_t packets_till_fifo_full_or_buffer_cleared = MAX(1u, MIN(FIFO_MAX_FILL_PACKETS, packets_generated_per_buffer_clear_interval)); // range: 1 - 227

  packets_per_transaction = MIN(packets_till_fifo_full_or_buffer_cleared, MAX_PACKETS_PER_TRANSACTION); // range: 1 - 14

  uint16_t bytes_till_fifo_full_or_buffer_cleared = BYTES_PER_DATA * DATAS_PER_PACKET * packets_till_fifo_full_or_buffer_cleared; // range: 18 - 4086
  LSM_ERROR_CHECK(lsm6dsl_fifo_watermark_set(dev_ctx, bytes_till_fifo_full_or_buffer_cleared >> 1));                              // register is in 2-byte-word format

  LSM_ERROR_CHECK(lsm6dsl_fifo_data_rate_set(dev_ctx, speed + 2));

  NRF_LOG_INFO("LSM SET SPEED FINISHED. final speed: %u. threshold: %u. ppt: %u", speed, bytes_till_fifo_full_or_buffer_cleared, packets_per_transaction);
  return speed;
}

// ===========================================================================

void lsm_get_data_loop(lsm6dsl_ctx_t *dev_ctx, bool (*send_data)(imu_data_t)) {
  while (true) {
    config_changed = false;
    uint16_t local_packets_per_transaction = packets_per_transaction;
    axis1bit16_t available_sensor_data; // we use this type, because we get uint8_t[2] and want that converted to uint16_t but since we dont use the most-significant 4-bit, we can safely use int16_t
    memset(&available_sensor_data, 0, sizeof(axis1bit16_t));
    LSM_ERROR_CHECK(lsm6dsl_read_reg(dev_ctx, LSM6DSL_FIFO_STATUS1, available_sensor_data.u8bit, 2)); // actually read LSM6DSL_FIFO_STATUS1 and LSM6DSL_FIFO_STATUS2, but this combined is faster since we dont need to transmit the address two times
    available_sensor_data.u8bit[1] &= 0x07u;                                                          // only the upper 11 bits are valid from the register
    available_sensor_data.i16bit = available_sensor_data.i16bit << 1;                                 // this register is in 2-byte-word format. after this, number of bytes is stored in this variable. 12 bits are used now.

    if (config_changed || available_sensor_data.i16bit < BYTES_PER_DATA * DATAS_PER_PACKET * local_packets_per_transaction)
      break;

    axis3bit16_t theData[DATAS_PER_PACKET * local_packets_per_transaction];
    memset(theData, 0, DATAS_PER_PACKET * local_packets_per_transaction * sizeof(axis3bit16_t));
    if (config_changed || lsm6dsl_fifo_raw_data_get(dev_ctx, theData->u8bit, BYTES_PER_DATA * DATAS_PER_PACKET * local_packets_per_transaction) != 0 || config_changed)
      break;

    for (uint16_t i = 0; i < local_packets_per_transaction; i++) {
      imu_data_t data;
      data.gyro_x = theData[i * DATAS_PER_PACKET].i16bit[0];
      data.gyro_y = theData[i * DATAS_PER_PACKET].i16bit[1];
      data.gyro_z = theData[i * DATAS_PER_PACKET].i16bit[2];
      data.accel_x = theData[i * DATAS_PER_PACKET + 1].i16bit[0];
      data.accel_y = theData[i * DATAS_PER_PACKET + 1].i16bit[1];
      data.accel_z = theData[i * DATAS_PER_PACKET + 1].i16bit[2];
      memset(&data.time, 0, sizeof(uint32_t));
      uint16_t time = (theData[i * DATAS_PER_PACKET + 2].u8bit[1] << 8 | theData[i * DATAS_PER_PACKET + 2].u8bit[0]);
      if (time < last_time) {
        current_time_prefix += 1u;
        if (current_time_prefix & 0x80 != 0u)
          current_time_prefix = 0u;
      }
      last_time = time;
      data.time = (((uint32_t)current_time_prefix) << 16) | time;
      if (!send_data(data))
        break;
    }
  }
}