package com.pascaldornfeld.gsdble.connected.gsdble_library.models

/**
 * Model for data characteristic
 *
 * @param accel_x data of x-axis of accelerometer
 * @param gyro_x data of x-axis of gyroscope
 * @param time sensor startTime in 6.4 ms units
 */
data class ImuData(
    val accel_x: Short?,
    val accel_y: Short?,
    val accel_z: Short?,
    val gyro_x: Short?,
    val gyro_y: Short?,
    val gyro_z: Short?,
    val time: Int
)