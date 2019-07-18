package com.pascaldornfeld.gsdble.models

import kotlin.experimental.or

data class ImuData (
    val accel_x: Short,
    val accel_y: Short,
    val accel_z: Short,
    val gyro_x: Short,
    val gyro_y: Short,
    val gyro_z: Short,
    val time: Int
) {
    companion object {
        const val MIN_TIME = 0
        val MAX_TIME = Int.MAX_VALUE
        const val TIME_SCALE_MS = 6.4f

        @Suppress("EXPERIMENTAL_API_USAGE")
        fun fromByteArray(byteArray: ByteArray): ImuData {
            assert(byteArray.size == 16)
            val uByteArray = byteArray.asUByteArray()
            val time =
                (uByteArray[15].toUInt() shl 24) or (uByteArray[14].toUInt() shl 16) or (uByteArray[13].toUInt() shl 8) or uByteArray[12].toUInt()
            return ImuData(
                ((uByteArray[1].toInt() shl 8).toShort()) or uByteArray[0].toShort(),
                ((uByteArray[3].toInt() shl 8).toShort()) or uByteArray[2].toShort(),
                ((uByteArray[5].toInt() shl 8).toShort()) or uByteArray[4].toShort(),
                ((uByteArray[7].toInt() shl 8).toShort()) or uByteArray[6].toShort(),
                ((uByteArray[9].toInt() shl 8).toShort()) or uByteArray[8].toShort(),
                ((uByteArray[11].toInt() shl 8).toShort()) or uByteArray[10].toShort(),
                time.toInt()
            )
        }
    }
}
