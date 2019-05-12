package com.pascaldornfeld.gsdble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.nio.ByteBuffer
import java.util.*

class BleDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    @SuppressLint("SetTextI18n")
    fun onBind(pair: Pair<Long, BluetoothGattCharacteristic>) {

        val time = pair.first.toString()
        val valueText: String
        valueText = when {
            pair.second.value == null -> {
                "none"
            }
            pair.second.value.size == 16 -> {
                val buffer = ByteBuffer.wrap(pair.second.value)
                "(accel: x:${buffer.short}, y:${buffer.short}, z: ${buffer.short}. Gyro: x:${buffer.short}, y:${buffer.short}, z: ${buffer.short}, time:${buffer.int})"
            }
            pair.second.value.size == 12 -> {
                val buffer = ByteBuffer.wrap(pair.second.value)
                "(accel: x:${buffer.short}, y:${buffer.short}, z: ${buffer.short}. Gyro: x:${buffer.short}, y:${buffer.short}, z: ${buffer.short})"
            }
            else -> Arrays.toString(pair.second.value)
        }

        itemView.findViewById<TextView>(R.id.tv_name).text =
            pair.second.uuid.toString() + " " + time.substring(
                Math.max(0, time.lastIndex - 4),
                time.lastIndex + 1
            ) + "\n\t" + valueText
    }


}