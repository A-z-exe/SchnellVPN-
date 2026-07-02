package com.schnellvpn.app

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * وضعیت اتصال VPN و آمار مصرف
 * برای استفاده در سراسر برنامه
 */
object VpnStatus {
    
    // ========== وضعیت اتصال ==========
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectStartMillis = MutableStateFlow(0L)
    val connectStartMillis: StateFlow<Long> = _connectStartMillis.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    // ========== آمار مصرف ==========
    private val _txBytes = MutableStateFlow(0L)  // آپلود
    val txBytes: StateFlow<Long> = _txBytes.asStateFlow()
    
    private val _rxBytes = MutableStateFlow(0L)  // دانلود
    val rxBytes: StateFlow<Long> = _rxBytes.asStateFlow()
    
    // ========== محاسبه حجم کل ==========
    val totalMB: Float
        get() = ((_txBytes.value + _rxBytes.value) / (1024.0 * 1024.0)).toFloat()
    
    val totalGB: Float
        get() = totalMB / 1024f
    
    // ========== مدت اتصال ==========
    val durationSeconds: Long
        get() = if (_isConnected.value) {
            (System.currentTimeMillis() - _connectStartMillis.value) / 1000
        } else 0L
    
    val durationFormatted: String
        get() {
            val sec = durationSeconds
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return String.format("%02d:%02d:%02d", h, m, s)
        }
    
    // ========== توابع کنترل ==========
    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
        if (connected) {
            _connectStartMillis.value = System.currentTimeMillis()
            _lastError.value = null
        } else {
            _connectStartMillis.value = 0L
        }
    }
    
    fun setError(error: String?) {
        _lastError.value = error
    }
    
    fun updateStats(tx: Long, rx: Long) {
        _txBytes.value = tx
        _rxBytes.value = rx
    }
    
    fun reset() {
        _isConnected.value = false
        _connectStartMillis.value = 0L
        _lastError.value = null
        _txBytes.value = 0L
        _rxBytes.value = 0L
    }
}
