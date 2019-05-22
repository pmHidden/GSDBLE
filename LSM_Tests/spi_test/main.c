#include "app_error.h"
#include "app_util_platform.h"
#include "boards.h"
#include "lsm6dsl_reg.h"
#include "nrf_delay.h"
#include "nrf_drv_gpiote.h"
#include "nrf_drv_spi.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"
#include <string.h>
/**
hookup on 'steval-mk178v2' -> 'nrf dk52':
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
lsm6dsl_ctx_t dev_ctx;
volatile bool newDataAvailable;

void spi_init() {
  nrf_drv_spi_config_t spi_config = NRF_DRV_SPI_DEFAULT_CONFIG;
  spi_config.ss_pin = 31;
  spi_config.miso_pin = 30;
  spi_config.mosi_pin = 29;
  spi_config.sck_pin = 27;
  spi_config.frequency = NRF_DRV_SPI_FREQ_125K;
  APP_ERROR_CHECK(nrf_drv_spi_init(&spi, &spi_config, NULL, NULL));
}
int32_t user_spi_read(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  NRF_LOG_INFO("spi read %u on %02x", len, reg_addr);
  if (len > 0xFEu)
    NRF_LOG_INFO("SPI-READ: YOU ARE READING TOO MUCH DATA! %u", len);
  NRF_LOG_FLUSH();

  uint8_t bigData[len + 1];

  ret_code_t err_code;
  uint8_t address = reg_addr | 0x80;
  err_code = nrf_drv_spi_transfer(&spi, &address, 1, bigData, len + 1);
  APP_ERROR_CHECK(err_code);

  memcpy(data, bigData + 1, len * sizeof(uint8_t));

  return 0;
}
int32_t user_spi_write(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len) {
  NRF_LOG_INFO("spi write %u on %02x", len, reg_addr);
  if (len > 0xFEu)
    NRF_LOG_INFO("SPI-WRITE: YOU ARE WRITING TOO MUCH DATA! %u", len);
  NRF_LOG_FLUSH();

  uint8_t bigData[len + 1];
  bigData[0] = reg_addr & 0x7F;
  memcpy(bigData + 1, data, len);
  ret_code_t err_code;
  err_code = nrf_drv_spi_transfer(&spi, bigData, len + 1, NULL, 0);
  APP_ERROR_CHECK(err_code);
  return 0;
}

void sensor_init() {
  dev_ctx.write_reg = user_spi_write;
  dev_ctx.read_reg = user_spi_read;

  uint8_t whoamI = 0;
  lsm6dsl_device_id_get(&dev_ctx, &whoamI);
  if (whoamI != LSM6DSL_ID) {
    NRF_LOG_INFO("rip", whoamI);
    NRF_LOG_FLUSH();
    while (1)
      ; /*manage here device not found */
  }
  lsm6dsl_reset_set(&dev_ctx, PROPERTY_ENABLE);
  uint8_t rst;
  do {
    lsm6dsl_reset_get(&dev_ctx, &rst);
  } while (rst);
}

void sensor_config() {
  lsm6dsl_block_data_update_set(&dev_ctx, PROPERTY_ENABLE);

  lsm6dsl_xl_data_rate_set(&dev_ctx, LSM6DSL_XL_ODR_12Hz5);
  lsm6dsl_xl_full_scale_set(&dev_ctx, LSM6DSL_2g);
  lsm6dsl_xl_filter_analog_set(&dev_ctx, LSM6DSL_XL_ANA_BW_400Hz);
  lsm6dsl_xl_lp2_bandwidth_set(&dev_ctx, LSM6DSL_XL_LOW_LAT_LP_ODR_DIV_9);

  lsm6dsl_gy_data_rate_set(&dev_ctx, LSM6DSL_GY_ODR_12Hz5);
  lsm6dsl_gy_full_scale_set(&dev_ctx, LSM6DSL_2000dps);
  lsm6dsl_gy_band_pass_set(&dev_ctx, LSM6DSL_HP_260mHz_LP1_STRONG);
}

void sensor_config_interrupt() {
  lsm6dsl_int1_route_t intConfig;
  memset(&intConfig, 0, sizeof(lsm6dsl_int1_route_t));
  intConfig.int1_full_flag = 1;
  int32_t result = lsm6dsl_pin_int1_route_set(&dev_ctx, intConfig);

  result = lsm6dsl_fifo_data_rate_set(&dev_ctx, LSM6DSL_FIFO_12Hz5);
  result = lsm6dsl_fifo_write_trigger_set(&dev_ctx, LSM6DSL_TRG_XL_GY_DRDY);
  result = lsm6dsl_fifo_mode_set(&dev_ctx, LSM6DSL_STREAM_MODE);
  lsm6dsl_fifo_pedo_and_timestamp_batch_set(&dev_ctx, PROPERTY_ENABLE);
  lsm6dsl_fifo_xl_batch_set(&dev_ctx, LSM6DSL_FIFO_XL_NO_DEC);
  lsm6dsl_fifo_gy_batch_set(&dev_ctx, LSM6DSL_FIFO_GY_NO_DEC);
  lsm6dsl_fifo_dataset_4_batch_set(&dev_ctx, LSM6DSL_FIFO_DS4_NO_DEC);
}

void in_pin_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  NRF_LOG_INFO("interrupt!");
  newDataAvailable = true;
}

static void gpio_init(void) {
  ret_code_t err_code;

  err_code = nrf_drv_gpiote_init();
  APP_ERROR_CHECK(err_code);

  nrf_drv_gpiote_in_config_t in_config = NRFX_GPIOTE_RAW_CONFIG_IN_SENSE_LOTOHI(true);
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

  sensor_init();
  sensor_config();
  gpio_init();

  sensor_config_interrupt();

  NRF_LOG_INFO("waiting for interrupt");

  while (true) {
    if (NRF_LOG_PROCESS() == false) {
      __SEV();
      __WFE();
      __WFE();

      if (newDataAvailable) {
        NRF_LOG_INFO("wtf");

        uint16_t fifo_fill_state = 0;

        lsm6dsl_reg_t output;
        memset(&output, 0, sizeof(lsm6dsl_reg_t));
        lsm6dsl_read_reg(&dev_ctx, LSM6DSL_FIFO_STATUS1, &output.byte, 1);
        fifo_fill_state = (uint16_t)output.byte;

        memset(&output, 0, sizeof(lsm6dsl_reg_t));
        lsm6dsl_read_reg(&dev_ctx, LSM6DSL_FIFO_STATUS2, &output.byte, 1);
        fifo_fill_state |= ((output.byte & 0x07) << 8);
        fifo_fill_state /= 3;
        NRF_LOG_INFO("fl_before: %u", fifo_fill_state);

        uint8_t howMuchToRead;
        if (fifo_fill_state * 6 > 0xFE)
          howMuchToRead = 0xFE / 6;
        else
          howMuchToRead = (uint8_t)fifo_fill_state;
        howMuchToRead -= howMuchToRead % 3;
        NRF_LOG_INFO("I will read: %u", howMuchToRead);

        axis3bit16_t theData[howMuchToRead];
        memset(theData, 0, howMuchToRead * sizeof(axis3bit16_t));
        lsm6dsl_fifo_raw_data_get(&dev_ctx, theData->u8bit, howMuchToRead * 6);

        for (uint16_t i = 0; i < howMuchToRead; i++) {
          NRF_LOG_INFO("%u %d %d %d", i, theData[i].i16bit[0], theData[i].i16bit[1], theData[i].i16bit[2]);
          NRF_LOG_FLUSH();
        }

        memset(&output, 0, sizeof(lsm6dsl_reg_t));
        lsm6dsl_read_reg(&dev_ctx, LSM6DSL_FIFO_STATUS1, &output.byte, 1);
        fifo_fill_state = (uint16_t)output.byte;

        memset(&output, 0, sizeof(lsm6dsl_reg_t));
        lsm6dsl_read_reg(&dev_ctx, LSM6DSL_FIFO_STATUS2, &output.byte, 1);
        fifo_fill_state |= ((output.byte & 0x07) << 8);
        fifo_fill_state /= 3;
        NRF_LOG_INFO("fl_after: %u", fifo_fill_state);

        newDataAvailable = false;
      }
    }
  }
}