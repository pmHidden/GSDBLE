//#define USE_LSM
#if defined(USE_LSM) && !defined(USE_BMI)

#include "imu.h"

sensor_data sensor_read() {
}

void imu_init() {

}
#endif