package com.cloakdroid.data.network

import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ProxyType { SOCKS5, HTTP, HTTPS, DIRECT }

data class ProxyConfig(
    val host: String?,
    val port: Int?,
    val username: String?,
    val password: String?,
    val type: ProxyType
)

sealed interface ProxyTestResult {
    data class Success(
        val latencyMs: Long,
        val publicIp: String,
        val countryCode: String,
        val city: String,
        val isp: String,
        val lat: Double,
        val lon: Double,
        val suggestedTimezoneId: String,
        val suggestedLocale: String,
        val metadataWarning: String? = null
    ) : ProxyTestResult

    object Timeout : ProxyTestResult
    data class AuthFailure(val msg: String) : ProxyTestResult
    data class Unsupported(val msg: String) : ProxyTestResult
    data class NetworkError(val msg: String) : ProxyTestResult
}

@Singleton
class ProxyTester @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val ipifyClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun test(proxy: ProxyConfig): ProxyTestResult = withContext(Dispatchers.IO) {
        if (proxy.type == ProxyType.DIRECT) {
            return@withContext runTest(ipifyClient, null)
        }

        val host = proxy.host?.trim().orEmpty()
        val port = proxy.port ?: 0
        if (host.isEmpty() || port <= 0 || port > 65535) {
            return@withContext ProxyTestResult.NetworkError("Invalid proxy host/port: $host:$port")
        }

        if (proxy.type == ProxyType.SOCKS5 && !proxy.username.isNullOrBlank()) {
            return@withContext ProxyTestResult.Unsupported(
                "Authenticated SOCKS5 is not implemented by this client"
            )
        }
        if (proxy.type == ProxyType.HTTPS) {
            return@withContext ProxyTestResult.Unsupported(
                "TLS-encrypted proxy endpoints are not implemented; HTTP CONNECT is supported"
            )
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        when (proxy.type) {
            ProxyType.SOCKS5 -> {
                val javaProxy = Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress.createUnresolved(host, port)
                )
                // Unresolved address forces the SOCKS5 server to resolve hostnames
                // remotely (remote DNS / no local DNS leak).
                builder.proxySelector(object : ProxySelector() {
                    override fun select(uri: URI?): List<Proxy> = listOf(javaProxy)
                    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
                })
                builder.dns(Dns.SYSTEM)
                // Also set the proxy explicitly so OkHttp routes every connection
                // through the SOCKS endpoint even when a default selector exists.
                builder.proxy(javaProxy)
            }

            ProxyType.HTTP, ProxyType.HTTPS -> {
                val scheme = if (proxy.type == ProxyType.HTTPS) "https" else "http"
                // Keep DNS local for HTTP proxies: the proxy needs a routable IP.
                builder.proxy(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress.createUnresolved(host, port)
                    )
                )
                builder.dns(Dns.SYSTEM)
                val username = proxy.username
                val password = proxy.password
                if (!username.isNullOrEmpty()) {
                    builder.proxyAuthenticator { route: Route?, response: Response ->
                        // Never retry credentials indefinitely; OkHttp may invoke
                        // the authenticator once per challenge/route.
                        if (responseCount(response, HTTP_PROXY_AUTH_REQUIRED) >= MAX_AUTH_ATTEMPTS) {
                            null
                        } else if (response.code == HTTP_PROXY_AUTH_REQUIRED ||
                            response.header("Proxy-Authenticate") != null
                        ) {
                            val credential = Credentials.basic(username, password.orEmpty())
                            response.request.newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        } else {
                            null
                        }
                    }
                    // Suppress unused warning for scheme var (documented preview behavior).
                    @Suppress("UNUSED_EXPRESSION")
                    scheme
                }
            }

            ProxyType.DIRECT -> Unit
        }

        runTest(builder.build(), proxy)
    }

    private fun runTest(client: OkHttpClient, proxy: ProxyConfig?): ProxyTestResult {
        val startNanos = System.nanoTime()
        val ip: String
        try {
            val ipRequest = Request.Builder()
                .url(IPIFY_URL)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(ipRequest).execute().use { response ->
                if (response.code == HTTP_PROXY_AUTH_REQUIRED) {
                    return ProxyTestResult.AuthFailure("Proxy authentication required (407)")
                }
                if (!response.isSuccessful) {
                    return ProxyTestResult.NetworkError(
                        "IP echo failed with HTTP ${response.code}"
                    )
                }
                val body = response.body?.string().orEmpty()
                val parsed = runCatching { json.decodeFromString(IpifyResponse.serializer(), body) }
                    .getOrElse { return ProxyTestResult.NetworkError("Malformed IP echo body") }
                ip = parsed.ip.trim()
                if (ip.isEmpty()) {
                    return ProxyTestResult.NetworkError("Empty public IP returned")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            return ProxyTestResult.Timeout
        } catch (e: java.net.ConnectException) {
            return if (isAuthMessage(e.message)) {
                ProxyTestResult.AuthFailure(e.message ?: "Proxy authentication failed")
            } else {
                ProxyTestResult.NetworkError(e.message ?: "Connection failed")
            }
        } catch (e: java.net.UnknownHostException) {
            return ProxyTestResult.NetworkError(e.message ?: "Unknown host")
        } catch (e: java.net.NoRouteToHostException) {
            return ProxyTestResult.NetworkError(e.message ?: "No route to host")
        } catch (e: javax.net.ssl.SSLException) {
            return if (isAuthMessage(e.message)) {
                ProxyTestResult.AuthFailure(e.message ?: "TLS auth failure")
            } else {
                ProxyTestResult.NetworkError(e.message ?: "TLS failure")
            }
        } catch (e: IOException) {
            return if (isAuthMessage(e.message)) {
                ProxyTestResult.AuthFailure(e.message ?: "Authentication failed")
            } else {
                ProxyTestResult.NetworkError(e.message ?: "I/O failure")
            }
        } catch (e: Exception) {
            return ProxyTestResult.NetworkError(e.message ?: "Unexpected failure")
        }

        val latencyMs = (System.nanoTime() - startNanos) / 1_000_000L

        var geo: GeoLookupResponse? = null
        try {
            val geoRequest = Request.Builder()
                .url("$IP_API_URL/$ip")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(geoRequest).execute().use { response ->
                if (response.code == HTTP_PROXY_AUTH_REQUIRED) {
                    return ProxyTestResult.AuthFailure("Proxy authentication required (407)")
                }
                if (!response.isSuccessful) {
                    return ProxyTestResult.NetworkError(
                        "Geo lookup failed with HTTP ${response.code}"
                    )
                }
                val body = response.body?.string().orEmpty()
                val parsed = runCatching {
                    json.decodeFromString(GeoLookupResponse.serializer(), body)
                }.getOrNull()
                geo = parsed?.takeIf { it.success }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            geo = null
        } catch (e: IOException) {
            if (isAuthMessage(e.message)) return ProxyTestResult.AuthFailure(e.message ?: "Authentication failed")
            geo = null
        } catch (_: Exception) {
            geo = null
        }

        if (geo == null) {
            return ProxyTestResult.Success(
                latencyMs = latencyMs,
                publicIp = ip,
                countryCode = "ZZ",
                city = "Unknown",
                isp = "Unknown",
                lat = 0.0,
                lon = 0.0,
                suggestedTimezoneId = DEFAULT_TIMEZONE,
                suggestedLocale = DEFAULT_LOCALE,
                metadataWarning = "Public IP verified; location metadata is unavailable"
            )
        }

        val resolvedGeo = geo ?: error("geo metadata unexpectedly unavailable")
        val countryCode = resolvedGeo.countryCode?.trim()?.uppercase().orEmpty().ifEmpty { "ZZ" }
        // Prefer the timezone reported by the geo service itself (exact city-
        // level match), then fall back to the country-level map.
        val geoTz = resolvedGeo.timezone?.id?.trim().orEmpty()
        val (mapTz, mapLocale) = timezoneAndLocaleFor(countryCode)
        val tz = geoTz.ifEmpty { mapTz }
        val locale = if (geoTz.isEmpty()) mapLocale else suggestedLocaleFor(tz)

        return ProxyTestResult.Success(
            latencyMs = latencyMs,
            publicIp = ip,
            countryCode = countryCode,
            city = resolvedGeo.city?.trim().orEmpty().ifEmpty { "Unknown" },
            isp = resolvedGeo.connection?.isp?.trim().orEmpty()
                .ifEmpty { resolvedGeo.isp?.trim().orEmpty() }
                .ifEmpty { resolvedGeo.connection?.org?.trim().orEmpty() }
                .ifEmpty { resolvedGeo.org?.trim().orEmpty() }
                .ifEmpty { "Unknown" },
            lat = resolvedGeo.latitude ?: resolvedGeo.lat ?: 0.0,
            lon = resolvedGeo.longitude ?: resolvedGeo.lon ?: 0.0,
            suggestedTimezoneId = tz,
            suggestedLocale = locale
        )
    }

    /** Derives a BCP-47 locale from an IANA timezone id, e.g.
     *  "Asia/Kuala_Lumpur" -> "ms-MY"-style best effort using the city's
     *  country from [COUNTRY_LOCALE_MAP] when available, otherwise a
     *  language-neutral locale from the timezone region. */
    private fun suggestedLocaleFor(tzId: String): String {
        val region = tzId.substringAfter('/', "").replace('_', '-')
        if (region.isEmpty()) return DEFAULT_LOCALE
        val countryEntry = COUNTRY_LOCALE_MAP.entries.firstOrNull {
            it.value.first.substringAfter('/') == region
        }
        return countryEntry?.value?.second ?: region.let { r ->
            // Best-effort: use region code as locale region with English.
            val parts = r.split("-")
            if (parts.size == 2 && parts[1].length == 2) "en-${parts[1]}" else DEFAULT_LOCALE
        }
    }

    private fun responseCount(response: Response, code: Int): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            if (prior.code == code) count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun isAuthMessage(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase()
        return lower.contains("407") ||
            lower.contains("authentication") ||
            lower.contains("authorisation") ||
            lower.contains("authorization") ||
            lower.contains("credentials") ||
            lower.contains("proxy-authenticate")
    }

    @VisibleForTesting
    internal fun timezoneAndLocaleFor(countryCode: String): Pair<String, String> {
        val entry = COUNTRY_LOCALE_MAP[countryCode]
            ?: COUNTRY_LOCALE_MAP[countryCode.take(2)]
        return entry ?: DEFAULT_TIMEZONE to DEFAULT_LOCALE
    }

    @Serializable
    data class IpifyResponse(val ip: String = "")

    @Serializable
    data class GeoLookupResponse(
        val success: Boolean = true,
        val message: String? = null,
        val country: String? = null,
        @kotlinx.serialization.SerialName("country_code") val countryCode: String? = null,
        val region: String? = null,
        @kotlinx.serialization.SerialName("region_name") val regionName: String? = null,
        val city: String? = null,
        val postal: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val timezone: TimezoneInfo? = null,
        val connection: ConnectionInfo? = null,
        // Legacy ip-api.com fields kept for backward compatibility.
        val lat: Double? = null,
        val lon: Double? = null,
        val isp: String? = null,
        val org: String? = null,
        val query: String? = null
    )

    @Serializable
    data class TimezoneInfo(val id: String? = null)

    @Serializable
    data class ConnectionInfo(
        val org: String? = null,
        val isp: String? = null,
        val domain: String? = null
    )

    companion object {
        private const val TIMEOUT_SECONDS = 15L
        private const val HTTP_PROXY_AUTH_REQUIRED = 407
        private const val MAX_AUTH_ATTEMPTS = 3
        private const val USER_AGENT = "CloakDroid/1.0"
        private const val IPIFY_URL = "https://api.ipify.org?format=json"
        // ip-api.com free tier is HTTP-only and the app forbids cleartext
        // traffic; ipwho.is serves the same fields over HTTPS.
        private const val IP_API_URL = "https://ipwho.is"
        private const val DEFAULT_TIMEZONE = "UTC"
        private const val DEFAULT_LOCALE = "en-US"

        // ISO 3166-1 alpha-2 -> IANA timezone + BCP-47 locale.
        val COUNTRY_LOCALE_MAP: Map<String, Pair<String, String>> = mapOf(
            "US" to ("America/New_York" to "en-US"),
            "GB" to ("Europe/London" to "en-GB"),
            "DE" to ("Europe/Berlin" to "de-DE"),
            "FR" to ("Europe/Paris" to "fr-FR"),
            "TR" to ("Europe/Istanbul" to "tr-TR"),
            "AE" to ("Asia/Dubai" to "ar-AE"),
            "IR" to ("Asia/Tehran" to "fa-IR"),
            "RU" to ("Europe/Moscow" to "ru-RU"),
            "CN" to ("Asia/Shanghai" to "zh-CN"),
            "JP" to ("Asia/Tokyo" to "ja-JP"),
            "IN" to ("Asia/Kolkata" to "en-IN"),
            "BR" to ("America/Sao_Paulo" to "pt-BR"),
            "CA" to ("America/Toronto" to "en-CA"),
            "AU" to ("Australia/Sydney" to "en-AU"),
            "NL" to ("Europe/Amsterdam" to "nl-NL"),
            "SE" to ("Europe/Stockholm" to "sv-SE"),
            "MY" to ("Asia/Kuala_Lumpur" to "ms-MY"),
            "SG" to ("Asia/Singapore" to "en-SG"),
            "HK" to ("Asia/Hong_Kong" to "zh-HK"),
            "KR" to ("Asia/Seoul" to "ko-KR"),
            "PH" to ("Asia/Manila" to "en-PH"),
            "ID" to ("Asia/Jakarta" to "id-ID"),
            "TH" to ("Asia/Bangkok" to "th-TH"),
            "VN" to ("Asia/Ho_Chi_Minh" to "vi-VN"),
            "TW" to ("Asia/Taipei" to "zh-TW"),
            "ES" to ("Europe/Madrid" to "es-ES"),
            "IT" to ("Europe/Rome" to "it-IT"),
            "PL" to ("Europe/Warsaw" to "pl-PL"),
            "CH" to ("Europe/Zurich" to "de-CH"),
            "AT" to ("Europe/Vienna" to "de-AT"),
            "IE" to ("Europe/Dublin" to "en-IE"),
            "FI" to ("Europe/Helsinki" to "fi-FI"),
            "NO" to ("Europe/Oslo" to "nb-NO"),
            "DK" to ("Europe/Copenhagen" to "da-DK"),
            "PT" to ("Europe/Lisbon" to "pt-PT"),
            "BE" to ("Europe/Brussels" to "nl-BE"),
            "UA" to ("Europe/Kyiv" to "uk-UA"),
            "ZA" to ("Africa/Johannesburg" to "en-ZA"),
            "MX" to ("America/Mexico_City" to "es-MX"),
            "AR" to ("America/Argentina/Buenos_Aires" to "es-AR"),
            "CL" to ("America/Santiago" to "es-CL"),
            "CO" to ("America/Bogota" to "es-CO"),
            "IL" to ("Asia/Jerusalem" to "he-IL"),
            "SA" to ("Asia/Riyadh" to "ar-SA"),
            "QA" to ("Asia/Qatar" to "ar-QA"),
            "PK" to ("Asia/Karachi" to "ur-PK"),
            "BD" to ("Asia/Dhaka" to "bn-BD"),
            "EG" to ("Africa/Cairo" to "ar-EG"),
            "NG" to ("Africa/Lagos" to "en-NG"),
            "RO" to ("Europe/Bucharest" to "ro-RO"),
            "CZ" to ("Europe/Prague" to "cs-CZ"),
            "GR" to ("Europe/Athens" to "el-GR"),
            "HU" to ("Europe/Budapest" to "hu-HU")
        )
    }
}
