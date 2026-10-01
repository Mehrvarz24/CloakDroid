package com.cloakdroid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Parses a stored tag color string into a Compose [Color], falling back to the
 * brand primary on any failure. Accepts both `0xFF6366F1` (the format used by
 * ProfileViewModel.TAG_COLORS) and `#6366F1` / `#FF6366F1` formats.
 */
fun parseTagColor(raw: String?, fallback: Color = Color(0xFF6366F1)): Color {
    val s = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return fallback
    return try {
        when {
            s.startsWith("0x", ignoreCase = true) || s.startsWith("#") -> {
                val hex = s.removePrefix("0x").removePrefix("0X").removePrefix("#")
                val argb = when (hex.length) {
                    6 -> java.lang.Long.parseLong("FF$hex", 16)   // add alpha
                    8 -> java.lang.Long.parseLong(hex, 16)
                    else -> return fallback
                }
                Color(argb.toInt())
            }
            else -> fallback
        }
    } catch (_: Throwable) {
        fallback
    }
}
