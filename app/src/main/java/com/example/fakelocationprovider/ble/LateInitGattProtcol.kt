package com.example.fakelocationprovider.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import com.example.fakelocationprovider.GattProtocol

class LateInitGattProtcol : GattProtocol {
    private lateinit var server: BluetoothGattServer

    fun setGattServer(s: BluetoothGattServer?)  {
        if (s != null) {
            server = s
        }
    }

    override fun gattSuccess(device: BluetoothDevice?, requestID: Int, offset: Int) {
        if (::server.isInitialized) {
            server.sendResponse(device, requestID,
                BluetoothGatt.GATT_SUCCESS, offset, null)
        }
    }

    override fun gattFailure(device: BluetoothDevice?, requestID: Int, offset: Int) {
        if (::server.isInitialized) {
            server.sendResponse(device, requestID,
                BluetoothGatt.GATT_FAILURE, offset, null)
        }
    }
}