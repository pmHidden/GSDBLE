package com.pascaldornfeld.gsdble.overview.components

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.overview.fragments.FloatTimeGraphFragment
import com.pascaldornfeld.gsdble.overview.fragments.LongTimeGraphFragment
import com.pascaldornfeld.gsdble.overview.fragments.SensorGraphFragment
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import com.pascaldornfeld.gsdble.overview.models.ImuData
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.callback.SuccessCallback
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow
import kotlin.math.sqrt

class CharaDataManager(
    fragmentById: ((Int) -> Fragment?),
    private val lastConfig: AtomicReference<ImuConfig?>
) : MyBleManager.MyDataReceivedCallback<ImuData>, SuccessCallback, FailCallback {
    private val vGraphAccel = fragmentById(R.id.vGraphAccel) as SensorGraphFragment
    private val vGraphGyro = fragmentById(R.id.vGraphGyro) as SensorGraphFragment
    private val vGraphTime = (fragmentById(R.id.vGraphTime) as LongTimeGraphFragment)
        .initialize(60000.0f, null, "data time vs delivery time")
    private val vGraphTimeDeviation = (fragmentById(R.id.vGraphTimeDeviation) as FloatTimeGraphFragment)
        .initialize(10000.0f, 1000.0f, "deviation")
    private val vGraphDataRate = (fragmentById(R.id.vGraphDataRate) as LongTimeGraphFragment)
        .initialize(300000.0f, 1000.0f, "data time vs data rate")
    private val vGraphDataRateDeviation = (fragmentById(R.id.vGraphDataRateDeviation) as FloatTimeGraphFragment)
        .initialize(10000.0f, 1000.0f, "deviation")

    private var currentTrackedSecond = 0L
    private var packetsThisSecond = 0L

    override fun onNewData(data: ImuData) {
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

                val tempCurConf = lastConfig.get()
                if (tempCurConf != null && vGraphDataRate.internalData.size >= 30) {
                    var sum = 0.0f
                    vGraphDataRate.internalData.forEach {
                        sum += (it.second - ImuConfig.actualOdr[tempCurConf.odr.ordinal]).toFloat().pow(2)
                    }
                    vGraphDataRateDeviation.addData(
                        currentTrackedSecond,
                        sqrt((sum / (vGraphDataRate.internalData.size - 1).toFloat()))
                    )
                }

                if (tempCurConf != null && vGraphTime.internalData.size >= 5) {
                    var sum = 0.0f
                    val avgTime = vGraphTime.internalData.mapIndexed { index, pair ->
                        if (index == 0) null
                        else {
                            val prev = vGraphTime.internalData[index - 1]
                            (pair.second.toDouble() - prev.second.toDouble()) / (pair.first.toDouble() - prev.first.toDouble())
                        }
                    }.filterNotNull().average()
                    vGraphTime.internalData.forEachIndexed { index, pair ->
                        if (index != 0) {
                            val prev = vGraphTime.internalData[index - 1]
                            val gradient =
                                (pair.second.toDouble() - prev.second.toDouble()) / (pair.first.toDouble() - prev.first.toDouble())
                            sum += (gradient - avgTime).pow(2).toFloat()
                        }
                    }
                    vGraphTimeDeviation.addData(
                        currentTrackedSecond,
                        sqrt((sum / (vGraphTime.internalData.size - 1 - 1).toFloat()))
                    )
                }

            }
            currentTrackedSecond = thisSecond
            packetsThisSecond = 0L
        }
        packetsThisSecond++
    }

    override fun onRequestCompleted(device: BluetoothDevice) {
        vGraphAccel.setTitle("Accelerometer")
        vGraphGyro.setTitle("Gyroscope")
        vGraphTime.setTitle("Packet Delivery Delay")
        vGraphDataRate.setTitle("Data Rate")
        vGraphDataRateDeviation.setTitle("Data Rate Variance")
    }

    override fun onRequestFailed(device: BluetoothDevice, status: Int) {
        vGraphAccel.setTitle("Failed Data Notify $status")
        vGraphGyro.setTitle("Failed Data Notify $status")
        vGraphTime.setTitle("Failed Data Notify $status")
        vGraphDataRate.setTitle("Failed Data Notify $status")
        vGraphDataRateDeviation.setTitle("Failed Data Notify $status")
    }

    fun resetGraphFragments() {
        vGraphAccel.internalData.clear()
        vGraphGyro.internalData.clear()
        vGraphTime.internalData.clear()
        vGraphDataRate.internalData.clear()
    }

    companion object {
        private val TAG = CharaDataManager::class.java.simpleName.filter { it.isUpperCase() }
    }
}