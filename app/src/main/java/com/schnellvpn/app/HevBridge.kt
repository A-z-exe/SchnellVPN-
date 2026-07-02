package com.schnellvpn.app

import android.util.Log

/**
 * JNI bridge for hev-socks5-tunnel native library.
 * All methods are safe-wrapped to prevent uncaught native exceptions.
 */
object HevBridge {
    private const val TAG = "HevBridge"

    fun load(): Boolean = try {
        System.loadLibrary("hev-socks5-tunnel")
        Log.d(TAG, "hev-socks5-tunnel loaded OK")
        true
    } catch (e: Throwable) {
        Log.e(TAG, "Failed to load hev-socks5-tunnel: ${e.message}")
        false
    }

    fun startService(configPath: String, fd: Int): Boolean = try {
        TProxyStartService(configPath, fd)
        true
    } catch (e: Throwable) {
        Log.e(TAG, "TProxyStartService failed: ${e.message}", e)
        false
    }

    fun stopService() {
        try { TProxyStopService() }
        catch (e: Throwable) { Log.e(TAG, "TProxyStopService failed: ${e.message}") }
    }

    fun getStats(): LongArray? = try {
        TProxyGetStats()
    } catch (e: Throwable) {
        Log.w(TAG, "TProxyGetStats failed: ${e.message}")
        null
    }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int)
    @JvmStatic external fun TProxyStopService()
    @JvmStatic external fun TProxyGetStats(): LongArray
}
