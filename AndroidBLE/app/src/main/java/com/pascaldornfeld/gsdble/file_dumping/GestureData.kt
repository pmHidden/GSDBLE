package com.pascaldornfeld.gsdble.file_dumping

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.pascaldornfeld.gsdble.connected.gsdble_library.models.ImuData
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/*
This is the object that is going to be written as json
 */
data class GestureData(
    var startTime: String? = null, // startTime
    var endTime: String? = null, // endTime
    val deviceId: String? = null, // smartphone id
    var label: Int = 0, // gesture class label
    @Suppress("ArrayInDataClass") val datas: Array<ExtremityData> = arrayOf()
) {
    @SuppressLint("HardwareIds")
    constructor(pDatas: Array<ExtremityData>, context: Context) : this(
        startTime = SimpleDateFormat("yyyy-MM-dd--HH-mm-ss")
            .format(Date(System.currentTimeMillis())),
        datas = pDatas,
        deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    )
}

data class ExtremityData(
    var deviceMac: String = "none",
    val accData: SensorData = SensorData(),
    val gyroData: SensorData = SensorData()
)

data class SensorData(
    val xAxisData: ArrayList<Short> = ArrayList(),
    val yAxisData: ArrayList<Short> = ArrayList(),
    val zAxisData: ArrayList<Short> = ArrayList(),
    val timeStamp: ArrayList<Int> = ArrayList()
)