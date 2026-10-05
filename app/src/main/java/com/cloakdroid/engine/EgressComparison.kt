package com.cloakdroid.engine

/** Sanitized observations from controlled egress fixtures. */
data class EgressObservation(
    val source: Source,
    val observedIp: String?,
    val family: IpFamily?,
    val completedAt: Long = System.currentTimeMillis(),
    val error: String? = null
) {
    enum class Source { OKHTTP_PROXY_TEST, GECKOVIEW_BROWSER }
    enum class IpFamily { IPV4, IPV6 }
}

enum class EgressComparisonStatus { MATCH, DIFFERENT, INCOMPLETE, FAILED }

data class EgressComparison(
    val status: EgressComparisonStatus,
    val tester: EgressObservation?,
    val browser: EgressObservation?,
    val detail: String
)

object EgressComparator {
    fun compare(tester: EgressObservation?, browser: EgressObservation?): EgressComparison {
        if (tester == null || browser == null) {
            return EgressComparison(
                EgressComparisonStatus.INCOMPLETE, tester, browser,
                "Both OkHttp and GeckoView observations are required"
            )
        }
        if (tester.error != null || browser.error != null) {
            return EgressComparison(
                EgressComparisonStatus.FAILED, tester, browser,
                "At least one egress observation failed"
            )
        }
        val sameIp = tester.observedIp != null && tester.observedIp == browser.observedIp
        return EgressComparison(
            if (sameIp) EgressComparisonStatus.MATCH else EgressComparisonStatus.DIFFERENT,
            tester, browser,
            if (sameIp) "Tester and browser observed the same egress IP"
            else "Tester and browser observed different egress IPs"
        )
    }
}
