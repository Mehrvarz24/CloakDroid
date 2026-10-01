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

        val session = engine.newSession()
        engine.applyProfileSettings(session, profileUserAgents[profileId])

        try {
            session.open(engine.runtime)
            session.loadUri(url)
        } catch (t: Throwable) {
            Log.w(TAG, "launch failed for profile=$profileId url=$url", t)
            engine.closeSession(session)
            throw t
        }

        _currentSession.value = session
        _currentUrl.value = url
        currentProfileId = profileId
        attachHistoryRecorder(session, profileId)
        Log.i(TAG, "launch profile=$profileId url=$url")
        session
    }

    /**
     * Records every successfully loaded page of [session] into the
     * per-profile history. The delegate registered here intentionally
     * replaces the engine's logging delegate; the log lines it drops are
     * duplicated below.
     */
    private fun attachHistoryRecorder(session: GeckoSession, profileId: String) {
        try {
            session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
                override fun onPageStart(s: GeckoSession, url: String) {
                    Log.d(TAG, "pageStart [${System.identityHashCode(s)}] url=$url")
                }

                override fun onPageStop(s: GeckoSession, success: Boolean) {
                    Log.d(TAG, "pageStop [${System.identityHashCode(s)}] success=$success")
                    if (!success) return
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
            Log.w(TAG, "failed to attach history recorder", t)
        }
    }

    private fun String.toUri(): android.net.Uri = android.net.Uri.parse(this)

    /**
     * Navigates the current session to [url]. No-op (with a warning) when no
     * session is open - call [launch] instead to create one.
     */
    fun loadUrl(url: String) {
        synchronized(lock) {
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
