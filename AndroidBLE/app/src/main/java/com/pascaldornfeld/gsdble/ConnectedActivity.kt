package com.pascaldornfeld.gsdble

import android.bluetooth.*
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class ConnectedActivity : AppCompatActivity() {
    private val vRecyclerView by lazy(LazyThreadSafetyMode.NONE) { findViewById<RecyclerView>(R.id.recyclerView) }
    private val vTextView by lazy(LazyThreadSafetyMode.NONE) { findViewById<TextView>(R.id.tv_bps) }

    var timeLog = Pair(0L, 0)
    private val adapter by lazy(LazyThreadSafetyMode.NONE) { BleDeviceListAdapter() }
    private var currentList = listOf<Pair<Long, BluetoothGattCharacteristic>>()

    private val callback by lazy(LazyThreadSafetyMode.NONE) {
        object : BluetoothGattCallback() {
            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                super.onCharacteristicChanged(gatt, characteristic)
                if (characteristic != null) characteristicChanged(gatt, characteristic)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                servicesDiscovered(gatt, status)
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                connectionChanged(gatt, status, newState)
            }
        }
    }

    private fun connectionChanged(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            gatt?.discoverServices()
            gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            try {
                gatt?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            finish()
        } else {
            Log.d("CONNECTED", "onConnectionStateChange: $status $newState")
            Toast.makeText(this@ConnectedActivity, "unknown state: $status $newState", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun servicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        val usefulCharas = gatt?.services?.map { service ->
            service.characteristics.filter { chara -> (chara.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 }
        }?.filter { it.isNotEmpty() }?.flatten()
        val time = System.currentTimeMillis()
        usefulCharas?.forEach {
            gatt.setCharacteristicNotification(it, true)
            val descriptor =
                it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")).apply {
                    value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
            gatt.writeDescriptor(descriptor)
            currentList = currentList.plus(Pair(time, it))
        }
        val list = currentList
        runOnUiThread { adapter.submitList(list) }
    }

    private fun characteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic) {
        val time = System.currentTimeMillis()
        // value logging
        currentList = currentList
            .plus(Pair(time, characteristic))
            .sortedByDescending { it.first }
            .distinctBy { it.second.uuid }
            .sortedBy { it.second.uuid.toString() }
        val list = currentList
        runOnUiThread { adapter.submitList(list) }
        // time logging
        val secs = time / 1000
        timeLog = if (secs == timeLog.first) Pair(secs, timeLog.second + 1)
        else {
            val tempSec = timeLog.second
            runOnUiThread { vTextView.text = tempSec.toString() }
            Pair(secs, 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connected)

        vRecyclerView.adapter = adapter

        val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
        device?.connectGatt(this, false, callback)
    }

    companion object {
        const val EXTRA_DEVICE = "DeviceToConnect"
    }

}
