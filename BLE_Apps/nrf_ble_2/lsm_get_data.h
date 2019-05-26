#ifndef LSM_GET_DATA_H_
#define LSM_GET_DATA_H_

#include "app_error.h"
#include "imu.h"
#include "lsm6dsl_reg.h"
#include <stdint.h>

#define LSM_ERROR_CHECK(ERR_CODE) APP_ERROR_CHECK_BOOL(((int32_t)ERR_CODE) == 0);

void lsm_get_data_init(lsm6dsl_ctx_t *dev_ctx);
void lsm_get_data_loop(lsm6dsl_ctx_t *dev_ctx, bool (*send_data)(imu_data));
void lsm_get_data_speed_set(lsm6dsl_ctx_t *dev_ctx, imu_speed_t speed);
void lsm_get_data_stop(lsm6dsl_ctx_t *dev_ctx);
void lsm_get_data_restart(lsm6dsl_ctx_t *dev_ctx);

#endif