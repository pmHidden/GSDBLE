package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pascaldornfeld.gsdble.fragments.*
import com.pascaldornfeld.gsdble.models.ImuConfig
import com.pascaldornfeld.gsdble.models.ImuData
import no.nordicsemi.android.ble.BleManagerCallbacks
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.callback.SuccessCallback
import kotlin.math.pow
import kotlin.math.sqrt

class ConnectionOverviewActivity : AppCompatActivity(), BleManagerCallbacks {
    private var lastConfig: ImuConfig? = null

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
                    if (currentTrackedSecond != 0L) {
                        vGraphDataRate.addData(currentTrackedSecond, packetsThisSecond)

                        val tempCurConf = lastConfig
                        if (tempCurConf != null && vGraphDataRate.internalData.size >= 30) {
                            var sum = 0.0f
                            vGraphDataRate.internalData.forEach {
                                sum += (it.second - ImuConfig.actualOdr[tempCurConf.odr.ordinal]).toFloat().pow(2)
                            }
                            vGraphDataRateVariance.addData(
                                currentTrackedSecond,
                                sqrt((sum / (vGraphDataRate.internalData.size - 1).toFloat()))
                            )
                        }
                    }
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
            vGraphDataRateVariance.setTitle("Data Rate Variance")
        }, FailCallback { _, status ->
            vGraphAccel.setTitle("Failed Data Notify $status")
            vGraphGyro.setTitle("Failed Data Notify $status")
            vGraphTime.setTitle("Failed Data Notify $status")
            vGraphDataRate.setTitle("Failed Data Notify $status")
            vGraphDataRateVariance.setTitle("Failed Data Notify $status")
        })
    }

    private val callbackConfig: Manager.CharaCallbacks<ImuConfig> by lazy {
        Manager.CharaCallbacks(object :
            Manager.CharaCallbacks.MyDataReceivedCallback<ImuConfig> {
            override fun onNewData(data: ImuConfig) {
                lastConfig = data
                vConfigOdr.setNewData(data.odr)
                vConfigPause.setNewData(data.paused)
                vGraphAccel.internalData.clear()
                vGraphGyro.internalData.clear()
                vGraphTime.internalData.clear()
                vGraphDataRate.internalData.clear()
            }
        }, SuccessCallback {
            vConfigPause.setTitle("Pause")
            vConfigPause.functionToApply = {
                // write new config
                if (lastConfig != null) {
                    lastConfig = lastConfig!!.copy(paused = it)
                    manager.writeNewConfig(lastConfig!!)
                }
            }

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
        ) {
            vConfigMtu.setNewData(it)
        }
    }

    // view related
    private val vGraphAccel by lazy { supportFragmentManager.findFragmentById(R.id.graph_accel) as SensorGraphFragment }
    private val vGraphGyro by lazy { supportFragmentManager.findFragmentById(R.id.graph_gyro) as SensorGraphFragment }
    private val vGraphTime by lazy {
        val view = supportFragmentManager.findFragmentById(R.id.graph_time) as LongTimeGraphFragment
        view.initialize(10000.0f, null, "data time vs delivery time")
        view
    }
    private val vGraphDataRate by lazy {
        val view = supportFragmentManager.findFragmentById(R.id.graph_dr) as LongTimeGraphFragment
        view.initialize(35000.0f, 1000.0f, "data time vs data rate")
        view
    }
    private val vGraphDataRateVariance by lazy {
        val view = supportFragmentManager.findFragmentById(R.id.graph_data_rate_variance) as FloatTimeGraphFragment
        view.initialize(10000.0f, 1000.0f, "variance")
        view
    }
    private val vConfigOdr by lazy { supportFragmentManager.findFragmentById(R.id.config_odr) as OdrFragment }
    private val vConfigPause by lazy { supportFragmentManager.findFragmentById(R.id.config_pause) as PauseFragment }
    private val vConfigIntv by lazy { supportFragmentManager.findFragmentById(R.id.config_intv) as IntervalFragment }
    private val vConfigMtu by lazy { supportFragmentManager.findFragmentById(R.id.config_mtu) as MtuFragment }
    private val vConfigSensor by lazy { supportFragmentManager.findFragmentById(R.id.config_sensor) as SensorFragment }

    /**
     * connect to the device that was found on start
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connected)

        vConfigSensor.setTitle("Sensor")
        vConfigSensor.functionToApply = {
            ImuConfig.actualOdr = if(it.ordinal == 0) ImuConfig.LSM_REAL_ODR else ImuConfig.BMI_REAL_ODR
            ImuData.actualTimeScaleMs = if(it.ordinal == 0) ImuData.TIME_SCALE_MS_LSM else ImuData.TIME_SCALE_MS_BMI
            vGraphAccel.internalData.clear()
            vGraphGyro.internalData.clear()
            vGraphTime.internalData.clear()
            vConfigSensor.setNewData(it)
        }

        val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
        manager.setGattCallbacks(this)
        manager.connect(device).enqueue()
    }

    override fun onBackPressed() {
        manager.disconnect().enqueue()
    }

    companion object {
        const val EXTRA_DEVICE = "DeviceToConnect"
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceDisconnecting")
        lastConfig = null
        vConfigIntv.functionToApply = null
        vConfigOdr.functionToApply = null
        vConfigPause.functionToApply = null
        vConfigMtu.functionToApply = null
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceDisconnected")
        super.onBackPressed()
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceConnected")
    }

    override fun onDeviceNotSupported(device: BluetoothDevice) {
        Log.v(TAG, "onDeviceNotSupported")
        manager.errorRefresh()
        Toast.makeText(this, "I hate when this happens. Please restart bluetooth",  Toast.LENGTH_LONG).show()
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
        vConfigMtu.setTitle("MTU")
        vConfigMtu.functionToApply = { manager.writeNewMtu(it) }
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