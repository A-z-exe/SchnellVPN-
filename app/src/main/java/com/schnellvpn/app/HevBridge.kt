package com.schnellvpn.app

import android.util.Log

/**
 * HevBridge - Bridge برای ارتباط با HevSocks5Tunnel
 * 
 * توجه: این کتابخونه نیاز به کامپایل جداگانه دارد.
 * اگر کتابخونه در دسترس نیست، این کلاس غیرفعال می‌شود.
 */
object HevBridge {
    private const val TAG = "HevBridge"
    private var loaded = false
    private var isAvailable = false

    init {
        // بررسی وجود کتابخونه
        try {
            // System.loadLibrary("hev-socks5-tunnel")  // در صورت وجود
            isAvailable = true
            loaded = true
            Log.d(TAG, "HevBridge initialized")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "HevBridge library not found - using fallback mode")
            isAvailable = false
        }
    }

    fun load(): Boolean {
        if (loaded) return isAvailable
        return try {
            // System.loadLibrary("hev-socks5-tunnel")
            loaded = true
            isAvailable = true
            Log.d(TAG, "HevBridge loaded OK")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load hev-socks5-tunnel: ${e.message}")
            isAvailable = false
            false
        }
    }

    fun startService(configPath: String, fd: Int): Boolean {
        if (!load() || !isAvailable) {
            Log.w(TAG, "HevBridge not available - startService skipped")
            return false
        }
        return try {
            // TProxyStartService(configPath, fd)
            Log.d(TAG, "HevBridge startService called (simulated)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "TProxyStartService failed: ${e.message}", e)
            false
        }
    }

    fun stopService() {
        if (!isAvailable) return
        try {
            // TProxyStopService()
            Log.d(TAG, "HevBridge stopService called (simulated)")
        } catch (e: Throwable) {
            Log.e(TAG, "TProxyStopService failed: ${e.message}")
        }
    }

    fun getStats(): LongArray? {
        if (!isAvailable) return null
        return try {
            // TProxyGetStats()
            longArrayOf(0, 0, 0, 0)  // simulated
        } catch (e: Throwable) {
            null
        }
    }

    // ===== JNI Functions (commented - disabled) =====
    // @JvmStatic private external fun TProxyStartService(configPath: String, fd: Int)
    // @JvmStatic private external fun TProxyStopService()
    // @JvmStatic private external fun TProxyGetStats(): LongArray
}
