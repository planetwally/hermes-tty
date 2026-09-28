package com.planetwally.hermestty

import java.net.URI
import java.net.URLDecoder

const val PAIR_SCHEME = "hermestty"

/** Connection details carried by a pairing link; only applied after the user confirms them. */
data class PairRequest(val url: String, val key: String) {
    val host: String get() = normalizeBaseUrl(url)
}

/** Parses `hermestty://connect?url=...&key=...` (what pair.py puts in its QR code). */
fun parsePairingLink(link: String?): PairRequest? {
    val uri = runCatching { URI(link?.trim().orEmpty()) }.getOrNull() ?: return null
    if (uri.scheme != PAIR_SCHEME || uri.host != "connect") return null
    val query = uri.rawQuery?.split('&').orEmpty().mapNotNull { part ->
        val (k, v) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }.toMap()
    val url = query["url"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val key = query["key"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return PairRequest(url, key)
}

enum class Transport { ENCRYPTED, LOCAL_CLEARTEXT, PUBLIC_CLEARTEXT }

/**
 * How exposed the API key and conversation are on the way to [baseUrl].
 * Tailscale (100.64.0.0/10, *.ts.net) is WireGuard-encrypted even over plain http.
 */
fun transportOf(baseUrl: String): Transport {
    val u = runCatching { URI(normalizeBaseUrl(baseUrl)) }.getOrNull() ?: return Transport.PUBLIC_CLEARTEXT
    if (u.scheme.equals("https", ignoreCase = true)) return Transport.ENCRYPTED
    val host = u.host?.lowercase()?.trim('[', ']') ?: return Transport.PUBLIC_CLEARTEXT
    if (host == "localhost" || host == "::1" || host.endsWith(".ts.net")) return Transport.ENCRYPTED
    if (':' in host) {
        return when {
            host.startsWith("fd7a:115c:a1e0:") -> Transport.ENCRYPTED // Tailscale IPv6
            host.startsWith("fd") || host.startsWith("fc") || host.startsWith("fe80:") -> Transport.LOCAL_CLEARTEXT
            else -> Transport.PUBLIC_CLEARTEXT
        }
    }
    val ip = host.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 && it.all { b -> b in 0..255 } }
    if (ip != null) {
        val (a, b) = ip
        return when {
            a == 127 -> Transport.ENCRYPTED
            a == 100 && b in 64..127 -> Transport.ENCRYPTED // Tailscale CGNAT range
            a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) ->
                Transport.LOCAL_CLEARTEXT
            else -> Transport.PUBLIC_CLEARTEXT
        }
    }
    if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa") || '.' !in host) {
        return Transport.LOCAL_CLEARTEXT
    }
    return Transport.PUBLIC_CLEARTEXT
}

/** One-line warning for the settings and pairing screens, or null when the link is encrypted. */
fun transportWarning(baseUrl: String): Pair<String, Tone>? = when (transportOf(baseUrl)) {
    Transport.ENCRYPTED -> null
    Transport.LOCAL_CLEARTEXT ->
        "! plain http: the key and chat cross this Wi-Fi unencrypted — fine at home, use Tailscale elsewhere" to Tone.WARN
    Transport.PUBLIC_CLEARTEXT ->
        "✗ plain http to a public address: anyone on the path can read the key and take over the agent — use Tailscale or https" to Tone.ERROR
}
