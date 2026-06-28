package com.schnellvpn.app

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder

/**
 * این فایل لینک Subscription رو می‌گیره، از اینترنت دانلودش می‌کنه،
 * و لیست واقعی سرورها (vless/vmess/trojan/ss) رو ازش استخراج می‌کنه.
 * دیگه هیچ کانفیگ نمونه/الکی توی اپ نیست — هرچی نشون داده می‌شه از همین لینک میاد.
 */
object SubscriptionFetcher {

    fun fetchAndParse(subUrl: String): List<VpnServer> {
        val raw = download(subUrl)
        val decoded = decodeIfBase64(raw)
        val links = decoded.lines()
            .map { it.trim() }
            .filter {
                it.startsWith("vless://") || it.startsWith("vmess://") ||
                it.startsWith("trojan://") || it.startsWith("ss://")
            }

        return links.mapIndexedNotNull { index, link ->
            try {
                val (flag, name, proto) = describeLink(link)
                VpnServer(id = index + 1, flag = flag, name = name, protocolLabel = proto, link = link, pingMs = null)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun download(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 9000
        conn.readTimeout = 9000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "SchnellVPN/1.0")
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeIfBase64(body: String): String {
        val clean = body.trim()
        return try {
            val candidate = clean.replace("\n", "").replace("\r", "").replace(" ", "")
            val decodedBytes = Base64.decode(candidate, Base64.DEFAULT)
            val text = String(decodedBytes)
            if (text.contains("://")) text else clean
        } catch (e: Exception) {
            clean
        }
    }

    private fun describeLink(link: String): Triple<String, String, String> {
        return when {
            link.startsWith("vmess://") -> {
                val json = JSONObject(String(Base64.decode(link.removePrefix("vmess://"), Base64.DEFAULT)))
                val remark = json.optString("ps").ifBlank { json.optString("add", "VMess Server") }
                val net = json.optString("net", "tcp").uppercase()
                val tls = if (json.optString("tls") == "tls") " · TLS" else ""
                val (flag, name) = splitFlag(remark)
                Triple(flag, name, "VMess · $net$tls")
            }
            else -> {
                val uri = URI(link)
                val remark = uri.rawFragment?.let { URLDecoder.decode(it, "UTF-8") }
                    ?: uri.host ?: "سرور"
                val params = (uri.rawQuery ?: "").split("&").mapNotNull {
                    val i = it.indexOf("=")
                    if (i < 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
                }.toMap()

                val proto = when {
                    link.startsWith("vless://") -> "VLESS" + when {
                        params["security"] == "reality" -> " · Reality"
                        params["type"] == "ws" -> " · WS"
                        params["type"] == "grpc" -> " · gRPC"
                        else -> ""
                    }
                    link.startsWith("trojan://") -> "Trojan" + if (params["type"] == "ws") " · WS" else ""
                    link.startsWith("ss://") -> "Shadowsocks"
                    else -> "نامشخص"
                }
                val (flag, name) = splitFlag(remark)
                Triple(flag, name, proto)
            }
        }
    }

    // اگه اسمِ سرور با یه ایموجی پرچم شروع شده باشه (که توی اکثر Subscription ها رایجه)، جداش می‌کنیم
    private fun splitFlag(remark: String): Pair<String, String> {
        if (remark.isEmpty()) return "🌐" to "سرور"
        return try {
            val cps = remark.codePoints().toArray()
            if (cps.size >= 2 && cps[0] in 0x1F1E6..0x1F1FF && cps[1] in 0x1F1E6..0x1F1FF) {
                val flag = String(cps, 0, 2)
                val rest = String(cps, 2, cps.size - 2).trim().trim('-', '·', ' ')
                flag to rest.ifEmpty { remark }
            } else {
                "🌐" to remark
            }
        } catch (e: Exception) {
            "🌐" to remark
        }
    }
}
