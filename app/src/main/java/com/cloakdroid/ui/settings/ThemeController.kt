package com.cloakdroid.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** App-wide theme mode. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Soft muted accent presets. */
enum class Accent { CARAMEL, SAGE, SKY, ROSE }

/**
 * Singleton holding the live theme state, persisted in SharedPreferences.
 * Reads are cheap and synchronous; CloakDroidTheme observes these fields.
 */
object ThemeController {

    private const val PREFS = "cloakdroid_theme"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_ACCENT = "accent"
    private const val KEY_BG_IMAGE = "bg_image"
    private const val KEY_KILL_SWITCH = "kill_switch"

    var themeMode by mutableStateOf(ThemeMode.DARK)
        private set

    var accent by mutableStateOf(Accent.CARAMEL)
        private set

    /** Absolute path of the user's custom background image, or null. */
    var backgroundImagePath by mutableStateOf<String?>(null)
        private set

    /** Kill switch: refuse all navigation if the proxied connection fails. */
    var killSwitchEnabled by mutableStateOf(true)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        themeMode = runCatching { ThemeMode.valueOf(prefs!!.getString(KEY_MODE, ThemeMode.DARK.name)!!) }
            .getOrDefault(ThemeMode.DARK)
        accent = runCatching { Accent.valueOf(prefs!!.getString(KEY_ACCENT, Accent.CARAMEL.name)!!) }
            .getOrDefault(Accent.CARAMEL)
        backgroundImagePath = prefs!!.getString(KEY_BG_IMAGE, null)
        killSwitchEnabled = prefs!!.getBoolean(KEY_KILL_SWITCH, true)
    }

    @JvmName("applyThemeMode")
    fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString(KEY_MODE, mode.name)?.apply()
    }

    @JvmName("applyAccent")
    fun setAccent(a: Accent) {
        accent = a
        prefs?.edit()?.putString(KEY_ACCENT, a.name)?.apply()
    }

    @JvmName("setKillSwitch")
    fun setKillSwitch(enabled: Boolean) {
        killSwitchEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_KILL_SWITCH, enabled)?.apply()
    }

    /** Copies the picked image into app-private storage and activates it. */
    @JvmName("applyBackgroundImage")
    fun setBackgroundImage(context: Context, uri: Uri): Boolean {
        return runCatching {
            val out = File(context.filesDir, "custom_background.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            backgroundImagePath = out.absolutePath
            prefs?.edit()?.putString(KEY_BG_IMAGE, out.absolutePath)?.apply()
            true
        }.getOrDefault(false)
    }

    @JvmName("resetBackgroundImage")
    fun clearBackgroundImage() {
        backgroundImagePath?.let { File(it).delete() }
        backgroundImagePath = null
        prefs?.edit()?.remove(KEY_BG_IMAGE)?.apply()
    }

    /** Decoded, downsampled custom background bitmap (or null). */
    fun backgroundBitmap(): ImageBitmap? {
        val path = backgroundImagePath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
}
