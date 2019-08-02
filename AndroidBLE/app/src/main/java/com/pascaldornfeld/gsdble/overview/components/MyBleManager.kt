package com.pascaldornfeld.gsdble.overview.components

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.overview.fragments.IntervalFragment
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import com.pascaldornfeld.gsdble.overview.models.ImuData
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest.*
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class MyBleManager(
    context: Context,
    fragmentById: ((Int) -> Fragment?),
    callbacksData: CharaDataManager,
    lastConfig: AtomicReference<ImuConfig?>,
    device: BluetoothDevice,
    onDisconnectFunction: (() -> Unit)
) : BleManager<MyBleManagerCallbacks>(context) {
    interface MyDataReceivedCallback<DataType> {
        fun onNewData(data: DataType)
    }

    private val charaConfigManager: CharaConfigManager =
        CharaConfigManager(fragmentById, lastConfig, { callbacksData.resetGraphFragments() })

    private val myBleCallbacks = MyBleManagerCallbacks(
        this,
        fragmentById,
        { charaConfigManager.disableOdrAndPauseConfig() },
        { onDisconnectFunction() },
        context
    )

    private val charaConfigSuccessCallback by lazy { charaConfigManager.buildSuccessCallback(this) }

    private var charaData: BluetoothGattCharacteristic? = null
    private var charaConfig: BluetoothGattCharacteristic? = null

    override fun getGattCallback(): BleManagerGattCallback = managerGattCallback
    private val managerGattCallback by lazy {
        object : BleManagerGattCallback() {
            private val callbackNewData by lazy {
                DataReceivedCallback { _, data ->
                    // TODO use data.getXXValue
                    data.value?.let { ImuData.fromByteArray(it) }
                        ?.let { callbacksData.onNewData(it) }
                }
            }
            private val callbackNewConfig by lazy {
                DataReceivedCallback { _, data ->
                    // TODO use data.getXXValue
                    data.value?.let { ImuConfig.fromByteArray(it) }
                        ?.let { charaConfigManager.onNewData(it) }
                }
            }

            override fun initialize() {
                super.initialize()
                setNotificationCallback(charaData).with(callbackNewData)
                enableNotifications(charaData).done(callbacksData)
                    .fail(callbacksData).enqueue()

                setNotificationCallback(charaConfig).with(callbackNewConfig)
                readCharacteristic(charaConfig).with(callbackNewConfig).enqueue()
                enableNotifications(charaConfig).done(charaConfigSuccessCallback)
                    .fail(charaConfigManager).enqueue()

                writeNewMtu(23)
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

    override fun shouldClearCacheWhenDisconnected(): Boolean = true

    fun writeNewInterval(interval: IntervalFragment.Interval) {
        when (interval) {
            IntervalFragment.Interval.CONNECTION_PRIORITY_LOW_POWER ->
                requestConnectionPriority(CONNECTION_PRIORITY_LOW_POWER).enqueue()
            IntervalFragment.Interval.CONNECTION_PRIORITY_BALANCED ->
                requestConnectionPriority(CONNECTION_PRIORITY_BALANCED).enqueue()
            IntervalFragment.Interval.CONNECTION_PRIORITY_HIGH ->
                requestConnectionPriority(CONNECTION_PRIORITY_HIGH).enqueue()
        }
        myBleCallbacks.onNewInterval(interval)
    }

    fun writeNewMtu(pMtu: Int) = requestMtu(pMtu).with { _, mtu -> myBleCallbacks.onNewMtu(mtu) }.enqueue()

    fun writeNewConfig(config: ImuConfig) = writeCharacteristic(charaConfig, config.toByteArray()).enqueue()

    fun errorRefresh() = refreshDeviceCache().enqueue()

    fun disconnectDevice() = disconnect().enqueue()

    init {
        setGattCallbacks(myBleCallbacks)
        connect(device).enqueue()
    }
}