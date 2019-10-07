package com.pascaldornfeld.gsdble.connected.gsdble_library

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.pascaldornfeld.gsdble.BuildConfig
import com.pascaldornfeld.gsdble.connected.gsdble_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.gsdble_library.models.ImuData
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest.CONNECTION_PRIORITY_BALANCED
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import java.util.*

/**
 * Manages a gsdble device. Implements a WriteToDeviceInterface to control the gsdble device.
 * Connects to the device on initialization.
 *
 * @param device The gsdble device to connect to. Obtained from ScanResult after a ble scan
 * @param context A context
 * @param readFromDeviceIfc Implementation of a ReadFromDeviceInterface. Responses from gsdble device are calling its methods
 */
class GsdbleManager(
    device: BluetoothDevice,
    context: Context,
    readFromDeviceIfc: ReadFromDeviceIfc
) : WriteToDeviceIfc {
    private val gsdbleManager = GsdbleBleManager(context, readFromDeviceIfc, this)
        .apply { connect(device).enqueue() }

    // WriteToDeviceIfc

    override fun doDisconnect() = gsdbleManager.disconnect().enqueue()

    override fun writeImuConfig(imuConfig: ImuConfig) {
        if (gsdbleManager.isReady) gsdbleManager.writeImuConfig(imuConfig)
    }

    override fun writeMtu(mtu: Int) {
        if (gsdbleManager.isReady) gsdbleManager.writeMtu(mtu)
    }

    override fun writeConnectionPriority(priority: Int) {
        if (gsdbleManager.isReady) gsdbleManager.writeConnectionPriority(priority)
    }

    // BleManager

    private class GsdbleBleManager(
        context: Context,
        var readFromDeviceIfc: ReadFromDeviceIfc,
        writeToDeviceIfc: WriteToDeviceIfc
    ) : BleManager<GsdbleCallbacks>(context) {
        init {
            setGattCallbacks(
                GsdbleCallbacks(
                    readFromDeviceIfc,
                    writeToDeviceIfc
                )
            )
        }

        // internal pseudo-WriteToDeviceIfc

        internal fun writeImuConfig(imuConfig: ImuConfig) =
            writeCharacteristic(charaConfig, imuConfig.toByteArray()).enqueue()

        internal fun writeMtu(mtu: Int) =
            requestMtu(mtu).with { _, newMtu -> readFromDeviceIfc.readMtu(newMtu) }.enqueue()

        internal fun writeConnectionPriority(priority: Int) =
            requestConnectionPriority(priority).with { _, interval, latency, timeout ->
                readFromDeviceIfc.readConnectionSpeed(interval, latency, timeout)
            }.enqueue()

        // BluetoothGattCharacteristics
        private var charaData: BluetoothGattCharacteristic? = null
        private var charaConfig: BluetoothGattCharacteristic? = null

        // BleManagerGattCallback
        override fun getGattCallback(): BleManagerGattCallback = managerGattCallback

        private val managerGattCallback = object : BleManagerGattCallback() {
            private val callbackNewData =
                DataReceivedCallback { _, data -> readFromDeviceIfc.readImuData(ImuData(data)) }
            private val callbackNewConfig =
                DataReceivedCallback { _, data -> readFromDeviceIfc.readImuConfig(ImuConfig(data)) }

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
                    gatt.getService(UUID.fromString(BuildConfig.UUID_SERVICE))
                charaData =
                    service?.getCharacteristic(UUID.fromString(BuildConfig.UUID_CHARA_DATA))
                charaConfig =
                    service?.getCharacteristic(UUID.fromString(BuildConfig.UUID_CHARA_CONFIG))

                val propertiesData = charaData?.properties ?: 0
                val propertiesConfig = charaConfig?.properties ?: 0
                return !(propertiesData and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_READ == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0)
            }
        }
    }
}