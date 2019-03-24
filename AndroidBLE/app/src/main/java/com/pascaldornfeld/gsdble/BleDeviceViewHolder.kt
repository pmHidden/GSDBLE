package com.pascaldornfeld.gsdble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class BleDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    @SuppressLint("SetTextI18n")
    fun onBind(pair: Pair<Long, BluetoothGattCharacteristic>) {
        val time = pair.first.toString()
        itemView.findViewById<TextView>(R.id.tv_name).text =
            pair.second.uuid.toString() + " " + time.substring(
                Math.max(0, time.lastIndex - 4),
                time.lastIndex+1
            ) + " " + Arrays.toString(pair.second.value)
    }


}