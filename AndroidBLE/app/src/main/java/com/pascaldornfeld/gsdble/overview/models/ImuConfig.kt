package com.pascaldornfeld.gsdble.overview.models

import no.nordicsemi.android.ble.data.Data
import kotlin.experimental.and
import kotlin.experimental.or

data class ImuConfig(
    val odrIndex: Int,
    val paused: Boolean
) {
    constructor(data: Data) : this(
        (data.value!![0] and 0x07).toInt(),
        (data.value!![0] and 0x08).toInt().shr(3) == 1
    )

    fun toByteArray(): ByteArray {
        val byteArray = ByteArray(1)
        byteArray[0] = odrIndex.toByte() or (if (paused) 1 else 0).shl(3).toByte()
        return byteArray
    }

    companion object {
        val ODR_INDEX_TO_FREQ = arrayOf(26, 52, 104, 208, 416, 833, 1666)
    }
}