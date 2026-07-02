package com.schnellvpn.app

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

class SchnellVpnService : VpnService(), CoreCallbackHandler {

    companion object {
        const val ACTION_CONNECT = "com.schnellvpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.schnellvpn.app.DISCONNECT"
        const val EXTRA_LINK = "extra_link"

        private const val TAG = "SchnellVPN"
        private const val CHANNEL_ID = "schnellvpn_service"
        private const val NOTIF_ID = 1

        private const val SOCKS_PORT = 10808
        private const val TUN_ADDR = "10.10.14.1"
    }

    private var tunPfd: ParcelFileDescriptor? = null
    private var coreCtrl: CoreController? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_LINK)?.let { startVpn(it) } ?: stopSelf()
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(link: String) {
        startForeground(NOTIF_ID, buildNotif("در حال اتصال..."))
        VpnStatus.reset()

        scope.launch {
            try {

                // 1) build config
                val config = XrayConfigBuilder.buildConfig(link, SOCKS_PORT)

                Libv2ray.initCoreEnv(filesDir.absolutePath, "")
                Log.d(TAG, "Xray init OK")

                // 2) start Xray core
                coreCtrl = CoreController(this@SchnellVpnService)
                coreCtrl!!.startLoop(config, -1)
                Log.d(TAG, "Xray started")

                // 3) WAIT until SOCKS is ready (important FIX)
                waitForSocksReady()

                // 4) create VPN TUN (FULL ROUTE FIXED)
                val builder = Builder()
                    .setSession("SchnellVPN")
                    .addAddress(TUN_ADDR, 24)

                    // FULL ROUTE
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0)

                    // DNS FIX
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .setMtu(1500)

                tunPfd = builder.establish()
                    ?: throw IllegalStateException("VPN establish failed")

                Log.d(TAG, "TUN ready fd=${tunPfd!!.fd}")

                // 5) start tun2socks bridge
                val hevConf = File(cacheDir, "hev.yml").apply {
                    writeText(
                        """
                        misc:
                          task-stack-size: 20480
                        tunnel:
                          mtu: 1500
                          ipv4: $TUN_ADDR
                        socks5:
                          port: $SOCKS_PORT
                          address: 127.0.0.1
                          udp: udp
                        """.trimIndent()
                    )
                }

                val ok = HevBridge.startService(hevConf.absolutePath, tunPfd!!.fd)
                Log.d(TAG, "Hev started = $ok")

                if (!ok) throw IllegalStateException("tun2socks failed to start")

                VpnStatus.isConnected.value = true
                withContext(Dispatchers.Main) {
                    updateNotif("Connected")
                }

                // 6) stats loop
                while (isActive && VpnStatus.isConnected.value) {
                    HevBridge.getStats()?.let {
                        if (it.size >= 4) {
                            VpnStatus.txBytes.value = it[1]
                            VpnStatus.rxBytes.value = it[3]
                        }
                    }
                    delay(1000)
                }

            } catch (e: Exception) {
                Log.e(TAG, "VPN error", e)
                VpnStatus.lastError.value = e.message
                stopVpn()
            }
        }
    }

    // 🔥 FIX: replace fixed delay with real wait
    private suspend fun waitForSocksReady() {
        var retry = 0
        while (retry < 50) {
            delay(100)
            if (HevBridge.isReady()) {
                Log.d(TAG, "SOCKS READY")
                return
            }
            retry++
        }
        throw IllegalStateException("SOCKS not ready timeout")
    }

    private fun stopVpn() {
        scope.launch {
            VpnStatus.isConnected.value = false

            try { HevBridge.stopService() } catch (_: Exception) {}
            try { coreCtrl?.stopLoop() } catch (_: Exception) {}
            try { tunPfd?.close() } catch (_: Exception) {}

            tunPfd = null
            coreCtrl = null

            VpnStatus.reset()

            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun startup(): Long = 0
    override fun shutdown(): Long = 0
    override fun onEmitStatus(code: Long, message: String?): Long = 0

    private fun buildNotif(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)

        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "SchnellVPN",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SchnellVPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotif(text))
    }
}
