package com.cloakdroid.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import com.cloakdroid.data.network.ProxyConfig
import org.mozilla.geckoview.WebResponse

/**
 * Process wide owner of the single [GeckoRuntime].
 *
 * GeckoView forbids creating more than one runtime per process, so creation is
 * guarded by a double checked lock on top of a `@Volatile` reference: every
 * caller is guaranteed to receive the very same runtime instance.
 *
 * Runtime level settings:
 *  - `userAgentOverride`  -> left at its default (`null`); per profile overrides
 *    are applied per session through [applyProfileSettings].
 *  - `consoleOutputToLogcat(true)` -> Gecko console output lands in logcat.
 *
 * WebRTC / media hardening and other `about:config` preferences are applied by
 * the profile layer (via [applyProfileSettings] and preference syncing), not by
 * the runtime builder, so the builder stays minimal and side effect free.
 */
@Singleton
class BrowserEngine @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        private const val TAG = "CloakDroidEngine"
    }

    /** Application context only - never an Activity. */
    private val appContext: Context = context.applicationContext

    /** Guards every read / write of [runtimeRef]. */
    private val runtimeLock = Any()

    @Volatile
    private var runtimeRef: GeckoRuntime? = null

    /**
     * Lazily created, process wide [GeckoRuntime]. Safe to call from any
     * thread, any number of times: the runtime is created exactly once.
     */
    /**
     * The proxy currently configured on the runtime, or null for direct.
     * Changing it after the runtime exists is refused. GeckoRuntime is
     * process-lifetime state; a different proxy requires a fresh process.
     */
    @Volatile
    var activeProxy: ProxyConfig? = null
        private set

    /** Non-null when Gecko rejected/failed to receive the selected proxy prefs. */
    @Volatile
    var proxyConfigurationError: String? = null
        private set

    fun runtimeMatchesProxy(proxy: ProxyConfig?): Boolean =
        runtimeRef != null && activeProxy == proxy

    val runtime: GeckoRuntime
        get() {
            runtimeRef?.let { cached -> return cached }
            synchronized(runtimeLock) {
                runtimeRef?.let { cached -> return cached }

                val settings = buildSettings(activeProxy)
                val created = GeckoRuntime.create(appContext, settings)
                applyPrefs(created, pendingPrefs)
                runtimeRef = created
                Log.i(TAG, "GeckoRuntime created (single process wide instance)")
                return created
            }
        }

    /** Destroys the current runtime so a new one with different proxy prefs
     *  can be created. GeckoView allows only one runtime per process, so this
     *  is only valid before any session is open. */
    fun resetRuntime(proxy: ProxyConfig?) {
        synchronized(runtimeLock) {
            if (activeProxy == proxy && runtimeRef != null) return
            if (runtimeRef != null && activeProxy != proxy) {
                // GeckoRuntime is process-lifetime state. Shutting it down while
                // switching a focused tab/profile is not a safe way to obtain
                // per-profile routing and can invalidate attached GeckoViews.
                // Refuse the switch; the caller must keep the current runtime
                // or restart the application under an explicit future policy.
                throw IllegalStateException(
                    "A different proxy requires a fresh process; runtime reset is refused"
                )
            }
            activeProxy = proxy
            proxyConfigurationError = null
            Log.i(TAG, "GeckoRuntime configured for proxy=${proxy?.type}")
        }
    }

    /** Prefs pending application to the next created runtime. */
    private var pendingPrefs: Map<String, Any> = emptyMap()

    /**
     * Builds the Gecko proxy prefs for [proxy]. Keys are standard
     * `network.proxy.*` preference names; values are String/Int/Boolean.
     */
    private fun proxyPrefs(proxy: ProxyConfig?): Map<String, Any> {
        if (proxy == null || proxy.type == com.cloakdroid.data.network.ProxyType.DIRECT) {
            return emptyMap()
        }
        val host = proxy.host.orEmpty().trim()
        val port = proxy.port ?: 0
        if (host.isBlank() || port !in 1..65535) return emptyMap()

        val prefs = LinkedHashMap<String, Any>()
        prefs["network.proxy.type"] = 1 // manual
        when (proxy.type) {
            com.cloakdroid.data.network.ProxyType.SOCKS5 -> {
                prefs["network.proxy.socks"] = host
                prefs["network.proxy.socks_port"] = port
                prefs["network.proxy.socks_version"] = 5
                prefs["network.proxy.socks5_remote_dns"] = true
                prefs["network.proxy.socks_remote_dns"] = true
                // HTTPS-over-SOCKS (ssl pref) so https:// also routes through.
                prefs["network.proxy.ssl"] = host
                prefs["network.proxy.ssl_port"] = port
            }
            else -> {
                // HTTP / HTTPS CONNECT proxy for both plain and TLS traffic.
                prefs["network.proxy.http"] = host
                prefs["network.proxy.http_port"] = port
                prefs["network.proxy.ssl"] = host
                prefs["network.proxy.ssl_port"] = port
                prefs["network.proxy.share_proxy_settings"] = true
            }
        }
        return prefs
    }

    private fun buildSettings(proxy: ProxyConfig?): GeckoRuntimeSettings {
        val b = GeckoRuntimeSettings.Builder().consoleOutput(true)
        val prefs = proxyPrefs(proxy)
        if (prefs.isNotEmpty()) {
            // Belt-and-braces: env vars read by some Gecko system-proxy paths.
            val host = proxy!!.host
            val port = proxy.port
            val extras = android.os.Bundle()
            extras.putString("env0", "HTTP_PROXY=http://$host:$port")
            extras.putString("env1", "HTTPS_PROXY=http://$host:$port")
            extras.putString("env2", "http_proxy=http://$host:$port")
            b.extras(extras)
        }
        pendingPrefs = prefs
        return b.build()
    }

    /**
     * Applies [prefs] as Gecko default prefs on the freshly created runtime via
     * the package-private GeckoRuntime.setDefaultPrefs(GeckoBundle). This is the
     * same channel GeckoRuntimeSettings uses internally ("GeckoView:SetDefaultPrefs").
     */
    private fun applyPrefs(runtime: GeckoRuntime, prefs: Map<String, Any>) {
        if (prefs.isEmpty()) return
        try {
            val bundle = org.mozilla.gecko.util.GeckoBundle(prefs.size)
            prefs.forEach { (name, value) ->
                when (value) {
                    is Int -> bundle.putInt(name, value)
                    is Boolean -> bundle.putBoolean(name, value)
                    is String -> bundle.putString(name, value)
                }
            }
            val method = GeckoRuntime::class.java.getDeclaredMethod(
                "setDefaultPrefs", org.mozilla.gecko.util.GeckoBundle::class.java
            )
            method.isAccessible = true
            method.invoke(runtime, bundle)
            proxyConfigurationError = null
            Log.i(TAG, "Applied ${'$'}{prefs.size} proxy prefs to runtime")
        } catch (t: Throwable) {
            proxyConfigurationError = "Gecko proxy preferences could not be applied"
            Log.e(TAG, "Failed to apply proxy prefs; refusing to claim proxy routing", t)
        }
    }

    /** `true` once the runtime has been materialised (never creates it). */
    val isRuntimeCreated: Boolean
        get() = runtimeRef != null

    /**
     * Creates a brand new, closed [GeckoSession] with JavaScript explicitly
     * enabled and a logging [GeckoSession.Delegate] attached.
     *
     * The caller is responsible for [GeckoSession.open] (see
     * [GeckoSessionManager]) and for eventually calling [closeSession].
     */
    fun newSession(): GeckoSession {
        val sessionSettings = GeckoSessionSettings.Builder()
            .allowJavascript(true)
            .build()

        val session = GeckoSession(sessionSettings)
        // Delegate extends ContentDelegate + ProgressDelegate (+ navigation,
        // permission, user agent and history delegates), so both required
        // delegate families are attached here through one call.
        session.setContentDelegate(SessionLoggingDelegate())
        session.setProgressDelegate(SessionLoggingDelegate())
        Log.d(TAG, "GeckoSession created")
        return session
    }

    /**
     * Applies per profile settings to a session. Must be called before
     * [GeckoSession.open] for the user agent override to take effect.
     *
     * @param userAgent `null` / blank keeps the runtime default user agent.
     */
    fun applyProfileSettings(session: GeckoSession, userAgent: String?) {
        try {
            if (!userAgent.isNullOrBlank()) {
                session.settings.userAgentOverride = userAgent
                Log.d(TAG, "Profile user-agent override applied")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "applyProfileSettings failed", t)
        }
    }

    /**
     * Detaches the delegate and closes [session]. Never throws, and is safe to
     * call on a session that was never opened or already closed.
     */
    fun closeSession(session: GeckoSession) {
        try {
            session.setContentDelegate(null)
            session.setProgressDelegate(null)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to detach delegates", t)
        }
        try {
            session.close()
            Log.d(TAG, "GeckoSession closed")
        } catch (t: Throwable) {
            Log.w(TAG, "GeckoSession.close() failed", t)
        }
    }

    /**
     * Minimal logging implementation of the content / progress delegate pair.
     *
     * All interface methods carry default no-op implementations in GeckoView
     * 128; the overrides below log the interesting lifecycle events and keep
     * the default policy (no session hand off, no navigation interception, no
     * permissions granted) for everything else.
     */
    private class SessionLoggingDelegate : GeckoSession.ContentDelegate, GeckoSession.ProgressDelegate {

        private fun id(session: GeckoSession): Int = System.identityHashCode(session)

        // ---- GeckoSession.ProgressDelegate ---------------------------------

        override fun onPageStart(session: GeckoSession, url: String) {
            Log.d(TAG, "pageStart [${id(session)}]")
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            Log.d(TAG, "pageStop [${id(session)}] success=$success")
        }

        override fun onSecurityChange(
            session: GeckoSession,
            securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
        ) {
            val host = try {
                securityInfo.origin
            } catch (t: Throwable) {
                "<unknown>"
            }
            Log.d(TAG, "securityChange [${id(session)}] origin=$host")
        }

        override fun onFirstContentfulPaint(session: GeckoSession) {
            Log.d(TAG, "firstContentfulPaint [${id(session)}]")
        }

        // ---- GeckoSession.ContentDelegate ----------------------------------

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            Log.d(TAG, "fullScreen [${id(session)}] = $fullScreen")
        }

    }
}
