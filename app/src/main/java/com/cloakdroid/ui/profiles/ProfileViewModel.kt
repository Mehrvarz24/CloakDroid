package com.cloakdroid.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloakdroid.data.local.ProfileEntity
import com.cloakdroid.data.network.ProxyTestResult
import com.cloakdroid.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ProfileRepository
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = repo.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testResult = MutableStateFlow<ProxyTestResult?>(null)
    val testing = MutableStateFlow(false)

    fun save(profile: ProfileEntity) {
        viewModelScope.launch { repo.saveProfile(profile) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteProfile(id) }
    }

    fun clone(id: String) {
        viewModelScope.launch { repo.cloneProfile(id) }
    }

    fun clearCache(id: String) {
        viewModelScope.launch { repo.clearCache(id) }
    }

    fun testProxy(profile: ProfileEntity) {
        testResult.value = null
        testing.value = true
        viewModelScope.launch {
            try {
                testResult.value = repo.testProxyFor(profile)
            } finally {
                testing.value = false
            }
        }
    }

    fun testProxy(
        proxyType: String,
        host: String,
        port: Int,
        username: String?,
        password: String?
    ) {
        testProxy(
            ProfileEntity(
                id = "adhoc",
                name = "adhoc",
                tagColor = "0xFF6366F1",
                userAgent = "",
                proxyType = proxyType,
                proxyHost = host,
                proxyPort = port,
                proxyUsername = username,
                proxyPassword = password,
                timezoneId = "UTC",
                localeTag = "en-US"
            )
        )
    }

    fun newRandomProfile(): ProfileEntity = ProfileEntity(
        id = java.util.UUID.randomUUID().toString(),
        name = ADJECTIVES.random() + " " + ANIMALS.random(),
        tagColor = TAG_COLORS.random(),
        userAgent = "",
        proxyType = "DIRECT",
        timezoneId = "UTC",
        localeTag = "en-US"
    )

    fun importJson(json: String) {
        viewModelScope.launch { repo.importProfile(json) }
    }

    suspend fun exportJson(id: String): String? = repo.exportProfile(id)

    companion object {
        private val ADJECTIVES = listOf(
            "Silent", "Crimson", "Velvet", "Iron", "Shadow", "Frost",
            "Neon", "Obsidian", "Azure", "Swift", "Hollow", "Radiant"
        )

        private val ANIMALS = listOf(
            "Fox", "Raven", "Wolf", "Panther", "Lynx", "Viper",
            "Heron", "Otter", "Jackal", "Falcon", "Moth", "Badger"
        )

        private val TAG_COLORS = listOf(
            "0xFF6366F1", "0xFF22C55E", "0xFFEF4444", "0xFFF59E0B",
            "0xFF06B6D4", "0xFFEC4899", "0xFF8B5CF6", "0xFF14B8A6"
        )
    }
}
