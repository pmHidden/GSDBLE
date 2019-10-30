package com.pascaldornfeld.gsdble.file_dumping

import com.pascaldornfeld.gsdble.connected.gsdble_library.models.ImuData
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/*
    selection from christian mueller:
    1-dev: right hand
    2-dev: right hand, right foot
    3-dev: hands, right foot
    4-dev: all
 */
data class GestureData(
    var time: String? = null, // time of first
    var user: String? = null, // user name
    var deviceId: String? = null, // smartphone
    var label: Int = 0, // gesture class label
    val leftHandData: ExtremityData = ExtremityData(),
    val rightHandData: ExtremityData = ExtremityData(),
    val leftFootData: ExtremityData = ExtremityData(),
    val rightFootData: ExtremityData = ExtremityData()
) {
    fun setTimeToNow() = setTimeToNow(Date())

    private fun setTimeToNow(millis: Long) = setTimeToNow(Date(millis))

    private fun setTimeToNow(date: Date) {
        time = SimpleDateFormat("yyyy-MM-dd--HH-mm-ss").format(date)
    }

    companion object {
        private const val THRESHOLD_MILLIS = 2500
    }

    class SingleDeviceFiller {
        private var gestureData: GestureData? = null
        private var startOfCurrent = Long.MAX_VALUE
        private var count = 0

        /**
         * adds content from imudata to extremitydata.
         *
         * @return extremityData if items added longer than threshold, else null
         */
        @Synchronized
        fun addFromImuData(imuData: ImuData): GestureData? {
            if (gestureData == null) {
                val currentMillis = System.currentTimeMillis()
                gestureData = GestureData().apply { setTimeToNow(currentMillis) }
                startOfCurrent = currentMillis
                count = 0
            }
            return gestureData?.let { currentGestureData: GestureData ->
                currentGestureData.rightHandData.run {
                    count++
                    accData.apply {
                        xAxisData.add(imuData.accel_x)
                        yAxisData.add(imuData.accel_y)
                        zAxisData.add(imuData.accel_z)
                    }
                    gyroData.apply {
                        xAxisData.add(imuData.gyro_x)
                        yAxisData.add(imuData.gyro_y)
                        zAxisData.add(imuData.gyro_z)
                    }
                    if (System.currentTimeMillis() - startOfCurrent >= THRESHOLD_MILLIS) {
                        gestureData = null
                        currentGestureData
                    } else null
                }
            }
        }
    }
}

data class ExtremityData(
    val accData: SensorData = SensorData(),
    val gyroData: SensorData = SensorData()
)

data class SensorData(
    val xAxisData: ArrayList<Short> = ArrayList(),
    val yAxisData: ArrayList<Short> = ArrayList(),
    val zAxisData: ArrayList<Short> = ArrayList()
)