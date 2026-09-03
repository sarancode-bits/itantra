package com.itantra.core.radio

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class RadioStatus(
    val isWifiOn: Boolean,
    val isBluetoothOn: Boolean
)

@Singleton
class RadioStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // IMPORTANT: The StateFlow must be initialized directly from readRadioState() — a pure
    // read that does NOT write back to the flow. The previous code called checkRadioState()
    // here, which itself wrote _radioStatus.value while _radioStatus was still null during
    // its own initializer, producing a NullPointerException that crashed the app on launch.
    private val _radioStatus = MutableStateFlow(readRadioState())
    val radioStatus: StateFlow<RadioStatus> = _radioStatus.asStateFlow()

    fun checkRadioState(): RadioStatus {
        val status = readRadioState()
        _radioStatus.value = status
        return status
    }

    private fun readRadioState(): RadioStatus {
        var isWifiOn = false
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            isWifiOn = wifiManager?.isWifiEnabled == true
        } catch (_: Exception) {
            // permission denied or hardware unavailable — treat as off
        }

        var isBluetoothOn = false
        try {
            val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            // On Android 12+ (API 31), BluetoothAdapter.isEnabled requires BLUETOOTH_CONNECT
            // runtime permission. If not yet granted, this throws SecurityException.
            isBluetoothOn = bluetoothManager?.adapter?.isEnabled == true
        } catch (_: Exception) {
            // BLUETOOTH_CONNECT permission not yet granted — treat as off
        }

        return RadioStatus(isWifiOn = isWifiOn, isBluetoothOn = isBluetoothOn)
    }
}
