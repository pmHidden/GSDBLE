#include "app_error.h"
#include "imu.h"
#include "nrf_delay.h"
#include "nrf_drv_gpiote.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"

uint64_t howMuchData = 0;

bool send_my_data(imu_data data) {
  howMuchData++;
  if (howMuchData % 2000 == 0)
    NRF_LOG_INFO("I received %u in total!", howMuchData);
  return true;
}

int main(void) {
  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  imu_init(send_my_data);
  imu_set_speed(IMU_ODR_1600Hz);

  NRF_LOG_INFO("starting loop");

  while (true) {
    if (NRF_LOG_PROCESS() == false) {
      NRF_LOG_INFO("sleep and %u", nrfx_gpiote_in_is_set(31));
      NRF_LOG_FLUSH();
      __SEV();
      __WFE();
      __WFE();
      NRF_LOG_INFO("after sleep and %u", nrfx_gpiote_in_is_set(31));
      NRF_LOG_FLUSH();
      imu_loop();
      NRF_LOG_INFO("after loop and %u", nrfx_gpiote_in_is_set(31));
      NRF_LOG_FLUSH();
    }
  }
}