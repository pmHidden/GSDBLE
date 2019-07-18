#ifndef BMI_GET_DATA_H_
#define BMI_GET_DATA_H_

#include "app_error.h"
#include "../imu.h"
#include "bmi160.h"
#include <stdint.h>

#define BMI_ERROR_CHECK(ERR_CODE) APP_ERROR_CHECK_BOOL(((int8_t)ERR_CODE) == BMI160_OK);

/** buffer_size = TX_BUFFER-Size */
void bmi_get_data_init(struct bmi160_dev *dev_ctx, uint16_t buffer_size);
void bmi_get_data_loop(struct bmi160_dev *dev_ctx, bool (*send_data)(imu_data_t), imu_speed_t cur_speed);
void bmi_get_data_stop(struct bmi160_dev *dev_ctx);
/** the last speed that was returned from lsm_get_data_speed_set */
void bmi_get_data_restart(struct bmi160_dev *dev_ctx, imu_speed_t speed);

/**
6 <= buffer_clear_interval <= 3200. Unit: 1.25 ms.
call lsm_get_data_stop before this method and lsm_get_data_restart after.

besides of setting the speed:
1) sets the fifo fill level threshold to the optimal value. if fifo would not be filled in one interval, the threshold is set lower.
2) avoids an extreme buffer overflow. if speed is set much too high for the buffer and interval, sets the speeds higher. new speed is returned.
*/
imu_speed_t bmi_get_data_speed_set(struct bmi160_dev *dev_ctx, imu_speed_t speed, uint16_t buffer_clear_interval);

#endif