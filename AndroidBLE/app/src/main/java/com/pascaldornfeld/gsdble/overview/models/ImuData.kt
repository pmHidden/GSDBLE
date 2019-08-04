package com.pascaldornfeld.gsdble.overview.models

import kotlin.math.pow

data class ImuData(
    val accel_x: Short,
    val accel_y: Short,
    val accel_z: Short,
    val gyro_x: Short,
    val gyro_y: Short,
    val gyro_z: Short,
    val time: Int
) {
    companion object {
        const val TIME_SCALE_MS_LSM = 6.4f
        const val TIME_SCALE_MS_BMI = 0.625f
        const val MIN_TIME = 0
        val MAX_TIME = 2f.pow(31).toInt() - 1
        var actualTimeScaleMs = TIME_SCALE_MS_LSM
    }
}
