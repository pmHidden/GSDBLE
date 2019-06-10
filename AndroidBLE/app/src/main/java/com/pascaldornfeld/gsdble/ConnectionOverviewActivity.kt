package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.pascaldornfeld.gsdble.fragments.*
import com.pascaldornfeld.gsdble.models.ImuConfig
import com.pascaldornfeld.gsdble.models.ImuData
import no.nordicsemi.android.ble.BleManagerCallbacks
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.callback.SuccessCallback

class ConnectionOverviewActivity : AppCompatActivity(), BleManagerCallbacks {
    // connection related
    private val callbackData: Manager.CharaCallbacks<ImuData> by lazy {
        Manager.CharaCallbacks(object :
            Manager.CharaCallbacks.MyDataReceivedCallback<ImuData> {
            var currentTrackedSecond = 0L
            var packetsThisSecond = 0L

            override fun onNewData(data: ImuData) {
                val timeOfPacketArrival = System.currentTimeMillis()

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
                    if (currentTrackedSecond != 0L) vGraphDataRate.addData(currentTrackedSecond, packetsThisSecond)
                    currentTrackedSecond = thisSecond
                    packetsThisSecond = 0L
                }
                packetsThisSecond++
            }
        }, SuccessCallback {
            vGraphAccel.setTitle("Accelerometer")
            vGraphGyro.setTitle("Gyroscope")
            vGraphTime.setTitle("Packet Delivery Delay")
            vGraphDataRate.setTitle("Data Rate")
        }, FailCallback { _, status ->
            vGraphAccel.setTitle("Failed Data Notify $status")
            vGraphGyro.setTitle("Failed Data Notify $status")
            vGraphTime.setTitle("Failed Data Notify $status")
            vGraphDataRate.setTitle("Failed Data Notify $status")
        })
    }

    private var lastConfig: ImuConfig? = null
    private val callbackConfig: Manager.CharaCallbacks<ImuConfig> by lazy {
        Manager.CharaCallbacks(object :
            Manager.CharaCallbacks.MyDataReceivedCallback<ImuConfig> {
            override fun onNewData(data: ImuConfig) {
                lastConfig = data
                vConfigOdr.setNewData(data.odr)
            }
        }, SuccessCallback {
            vConfigOdr.setTitle("ODR")
            vConfigOdr.functionToApply = {
                // write new config
                if (lastConfig != null) {
                    lastConfig = lastConfig!!.copy(odr = it)
                    manager.writeNewConfig(lastConfig!!)
                }
            }
        },
            FailCallback { _, status -> vConfigOdr.setTitle("Failed Notify Config $status") })
    }
    private val manager: Manager<ConnectionOverviewActivity> by lazy {
        Manager<ConnectionOverviewActivity>(
            this,
            callbackData,
            callbackConfig,
            object : Manager.CharaCallbacks.MyDataReceivedCallback<IntervalFragment.Interval> {
                override fun onNewData(data: IntervalFragment.Interval) = vConfigIntv.setNewData(data)
            }
        )
    }

    // view related
    private val vGraphAccel by lazy { supportFragmentManager.findFragmentById(R.id.graph_accel) as SensorGraphFragment }
    private val vGraphGyro by lazy { supportFragmentManager.findFragmentById(R.id.graph_gyro) as SensorGraphFragment }
    private val vGraphTime by lazy { supportFragmentManager.findFragmentById(R.id.graph_time) as TimeGraphFragment }
    private val vGraphDataRate by lazy { supportFragmentManager.findFragmentById(R.id.graph_dr) as DataRateGraphFragment }
    private val vConfigOdr by lazy { supportFragmentManager.findFragmentById(R.id.config_odr) as OdrFragment }
    private val vConfigIntv by lazy { supportFragmentManager.findFragmentById(R.id.config_intv) as IntervalFragment }

    /**
     * connect to the device that was found on start
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connected)

        val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
        manager.setGattCallbacks(this)
        manager.connect(device).enqueue()
    }

    override fun onBackPressed() {
        if (manager.isConnected) manager.disconnect().enqueue()
        else super.onBackPressed()
    }

    companion object {
        const val EXTRA_DEVICE = "DeviceToConnect"
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceDisconnecting")
        lastConfig = null
        vConfigIntv.functionToApply = null
        vConfigOdr.functionToApply = null
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceDisconnected")
        onBackPressed()
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceConnected")
    }

    override fun onDeviceNotSupported(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceNotSupported")
        onBackPressed()
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        Log.v(TAG, "onBondingFailed")
    }

    override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) {
        Log.v(TAG, "onServicesDiscovered")
    }

    override fun onBondingRequired(device: BluetoothDevice) {
        Log.v(TAG, "onBondingRequired")
    }

    override fun onLinkLossOccurred(device: BluetoothDevice) {
        Log.v(TAG, "onLinkLossOccurred")
        onBackPressed()
    }

    override fun onBonded(device: BluetoothDevice) {
        Log.v(TAG, "onBonded")
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceReady")
        vConfigIntv.functionToApply = { manager.writeNewInterval(it) }
        vConfigIntv.setTitle("Interval Time")
    }

    override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
        Log.v(TAG, "onError")
        onBackPressed()
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceConnecting")
    }

    private val TAG = "MY_MANAGER"
}