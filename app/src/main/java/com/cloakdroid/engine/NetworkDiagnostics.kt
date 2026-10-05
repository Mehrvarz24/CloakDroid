package com.cloakdroid.engine

/** Honest browser-network diagnostic state; it never claims anonymity. */
enum class DiagnosticStatus { PASS, WARNING, FAILED, UNSUPPORTED, NOT_TESTED }

data class NetworkDiagnostic(
    val name: String,
    val status: DiagnosticStatus,
    val detail: String,
    val testedAt: Long? = null
)
