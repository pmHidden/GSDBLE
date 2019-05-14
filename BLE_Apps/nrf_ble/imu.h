#ifndef IMU_H_
#define IMU_H_

#include <stdint.h>
#include "my_service.h"

typedef struct {
  int16_t accel_x;
  int16_t accel_y;
  int16_t accel_z;
  int16_t gyro_x;
  int16_t gyro_y;
  int16_t gyro_z;
  //uint32_t time;
} imu_data;

void imu_init(my_service_t *p_cus);

#endif