package com.pascaldornfeld.gsdble.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.ReadDeviceIfc
import com.pascaldornfeld.gsdble.WriteDeviceIfc
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import com.pascaldornfeld.gsdble.overview.models.ImuData
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest.CONNECTION_PRIORITY_BALANCED
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import java.util.*

class GsdbleDevice(context: Context, var readDeviceIfc: ReadDeviceIfc) :
    BleManager<GsdbleCallbacks>(context), WriteDeviceIfc {

    init {
        setGattCallbacks(GsdbleCallbacks(readDeviceIfc, this))
    }

    // WriteDeviceIfc
    override fun isReadyToBeWritten(): Boolean = isReady

    override fun doConnect(device: BluetoothDevice) = connect(device).enqueue()

    override fun doDisconnect() = disconnect().enqueue()

    override fun writeImuConfig(imuConfig: ImuConfig) =
        writeCharacteristic(charaConfig, imuConfig.toByteArray()).enqueue()

    override fun writeMtu(mtu: Int) =
        requestMtu(mtu).with { _, newMtu -> readDeviceIfc.readMtu(newMtu) }.enqueue()

    override fun writeConnectionPriority(priority: Int) =
        requestConnectionPriority(priority).with { _, interval, latency, timeout ->
            readDeviceIfc.readConnectionSpeed(interval, latency, timeout)
        }.enqueue()

    // BluetoothGattCharacteristics
    private var charaData: BluetoothGattCharacteristic? = null
    private var charaConfig: BluetoothGattCharacteristic? = null

    // BleManagerGattCallback
    override fun getGattCallback(): BleManagerGattCallback = managerGattCallback

    private val managerGattCallback = object : BleManagerGattCallback() {
        private val callbackNewData =
            DataReceivedCallback { _, data -> readDeviceIfc.readImuData(ImuData(data)) }
        private val callbackNewConfig =
            DataReceivedCallback { _, data -> readDeviceIfc.readImuConfig(ImuConfig(data)) }

        override fun initialize() {
            super.initialize()
            setNotificationCallback(charaData).with(callbackNewData)
            enableNotifications(charaData).enqueue()

            setNotificationCallback(charaConfig).with(callbackNewConfig)
            readCharacteristic(charaConfig).with(callbackNewConfig).enqueue()
            enableNotifications(charaConfig).enqueue()

            writeMtu(23)
            writeConnectionPriority(CONNECTION_PRIORITY_BALANCED)
        }

        override fun onDeviceDisconnected() {
            charaConfig = null
            charaData = null
        }

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service: BluetoothGattService? =
                gatt.getService(UUID.fromString(context.getString(R.string.uuid_service)))
            charaData =
                service?.getCharacteristic(UUID.fromString(context.getString(R.string.uuid_chara_data)))
            charaConfig =
                service?.getCharacteristic(UUID.fromString(context.getString(R.string.uuid_chara_cfg)))

            val propertiesData = charaData?.properties ?: 0
            val propertiesConfig = charaConfig?.properties ?: 0
            return !(propertiesData and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 ||
                    propertiesConfig and BluetoothGattCharacteristic.PROPERTY_READ == 0 ||
                    propertiesConfig and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
                    propertiesConfig and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0)
        }
    }
}