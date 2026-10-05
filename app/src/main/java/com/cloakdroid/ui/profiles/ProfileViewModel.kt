package com.cloakdroid.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloakdroid.data.fingerprint.FingerprintGenerator
import com.cloakdroid.data.fingerprint.GeneratedIdentity
import com.cloakdroid.data.local.BookmarkEntity
import com.cloakdroid.data.local.ProfileEntity
import com.cloakdroid.data.network.ProxyTestResult
import com.cloakdroid.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    private val fingerprintGenerator: FingerprintGenerator
) : ViewModel() {

    companion object {
        /** Max attempts when searching for an unused fingerprint hash. */
        private const val UNIQUENESS_RETRY_LIMIT = 50

        private val ADJECTIVES = listOf(
            "Silent", "Crimson", "Velvet", "Iron", "Shadow", "Frost",
            "Neon", "Obsidian", "Azure", "Swift", "Hollow", "Radiant"
        )

        private val ANIMALS = listOf(
            "Fox", "Raven", "Wolf", "Panther", "Lynx", "Viper",
            "Heron", "Otter", "Jackal", "Falcon", "Moth", "Badger"
        )

        private val TAG_COLORS = listOf(
            "0xFFD9A05B", "0xFF8FA98F", "0xFFD97A6C", "0xFFD9B25B",
            "0xFF8FB0C9", "0xFFC99AA4", "0xFFA9927F", "0xFF7FA97A"
        )
    }

    val profiles: StateFlow<List<ProfileEntity>> = repo.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testResult = MutableStateFlow<ProxyTestResult?>(null)
    val testing = MutableStateFlow(false)
    private var testGeneration = 0L

    /** Number of profiles created by the most recent batch generation. */
    val lastBatchCreated = MutableStateFlow(0)

    /** Bulk "Test all": progress state. profileId -> result summary. */
    data class BulkEntry(val name: String, val ok: Boolean, val latencyMs: Long)
    val bulkTesting = MutableStateFlow(false)
    val bulkProgress = MutableStateFlow(0)
    val bulkTotal = MutableStateFlow(0)
    val bulkResults = MutableStateFlow<List<BulkEntry>>(emptyList())

    /**
     * Tests every profile with a proxy configured, sequentially (so we don't
     * hammer the network), publishing progress. Results arrive sorted by
     * latency (fastest first; failures last).
     */
    fun testAllProfiles() {
        if (bulkTesting.value) return
        val snapshot = profiles.value.filter { !it.proxyHost.isNullOrBlank() }
        if (snapshot.isEmpty()) return
        bulkTesting.value = true
        bulkProgress.value = 0
        bulkTotal.value = snapshot.size
        bulkResults.value = emptyList()
        viewModelScope.launch {
            val results = mutableListOf<BulkEntry>()
            try {
                snapshot.forEach { profile ->
                    val entry = try {
                        when (val r = repo.testProxyFor(profile)) {
                            is com.cloakdroid.data.network.ProxyTestResult.Success ->
                                BulkEntry(profile.name, true, r.latencyMs)
                            else -> BulkEntry(profile.name, false, 0)
                        }
                    } catch (t: Throwable) {
                        BulkEntry(profile.name, false, 0)
                    }
                    results.add(entry)
                    bulkResults.value = results.sortedWith(
                        compareByDescending<BulkEntry> { it.ok }.thenBy { it.latencyMs }
                    )
                    bulkProgress.value = bulkProgress.value + 1
                }
            } finally {
                bulkTesting.value = false
            }
        }
    }

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
        if (testing.value) return
        val generation = ++testGeneration
        testResult.value = null
        testing.value = true
        viewModelScope.launch {
            try {
                val result = repo.testProxyFor(profile)
                if (generation == testGeneration) testResult.value = result
            } finally {
                if (generation == testGeneration) testing.value = false
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
                tagColor = "0xFFD9A05B",
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

    // ------------------------------------------------------- fingerprinting

    /**
     * Generates a fresh identity and retries (up to
     * [UNIQUENESS_RETRY_LIMIT] tries) until its fingerprint hash is not
     * already used by any existing profile or by [taken] hashes.
     *
     * @return the identity, or `null` when no unique one was found in time.
     */
    suspend fun generateUniqueIdentity(
        taken: Set<String> = emptySet()
    ): GeneratedIdentity? {
        val existing = repo.existingFingerprintHashes() + taken
        repeat(UNIQUENESS_RETRY_LIMIT) {
            val candidate = fingerprintGenerator.generate()
            if (candidate.fingerprintHash !in existing) {
                return candidate
            }
        }
        return null
    }

    /** Creates [count] unique profiles at once with distinct fingerprints. */
    fun generateBatch(count: Int) {
        viewModelScope.launch {
            var created = 0
            val takenHashes = mutableSetOf<String>()
            repeat(count) {
                val identity = generateUniqueIdentity(takenHashes) ?: return@repeat
                val profile = ProfileEntity(
                    name = ADJECTIVES.random() + " " + ANIMALS.random(),
                    tagColor = TAG_COLORS.random(),
                    userAgent = identity.userAgent,
                    screenW = identity.screenW,
                    screenH = identity.screenH,
                    devicePixelRatio = identity.devicePixelRatio,
                    deviceName = identity.deviceName,
                    fingerprintHash = identity.fingerprintHash,
                    canvasNoise = true,
                    audioNoise = true,
                    timezoneId = "UTC",
                    localeTag = "en-US"
                )
                repo.saveProfile(profile)
                takenHashes.add(identity.fingerprintHash)
                created++
            }
            lastBatchCreated.value = created
        }
    }

    /**
     * A brand new random profile. Its fingerprint (if any) is finalized by
     * the editor's "Generate fingerprint" action, which enforces uniqueness.
     */
    fun newRandomProfile(): ProfileEntity = ProfileEntity(
        id = java.util.UUID.randomUUID().toString(),
        name = ADJECTIVES.random() + " " + ANIMALS.random(),
        tagColor = TAG_COLORS.random(),
        userAgent = "",
        proxyType = "DIRECT",
        timezoneId = "UTC",
        localeTag = "en-US"
    )

    // -------------------------------------------------------- import/export

    fun importJson(json: String) {
        viewModelScope.launch { repo.importProfile(json) }
    }

    suspend fun exportJson(id: String): String? = repo.exportProfile(id)

    // ------------------------------------------------------- bookmarks

    fun observeBookmarks(profileId: String): Flow<List<BookmarkEntity>> =
        repo.observeBookmarks(profileId)

    fun toggleBookmark(profileId: String, url: String, title: String?) {
        viewModelScope.launch { repo.toggleBookmark(profileId, url, title) }
    }

    fun isBookmarked(profileId: String, url: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.isBookmarked(profileId, url)) }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch { repo.deleteBookmark(bookmark) }
    }

    // -------------------------------------------------------- history

    fun observeHistory(profileId: String) = repo.observeHistory(profileId)

    fun clearHistory(profileId: String) {
        viewModelScope.launch { repo.clearHistory(profileId) }
    }
}
