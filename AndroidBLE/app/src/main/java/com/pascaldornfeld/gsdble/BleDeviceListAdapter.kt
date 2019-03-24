package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothGattCharacteristic
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class BleDeviceListAdapter :
    ListAdapter<Pair<Long, BluetoothGattCharacteristic>, BleDeviceViewHolder>(object :
        DiffUtil.ItemCallback<Pair<Long, BluetoothGattCharacteristic>>() {
        override fun areItemsTheSame(
            oldItem: Pair<Long, BluetoothGattCharacteristic>,
            newItem: Pair<Long, BluetoothGattCharacteristic>
        ): Boolean = oldItem.second.uuid == newItem.second.uuid

        override fun areContentsTheSame(
            oldItem: Pair<Long, BluetoothGattCharacteristic>,
            newItem: Pair<Long, BluetoothGattCharacteristic>
        ): Boolean = oldItem == newItem

    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleDeviceViewHolder =
        BleDeviceViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.viewholder_bledevice, parent, false))

    override fun onBindViewHolder(holder: BleDeviceViewHolder, position: Int) {
        holder.onBind(getItem(position))
    }
}