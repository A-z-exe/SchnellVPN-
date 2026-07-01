package com.schnellvpn.app

import android.util.Log

/**
 * Native bridge for hev-socks5-tunnel.
 *
 * We keep the JNI signatures stable (they match the native library) and provide
 * safe Kotlin wrappers that catch errors and return status results. This avoids
 * uncaught UnsatisfiedLinkError or other native exceptions bubbling up.
 */
object HevBridge {
    private const val TAG = "HevBridge"

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
        } catch (t: Throwable) {
            // Loading may fail on some devices/ABIs; log it so callers can react.
            Log.e(TAG, "loadLibrary failed: ${t.message}")
        }
    }

    // JNI bindings (keep signatures aligned with native .so)
    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int)
    @JvmStatic external fun TProxyStopService()
    @JvmStatic external fun TProxyGetStats(): LongArray

    // Safe wrappers used by Kotlin code
    fun startServiceSafe(configPath: String, fd: Int): Boolean {
        return try {
            TProxyStartService(configPath, fd)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "TProxyStartService failed: ${t.message}", t)
            false
        }
    }

    fun stopServiceSafe() {
        try {
            TProxyStopService()
        } catch (t: Throwable) {
            Log.w(TAG, "TProxyStopService failed: ${t.message}")
        }
    }

    fun getStatsSafe(): LongArray {
        return try {
            TProxyGetStats() ?: longArrayOf()
        } catch (t: Throwable) {
            Log.w(TAG, "TProxyGetStats failed: ${t.message}")
            longArrayOf()
        }
    }
}
