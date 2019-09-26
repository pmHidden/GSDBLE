package com.pascaldornfeld.gsdble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : FragmentActivity() {
    private val bleReady = MutableLiveData<Boolean>()
    private val bluetoothAdapter: BluetoothAdapter? by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter }
    private val fragmentList = mutableListOf<Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val connectFragment = ConnectFragment(
            bleReady,
            { bluetoothAdapter!!.bluetoothLeScanner },
            { bluetoothDevice -> })
        val fragmentAdapter =
            MyFragmentAdapter(supportFragmentManager, connectFragment, fragmentList)
        vFragmentPager.adapter = fragmentAdapter

        if (bluetoothAdapter != null) {
        } else bleReady.postValue(false)
    }


    override fun onStart() {
        super.onStart()
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter!!.isEnabled) startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                REQUEST_ENABLE_BT
            )
            else askForLocationPermission()
        } else {
            Toast.makeText(this, R.string.bt_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) askForLocationPermission()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_LOC && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            bleReady.postValue(true)
    }

    private fun askForLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_PERMISSION_LOC
            )
            return
        }
        bleReady.postValue(true)
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSION_LOC = 2
    }
}