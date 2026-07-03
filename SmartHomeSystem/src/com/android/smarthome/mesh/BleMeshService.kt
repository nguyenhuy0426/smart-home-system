package com.android.smarthome.mesh

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class BleMeshService : Service() {

    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false

    companion object {
        private const val TAG = "BleMeshService"
        // Bluetooth Mesh Provisioning Service UUID for PB-GATT discovery.
        val MESH_SERVICE_UUID: UUID = UUID.fromString("00001827-0000-1000-8000-00805f9b34fb")
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleMeshService = this@BleMeshService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BLE Mesh Service initializing...")
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        startScanning()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY;
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun startScanning() {
        if (isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner not available (Bluetooth disabled or unsupported)")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            isScanning = true
            scanner.startScan(filters, settings, scanCallback)
            Log.i(TAG, "Started BLE Mesh scanning for unprovisioned nodes")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for BLE scanning", e)
        }
    }

    fun stopScanning() {
        if (!isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        try {
            scanner?.stopScan(scanCallback)
            isScanning = false
            Log.i(TAG, "Stopped BLE Mesh scanning")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for stopping BLE scanning", e)
        }
    }

    /**
     * Android's public BLE APIs do not implement the Bluetooth Mesh provisioner protocol.
     * Do not report a simulated success: a native/provisioner stack must be integrated first.
     */
    fun provisionNode(deviceAddress: String): Nothing {
        throw UnsupportedOperationException(
            "Bluetooth Mesh provisioning is unavailable for $deviceAddress; " +
                "integrate a real provisioner stack before enabling onboarding"
        )
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                try {
                    val name = result.scanRecord?.deviceName ?: "Unknown Node"
                    Log.i(TAG, "Found potential unprovisioned node: $name [${device.address}]")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Cannot read device name due to permission restrictions")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with error code $errorCode")
        }
    }

    override fun onDestroy() {
        stopScanning()
        super.onDestroy()
    }
}
