package com.cloakdroid.data.network

import java.net.URI

/** Parsed, validated shape of a proxy line or URL. Credentials remain optional. */
data class ParsedProxyInput(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null
)

/**
 * Parses only unambiguous proxy formats. Scheme-less input explicitly means
 * HTTP; callers must not silently reinterpret malformed input as Direct.
 */
object ProxyInputParser {
    fun parse(value: String, defaultType: ProxyType = ProxyType.HTTP): ParsedProxyInput? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        val withScheme = if (raw.contains("://")) raw else {
            val scheme = when (defaultType) {
                ProxyType.SOCKS5 -> "socks5"
                ProxyType.HTTP -> "http"
                ProxyType.HTTPS -> "https"
                ProxyType.DIRECT -> return null
            }
            "$scheme://$raw"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        val type = when (uri.scheme.lowercase()) {
            "http" -> ProxyType.HTTP
            "https" -> ProxyType.HTTPS
            "socks5", "socks5h", "socks" -> ProxyType.SOCKS5
            else -> return null
        }
        val credentials = uri.rawUserInfo?.split(":", limit = 2)
        return ParsedProxyInput(
            type = type,
            host = host,
            port = port,
            username = credentials?.getOrNull(0)?.let(::decode) ?: null,
            password = credentials?.getOrNull(1)?.let(::decode) ?: null
        )
    }

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }
            .getOrDefault(value)
}
