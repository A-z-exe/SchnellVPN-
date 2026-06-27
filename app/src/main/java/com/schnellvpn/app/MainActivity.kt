package com.schnellvpn.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// همون رنگ‌های طراحی UI قبلی
private val BgColor = Color(0xFF0D1526)
private val SurfaceColor = Color(0xFF16213A)
private val AmberColor = Color(0xFFF5A623)
private val TealColor = Color(0xFF38C9B9)
private val TextColor = Color(0xFFEDF1F7)
private val TextDim = Color(0xFF8C9AB8)

/**
 * این فایل همون لایه‌ی [UI - Kotlin] توی نمودار هست.
 * کارش: نشون دادن لیست سرورها + دکمه‌ی اتصال، و صدا زدن SchnellVpnService.
 * این خودش VPN رو روشن نمی‌کنه، فقط به سرویس می‌گه روشن/خاموش شو.
 */
class MainActivity : ComponentActivity() {

    // 👇 لینک‌های واقعی سرورهات رو همینجا جای PUT-YOUR-LINK-HERE بگذار
    private val servers = mutableStateListOf(
        VpnServer(1, "🇩🇪", "آلمان · فرانکفورت", "VLESS · Reality", "vless://PUT-YOUR-LINK-HERE"),
        VpnServer(2, "🇳🇱", "هلند · آمستردام", "Trojan", "trojan://PUT-YOUR-LINK-HERE"),
        VpnServer(3, "🇫🇮", "فنلاند · هلسینکی", "VMESS", "vmess://PUT-YOUR-LINK-HERE"),
    )

    private var selectedServer by mutableStateOf<VpnServer?>(null)
    private var connected by mutableStateOf(false)

    // وقتی کاربر اجازه‌ی VPN رو تأیید کرد، این تابع صدا زده می‌شه
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            actuallyStartVpn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedServer = servers.first()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgColor,
                    surface = SurfaceColor,
                    primary = AmberColor,
                    onBackground = TextColor,
                    onSurface = TextColor
                )
            ) {
                Surface(color = BgColor, modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        servers = servers,
                        selected = selectedServer,
                        connected = connected,
                        onSelect = { selectedServer = it },
                        onToggleConnect = { toggleConnect() }
                    )
                }
            }
        }
    }

    private fun toggleConnect() {
        if (connected) stopVpn() else requestVpnPermissionAndConnect()
    }

    // اندروید قبل از روشن شدن VPN حتماً باید از کاربر اجازه بگیره — این همون پاپ‌آپ سیستمی معروفه
    private fun requestVpnPermissionAndConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else actuallyStartVpn()
    }

    private fun actuallyStartVpn() {
        val link = selectedServer?.link ?: return
        val intent = Intent(this, SchnellVpnService::class.java).apply {
            action = SchnellVpnService.ACTION_CONNECT
            putExtra(SchnellVpnService.EXTRA_LINK, link)
        }
        startForegroundService(intent)
        connected = true
    }

    private fun stopVpn() {
        val intent = Intent(this, SchnellVpnService::class.java).apply {
            action = SchnellVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        connected = false
    }
}

@Composable
fun HomeScreen(
    servers: List<VpnServer>,
    selected: VpnServer?,
    connected: Boolean,
    onSelect: (VpnServer) -> Unit,
    onToggleConnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SchnellVPN", color = AmberColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onToggleConnect,
            modifier = Modifier.size(180.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connected) TealColor else SurfaceColor,
                contentColor = TextColor
            )
        ) {
            Text(
                if (connected) "متصل\nبرای قطع ضربه بزن" else "قطع\nبرای اتصال ضربه بزن",
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("سرور:", color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(servers) { server ->
                val isSelected = server.id == selected?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(
                            if (isSelected) SurfaceColor else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(server) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(server.flag, fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(server.name, color = TextColor, fontSize = 14.sp)
                        Text(server.protocolLabel, color = TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
