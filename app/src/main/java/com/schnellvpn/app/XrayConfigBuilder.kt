package com.schnellvpn.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/**
 * این کلاس لینک‌های vless:// , vmess:// , trojan:// , ss:// رو می‌خونه
 * و کانفیگ JSON کامل Xray-core که برای StartLoop لازمه رو می‌سازه.
 *
 * توجه: این پیاده‌سازی پایه‌ست و موارد رایج (VLESS+Reality, VLESS+WS, VMESS, Trojan, Shadowsocks)
 * رو پوشش می‌ده. برای پروتکل‌ها/تنظیمات خیلی خاص ممکنه نیاز به تکمیل داشته باشه.
 */
object XrayConfigBuilder {

    fun buildConfig(link: String, socksPort: Int = 10808): String {
        val outbound = parseLinkToOutbound(link.trim())

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        val inbounds = JSONArray().put(
            JSONObject()
                .put("tag", "socks-in")
                .put("listen", "127.0.0.1")
                .put("port", socksPort)
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))
        )
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
            .put(outbound)
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        root.put("outbounds", outbounds)

        return root.toString()
    }

    private fun parseLinkToOutbound(link: String): JSONObject = when {
        link.startsWith("vless://") -> parseVless(link)
        link.startsWith("vmess://") -> parseVmess(link)
        link.startsWith("trojan://") -> parseTrojan(link)
        link.startsWith("ss://") -> parseShadowsocks(link)
        else -> throw IllegalArgumentException("پروتکل پشتیبانی نمی‌شود: $link")
    }

    // ---------------- VLESS ----------------
    private fun parseVless(link: String): JSONObject {
        val uri = URI(link)
        val uuid = uri.userInfo
        val address = uri.host
        val port = uri.port
        val params = parseQuery(uri.rawQuery)

        val network = params["type"] ?: "tcp"
        val streamSettings = JSONObject().put("network", network)

        when (network) {
            "ws" -> streamSettings.put(
                "wsSettings", JSONObject()
                    .put("path", params["path"] ?: "/")
                    .put("headers", JSONObject().put("Host", params["host"] ?: address))
            )
            "grpc" -> streamSettings.put(
                "grpcSettings", JSONObject().put("serviceName", params["serviceName"] ?: "")
            )
        }

        val security = params["security"] ?: "none"
        streamSettings.put("security", security)
        when (security) {
            "tls" -> streamSettings.put(
                "tlsSettings", JSONObject()
                    .put("serverName", params["sni"] ?: address)
                    .put("allowInsecure", false)
            )
            "reality" -> streamSettings.put(
                "realitySettings", JSONObject()
                    .put("serverName", params["sni"] ?: address)
                    .put("fingerprint", params["fp"] ?: "chrome")
                    .put("publicKey", params["pbk"] ?: "")
                    .put("shortId", params["sid"] ?: "")
                    .put("spiderX", params["spx"] ?: "")
            )
        }

        val user = JSONObject()
            .put("id", uuid)
            .put("encryption", "none")
            .put("flow", params["flow"] ?: "")

        val vnext = JSONObject()
            .put("address", address)
            .put("port", port)
            .put("users", JSONArray().put(user))

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings)
    }

    // ---------------- VMESS ----------------
    private fun parseVmess(link: String): JSONObject {
        val raw = link.removePrefix("vmess://")
        val json = String(Base64.decode(raw, Base64.DEFAULT))
        val obj = JSONObject(json)

        val network = obj.optString("net", "tcp")
        val streamSettings = JSONObject().put("network", network)

        if (network == "ws") {
            streamSettings.put(
                "wsSettings", JSONObject()
                    .put("path", obj.optString("path", "/"))
                    .put("headers", JSONObject().put("Host", obj.optString("host")))
            )
        }
        if (obj.optString("tls") == "tls") {
            streamSettings.put("security", "tls")
            streamSettings.put(
                "tlsSettings",
                JSONObject().put("serverName", obj.optString("sni", obj.optString("add")))
            )
        } else {
            streamSettings.put("security", "none")
        }

        val user = JSONObject()
            .put("id", obj.getString("id"))
            .put("alterId", obj.optInt("aid", 0))
            .put("security", "auto")

        val vnext = JSONObject()
            .put("address", obj.getString("add"))
            .put("port", obj.getString("port").toInt())
            .put("users", JSONArray().put(user))

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings)
    }

    // ---------------- TROJAN ----------------
    private fun parseTrojan(link: String): JSONObject {
        val uri = URI(link)
        val password = uri.userInfo
        val params = parseQuery(uri.rawQuery)

        val streamSettings = JSONObject()
            .put("network", params["type"] ?: "tcp")
            .put("security", params["security"] ?: "tls")
            .put(
                "tlsSettings", JSONObject()
                    .put("serverName", params["sni"] ?: uri.host)
                    .put("allowInsecure", false)
            )

        val server = JSONObject()
            .put("address", uri.host)
            .put("port", uri.port)
            .put("password", password)

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", streamSettings)
    }

    // ---------------- SHADOWSOCKS ----------------
    private fun parseShadowsocks(link: String): JSONObject {
        val body = link.removePrefix("ss://").substringBefore("#")
        val atIndex = body.lastIndexOf('@')
        val userInfoRaw = if (atIndex >= 0) body.substring(0, atIndex) else ""
        val hostPort = if (atIndex >= 0) body.substring(atIndex + 1) else body

        val userInfo = try {
            String(Base64.decode(userInfoRaw, Base64.DEFAULT))
        } catch (e: Exception) {
            userInfoRaw
        }
        val parts = userInfo.split(":", limit = 2)
        val method = parts.getOrElse(0) { "aes-256-gcm" }
        val password = parts.getOrElse(1) { "" }

        val cleanHostPort = hostPort.substringBefore("?")
        val hpParts = cleanHostPort.split(":", limit = 2)
        val host = hpParts.getOrElse(0) { "" }
        val port = hpParts.getOrElse(1) { "443" }.toIntOrNull() ?: 443

        val server = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("method", method)
            .put("password", password)

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf("=")
            if (idx < 0) null
            else {
                val key = pair.substring(0, idx)
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                key to value
            }
        }.toMap()
    }
}
