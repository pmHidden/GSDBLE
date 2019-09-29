package com.pascaldornfeld.gsdble.overview.models

import no.nordicsemi.android.ble.data.Data

data class ImuData(
    val accel_x: Short,
    val accel_y: Short,
    val accel_z: Short,
    val gyro_x: Short,
    val gyro_y: Short,
    val gyro_z: Short,
    val time: Int
) {
    constructor(data: Data) : this(
        data.getIntValue(Data.FORMAT_SINT16, 0)!!.toShort(),
        data.getIntValue(Data.FORMAT_SINT16, 2)!!.toShort(),
        data.getIntValue(Data.FORMAT_SINT16, 4)!!.toShort(),
        data.getIntValue(Data.FORMAT_SINT16, 6)!!.toShort(),
        data.getIntValue(Data.FORMAT_SINT16, 8)!!.toShort(),
        data.getIntValue(Data.FORMAT_SINT16, 10)!!.toShort(),
        data.getIntValue(Data.FORMAT_UINT32, 12)!!
    )
}