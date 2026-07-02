package com.schnellvpn.app

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val TAG = "MainActivity"

data class AppColors(
    val bg: Color, val surface: Color, val surface2: Color, val border: Color,
    val text: Color, val textDim: Color,
    val amber: Color, val teal: Color, val coral: Color
)

val DarkColors = AppColors(
    bg = Color(0xFF0D1526), surface = Color(0xFF16213A), surface2 = Color(0xFF1C2942),
    border = Color(0xFF2B3A5C), text = Color(0xFFEDF1F7), textDim = Color(0xFF8C9AB8),
    amber = Color(0xFFF5A623), teal = Color(0xFF38C9B9), coral = Color(0xFFFF6B5E)
)
val LightColors = AppColors(
    bg = Color(0xFFF4F6FB), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFECEFF6),
    border = Color(0xFFD8DEEB), text = Color(0xFF131A2B), textDim = Color(0xFF5B6781),
    amber = Color(0xFFD9870A), teal = Color(0xFF1F9E8F), coral = Color(0xFFE0473A)
)

enum class Tab { HOME, SERVERS, SETTINGS }

class MainActivity : ComponentActivity() {

    private val servers = mutableStateListOf<VpnServer>()
    private var loggedIn by mutableStateOf(false)
    private var isDark by mutableStateOf(true)
    private var currentTab by mutableStateOf(Tab.HOME)
    private var selectedServerId by mutableStateOf(-1)
    private var connected by mutableStateOf(false)
    private var connecting by mutableStateOf(false)
    private var durationSec by mutableStateOf(0)
    private var dataMB by mutableStateOf(0f)
    private var subLink by mutableStateOf("")
    private var searchQuery by mutableStateOf("")
    private var toastText by mutableStateOf<String?>(null)
    private var loginLoading by mutableStateOf(false)
    private var showAddLinkDialog by mutableStateOf(false)
    private var addLinkInput by mutableStateOf("")
    private var addLinkLoading by mutableStateOf(false)

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            actuallyStartVpn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        
        // خواندن crash قبلی
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        if (crashFile.exists()) {
            val crashMsg = crashFile.readText().take(200)
            crashFile.delete()
            Log.d(TAG, "Found last crash: $crashMsg")
        }

        setContent {
            val colors = if (isDark) DarkColors else LightColors
            val scope = rememberCoroutineScope()

            LaunchedEffect(connected) {
                if (connected) {
                    durationSec = 0; dataMB = 0f
                    while (connected) {
                        delay(1000)
                        durationSec++
                        dataMB = VpnStatus.totalMB
                    }
                }
            }

            MaterialTheme {
                Surface(color = colors.bg, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        if (!loggedIn) {
                            LoginScreen(
                                colors = colors,
                                subLink = subLink,
                                loading = loginLoading,
                                onSubLinkChange = { subLink = it },
                                onImport = { importSubscription(scope, subLink, isInitialLogin = true) },
                                onScanQr = { showToast(scope, "اسکن QR هنوز فعال نیست") }
                            )
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f)) {
                                    when (currentTab) {
                                        Tab.HOME -> HomeScreen(
                                            colors = colors,
                                            servers = servers,
                                            selectedId = selectedServerId,
                                            connected = connected,
                                            connecting = connecting,
                                            durationSec = durationSec,
                                            dataMB = dataMB,
                                            onToggleConnect = { toggleConnect(scope) },
                                            onOpenServers = { currentTab = Tab.SERVERS },
                                            onOpenSettings = { currentTab = Tab.SETTINGS }
                                        )
                                        Tab.SERVERS -> ServersScreen(
                                            colors = colors,
                                            servers = servers,
                                            selectedId = selectedServerId,
                                            query = searchQuery,
                                            onQueryChange = { searchQuery = it },
                                            onSelect = { id ->
                                                selectedServerId = id
                                                scope.launch {
                                                    toastText = "سرور انتخاب شد"
                                                    delay(450)
                                                    currentTab = Tab.HOME
                                                    delay(1800)
                                                    if (toastText == "سرور انتخاب شد") toastText = null
                                                }
                                            },
                                            onTestPings = {
                                                for (i in servers.indices) {
                                                    val s = servers[i]
                                                    val np = max(18, (s.pingMs ?: 80) + (Math.random() * 30 - 15).toInt())
                                                    servers[i] = s.copy(pingMs = np)
                                                }
                                                showToast(scope, "پینگ تاز شد")
                                            },
                                            onImport = { addLinkInput = ""; showAddLinkDialog = true },
                                            onScanQr = { showToast(scope, "اسکن QR هنوز فعال نیست") }
                                        )
                                        Tab.SETTINGS -> SettingsScreen(
                                            colors = colors,
                                            isDark = isDark,
                                            onToggleDark = { isDark = !isDark },
                                            onLogout = {
                                                stopVpn()
                                                servers.clear()
                                                selectedServerId = -1
                                                loggedIn = false
                                                currentTab = Tab.HOME
                                            }
                                        )
                                    }
                                }
                                BottomNav(colors = colors, current = currentTab, onSelect = { currentTab = it })
                            }
                        }

                        if (showAddLinkDialog) {
                            AddLinkDialog(
                                colors = colors,
                                value = addLinkInput,
                                loading = addLinkLoading,
                                onChange = { addLinkInput = it },
                                onConfirm = { importSubscription(scope, addLinkInput, isInitialLogin = false) },
                                onDismiss = { if (!addLinkLoading) showAddLinkDialog = false }
                            )
                        }

                        toastText?.let { msg ->
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 96.dp, start = 18.dp, end = 18.dp)
                                    .fillMaxWidth()
                                    .background(colors.surface2, RoundedCornerShape(14.dp))
                                    .border(1.dp, colors.amber, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Text(msg, color = colors.text, fontSize = 12.5.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showToast(scope: kotlinx.coroutines.CoroutineScope, msg: String) {
        scope.launch {
            toastText = msg
            delay(2200)
            if (toastText == msg) toastText = null
        }
    }

    private fun importSubscription(scope: kotlinx.coroutines.CoroutineScope, link: String, isInitialLogin: Boolean) {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) {
            showToast(scope, "لطفا لینک وارد کن")
            return
        }
        scope.launch {
            if (isInitialLogin) loginLoading = true else addLinkLoading = true
            try {
                val result = withContext(Dispatchers.IO) { SubscriptionFetcher.fetchAndParse(trimmed) }
                if (result.isEmpty()) {
                    showToast(scope, "سرور پیدا نشد")
                } else if (isInitialLogin) {
                    servers.clear()
                    servers.addAll(result)
                    selectedServerId = result.first().id
                    loggedIn = true
                    showToast(scope, "${result.size} سرور اضافه شد")
                } else {
                    val existingLinks = servers.map { it.link }.toSet()
                    val newOnes = result.filter { it.link !in existingLinks }
                    val startId = (servers.maxOfOrNull { it.id } ?: 0) + 1
                    newOnes.forEachIndexed { i, s -> servers.add(s.copy(id = startId + i)) }
                    if (selectedServerId == -1 && servers.isNotEmpty()) selectedServerId = servers.first().id
                    showAddLinkDialog = false
                    showToast(scope, "${newOnes.size} سرور جدید")
                }
            } catch (e: Exception) {
                Log.e(TAG, "import error: ${e.message}")
                showToast(scope, "خطا: ${e.message}")
            } finally {
                if (isInitialLogin) loginLoading = false else addLinkLoading = false
            }
        }
    }

    private fun toggleConnect(scope: kotlinx.coroutines.CoroutineScope) {
        if (connected) {
            stopVpn()
            showToast(scope, "قطع شد")
        } else {
            requestVpnPermissionAndConnect()
        }
    }

    private fun requestVpnPermissionAndConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else actuallyStartVpn()
    }

    private fun actuallyStartVpn() {
        val link = servers.find { it.id == selectedServerId }?.link ?: return
        val intent = Intent(this, SchnellVpnService::class.java).apply {
            action = SchnellVpnService.ACTION_CONNECT
            putExtra(SchnellVpnService.EXTRA_LINK, link)
        }
        startForegroundService(intent)
        connected = true
        connecting = true
    }

    private fun stopVpn() {
        val intent = Intent(this, SchnellVpnService::class.java).apply {
            action = SchnellVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        connected = false
        connecting = false
    }
}

// UI Composables
@Composable
fun LoginScreen(colors: AppColors, subLink: String, loading: Boolean, onSubLinkChange: (String) -> Unit, onImport: () -> Unit, onScanQr: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("SchnellVPN", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        BasicField(colors, subLink, onSubLinkChange, "لینک Subscription")
        Spacer(Modifier.height(10.dp))
        PrimaryButton(colors, if (loading) "درحال..." else "وارد کردن", onClick = { if (!loading) onImport() })
    }
}

@Composable
fun HomeScreen(colors: AppColors, servers: List<VpnServer>, selectedId: Int, connected: Boolean, connecting: Boolean, durationSec: Int, dataMB: Float, onToggleConnect: () -> Unit, onOpenServers: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("خانه", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PrimaryButton(colors, if (connected) "قطع کن" else "متصل شو", onClick = onToggleConnect)
        }
        Spacer(Modifier.height(20.dp))
        Text("مدت: ${fmtTime(durationSec)}", color = colors.text)
        Text("حجم: %.1f MB".format(dataMB), color = colors.text)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(colors, "سرورها", onClick = onOpenServers)
        Spacer(Modifier.height(10.dp))
        PrimaryButton(colors, "تنظیمات", onClick = onOpenSettings)
    }
}

@Composable
fun ServersScreen(colors: AppColors, servers: List<VpnServer>, selectedId: Int, query: String, onQueryChange: (String) -> Unit, onSelect: (Int) -> Unit, onTestPings: () -> Unit, onImport: () -> Unit, onScanQr: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("سرورها", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        PrimaryButton(colors, "اضافه کردن", onClick = onImport)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(servers) { server ->
                Box(Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(8.dp)).padding(12.dp).clickable { onSelect(server.id) }) {
                        Text(server.flag, fontSize = 16.sp)
                        Text(server.name, color = colors.text, modifier = Modifier.padding(start = 8.dp))
                        Text(server.protocolLabel, color = colors.textDim, modifier = Modifier.padding(start = 8.dp), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(colors: AppColors, isDark: Boolean, onToggleDark: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("تنظیمات", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        PrimaryButton(colors, if (isDark) "🌙 Dark Mode" else "☀️ Light Mode", onClick = onToggleDark)
        Spacer(Modifier.height(10.dp))
        PrimaryButton(colors, "خروج", onClick = onLogout)
    }
}

@Composable
fun AddLinkDialog(colors: AppColors, value: String, loading: Boolean, onChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp).background(colors.surface, RoundedCornerShape(18.dp)).padding(18.dp)) {
            Text("اضافه کردن لینک", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            BasicField(colors, value, onChange, "URL")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PrimaryButton(colors, "لغو", onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                PrimaryButton(colors, if (loading) "..." else "تایید", onClick = { if (!loading) onConfirm() }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun BottomNav(colors: AppColors, current: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(colors.surface).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Text("خانه", color = if (current == Tab.HOME) colors.amber else colors.textDim, modifier = Modifier.clickable { onSelect(Tab.HOME) })
        Text("سرورها", color = if (current == Tab.SERVERS) colors.amber else colors.textDim, modifier = Modifier.clickable { onSelect(Tab.SERVERS) })
        Text("تنظیمات", color = if (current == Tab.SETTINGS) colors.amber else colors.textDim, modifier = Modifier.clickable { onSelect(Tab.SETTINGS) })
    }
}

@Composable
fun BasicField(colors: AppColors, value: String, onChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().background(colors.surface2, RoundedCornerShape(14.dp)).padding(14.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) Text(placeholder, color = colors.textDim, fontSize = 12.sp)
            innerTextField()
        }
    )
}

@Composable
fun PrimaryButton(colors: AppColors, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(colors.amber, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF1A1300), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

fun fmtTime(sec: Int): String = "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
