package com.pascaldornfeld.gsdble.models

import kotlin.experimental.and

data class ImuConfig(
    val odr: ImuOdr
) {
    fun toByteArray(): ByteArray {
        val byteArray = ByteArray(1)
        byteArray[0] = odr.ordinal.toByte()
        return byteArray
    }

    companion object {
        fun fromByteArray(byteArray: ByteArray): ImuConfig {
            assert(byteArray.size == 1)
            return ImuConfig(ImuOdr.values()[(byteArray[0] and 0x07).toInt()])
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