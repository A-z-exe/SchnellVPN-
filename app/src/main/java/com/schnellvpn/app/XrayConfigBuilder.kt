package com.schnellvpn.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/**
 * این کلاس لینک‌های vless:// , vmess:// , trojan:// , ss:// رو می‌خونه
 * و کانفیگ JSON کامل Xray-core که برای StartLoop لازمه رو می‌سازه.
 *
 * بازنویسی شده تا ورودی‌ها با اعتبارسنجی بیشتری پردازش شوند و خطاهای
 * پارس شدن شفاف‌تر باشند.
 */
object XrayConfigBuilder {

    fun buildConfig(link: String, socksPort: Int = 10808): String {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("link is empty")

        val outbound = try {
            parseLinkToOutbound(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid link format: ${e.message}")
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // Inbounds
        val inbounds = JSONArray().put(
            JSONObject()
                .put("tag", "socks-in")
                .put("listen", "127.0.0.1")
                .put("port", socksPort)
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))
        )
        root.put("inbounds", inbounds)

        // Outbounds
        val outbounds = JSONArray()
            .put(outbound)
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        root.put("outbounds", outbounds)

        // === Routing Rule - این بخش خیلی مهمه ===
        val routing = JSONObject().apply {
            put("domainStrategy", "AsIs")
            val rules = JSONArray().put(
                JSONObject().apply {
                    put("type", "field")
                    put("network", "tcp,udp")
                    put("outboundTag", "proxy")
                }
            )
            put("rules", rules)
        }
        root.put("routing", routing)

        return root.toString()
    }

    private fun parseLinkToOutbound(link: String): JSONObject = when {
        link.startsWith("vless://") -> parseVless(link)
        link.startsWith("vmess://") -> parseVmess(link)
        link.startsWith("trojan://") -> parseTrojan(link)
        link.startsWith("ss://") -> parseShadowsocks(link)
        else -> throw IllegalArgumentException("unsupported protocol: $link")
    }

    // ==================== VLESS ====================
    private fun parseVless(link: String): JSONObject {
        val uri = try { URI(link) } catch (e: Exception) { throw IllegalArgumentException("malformed vless uri") }
        val uuid = uri.userInfo ?: throw IllegalArgumentException("missing uuid in vless link")
        // validate uuid
        try { UUID.fromString(uuid) } catch (e: Exception) { /* not fatal: some providers use non-hyphen format */ }

        val address = uri.host ?: throw IllegalArgumentException("missing host in vless link")
        val port = if (uri.port > 0) uri.port else 443
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

    // ==================== VMESS ====================
    private fun parseVmess(link: String): JSONObject {
        val raw = link.removePrefix("vmess://")
        val json = try {
            val padded = padBase64(raw)
            String(Base64.decode(padded, Base64.DEFAULT))
        } catch (e: Exception) { throw IllegalArgumentException("invalid vmess base64: ${e.message}") }

        val obj = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException("vmess json parse failed") }

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
            .put("id", obj.optString("id"))
            .put("alterId", obj.optInt("aid", 0))
            .put("security", "auto")

        val vnext = JSONObject()
            .put("address", obj.optString("add"))
            .put("port", obj.optString("port").toIntOrNull() ?: 443)
            .put("users", JSONArray().put(user))

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings)
    }

    // ==================== TROJAN ====================
    private fun parseTrojan(link: String): JSONObject {
        val uri = try { URI(link) } catch (e: Exception) { throw IllegalArgumentException("malformed trojan uri") }
        val password = uri.userInfo ?: throw IllegalArgumentException("missing password in trojan link")
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
            .put("address", uri.host ?: throw IllegalArgumentException("missing host in trojan link"))
            .put("port", if (uri.port > 0) uri.port else 443)
            .put("password", password)

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", streamSettings)
    }

    // ==================== SHADOWSOCKS ====================
    private fun parseShadowsocks(link: String): JSONObject {
        // handle ss://base64@host:port or ss://method:pass@host:port or ss://base64#tag
        val body = link.removePrefix("ss://").substringBefore("#")
        val atIndex = body.lastIndexOf('@')
        val userInfoRaw = if (atIndex >= 0) body.substring(0, atIndex) else ""
        val hostPort = if (atIndex >= 0) body.substring(atIndex + 1) else body

        val userInfo = if (userInfoRaw.isNotEmpty() && userInfoRaw.contains(":")) {
            userInfoRaw
        } else if (userInfoRaw.isNotEmpty()) {
            try { String(Base64.decode(padBase64(userInfoRaw), Base64.DEFAULT)) } catch (_: Exception) { userInfoRaw }
        } else ""

        val parts = userInfo.split(":", limit = 2)
        val method = parts.getOrElse(0) { "aes-256-gcm" }
        val password = parts.getOrElse(1) { "" }

        val cleanHostPort = hostPort.substringBefore("?")
        val hpParts = cleanHostPort.split(":", limit = 2)
        val host = hpParts.getOrElse(0) { throw IllegalArgumentException("missing host in ss link") }
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

    private fun padBase64(s: String): String {
        val mod = s.length % 4
        return if (mod == 0) s else s + "=".repeat(4 - mod)
    }
}
