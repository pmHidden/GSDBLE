#ifndef IMU_H_
#define IMU_H_

#include <stdbool.h>
#include <stdint.h>

/**
definitions to control an imu from an uc
*/
/**
sensor data rate. speed in hz = 25 * 2 ^ imu_speed_t.
*/
typedef enum {
  IMU_ODR_25Hz = 0,
  IMU_ODR_50Hz = 1,
  IMU_ODR_100Hz = 2,
  IMU_ODR_200Hz = 3,
  IMU_ODR_400Hz = 4,
  IMU_ODR_800Hz = 5,
  IMU_ODR_1600Hz = 6,
  IMU_ODR_NOT_DEF = 7
} imu_speed_t;

typedef struct {
  int16_t accel_x;
  int16_t accel_y;
  int16_t accel_z;
  int16_t gyro_x;
  int16_t gyro_y;
  int16_t gyro_z;
  uint32_t time; // msb not used, so unsigned value = signed value. 1 timestep = 6,4 ms
} imu_data_t;

/** 
init imu and all protocols. 

parameter is the function pointer, where the data is going to be delivered.
its return boolean is true, if the data could be sent, otherwise false
*/
void imu_init(bool (*send_data)(imu_data_t));

/** function that is called in the main loop */
void imu_loop(void);

/** called, when a new interval is set */
void imu_on_new_interval(uint16_t buffer_clear_interval, uint16_t buffer_size);

/** set speed of data */
void imu_speed_set(imu_speed_t speed, uint16_t buffer_size);

/** get speed of data */
imu_speed_t imu_speed_get(void);

/** stop sending data */
void imu_stop(bool reset);

/** continue sending data */
void imu_restart(void);

#endif