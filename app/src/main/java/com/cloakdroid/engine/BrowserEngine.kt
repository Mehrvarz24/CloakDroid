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
    val runtime: GeckoRuntime
        get() {
            runtimeRef?.let { cached -> return cached }
            synchronized(runtimeLock) {
                runtimeRef?.let { cached -> return cached }

                val settings = GeckoRuntimeSettings.Builder()
                    .consoleOutput(true)
                    .build()
                

                val created = GeckoRuntime.create(appContext, settings)
                runtimeRef = created
                Log.i(TAG, "GeckoRuntime created (single process wide instance)")
                return created
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
