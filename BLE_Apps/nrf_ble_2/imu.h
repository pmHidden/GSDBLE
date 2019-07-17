#ifndef IMU_H_
#define IMU_H_

#include <stdbool.h>
#include <stdint.h>

/** definitions to control an imu from an mcu */

/** sensor data rate. speed in hz = 25 * 2 ^ imu_speed_t. */
typedef enum {
  IMU_ODR_25Hz = 0,
  IMU_ODR_50Hz = 1,
  IMU_ODR_100Hz = 2,
  IMU_ODR_200Hz = 3,
  IMU_ODR_400Hz = 4,
  IMU_ODR_800Hz = 5,
  IMU_ODR_1600Hz = 6
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
init imu and all protocols. set sensor speed to default. dont start sending data

parameter is the function pointer, where the data is going to be delivered.
its return boolean is true, if the data could be sent, otherwise false
*/
void imu_init(bool (*send_data)(imu_data_t), uint16_t buffer_size);

/** function that is called in the main loop */
void imu_loop(void);

/** called, when a new connection interval is set */
void imu_on_new_interval(uint16_t buffer_clear_interval);

/** try to set current sensor speed. restart, if stopped */
void imu_speed_set(imu_speed_t speed);

/** get current sensor speed */
imu_speed_t imu_speed_get(void);

/** stop sending data. if reset == true, set the sensor speed to default */
void imu_stop(bool reset);

bool imu_is_paused();

/** starts sending data. applies old config (or default if there is no old) */
void imu_restart(void);

#endif