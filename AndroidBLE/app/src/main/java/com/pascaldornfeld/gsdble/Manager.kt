package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.pascaldornfeld.gsdble.fragments.IntervalFragment
import com.pascaldornfeld.gsdble.models.ImuConfig
import com.pascaldornfeld.gsdble.models.ImuData
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.BleManagerCallbacks
import no.nordicsemi.android.ble.ConnectionPriorityRequest.*
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.callback.SuccessCallback
import java.util.*

class Manager<E : BleManagerCallbacks>(
    context: Context,
    private val callbacksData: CharaCallbacks<ImuData>,
    private val callbacksConfig: CharaCallbacks<ImuConfig>,
    private val callbackInterval: CharaCallbacks.MyDataReceivedCallback<IntervalFragment.Interval>
) : BleManager<E>(context) {
    data class CharaCallbacks<DataType>(
        val newDataCallback: MyDataReceivedCallback<DataType>,
        val notifySuccessCallback: SuccessCallback,
        val notifyFailCallback: FailCallback
    ) {
        interface MyDataReceivedCallback<DataType> {
            fun onNewData(data: DataType)
        }
    }


    private var charaData: BluetoothGattCharacteristic? = null
    private var charaConfig: BluetoothGattCharacteristic? = null

    override fun getGattCallback(): BleManagerGattCallback = managerGattCallback
    private val managerGattCallback by lazy {
        object : BleManagerGattCallback() {
            private val callbackNewData by lazy {
                DataReceivedCallback { _, data ->
                    // TODO use data.getXXValue
                    data.value?.let { ImuData.fromByteArray(it) }
                        ?.let { callbacksData.newDataCallback.onNewData(it) }
                }
            }
            private val callbackNewConfig by lazy {
                DataReceivedCallback { _, data ->
                    // TODO use data.getXXValue
                    data.value?.let { ImuConfig.fromByteArray(it) }
                        ?.let { callbacksConfig.newDataCallback.onNewData(it) }
                }
            }

            override fun initialize() {
                super.initialize()
                setNotificationCallback(charaData).with(callbackNewData)
                enableNotifications(charaData).done(callbacksData.notifySuccessCallback)
                    .fail(callbacksData.notifyFailCallback).enqueue()

                setNotificationCallback(charaConfig).with(callbackNewConfig)
                readCharacteristic(charaConfig).with(callbackNewConfig).enqueue()
                enableNotifications(charaConfig).done(callbacksConfig.notifySuccessCallback)
                    .fail(callbacksConfig.notifyFailCallback).enqueue()

                writeNewInterval(IntervalFragment.Interval.CONNECTION_PRIORITY_BALANCED)
            }

            override fun onDeviceDisconnected() {
                charaConfig = null
                charaData = null
            }

            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service: BluetoothGattService? =
                    gatt.getService(UUID.fromString(context.getString(R.string.uuid_service)))
                charaData = service?.getCharacteristic(UUID.fromString(context.getString(R.string.uuid_chara_data)))
                charaConfig = service?.getCharacteristic(UUID.fromString(context.getString(R.string.uuid_chara_cfg)))

                val propertiesData = charaData?.properties ?: 0
                val propertiesConfig = charaConfig?.properties ?: 0
                return !(propertiesData and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_READ == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0)
            }
        }
    }

    fun writeNewConfig(config: ImuConfig) = writeCharacteristic(charaConfig, config.toByteArray()).enqueue()

    fun writeNewInterval(interval: IntervalFragment.Interval) {
        when (interval) {
            IntervalFragment.Interval.CONNECTION_PRIORITY_LOW_POWER ->
                requestConnectionPriority(CONNECTION_PRIORITY_LOW_POWER).enqueue()
            IntervalFragment.Interval.CONNECTION_PRIORITY_BALANCED ->
                requestConnectionPriority(CONNECTION_PRIORITY_BALANCED).enqueue()
            IntervalFragment.Interval.CONNECTION_PRIORITY_HIGH ->
                requestConnectionPriority(CONNECTION_PRIORITY_HIGH).enqueue()
        }
        callbackInterval.onNewData(interval)
    }
}