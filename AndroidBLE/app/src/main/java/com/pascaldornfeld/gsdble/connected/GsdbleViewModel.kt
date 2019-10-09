package com.pascaldornfeld.gsdble.connected

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.pascaldornfeld.gsdble.connected.async_calculations.CharaDataStatsByGradient
import com.pascaldornfeld.gsdble.connected.async_calculations.CharaDataStatsBySecondValue
import com.pascaldornfeld.gsdble.connected.gsdble_library.ReadFromDeviceIfc
import com.pascaldornfeld.gsdble.connected.gsdble_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.gsdble_library.models.ImuData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class GsdbleViewModel(application: Application) : AndroidViewModel(application), ReadFromDeviceIfc {
    val dataAccel = SmallDataHolder<Triple<Short, Short, Short>>(5, 6.4f)
    val dataGyro = SmallDataHolder<Triple<Short, Short, Short>>(5, 6.4f)
    val dataTime = SmallDataHolder<Long>(5, 1000f)
    val dataTimeDev = SmallDataHolder<Double>(10, 1000f)
    val dataDataRate = SmallDataHolder<Long>(300, 1000f)
    val dataDataRateAvg = SmallDataHolder<Double>(10, 1000f)
    val dataDataRateDev = SmallDataHolder<Double>(10, 1000f)
    val dataMtu = MutableLiveData<Int>()
    val dataInterval = MutableLiveData<Int>()
    val dataImuConfig = MutableLiveData<ImuConfig>()
    val disconnected = MutableLiveData<Boolean>(false)

    init {
        disconnected.observeForever(object : Observer<Boolean?> {
            override fun onChanged(t: Boolean?) {
                if (t != null && t) {
                    clearScheduled.set(true)
                    disconnected.removeObserver(this)
                }
            }
        })
    }
    // ReadFromDeviceIfc

    private val dataRateStatExecutor = Executors.newSingleThreadExecutor()
    private val graphTimeStatExecutor = Executors.newSingleThreadExecutor()
    private val clearScheduled = AtomicBoolean(false)
    private var currentTrackedSecond = 0L
    private var packetsThisSecond = 0L

    override fun readImuData(imuData: ImuData) {
        val timeOfPacketArrival = System.currentTimeMillis()
        dataAccel.addData(
            imuData.time,
            Triple(imuData.accel_x, imuData.accel_y, imuData.accel_z)
        )
        dataGyro.addData(
            imuData.time,
            Triple(imuData.gyro_x, imuData.gyro_y, imuData.gyro_z)
        )
        val currentDataTimeList = dataTime.addData(imuData.time, timeOfPacketArrival)

        val thisSecond = timeOfPacketArrival / 1000L
        if (thisSecond != currentTrackedSecond) {
            if (currentTrackedSecond != 0L) {
                val currentDataRateList =
                    dataDataRate.addData(currentTrackedSecond, packetsThisSecond)

                // TODO: fix gradient graph
                // the first graph will not work, when there are multiple y values for the same x values.
                // in that case, it will just print NaN, because it cant calculate the gradient.
                // This happens on LSM6DSL, when the sensordatarate is < 6.4 ms (> 125 Hz).
                // So on LSM6DSL, when you setup a sensordatarate of 200 hz or more.
                val clearGraphs = clearScheduled.compareAndSet(true, false)
                val graphTimeData =
                    if (clearGraphs) null
                    else currentDataTimeList.toTypedArray()
                CharaDataStatsByGradient(
                    currentTrackedSecond,
                    null,
                    dataTimeDev
                ).executeOnExecutor(graphTimeStatExecutor, graphTimeData)

                val dataRateData =
                    if (clearGraphs) null
                    else currentDataRateList.toTypedArray()
                CharaDataStatsBySecondValue(
                    currentTrackedSecond,
                    dataDataRateAvg,
                    dataDataRateDev
                ).executeOnExecutor(dataRateStatExecutor, dataRateData)
            }
            currentTrackedSecond = thisSecond
            packetsThisSecond = 0L
        }
        packetsThisSecond++
    }

    override fun afterDisconnect() = disconnected.postValue(true)

    override fun readImuConfig(imuConfig: ImuConfig) {
        Log.i(TAG, "New Imu Config: $imuConfig")
        dataImuConfig.postValue(imuConfig)
    }

    override fun readMtu(mtu: Int) = dataMtu.postValue(mtu)

    override fun readConnectionSpeed(interval: Int, latency: Int, timeout: Int) =
        dataInterval.postValue(
            when (interval) {
                in Int.MIN_VALUE..22 -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
                in 23..75 -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
                else -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
            }
        )

    companion object {
        private val TAG = GsdbleViewModel::class.java.simpleName.filter { it.isUpperCase() }
    }
}