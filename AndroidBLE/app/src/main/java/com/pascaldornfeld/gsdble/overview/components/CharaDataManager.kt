package com.pascaldornfeld.gsdble.overview.components

import android.util.Log
import com.pascaldornfeld.gsdble.DeviceFragment
import com.pascaldornfeld.gsdble.overview.fragments.DoubleTimeGraphFragment
import com.pascaldornfeld.gsdble.overview.fragments.LongTimeGraphFragment
import com.pascaldornfeld.gsdble.overview.fragments.SensorGraphFragment
import com.pascaldornfeld.gsdble.overview.models.ImuData
import kotlinx.android.synthetic.main.device_fragment.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CharaDataManager(deviceFragment: DeviceFragment) {
    private val vGraphAccel =
        (deviceFragment.vGraphAccel as SensorGraphFragment).apply { setTitle("Accelerometer") }
    private val vGraphGyro =
        (deviceFragment.vGraphGyro as SensorGraphFragment).apply { setTitle("Gyroscope") }
    private val vGraphTime = (deviceFragment.vGraphTime as LongTimeGraphFragment).apply {
        setTitle("Packet Delivery Delay")
        initialize(300000.0f, null, "data time vs delivery time")
    }
    private val vGraphDataRate =
        (deviceFragment.vGraphDataRate as LongTimeGraphFragment).apply {
            setTitle("Data Rate")
            initialize(300000.0f, 1000.0f, "data time vs data rate")
        }

    private val vGraphDataRateAverage =
        (deviceFragment.vGraphDataRateAverage as DoubleTimeGraphFragment).apply {
            setTitle("Data Rate Average")
            initialize(10000.0f, 1000.0f, "average")
        }
    private val vGraphDataRateDeviation =
        (deviceFragment.vGraphDataRateDeviation as DoubleTimeGraphFragment).apply {
            setTitle("Data Rate Variance")
            initialize(10000.0f, 1000.0f, "deviation")
        }
    private val dataRateStatExecutor = Executors.newSingleThreadExecutor()

    private val vGraphTimeDeviation =
        (deviceFragment.vGraphTimeDeviation as DoubleTimeGraphFragment).apply {
            setTitle("Delay Deviation")
            initialize(10000.0f, 1000.0f, "deviation")
        }
    private val graphTimeStatExecutor = Executors.newSingleThreadExecutor()

    private var currentTrackedSecond = 0L
    private var packetsThisSecond = 0L
    private val clearScheduled = AtomicBoolean(false)


    fun onNewData(data: ImuData) {
        val timeOfPacketArrival = System.currentTimeMillis()

        Log.v(TAG, "Received Data: $data")
        vGraphAccel.addData(
            data.time,
            Triple(data.accel_x, data.accel_y, data.accel_z)
        )
        vGraphGyro.addData(
            data.time,
            Triple(data.gyro_x, data.gyro_y, data.gyro_z)
        )
        vGraphTime.addData(data.time, timeOfPacketArrival)

        val thisSecond = timeOfPacketArrival / 1000L
        if (thisSecond != currentTrackedSecond) {
            if (currentTrackedSecond != 0L) {
                vGraphDataRate.addData(currentTrackedSecond, packetsThisSecond)

                // the first graph will not work, when there are multiple y values for the same x values.
                // in that case, it will just print NaN, because it cant calculate the gradient.
                // This happens on LSM6DSL, when the sensordatarate is < 6.4 ms (> 125 Hz).
                // So on LSM6DSL, when you setup a sensordatarate of 200 hz or more.
                val clearGraphs = clearScheduled.compareAndSet(true, false)
                val graphTimeData =
                    if (clearGraphs || vGraphTimeDeviation.isPaused()) null else vGraphTime.internalData.toTypedArray()
                CharaDataStatsByGradient(
                    currentTrackedSecond,
                    null,
                    vGraphTimeDeviation
                ).executeOnExecutor(graphTimeStatExecutor, graphTimeData)

                val dataRateData =
                    if (clearGraphs || (vGraphDataRateAverage.isPaused() && vGraphDataRateDeviation.isPaused())) null else vGraphDataRate.internalData.toTypedArray()
                CharaDataStatsBySecondValue(
                    currentTrackedSecond,
                    vGraphDataRateAverage,
                    vGraphDataRateDeviation
                ).executeOnExecutor(dataRateStatExecutor, dataRateData)
            }
            currentTrackedSecond = thisSecond
            packetsThisSecond = 0L
        }
        packetsThisSecond++
    }


    fun resetGraphFragments() {
        vGraphAccel.internalData.clear()
        vGraphGyro.internalData.clear()
        vGraphTime.internalData.clear()
        vGraphDataRate.internalData.clear()
        clearScheduled.set(true)
    }

    companion object {
        private val TAG = CharaDataManager::class.java.simpleName.filter { it.isUpperCase() }
    }
}