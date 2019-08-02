package com.pascaldornfeld.gsdble.overview.models

import kotlin.experimental.and
import kotlin.experimental.or

data class ImuConfig(
    val odr: ImuOdr,
    val paused: Boolean
) {
    fun toByteArray(): ByteArray {
        val byteArray = ByteArray(1)
        byteArray[0] = odr.ordinal.toByte() or ((if (paused) 1 else 0).shl(3)).toByte()
        return byteArray
    }

    companion object {
        val LSM_REAL_ODR = arrayOf(26, 52, 104, 208, 416, 833, 1666)
        val BMI_REAL_ODR = arrayOf(25, 50, 100, 200, 400, 800, 1600)
        var actualOdr = LSM_REAL_ODR

        fun fromByteArray(byteArray: ByteArray): ImuConfig {
            assert(byteArray.size == 1)
            return ImuConfig(
                ImuOdr.values()[(byteArray[0] and 0x07).toInt()],
                (byteArray[0] and 0x08).toInt().shr(3) == 1
            )
        }

    }
}


enum class ImuOdr {
    ODR_25_HZ, // 0
    ODR_50_HZ, // 1
    ODR_100_HZ, // 2
    ODR_200_HZ, // 3
    ODR_400_HZ, // 4
    ODR_800_HZ, // 5
    ODR_1600_HZ // 6
}