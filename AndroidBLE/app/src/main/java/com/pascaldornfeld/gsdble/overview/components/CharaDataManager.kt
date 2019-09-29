package com.pascaldornfeld.gsdble.overview.components

import com.pascaldornfeld.gsdble.overview.fragments.DoubleTimeGraphFragment
import com.pascaldornfeld.gsdble.overview.fragments.LongTimeGraphFragment
import com.pascaldornfeld.gsdble.overview.fragments.SensorGraphFragment
import com.pascaldornfeld.gsdble.overview.models.ImuData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CharaDataManager(
    private val graphAccel: SensorGraphFragment,
    private val graphGyro: SensorGraphFragment,
    private val graphTime: LongTimeGraphFragment,
    private val graphTimeDeviation: DoubleTimeGraphFragment,
    private val graphDataRate: LongTimeGraphFragment,
    private val graphDataRateAverage: DoubleTimeGraphFragment,
    private val graphDataRateDeviation: DoubleTimeGraphFragment
) {
    init {
        graphAccel.setTitle("Accelerometer")
        graphGyro.setTitle("Gyroscope")
        graphTime.apply {
            setTitle("Packet Delivery Time")
            dataDescription = "data time vs delivery time"
        }
        graphTimeDeviation.apply {
            setTitle("Time Deviation")
            showSeconds = 10
            timestepScalingMs = 1000f
            dataDescription = "deviation"
        }
        graphDataRate.apply {
            setTitle("Data Rate")
            showSeconds = 300
            timestepScalingMs = 1000f
            dataDescription = "data time vs data rate"
        }
        graphDataRateAverage.apply {
            setTitle("Data Rate Average")
            showSeconds = 10
            timestepScalingMs = 1000f
            dataDescription = "average"
        }
        graphDataRateDeviation.apply {
            setTitle("Data Rate Deviation")
            showSeconds = 10
            timestepScalingMs = 1000f
            dataDescription = "deviation"
        }
    }

    private val dataRateStatExecutor = Executors.newSingleThreadExecutor()
    private val graphTimeStatExecutor = Executors.newSingleThreadExecutor()

    private var currentTrackedSecond = 0L
    private var packetsThisSecond = 0L
    private val clearScheduled = AtomicBoolean(false)


    fun onNewData(data: ImuData) {
        val timeOfPacketArrival = System.currentTimeMillis()

        //Log.v(TAG, "Received Data: $data")
        graphAccel.addData(
            data.time,
            Triple(data.accel_x, data.accel_y, data.accel_z)
        )
        graphGyro.addData(
            data.time,
            Triple(data.gyro_x, data.gyro_y, data.gyro_z)
        )
        graphTime.addData(data.time, timeOfPacketArrival)

        val thisSecond = timeOfPacketArrival / 1000L
        if (thisSecond != currentTrackedSecond) {
            if (currentTrackedSecond != 0L) {
                graphDataRate.addData(currentTrackedSecond, packetsThisSecond)

                // the first graph will not work, when there are multiple y values for the same x values.
                // in that case, it will just print NaN, because it cant calculate the gradient.
                // This happens on LSM6DSL, when the sensordatarate is < 6.4 ms (> 125 Hz).
                // So on LSM6DSL, when you setup a sensordatarate of 200 hz or more.
                val clearGraphs = clearScheduled.compareAndSet(true, false)
                val graphTimeData =
                    if (clearGraphs || graphTimeDeviation.isPausedByUser()) null else graphTime.internalData.toTypedArray()
                CharaDataStatsByGradient(
                    currentTrackedSecond,
                    null,
                    graphTimeDeviation
                ).executeOnExecutor(graphTimeStatExecutor, graphTimeData)

                val dataRateData =
                    if (clearGraphs || (graphDataRateAverage.isPausedByUser() && graphDataRateDeviation.isPausedByUser())) null else graphDataRate.internalData.toTypedArray()
                CharaDataStatsBySecondValue(
                    currentTrackedSecond,
                    graphDataRateAverage,
                    graphDataRateDeviation
                ).executeOnExecutor(dataRateStatExecutor, dataRateData)
            }
            currentTrackedSecond = thisSecond
            packetsThisSecond = 0L
        }
        packetsThisSecond++
    }

    fun resetGraphFragments() {
        graphAccel.internalData.clear()
        graphGyro.internalData.clear()
        graphTime.internalData.clear()
        graphDataRate.internalData.clear()
        clearScheduled.set(true)
    }

    companion object {
        private val TAG = CharaDataManager::class.java.simpleName.filter { it.isUpperCase() }
    }
}