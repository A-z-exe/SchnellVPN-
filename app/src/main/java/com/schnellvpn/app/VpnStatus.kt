package com.schnellvpn.app

import androidx.compose.runtime.mutableStateOf

object VpnStatus {
    val isConnected = mutableStateOf(false)
    val txBytes = mutableStateOf(0L)
    val rxBytes = mutableStateOf(0L)
    val connectStartMillis = mutableStateOf(0L)
    val lastError = mutableStateOf<String?>(null)

    fun reset() {
        isConnected.value = false
        txBytes.value = 0L
        rxBytes.value = 0L
        connectStartMillis.value = 0L
    }

    val totalMB: Float
        get() = (txBytes.value + rxBytes.value) / (1024f * 1024f)
}
