package com.cloakdroid.engine

import java.util.UUID

/** UI/session identity for a tab. A tab is never allowed to silently change profile. */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val title: String = "New tab",
    val url: String = "about:blank",
    val active: Boolean = false,
    val floating: Boolean = false
)

/** Pure workspace state used before Gecko multi-session support is enabled. */
data class BrowserWorkspace(
    val tabs: List<BrowserTab> = emptyList(),
    val activeTabId: String? = null
) {
    fun open(profileId: String): BrowserWorkspace {
        val tab = BrowserTab(profileId = profileId, active = true)
        return copy(tabs = tabs.map { it.copy(active = false) } + tab, activeTabId = tab.id)
    }

    fun close(tabId: String): BrowserWorkspace {
        val remaining = tabs.filterNot { it.id == tabId }
        val next = remaining.firstOrNull()
        return copy(
            tabs = remaining.map { it.copy(active = it.id == next?.id) },
            activeTabId = next?.id
        )
    }

    fun select(tabId: String): BrowserWorkspace {
        if (tabs.none { it.id == tabId }) return this
        return copy(
            tabs = tabs.map { it.copy(active = it.id == tabId) },
            activeTabId = tabId
        )
    }

    fun navigate(tabId: String, url: String, title: String = url): BrowserWorkspace =
        copy(tabs = tabs.map { if (it.id == tabId) it.copy(url = url, title = title) else it })
}
