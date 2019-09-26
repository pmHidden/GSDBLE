package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

/**
 * adapter to show scan results
 *
 * @param connectCallback what to do, when user wants to doConnect to a device
 */
class DeviceAdapter(private val connectCallback: (BluetoothDevice) -> Unit) :
    ListAdapter<ScanResult, DeviceViewHolder>(DIFF_CALLBACK) {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ScanResult>() {
            override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean =
                oldItem.device.address == newItem.device.address
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder =
        DeviceViewHolder(
            connectCallback, LayoutInflater.from(parent.context).inflate(
                R.layout.connect_viewholder,
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}