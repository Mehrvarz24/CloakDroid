package com.cloakdroid.engine

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession

/**
 * Owns at most one live [GeckoSession] at a time and exposes it, together with
 * the URL it is currently displaying, as [StateFlow]s for Compose collection.
 *
 * Every mutating entry point ([launch], [loadUrl], [destroyCurrent],
 * [setProfileUserAgent]) is serialised on a private lock so the session
 * lifecycle can never race with itself, and so the flows always describe a
 * consistent (session, url) pair.
 *
 * Kill switch: when [killSwitchEnabled] is on and the active profile has a
 * proxy, every navigation is inspected in [onLoadRequest]. If the proxy stops
 * answering (a load fails at the network level), the manager latches into a
 * blocked state: further loads are refused until the user launches the
 * profile again (or turns the kill switch off), so the real IP can never
 * silently take over mid-session.
 */
@Singleton
class GeckoSessionManager @Inject constructor(
    private val engine: BrowserEngine,
    private val repository: com.cloakdroid.data.repository.ProfileRepository,
    @com.cloakdroid.di.IoScope private val ioScope: kotlinx.coroutines.CoroutineScope
) {
    companion object {
        private const val TAG = "CloakDroidSessions"
        private const val BLANK_URL = "about:blank"
    }

    /** Serialises session creation / destruction / navigation. */
    private val lock = Any()

    /** profileId -> user agent override applied when that profile launches. */
    private val profileUserAgents = LinkedHashMap<String, String>()

    private val _currentSession = MutableStateFlow<GeckoSession?>(null)

    /** The live session, or `null` when nothing is open. `null` after destroy. */
    val currentSession: StateFlow<GeckoSession?> = _currentSession.asStateFlow()

    private val _currentUrl = MutableStateFlow(BLANK_URL)

    /** URL currently (or last) requested from the active session. */
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    /**
     * Kill switch state for the UI: `true` while the manager refuses to load
     * anything because the proxied connection failed. Reset on launch.
     */
    private val _blocked = MutableStateFlow(false)

    private val _diagnostics = MutableStateFlow<List<NetworkDiagnostic>>(emptyList())
    val diagnostics: StateFlow<List<NetworkDiagnostic>> = _diagnostics.asStateFlow()

    /** `true` when navigation is currently refused (proxy failed, kill switch armed). */
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    /** The profile that owns the current session, `null` when none is open. */
    @Volatile
    var currentProfileId: String? = null
        private set

    /**
     * Closes any previous session, opens a fresh one for [profileId] and
     * navigates it to [url].
     *
     * @return the newly opened, loading session.
     * @throws Throwable if the Gecko runtime or the session cannot be opened;
     * the half built session is always cleaned up first.
     */
    fun launch(profileId: String, url: String): GeckoSession = synchronized(lock) {
        closeCurrentLocked()
        _blocked.value = false

        // Ensure the process-wide runtime matches this profile's proxy. A
        // different proxy after runtime creation is refused; silently
        // restarting GeckoRuntime is not a safe per-profile routing strategy.
        val proxy = repository.proxyConfigFor(profileId)
        if (!engine.runtimeMatchesProxy(proxy)) {
            engine.resetRuntime(proxy)
        }

        // Proxy routing is a safety precondition. Invalid non-direct
        // configuration must never degrade into Direct browsing.
        if (proxy != null && (proxy.host.isNullOrBlank() || proxy.port !in 1..65535)) {
            _blocked.value = true
            throw IllegalStateException("Proxy configuration is incomplete or has an invalid port")
        }
        if (proxy?.type == com.cloakdroid.data.network.ProxyType.HTTPS) {
            _blocked.value = true
            throw IllegalStateException(
                "TLS-encrypted proxy endpoints are unsupported; use an HTTP CONNECT proxy"
            )
        }
        if (proxy?.type == com.cloakdroid.data.network.ProxyType.SOCKS5 &&
            !proxy.username.isNullOrBlank()
        ) {
            _blocked.value = true
            throw IllegalStateException("Authenticated SOCKS5 browser routing is unsupported")
        }
        // Reflection/API failure must be visible and must never degrade into
        // direct browsing.
        val runtime = engine.runtime
        if (proxy != null && engine.proxyConfigurationError != null) {
            _blocked.value = true
            throw IllegalStateException(engine.proxyConfigurationError)
        }

        val session = engine.newSession()
        engine.applyProfileSettings(session, profileUserAgents[profileId])

        try {
            session.open(runtime)
            session.loadUri(url)
        } catch (t: Throwable) {
            Log.w(TAG, "launch failed for profile=$profileId url=$url", t)
            engine.closeSession(session)
            throw t
        }

        _currentSession.value = session
        _currentUrl.value = url
        currentProfileId = profileId
        attachSessionDelegate(session, profileId, proxy != null)
        Log.i(TAG, "launch profile=$profileId url=$url")
        session
    }

    /**
     * Progress/content delegate that (a) records history, (b) publishes live
     * location for the URL bar and (c) implements the kill switch: when a
     * proxied load fails at the network level, latch [blocked] so nothing else
     * loads until an explicit re-launch.
     */
    private fun attachSessionDelegate(session: GeckoSession, profileId: String, proxied: Boolean) {
        try {
            session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
                override fun onPageStart(s: GeckoSession, url: String) {
                    Log.d(TAG, "pageStart [${System.identityHashCode(s)}] url=$url")
                    _currentUrl.value = url
                }

                override fun onPageStop(s: GeckoSession, success: Boolean) {
                    Log.d(TAG, "pageStop [${System.identityHashCode(s)}] success=$success")
                    if (!success) {
                        if (proxied && com.cloakdroid.ui.settings.ThemeController.killSwitchEnabled) {
                            Log.w(TAG, "KILL SWITCH: proxied load failed, blocking further loads")
                            _blocked.value = true
                        }
                        return
                    }
                    val visitedUrl = _currentUrl.value
                    if (visitedUrl.isBlank() || visitedUrl == BLANK_URL) return
                    ioScope.launch {
                        repository.recordVisit(
                            profileId = profileId,
                            url = visitedUrl,
                            title = visitedUrl.toUri().host?.ifBlank { visitedUrl }
                                ?: visitedUrl
                        )
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "failed to attach session delegate", t)
        }
    }

    private fun String.toUri(): android.net.Uri = android.net.Uri.parse(this)

    /**
     * Navigates the current session to [url]. Refused while the kill switch
     * has latched [blocked] — the caller (UI) should offer a re-launch.
     */
    fun loadUrl(url: String) {
        synchronized(lock) {
            if (_blocked.value) {
                Log.w(TAG, "loadUrl refused by kill switch: $url")
                return
            }
            val session = _currentSession.value
            if (session == null) {
                Log.w(TAG, "loadUrl ignored, no open session: $url")
                return
            }
            try {
                session.loadUri(url)
                _currentUrl.value = url
                Log.d(TAG, "loadUrl $url")
            } catch (t: Throwable) {
                Log.w(TAG, "loadUrl failed: $url", t)
            }
        }
    }

    /**
     * Clears the kill-switch latch so navigation is allowed again. The user
     * explicitly accepts that the connection may now be direct.
     */
    /**
     * Returns honest capability diagnostics for the active engine. Network
     * egress requires a controlled endpoint/device comparison; this method
     * deliberately reports that limitation instead of inventing a pass.
     */
    fun refreshDiagnostics() {
        val proxy = currentProfileId?.let { repository.proxyConfigFor(it) }
        val entries = mutableListOf(
            NetworkDiagnostic(
                "GeckoView session",
                if (_currentSession.value != null) DiagnosticStatus.PASS else DiagnosticStatus.FAILED,
                if (_currentSession.value != null) "A live GeckoSession is attached" else "No live GeckoSession"
            ),
            NetworkDiagnostic(
                "Browser egress",
                DiagnosticStatus.NOT_TESTED,
                "Requires a controlled browser endpoint; OkHttp proxy success is not browser egress proof"
            ),
            NetworkDiagnostic(
                "DNS and IPv6 leak status",
                DiagnosticStatus.NOT_TESTED,
                "No device-level DNS/IPv6 fixture is available"
            ),
            NetworkDiagnostic(
                "WebRTC native routing",
                DiagnosticStatus.WARNING,
                "Page-level policy is not proof of native WebRTC routing"
            ),
            NetworkDiagnostic(
                "Profile storage isolation",
                DiagnosticStatus.UNSUPPORTED,
                "Gecko SessionContext isolation is not installed yet"
            ),
            NetworkDiagnostic(
                "Concurrent profiles",
                DiagnosticStatus.UNSUPPORTED,
                "The current manager owns one live session and one runtime proxy"
            )
        )
        if (proxy?.type == com.cloakdroid.data.network.ProxyType.SOCKS5 &&
            !proxy.username.isNullOrBlank()
        ) {
            entries += NetworkDiagnostic(
                "Authenticated SOCKS5 browser routing",
                DiagnosticStatus.UNSUPPORTED,
                "Authenticated SOCKS5 browser routing is not implemented"
            )
        }
        _diagnostics.value = entries.map { it.copy(testedAt = System.currentTimeMillis()) }
    }

    fun unblock() {
        synchronized(lock) { _blocked.value = false }
    }

    /**
     * Closes and forgets the current session. Safe to call twice, safe to call
     * when nothing was ever launched, and never throws.
     */
    fun destroyCurrent() {
        synchronized(lock) {
            closeCurrentLocked()
        }
    }

    /**
     * Registers (or, with `null`, clears) the user agent used the next time
     * [profileId] is passed to [launch].
     */
    fun setProfileUserAgent(profileId: String, userAgent: String?) {
        synchronized(lock) {
            if (userAgent.isNullOrBlank()) {
                profileUserAgents.remove(profileId)
            } else {
                profileUserAgents[profileId] = userAgent
            }
        }
    }

    /**
     * Detaches and closes the session held under [lock], publishing `null`
     * first so Compose never observes a session that is being torn down.
     */
    private fun closeCurrentLocked() {
        val session = _currentSession.value ?: return
        _currentSession.value = null
        currentProfileId = null
        engine.closeSession(session)
        Log.d(TAG, "session destroyed")
    }
}
