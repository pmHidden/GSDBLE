package com.pascaldornfeld.gsdble.overview.components

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.overview.fragments.FloatTimeGraphFragment
import com.pascaldornfeld.gsdble.overview.fragments.LongTimeGraphFragment
import com.pascaldornfeld.gsdble.overview.fragments.SensorGraphFragment
import com.pascaldornfeld.gsdble.overview.models.ImuData
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.callback.SuccessCallback
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CharaDataManager(fragmentById: ((Int) -> Fragment?)) : MyBleManager.MyDataReceivedCallback<ImuData>,
    SuccessCallback, FailCallback {
    private val vGraphAccel = fragmentById(R.id.vGraphAccel) as SensorGraphFragment
    private val vGraphGyro = fragmentById(R.id.vGraphGyro) as SensorGraphFragment
    private val vGraphTime = (fragmentById(R.id.vGraphTime) as LongTimeGraphFragment)
        .initialize(300000.0f, null, "data time vs delivery time")
    private val vGraphDataRate = (fragmentById(R.id.vGraphDataRate) as LongTimeGraphFragment)
        .initialize(300000.0f, 1000.0f, "data time vs data rate")

    private val vGraphDataRateDeviation = (fragmentById(R.id.vGraphDataRateDeviation) as FloatTimeGraphFragment)
        .initialize(10000.0f, 1000.0f, "deviation")
    private val dataRateDeviationExecutor = Executors.newSingleThreadExecutor()

    private val vGraphTimeDeviation = (fragmentById(R.id.vGraphTimeDeviation) as FloatTimeGraphFragment)
        .initialize(10000.0f, 1000.0f, "deviation")
    private val graphTimeDeviationExecutor = Executors.newSingleThreadExecutor()

    private var currentTrackedSecond = 0L
    private var packetsThisSecond = 0L
    private val clearScheduled = AtomicBoolean(false)

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

                val clearGraphs = clearScheduled.compareAndSet(true, false)
                val graphTimeData =
                    if (clearGraphs || vGraphTimeDeviation.isPaused()) null else vGraphTime.internalData.toTypedArray()
                CharaDataDeviationCalculatorAsyncTask().executeOnExecutor(
                    graphTimeDeviationExecutor,
                    Triple(currentTrackedSecond, graphTimeData, vGraphTimeDeviation)
                )

                val dataRateData =
                    if (clearGraphs || vGraphDataRateDeviation.isPaused()) null else vGraphDataRate.internalData.toTypedArray()
                CharaDataDeviationCalculatorAsyncTask().executeOnExecutor(
                    dataRateDeviationExecutor,
                    Triple(currentTrackedSecond, dataRateData, vGraphDataRateDeviation)
                )
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
        vGraphTimeDeviation.setTitle("Delay Variance")
        vGraphDataRate.setTitle("Data Rate")
        vGraphDataRateDeviation.setTitle("Data Rate Variance")
    }

    override fun onRequestFailed(device: BluetoothDevice, status: Int) {
        vGraphAccel.setTitle("Failed Data Notify $status")
        vGraphGyro.setTitle("Failed Data Notify $status")
        vGraphTime.setTitle("Failed Data Notify $status")
        vGraphTimeDeviation.setTitle("Failed Data Notify $status")
        vGraphDataRate.setTitle("Failed Data Notify $status")
        vGraphDataRateDeviation.setTitle("Failed Data Notify $status")
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