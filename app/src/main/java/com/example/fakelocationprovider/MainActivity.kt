package com.example.fakelocationprovider

import android.app.AppOpsManager
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import com.example.fakelocationprovider.ble.BleHelper
import com.example.fakelocationprovider.ble.LateInitGattProtcol
import com.example.fakelocationprovider.location.FakeLocation
import com.example.fakelocationprovider.location.FakeLocationConverter
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

// HACK BackgroundService にした方が良いのだが、簡単のため、Activityで行っている (Bluetooth 関連も)
class MainActivity : AppCompatActivity(), LocationBlePeripheralCallback {

    val REQUEST_ALLOW_LOCATION = 1
    val REQUEST_ENABLE_BT = 2

    val blePeripheral: LocationBlePeripheral = LocationBlePeripheral()

    lateinit var timer: Timer

    lateinit var timerTask: LocationTimerTask


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // BLEから受信した位置情報がある場合、毎秒セットする
        timerTask = LocationTimerTask(this)
        timer = Timer(true)
        timer.schedule(timerTask, 1000, 1000)
    }


    override fun onResume() {
        super.onResume()

        checkFeatures()
    }


    private fun checkFeatures() {
        if (checkLocationServicesPermission() == false) return
        if (checkMockLocationApp() == false) return
        if (checkBluetoothEnabled() == false) return
    }

    // LocationServices のパーミッションを確認
    private fun checkLocationServicesPermission(): Boolean {
        val p = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        var hasPermission = p.all {
            return@all checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            return true
        }

        requestPermissions(p, REQUEST_ALLOW_LOCATION)
        return false
    }


    // このアプリが MOCK_LOCATION に指定されているか確認
    private fun checkMockLocationApp(): Boolean {
        val manager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        try {
            val op = manager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), packageName)
            if (op == AppOpsManager.MODE_ALLOWED) {
                return true
            }
        }
        catch (e: SecurityException) {
        }

        Toast.makeText(this, "仮の現在地情報アプリに設定してください", Toast.LENGTH_LONG).show()

        val i = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        return false
    }

    // bluetooth が有効かどうか
    private fun checkBluetoothEnabled(): Boolean {
        val b = BleHelper.getBluetoothManager(this)
        if (b == null) {
            // TODO warning bluetooth is nothing.
            return false
        }

        if (b.adapter.isEnabled == false) {
            val i = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(i, REQUEST_ENABLE_BT)
            return false
        }

        if (blePeripheral.isPrepared() == false) {
            blePeripheral.prepareGattService(this, b, this)
        }

        return true
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT) {
            checkFeatures()
            return
        }

        if (requestCode == REQUEST_ALLOW_LOCATION) {
            checkFeatures()
            return
        }
    }


    override fun onPause() {
        super.onPause()
    }


    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d("bletest", "disposing")
        blePeripheral.close()

        if (::timer.isInitialized) {
            timer.cancel()
        }

        timerTask.dispose();
    }


    override fun connectedStateChange(connected: Boolean) {
        runOnUiThread {
            this.connected_status?.text = if (connected) "connected" else "disconnected"
        }
    }

    override fun receiveNewLocation(location: FakeLocation?) {
        val date = Date()
        runOnUiThread {
            val timeFormat = SimpleDateFormat("HH:mm:ss")
            val t = timeFormat.format(date)
            this.recent_location?.text = "$t $location"

            timerTask.setLocation(location)
        }
    }
}


interface LocationBlePeripheralCallback {

    fun connectedStateChange(connected: Boolean)

    fun receiveNewLocation(location: FakeLocation?)

}


class LocationBlePeripheral {
    companion object {
        // GATTのコマンドは3つ
        // READ: セントラルからペリフェラルへの要求: ペリフェラルのデータをセントラルへ送信する
        // WRITE: セントラルからペリフェラルへのデータ送信
        // NOTIFY: セントラルからペリフェラルへの要求: ペリフェラルのデータを継続的にセントラルへ送信する

        val UUID_LOCATION_SERVICE_STRING = "a791c20e-aa32-11ea-bb37-0242ac130002"
        val UUID_LOCATION_SERVICE = UUID.fromString(UUID_LOCATION_SERVICE_STRING)
        val UUID_LOCATION_WRITE = UUID.fromString("c473c85e-aa32-11ea-bb37-0242ac130002")
        val UUID_LOCATION_READ = UUID.fromString("c473cab6-aa32-11ea-bb37-0242ac130002")
        //val UUID_LOCATION_NOTIFY = UUID.fromString("c473cbd8-aa32-11ea-bb37-0242ac130002")
    }


    private var gattServer: BluetoothGattServer? = null

    private var gattServerCallback: BleCallback? = null
    private var gattProtocol = LateInitGattProtcol()
    private lateinit var locationCallback: LocationBlePeripheralCallback

    //
    private var gattLocationService: BluetoothGattService? = null
    //private var locationReadCharacteristic: BluetoothGattCharacteristic? = null
    private var locationWriteCharacteristic: BluetoothGattCharacteristic? = null


    fun isPrepared(): Boolean {
        // TODO mutex
        if (gattServer == null) {
            return false
        }
        return true
    }

    fun close() {
        // TODO mutex
        gattServer?.close()
        gattServer = null
    }


    fun prepareGattService(context: Context, manager: BluetoothManager, c: LocationBlePeripheralCallback) {
        // TODO mutex

        if (gattServer != null) {
            return
        }

        locationCallback = c

        gattProtocol = LateInitGattProtcol()
        gattServerCallback = BleCallback(gattProtocol, locationCallback)
        gattServer = manager.openGattServer(context, gattServerCallback)
        gattProtocol.setGattServer(gattServer)

        //
        gattLocationService = BluetoothGattService(UUID_LOCATION_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        // READ コマンドの登録
        //locationReadCharacteristic = BluetoothGattCharacteristic(UUID_LOCATION_READ, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
        //gattLocationService?.addCharacteristic(locationReadCharacteristic)
        // WRITE コマンドの登録
        locationWriteCharacteristic = BluetoothGattCharacteristic(UUID_LOCATION_WRITE, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE)
        gattLocationService?.addCharacteristic(locationWriteCharacteristic)

        gattServer?.addService(gattLocationService)

        startBleAdvertising(manager.adapter)
    }

    // スキャン可能にする
    private fun startBleAdvertising(adapter: BluetoothAdapter) {
        val data = AdvertiseData.Builder()
        data.setIncludeTxPowerLevel(true)
        data.addServiceUuid(ParcelUuid.fromString(UUID_LOCATION_SERVICE_STRING))

        val settings = AdvertiseSettings.Builder()
        settings.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        settings.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        settings.setTimeout(0)
        settings.setConnectable(true)

        val resp = AdvertiseData.Builder()
        resp.setIncludeDeviceName(true)

        val ad = adapter.getBluetoothLeAdvertiser()
        ad.startAdvertising(settings.build(),  data.build(), resp.build(), LogAdvertiseCallback())
    }

}


class LogAdvertiseCallback : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
        super.onStartSuccess(settingsInEffect)

        Log.d("bletest", "onStartSuccess")
    }

    override fun onStartFailure(errorCode: Int) {
        super.onStartFailure(errorCode)

        Log.d("bletest", "onStartFailure $errorCode")
    }
}


class BleCallback(val gatt: GattProtocol, val callback: LocationBlePeripheralCallback) : BluetoothGattServerCallback() {

    override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
        Log.d("bletest", "onMtuChanged: $mtu")
    }

    override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
        Log.d("bletest", "onConnectionStateChange: $newState")

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            callback.connectedStateChange(true)
        }
        else {
            callback.connectedStateChange(false)
        }
    }

    override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
        Log.d("bletest", "onCharacteristicReadRequest")

        gatt.gattFailure(device, requestId, offset)
    }

    override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
        Log.d("bletest", "onCharacteristicWriteRequest")
        if (characteristic != null) {
            if (characteristic.uuid.compareTo(LocationBlePeripheral.UUID_LOCATION_WRITE) == 0) {
                gatt.gattSuccess(device, requestId, offset)

                if (value != null) {
                    var l = FakeLocationConverter.from(value)
                    callback.receiveNewLocation(l)
                }

                return
            }
        }

        if (responseNeeded) {
            gatt.gattFailure(device, requestId, offset)
        }
    }

    override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
        Log.d("bletest", "onDescriptorReadRequest")
    }

    override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
        Log.d("bletest", "onDescriptorWriteRequest")
    }
}




class LocationTimerTask(val c: Context) : TimerTask() {
    val o:Object = Object()

    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    private var isDisposed = false

    private var fakeLocation: FakeLocation? = null

    private var hasAddedTestProvider = false


    fun setLocation(l: FakeLocation?) {
        synchronized(o) {
            fakeLocation = l
        }
    }

    fun dispose() {
        synchronized(o) {
            if (isDisposed) {
                return
            }
            isDisposed = true
        }

        val locationManager = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (p in providers) {
            try {
                locationManager.removeTestProvider(p)
            }
            catch (e: Exception){
                //
            }
        }
    }

    override fun run() {

        var targetLocation: Location? = null
        synchronized(o) {
            if (isDisposed) {
                return
            }

            val date = Date()

            // FakeLocation を Location へ変換
            targetLocation = fakeLocation?.let {
                val l = Location("")
                l.latitude = it.lat
                l.longitude = it.lon
                l.altitude = it.alt
                l.accuracy = it.hacc.toFloat()
                l.time = date.time  // Date.time は UTC時刻を返す
                l.speed = 0f
                l.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                l
            }

            if (targetLocation == null) {
                return
            }

            // 各プロバイダーへ偽の位置情報をセットする
            val locationManager = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            for (p in providers) {
                // 一度実行するだけでいいのかも
                locationManager.addTestProvider(
                    p,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(p, true)

                //
                val newLocation = Location(targetLocation)
                newLocation.provider = p
                locationManager.setTestProviderLocation(p, newLocation)
            }
        }

        // 確認
        LocationServices.getFusedLocationProviderClient(c)
            .lastLocation
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    val l = it.result
                    if (l != null) {
                        Log.d("bletest", "LocationServices.lastLocation ${l.latitude},${l.longitude} alt=${l.altitude} acc=${l.accuracy}")
                    }
                }
                else {
                    val e = it.exception
                    Log.d("bletest", "LocationServices.lastLocation $e")
                }
            }
    }
}





