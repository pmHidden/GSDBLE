#ifndef LSM_GET_DATA_H_
#define LSM_GET_DATA_H_

#include "app_error.h"
#include "../imu.h"
#include "lsm6dsl_reg.h"
#include <stdint.h>

#define LSM_ERROR_CHECK(ERR_CODE) APP_ERROR_CHECK_BOOL(((int32_t)ERR_CODE) == 0);

/** buffer_size = TX_BUFFER-Size */
void lsm_get_data_init(lsm6dsl_ctx_t *dev_ctx, uint16_t buffer_size);
void lsm_get_data_loop(lsm6dsl_ctx_t *dev_ctx, bool (*send_data)(imu_data_t));
void lsm_get_data_stop(lsm6dsl_ctx_t *dev_ctx);
/** the last speed that was returned from lsm_get_data_speed_set */
void lsm_get_data_restart(lsm6dsl_ctx_t *dev_ctx, imu_speed_t speed);

/**
6 <= buffer_clear_interval <= 3200. Unit: 1.25 ms.
call lsm_get_data_stop before this method and lsm_get_data_restart after.

besides of setting the speed:
1) sets the fifo fill level threshold to the optimal value. if fifo would not be filled in one interval, the threshold is set lower.
2) avoids an extreme buffer overflow. if speed is set much too high for the buffer and interval, sets the speeds higher. new speed is returned.
*/
imu_speed_t lsm_get_data_speed_set(lsm6dsl_ctx_t *dev_ctx, imu_speed_t speed, uint16_t buffer_clear_interval);

#endif