package com.pascaldornfeld.gsdble

import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class ConnectionOverviewActivity : AppCompatActivity() {
    // connection related
    private var gattConnection: BluetoothGatt? = null
    private val callback by lazy {
        object : BluetoothGattCallback() {
            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                super.onCharacteristicChanged(gatt, characteristic)
                if (gatt != null && characteristic != null) characteristicChanged(gatt, characteristic)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (gatt != null) servicesDiscovered(gatt, status)
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                if (gatt != null) connectionChanged(gatt, status, newState)
            }
        }
    }
    private var charaCfg: BluetoothGattCharacteristic? = null

    // view related
    private val graphAccel by lazy { supportFragmentManager.findFragmentById(R.id.graph_accel) as SensorGraphFragment }
    private val graphGyro by lazy { supportFragmentManager.findFragmentById(R.id.graph_gyro) as SensorGraphFragment }
    private val graphTime by lazy { supportFragmentManager.findFragmentById(R.id.graph_time) as TimeGraphFragment }
    private val graphDataRate by lazy { supportFragmentManager.findFragmentById(R.id.graph_dr) as DataRateGraphFragment }

    /**
     * connect to the device that was found on start
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connected)

        graphAccel.setTitle("Accelerometer")
        graphGyro.setTitle("Gyroscope")
        graphTime.setTitle("Packet Delivery Delayf")
        graphDataRate.setTitle("Data Rate")

        val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
        device?.connectGatt(this, false, callback)
    }

    /**
     * on connect: discover devices. set connection priority to high priority.
     * on disconnect: close gatt and finish activity
     * else: print what happened
     */
    private fun connectionChanged(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()
            if (!gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)) Toast.makeText(
                this, "could not set high priority",
                LENGTH_SHORT
            ).show()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            try {
                gatt.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            finish()
        } else {
            Log.e(TAG, "onConnectionStateChange unknown: $status $newState")
            Toast.makeText(this@ConnectionOverviewActivity, "unknown state: $status $newState", LENGTH_SHORT)
                .show()
        }
        gattConnection = gatt
    }

    /**
     * on services discovered: check for my service and characteristics
     * if my service not available: notify user and disconnect
     */
    private fun servicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == GATT_SUCCESS) {
            val service = gatt.services.find {
                //it.uuid == UUID.fromString(getString(R.string.uuid_service))
                val lookingFor = UUID.fromString(getString(R.string.uuid_service))
                val isCorrect = it.uuid == UUID.fromString(getString(R.string.uuid_service))
                Log.d(TAG, "uuids svc: $isCorrect. mine: ${it.uuid}. looking for: $lookingFor")
                isCorrect
            }
            if (service != null) {
                val charaData =
                    service.characteristics.find {
                        val lookingFor = UUID.fromString(getString(R.string.uuid_chara_data))
                        val isCorrect = it.uuid == UUID.fromString(getString(R.string.uuid_chara_data))
                        Log.d(TAG, "uuids data: $isCorrect. mine: ${it.uuid}. looking for: $lookingFor")
                        isCorrect
                    }
                if (charaData == null || (charaData.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
                    Toast.makeText(this, "could not find data chara or it is not notifiable", LENGTH_SHORT).show()
                else {
                    gatt.setCharacteristicNotification(charaData, true)
                    val descriptor =
                        charaData.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")).apply {
                            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        }
                    gatt.writeDescriptor(descriptor)
                }
                charaCfg =
                    service.characteristics.find { it.uuid == UUID.fromString(getString(R.string.uuid_chara_cfg)) }
                if (charaData == null)
                    Toast.makeText(this, "could not find cfg chara", LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "could not find my service", LENGTH_SHORT).show()
                onBackPressed()
            }
        } else {
            Toast.makeText(this, "could not discover services", LENGTH_SHORT).show()
            onBackPressed()
        }
    }

    var currentTrackedSecond = 0L
    var packetsThisSecond = 0L

    @Synchronized
    private fun characteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == UUID.fromString(getString(R.string.uuid_chara_data))) {
            val timeOfPacketArrival = System.currentTimeMillis()
            val parsedImuData = ImuData.fromByteArray(characteristic.value)

            graphAccel.addData(
                parsedImuData.time,
                Triple(parsedImuData.accel_x, parsedImuData.accel_y, parsedImuData.accel_z)
            )
            graphGyro.addData(
                parsedImuData.time,
                Triple(parsedImuData.gyro_x, parsedImuData.gyro_y, parsedImuData.gyro_z)
            )
            graphTime.addData(parsedImuData.time, timeOfPacketArrival)

            val thisSecond = timeOfPacketArrival / 1000L
            if (thisSecond != currentTrackedSecond) {
                if (currentTrackedSecond != 0L) graphDataRate.addData(currentTrackedSecond, packetsThisSecond)
                currentTrackedSecond = thisSecond
                packetsThisSecond = 0L
            }
            packetsThisSecond++
        }
    }

    override fun onBackPressed() {
        try {
            gattConnection?.disconnect()
            super.onBackPressed()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val EXTRA_DEVICE = "DeviceToConnect"
        const val TAG = "ConnectionOverviewAct"
    }

}
