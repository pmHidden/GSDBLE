#include "bmi_get_data.h"
#include "nrf_drv_gpiote.h"
#include "nrf_log.h"

/**
implementation of lsm_get_data.h. uses fifo for data retrieval

hookup on 'steval-mk178v2' -> 'nrf dk52':
int1 -> P0.8
*/
#define IMU_PIN_INT1 8

#define DATAS_PER_PACKET 2u                                  // we have 2 sensor data
#define BYTES_PER_DATA 6u                                    // every data is 6 byte
#define BYTES_PER_PACKET (DATAS_PER_PACKET * BYTES_PER_DATA) // 12

#define FIFO_MAX_FILL_BYTES 768u                                       // 1024 is fifo size. we only want to fill it for 75%. 768 % 12 = 0
#define FIFO_MAX_FILL_PACKETS (FIFO_MAX_FILL_BYTES / BYTES_PER_PACKET) // = 64

#define MAX_PACKETS_PER_TRANSACTION ((0xFFu - 1u) / BYTES_PER_PACKET) // = 21. each transaction can have nearly 255 byte. one byte is reserved for address.
static uint16_t packets_per_transaction = MAX_PACKETS_PER_TRANSACTION;
static volatile bool config_changed = false;

static uint16_t buffer_size;
static uint32_t current_time = 0u;

static void interrupt_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  // we just use the interrupt for the wakeup
}

void bmi_get_data_init(struct bmi160_dev *dev_ctx, uint16_t p_buffer_size) {
  buffer_size = p_buffer_size;
  // config gpiote
  if (!nrf_drv_gpiote_is_init())
    APP_ERROR_CHECK(nrf_drv_gpiote_init());
  nrf_drv_gpiote_in_config_t in_config = NRFX_GPIOTE_RAW_CONFIG_IN_SENSE_HITOLO(true);
  in_config.pull = NRF_GPIO_PIN_NOPULL;
  APP_ERROR_CHECK(nrf_drv_gpiote_in_init(IMU_PIN_INT1, &in_config, interrupt_handler));
  nrf_drv_gpiote_in_event_enable(IMU_PIN_INT1, true);

  // enable fifo_threshold_interrupt on int1
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
  int_config.int_type = BMI160_ACC_GYRO_FIFO_WATERMARK_INT;
  int_config.fifo_WTM_int_en = BMI160_ENABLE;

  BMI_ERROR_CHECK(bmi160_set_int_config(&int_config, dev_ctx));

  BMI_ERROR_CHECK(bmi160_set_fifo_down(BMI160_GYRO_FIFO_DOWN_ONE | BMI160_ACCEL_FIFO_DOWN_ONE, dev_ctx));
  BMI_ERROR_CHECK(bmi160_set_fifo_config(BMI160_FIFO_ACCEL | BMI160_FIFO_GYRO, BMI160_ENABLE, dev_ctx));
}

void bmi_get_data_stop(struct bmi160_dev *dev_ctx) {
  // stop fifo data collection
  config_changed = true;
  BMI_ERROR_CHECK(bmi160_set_fifo_down(BMI160_GYRO_FIFO_DOWN_ZERO | BMI160_ACCEL_FIFO_DOWN_ZERO, dev_ctx));
  BMI_ERROR_CHECK(bmi160_set_fifo_flush(dev_ctx));
  NRF_LOG_INFO("stopped fifo sensor");
}

void bmi_get_data_restart(struct bmi160_dev *dev_ctx, imu_speed_t speed) {
  // start fifo data collection
  BMI_ERROR_CHECK(bmi160_set_fifo_down(BMI160_GYRO_FIFO_DOWN_ONE | BMI160_ACCEL_FIFO_DOWN_ONE, dev_ctx));
  NRF_LOG_INFO("restarted fifo sensor");
}

imu_speed_t bmi_get_data_speed_set(struct bmi160_dev *dev_ctx, imu_speed_t speed, uint16_t connection_interval) {
  float connection_interval_ms = ((float)connection_interval) * 1.25f; // range: 7.5 - 4000

#define packets_generated_per_connection_interval(speed_param) ((uint16_t)(connection_interval_ms / (1000.0f / ((float)(25 * (0b1 << speed_param)))))) // range: 0 - 6400. actual minimum is 0.1875 packets per connection interval = more then 5 intervals till one packet
  bool didGoLowerOnceAndNowOk = false;
  while (packets_generated_per_connection_interval(speed) > buffer_size) {
    if (speed > 0) {
      didGoLowerOnceAndNowOk = true;
      speed--;
    } else {
      didGoLowerOnceAndNowOk = false;
      break;
    }
  }
  if (didGoLowerOnceAndNowOk)
    speed++; // better one too high then one too low so its less restrictive

  /** we got the final speed. now calculate the threshold */
  uint16_t packets_till_fifo_full_or_buffer_cleared = MAX(1u, MIN(FIFO_MAX_FILL_PACKETS, packets_generated_per_connection_interval(speed))); // range: 1 - 227

  packets_per_transaction = MIN(packets_till_fifo_full_or_buffer_cleared, MAX_PACKETS_PER_TRANSACTION); // range: 1 - 14

  uint16_t bytes_till_fifo_full_or_buffer_cleared = BYTES_PER_PACKET * packets_till_fifo_full_or_buffer_cleared; // range: 18 - 4086
  BMI_ERROR_CHECK(bmi160_set_fifo_wm(bytes_till_fifo_full_or_buffer_cleared >> 2, dev_ctx));

  NRF_LOG_INFO("BMI SET SPEED FINISHED. final speed: %u. th: %u. ppt: %u. pgpci: %u", speed, bytes_till_fifo_full_or_buffer_cleared, packets_per_transaction, packets_generated_per_connection_interval(speed));
  return speed;
}

// ===========================================================================

void bmi_get_data_loop(struct bmi160_dev *dev_ctx, bool (*send_data)(imu_data_t), imu_speed_t cur_speed) {
  config_changed = false;
  uint16_t local_packets_per_transaction = packets_per_transaction;
  while (true) {
    struct bmi160_fifo_frame fifo_frame;
    memset(&fifo_frame, 0, sizeof(struct bmi160_fifo_frame));
    dev_ctx->fifo = &fifo_frame;

    uint8_t buffer[BYTES_PER_PACKET * local_packets_per_transaction];
    dev_ctx->fifo->length = BYTES_PER_PACKET * local_packets_per_transaction;
    dev_ctx->fifo->data = buffer;
    NRF_LOG_INFO("trying to get data");
    bool configChangedBefore = config_changed;
    bool response = BMI160_OK != bmi160_get_fifo_data(dev_ctx);
    bool lengthOk = dev_ctx->fifo->length == 0;
    bool configChangedAfter = config_changed;

    if (configChangedBefore || response || lengthOk || configChangedAfter)
      break;
    NRF_LOG_INFO("got data: %d", dev_ctx->fifo->length);

    uint8_t packets_read_accel = dev_ctx->fifo->length / BYTES_PER_PACKET;
    uint8_t packets_read_gyro = dev_ctx->fifo->length / BYTES_PER_PACKET;
    struct bmi160_sensor_data accel_data[packets_read_accel];
    struct bmi160_sensor_data gyro_data[packets_read_gyro];
    BMI_ERROR_CHECK(bmi160_extract_accel(accel_data, &packets_read_accel, dev_ctx));
    BMI_ERROR_CHECK(bmi160_extract_gyro(gyro_data, &packets_read_gyro, dev_ctx));

    for (uint16_t i = 0; i < MIN(packets_read_accel, packets_read_gyro); i++) {
      imu_data_t data;
      data.gyro_x = gyro_data[i].x;
      data.gyro_y = gyro_data[i].y;
      data.gyro_z = gyro_data[i].z;
      data.accel_x = accel_data[i].x;
      data.accel_y = accel_data[i].y;
      data.accel_z = accel_data[i].z;

      current_time += 2 ^ (6 - cur_speed);
      if (current_time & 0x80000000 != 0u)
        current_time = 0u;
      data.time = current_time;
      NRF_LOG_INFO("One Packet Was Sent");
      if (!send_data(data))
        break;
    }
  }
}