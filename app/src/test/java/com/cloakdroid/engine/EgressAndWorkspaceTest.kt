package com.cloakdroid.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EgressAndWorkspaceTest {
    @Test fun comparisonRequiresBothObservations() {
        val result = EgressComparator.compare(null, null)
        assertEquals(EgressComparisonStatus.INCOMPLETE, result.status)
    }

    @Test fun comparisonDetectsDifferentBrowserEgress() {
        val a = EgressObservation(EgressObservation.Source.OKHTTP_PROXY_TEST, "1.1.1.1", EgressObservation.IpFamily.IPV4)
        val b = EgressObservation(EgressObservation.Source.GECKOVIEW_BROWSER, "2.2.2.2", EgressObservation.IpFamily.IPV4)
        assertEquals(EgressComparisonStatus.DIFFERENT, EgressComparator.compare(a, b).status)
    }

    @Test fun workspaceDoesNotMixProfileOwnership() {
        val first = BrowserWorkspace().open("profile-a")
        val second = first.open("profile-b")
        assertEquals("profile-a", second.tabs[0].profileId)
        assertEquals("profile-b", second.tabs[1].profileId)
    }
}
