package com.schnellvpn.app

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object HevBridge {

    private const val TAG = "HevBridge"

    private val loaded = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    fun load(): Boolean {
        if (loaded.get()) return true

        return try {
            System.loadLibrary("hev-socks5-tunnel")
            loaded.set(true)
            Log.i(TAG, "Native library loaded.")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Cannot load hev-socks5-tunnel", e)
            false
        }
    }

    fun startService(configPath: String, fd: Int): Boolean {

        if (!load())
            return false

        if (running.get()) {
            Log.w(TAG, "Service already running.")
            return true
        }

        return try {

            Log.d(TAG, "Starting tunnel...")
            Log.d(TAG, "Config = $configPath")
            Log.d(TAG, "FD = $fd")

            TProxyStartService(configPath, fd)

            running.set(true)

            Log.i(TAG, "Tunnel started.")

            true

        } catch (t: Throwable) {

            running.set(false)

            Log.e(TAG, "Failed starting tunnel.", t)

            false
        }
    }

    fun stopService() {

        if (!running.get())
            return

        try {

            TProxyStopService()

        } catch (t: Throwable) {

            Log.e(TAG, "Stop failed.", t)

        } finally {

            running.set(false)

        }
    }

    fun isRunning(): Boolean = running.get()

    fun getStats(): LongArray? {

        if (!running.get())
            return null

        return try {

            TProxyGetStats()

        } catch (t: Throwable) {

            Log.e(TAG, "Stats failed.", t)

            null
        }
    }

    @JvmStatic
    private external fun TProxyStartService(
        configPath: String,
        fd: Int
    )

    @JvmStatic
    private external fun TProxyStopService()

    @JvmStatic
    private external fun TProxyGetStats(): LongArray
}
