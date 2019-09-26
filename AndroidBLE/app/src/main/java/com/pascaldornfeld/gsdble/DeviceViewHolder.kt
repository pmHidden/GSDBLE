package com.pascaldornfeld.gsdble


import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.connect_viewholder.view.*

/**
 * viewHolder to show a scan result
 *
 * @param connectCallback what to do, when user wants to doConnect to the device
 * @param itemView view of viewHolder
 */
class DeviceViewHolder(val connectCallback: (BluetoothDevice) -> Unit, itemView: View) :
    RecyclerView.ViewHolder(itemView) {
    /**
     * bind view to a scan result
     */
    fun bind(scanResult: ScanResult) {
        itemView.vConnectButton.setOnClickListener { connectCallback(scanResult.device) }
        itemView.vDeviceAddress.text = scanResult.device.name
    }
}
