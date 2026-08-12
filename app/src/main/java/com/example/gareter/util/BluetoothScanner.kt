package com.example.gareter.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothDeviceInfo(
    val address: String,
    val name: String,
    val rssi: Int  // Signal strength
)

class BluetoothScanner(private val context: Context) {
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private val _devices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BluetoothDeviceInfo>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _pairingDeviceAddress = MutableStateFlow<String?>(null)  // Adresse du device en cours d'appairage
    val pairingDeviceAddress: StateFlow<String?> = _pairingDeviceAddress.asStateFlow()

    private val _pairingStatus = MutableStateFlow<String?>(null)  // null = idle, "Appairage..." = en cours, "Appairé ✓" = succès, "Appairage échoué" = échec
    val pairingStatus: StateFlow<String?> = _pairingStatus.asStateFlow()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    device?.let {
                        val deviceName = it.name ?: "Appareil inconnu"
                        val newDevice = BluetoothDeviceInfo(it.address, deviceName, rssi)

                        val current = _devices.value.toMutableList()
                        val existingIndex = current.indexOfFirst { dev -> dev.address == it.address }
                        if (existingIndex >= 0) {
                            current[existingIndex] = newDevice
                        } else {
                            current.add(newDevice)
                        }
                        _devices.value = current.sortedByDescending { dev -> dev.rssi }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    val bondState = intent?.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)

                    when (bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            _pairingStatus.value = "Appairé ✓"
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (_pairingStatus.value == "Appairé ✓") {
                                    _pairingStatus.value = null
                                    _pairingDeviceAddress.value = null
                                }
                            }, 2000)
                        }
                        BluetoothDevice.BOND_BONDING -> {
                            _pairingStatus.value = "Appairage..."
                        }
                        BluetoothDevice.BOND_NONE -> {
                            _pairingStatus.value = "Appairage échoué"
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (_pairingStatus.value == "Appairage échoué") {
                                    _pairingStatus.value = null
                                    _pairingDeviceAddress.value = null
                                }
                            }, 2000)
                        }
                    }
                }
            }
        }
    }

    fun startScan() {
        if (bluetoothAdapter == null) return

        _devices.value = emptyList()
        _isScanning.value = true

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(discoveryReceiver, filter)
        }

        bluetoothAdapter.startDiscovery()
    }

    fun stopScan() {
        if (bluetoothAdapter == null) return
        bluetoothAdapter.cancelDiscovery()
        _isScanning.value = false
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled ?: false

    fun enableBluetooth() {
        bluetoothAdapter?.enable()
    }

    fun disableBluetooth() {
        bluetoothAdapter?.disable()
    }

    fun pairDevice(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            _pairingDeviceAddress.value = address
            _pairingStatus.value = "Déjà appairé"
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                _pairingStatus.value = null
                _pairingDeviceAddress.value = null
            }, 2000)
            return
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(pairingReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(pairingReceiver, filter)
        }

        _pairingDeviceAddress.value = address
        _pairingStatus.value = "Appairage..."
        device.createBond()
    }

    fun stopPairing() {
        try {
            context.unregisterReceiver(pairingReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        _pairingStatus.value = null
        _pairingDeviceAddress.value = null
    }
}
