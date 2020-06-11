package com.example.fakelocationprovider

import android.bluetooth.BluetoothDevice

interface GattProtocol {
    fun gattSuccess(device: BluetoothDevice?, requestID: Int, offset: Int)

    fun gattFailure(device: BluetoothDevice?, requestID: Int, offset: Int)
}