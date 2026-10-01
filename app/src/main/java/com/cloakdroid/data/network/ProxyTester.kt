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
        val suggestedLocale: String
    ) : ProxyTestResult

    object Timeout : ProxyTestResult
    data class AuthFailure(val msg: String) : ProxyTestResult
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
                        if (response.code == HTTP_PROXY_AUTH_REQUIRED ||
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

        val geo: GeoLookupResponse
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
                }.getOrElse { return ProxyTestResult.NetworkError("Malformed geo body") }

                if (!parsed.success) {
                    return ProxyTestResult.NetworkError(parsed.message ?: "Geo lookup failed")
                }
                geo = parsed
            }
        } catch (e: SocketTimeoutException) {
            return ProxyTestResult.Timeout
        } catch (e: IOException) {
            return if (isAuthMessage(e.message)) {
                ProxyTestResult.AuthFailure(e.message ?: "Authentication failed")
            } else {
                ProxyTestResult.NetworkError(e.message ?: "Geo lookup I/O failure")
            }
        } catch (e: Exception) {
            return ProxyTestResult.NetworkError(e.message ?: "Unexpected geo failure")
        }

        val countryCode = geo.countryCode?.trim()?.uppercase().orEmpty().ifEmpty { "ZZ" }
        val (timezoneId, locale) = timezoneAndLocaleFor(countryCode)

        return ProxyTestResult.Success(
            latencyMs = latencyMs,
            publicIp = ip,
            countryCode = countryCode,
            city = geo.city?.trim().orEmpty().ifEmpty { "Unknown" },
            isp = geo.connection?.isp?.trim().orEmpty()
                .ifEmpty { geo.isp?.trim().orEmpty() }
                .ifEmpty { geo.connection?.org?.trim().orEmpty() }
                .ifEmpty { geo.org?.trim().orEmpty() }
                .ifEmpty { "Unknown" },
            lat = geo.latitude ?: geo.lat ?: 0.0,
            lon = geo.longitude ?: geo.lon ?: 0.0,
            suggestedTimezoneId = timezoneId,
            suggestedLocale = locale
        )
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
            "SE" to ("Europe/Stockholm" to "sv-SE")
        )
    }
}
