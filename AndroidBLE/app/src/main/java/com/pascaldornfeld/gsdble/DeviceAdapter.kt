package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

/**
 * adapter to show scan results
 *
 * @param connectCallback what to do, when user wants to doConnect to a device
 */
class DeviceAdapter(private val connectCallback: (BluetoothDevice) -> Unit) :
    ListAdapter<ScanResult, DeviceViewHolder>(SCAN_RESULT_DIFF_CALLBACK) {

    companion object {
        private val SCAN_RESULT_DIFF_CALLBACK = object : DiffUtil.ItemCallback<ScanResult>() {
            override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean =
                oldItem.device.address == newItem.device.address
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder =
        DeviceViewHolder(connectCallback, parent)

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) =
        holder.bind(getItem(position))
}