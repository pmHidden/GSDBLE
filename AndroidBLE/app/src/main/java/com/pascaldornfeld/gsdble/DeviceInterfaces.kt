package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothDevice
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import com.pascaldornfeld.gsdble.overview.models.ImuData


/**
 * Interface To Read Events From Device
 */
interface ReadDeviceIfc {
    fun afterConnect()
    fun afterDisconnect()
    fun readImuData(imuData: ImuData)
    fun readImuConfig(imuConfig: ImuConfig)
    fun readMtu(mtu: Int)
    fun readConnectionSpeed(interval: Int, latency: Int, timeout: Int)
}


/**
 * Interface To Write Events To Device
 */
interface WriteDeviceIfc {
    fun doConnect(device: BluetoothDevice)
    fun doDisconnect()
    fun writeImuConfig(imuConfig: ImuConfig)
    fun writeMtu(mtu: Int)
    fun writeConnectionPriority(priority: Int)
}