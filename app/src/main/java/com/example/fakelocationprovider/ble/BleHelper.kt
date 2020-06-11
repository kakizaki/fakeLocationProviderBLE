package com.example.fakelocationprovider.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

class BleHelper {
    companion object {
        fun getBluetoothManager(c: Context): BluetoothManager? {
            val bluetoothManager = c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            return bluetoothManager
        }

        fun getBluetoothAdapter(c: Context): BluetoothAdapter? {
            val bluetoothManager = c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            return bluetoothManager.adapter
        }
    }
}