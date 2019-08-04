package com.pascaldornfeld.gsdble.overview

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.overview.components.CharaDataManager
import com.pascaldornfeld.gsdble.overview.components.MyBleManager
import com.pascaldornfeld.gsdble.overview.components.SensorConfigManager
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import java.util.concurrent.atomic.AtomicReference

class ConnectionOverviewActivity : AppCompatActivity() {
    private val lastConfig = AtomicReference<ImuConfig?>()
    private lateinit var myBleManager: MyBleManager

    /**
     * connect to the device that was found on start
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connected)

        val charaDataManager = CharaDataManager { id -> supportFragmentManager.findFragmentById(id) }

        SensorConfigManager.initialize(
            { id -> supportFragmentManager.findFragmentById(id) },
            { charaDataManager.resetGraphFragments() }
        )

        val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
        myBleManager = MyBleManager(
            this,
            { id -> supportFragmentManager.findFragmentById(id) },
            charaDataManager,
            lastConfig,
            device,
            { super.onBackPressed() }
        )
    }

    override fun onBackPressed() {
        myBleManager.disconnectDevice()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (myBleManager.isConnected) myBleManager.disconnectDevice()
    }

    companion object {
        const val EXTRA_DEVICE = "DeviceToConnect"
        private val TAG = ConnectionOverviewActivity::class.java.simpleName.filter { it.isUpperCase() }
    }

}