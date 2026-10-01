package com.cloakdroid.data.repository

import android.content.Context
import com.cloakdroid.data.local.AppDatabase
import com.cloakdroid.data.local.BookmarkDao
import com.cloakdroid.data.local.BookmarkEntity
import com.cloakdroid.data.local.HistoryDao
import com.cloakdroid.data.local.HistoryEntity
import com.cloakdroid.data.local.ProfileDao
import com.cloakdroid.data.local.ProfileEntity
import com.cloakdroid.data.local.ProxyTestResultEntity
import com.cloakdroid.data.network.ProxyConfig
import com.cloakdroid.data.network.ProxyTester
import com.cloakdroid.data.network.ProxyTestResult
import com.cloakdroid.data.network.ProxyType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ProfileTransfer(
    val id: String,
    val name: String,
    val tagColor: String,
    val userAgent: String,
    val proxyType: String,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val autoSyncGeolocation: Boolean = false,
    val spoofLat: Double? = null,
    val spoofLon: Double? = null,
    val webrtcEnabled: Boolean = true,
    val webrtcPolicy: String = "DISABLED",
    val screenW: Int? = null,
    val screenH: Int? = null,
    val devicePixelRatio: Float? = null,
    val deviceName: String? = null,
    val fingerprintHash: String? = null,
    val canvasNoise: Boolean = true,
    val audioNoise: Boolean = true,
    val timezoneId: String = "UTC",
    val localeTag: String = "en-US",
    val createdAt: Long = 0L,
    val lastUsedAt: Long? = null
)

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val database: AppDatabase,
    private val proxyTester: ProxyTester,
    @ApplicationContext private val context: Context
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeProfiles(): Flow<List<ProfileEntity>> = dao.observeAll()

    suspend fun saveProfile(profile: ProfileEntity) {
        dao.upsert(profile)
    }

    suspend fun deleteProfile(id: String) {
        dao.deleteById(id)
        withContext(Dispatchers.IO) {
            val dir = sandboxDir(id)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    suspend fun cloneProfile(id: String): ProfileEntity? {
        val source = dao.getById(id) ?: return null
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = source.name + " (copy)",
            createdAt = System.currentTimeMillis(),
            lastUsedAt = 0L
        )
        dao.upsert(copy)
        return copy
    }

    suspend fun clearCache(id: String) {
        withContext(Dispatchers.IO) {
            val cacheDir = File(sandboxDir(id), "cache")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { child ->
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            } else {
                cacheDir.deleteRecursively()
            }
        }
    }

    fun sandboxDir(id: String): File = File(context.filesDir, "profiles/$id")

    private fun cacheDir(id: String): File = File(sandboxDir(id), "cache")

    /**
     * Builds the [ProxyConfig] for a profile without testing it. Returns a
     * DIRECT config when the profile has no host set.
     */
    fun proxyConfigFor(profileId: String): ProxyConfig? =
        kotlinx.coroutines.runBlocking { proxyConfigForSuspend(profileId) }

    private suspend fun proxyConfigForSuspend(profileId: String): ProxyConfig? {
        val profile = dao.getById(profileId) ?: return null
        if (profile.proxyHost.isNullOrBlank()) return null
        val rawType = profile.proxyType.uppercase()
        val proxyType = when {
            rawType.contains("SOCKS") -> ProxyType.SOCKS5
            rawType.contains("HTTPS") -> ProxyType.HTTPS
            rawType.contains("HTTP") -> ProxyType.HTTP
            else -> ProxyType.DIRECT
        }
        if (proxyType == ProxyType.DIRECT) return null
        return ProxyConfig(
            host = profile.proxyHost,
            port = profile.proxyPort,
            username = profile.proxyUsername,
            password = profile.proxyPassword,
            type = proxyType
        )
    }

    /**
     * Runs a proxy test and persists the outcome — but only for profiles that
     * actually exist in the DB. Ad-hoc tests (from the editor before Save use
     * the synthetic id "adhoc") violate the proxy_test_results FK, so their
     * results stay in memory/UI only.
     */
    suspend fun testProxyFor(profile: ProfileEntity): ProxyTestResult {
        val rawType = profile.proxyType.uppercase()
        val proxyType = when {
            profile.proxyHost.isNullOrBlank() -> ProxyType.DIRECT
            rawType.contains("SOCKS") -> ProxyType.SOCKS5
            rawType.contains("HTTPS") -> ProxyType.HTTPS
            rawType.contains("HTTP") -> ProxyType.HTTP
            else -> ProxyType.DIRECT
        }

        val config = ProxyConfig(
            host = profile.proxyHost,
            port = profile.proxyPort,
            username = profile.proxyUsername,
            password = profile.proxyPassword,
            type = proxyType
        )

        val result = proxyTester.test(config)

        val profileExists = profile.id != "adhoc" &&
            withContext(Dispatchers.IO) { dao.getById(profile.id) != null }
        if (profileExists) {
            val entity = ProxyTestResultEntity(
                profileId = profile.id,
                success = result is ProxyTestResult.Success,
                latencyMs = when (result) {
                    is ProxyTestResult.Success -> result.latencyMs
                    else -> 0L
                },
                publicIp = when (result) {
                    is ProxyTestResult.Success -> result.publicIp
                    else -> null
                },
                countryCode = when (result) {
                    is ProxyTestResult.Success -> result.countryCode
                    else -> null
                },
                city = when (result) {
                    is ProxyTestResult.Success -> result.city
                    else -> null
                },
                isp = when (result) {
                    is ProxyTestResult.Success -> result.isp
                    else -> null
                },
                testedAt = System.currentTimeMillis()
            )
            dao.insertTestResult(entity)
        }

        return result
    }

    suspend fun exportProfile(id: String): String? {
        val profile = dao.getById(id) ?: return null
        val transfer = ProfileTransfer(
            id = profile.id,
            name = profile.name,
            tagColor = profile.tagColor,
            userAgent = profile.userAgent,
            proxyType = profile.proxyType?.toString() ?: "",
            proxyHost = profile.proxyHost,
            proxyPort = profile.proxyPort,
            proxyUsername = profile.proxyUsername,
            proxyPassword = profile.proxyPassword,
            autoSyncGeolocation = profile.autoSyncGeolocation,
            spoofLat = profile.spoofLat,
            spoofLon = profile.spoofLon,
            webrtcEnabled = profile.webrtcEnabled,
            webrtcPolicy = profile.webrtcPolicy,
            screenW = profile.screenW,
            screenH = profile.screenH,
            devicePixelRatio = profile.devicePixelRatio,
            deviceName = profile.deviceName,
            fingerprintHash = profile.fingerprintHash,
            canvasNoise = profile.canvasNoise,
            audioNoise = profile.audioNoise,
            timezoneId = profile.timezoneId,
            localeTag = profile.localeTag,
            createdAt = profile.createdAt,
            lastUsedAt = profile.lastUsedAt
        )
        return json.encodeToString(transfer)
    }

    suspend fun importProfile(raw: String): ProfileEntity? {
        return try {
            val transfer = json.decodeFromString(ProfileTransfer.serializer(), raw)
            val now = System.currentTimeMillis()
            val profile = ProfileEntity(
                id = UUID.randomUUID().toString(),
                name = transfer.name,
                tagColor = transfer.tagColor,
                userAgent = transfer.userAgent,
                proxyType = transfer.proxyType,
                proxyHost = transfer.proxyHost,
                proxyPort = transfer.proxyPort,
                proxyUsername = transfer.proxyUsername,
                proxyPassword = transfer.proxyPassword,
                autoSyncGeolocation = transfer.autoSyncGeolocation,
                spoofLat = transfer.spoofLat,
                spoofLon = transfer.spoofLon,
                webrtcEnabled = transfer.webrtcEnabled,
                webrtcPolicy = transfer.webrtcPolicy,
                screenW = transfer.screenW,
                screenH = transfer.screenH,
                devicePixelRatio = transfer.devicePixelRatio,
                deviceName = transfer.deviceName,
                fingerprintHash = transfer.fingerprintHash,
                canvasNoise = transfer.canvasNoise,
                audioNoise = transfer.audioNoise,
                timezoneId = transfer.timezoneId,
                localeTag = transfer.localeTag,
                createdAt = now,
                lastUsedAt = now
            )
            dao.upsert(profile)
            profile
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun enforceCacheLimit(id: String, maxBytes: Long = 52_428_800L) {
        withContext(Dispatchers.IO) {
            val dir = cacheDir(id)
            if (!dir.exists()) return@withContext

            val files = dir.listFiles { f -> f.isFile }?.toMutableList()
                ?: return@withContext
            if (files.isEmpty()) return@withContext

            var total = files.sumOf { it.length() }
            files.sortBy { it.lastModified() }

            var index = 0
            while (total > maxBytes && index < files.size) {
                val file = files[index]
                val size = file.length()
                if (file.delete()) {
                    total -= size
                }
                index++
            }
        }
    }

    // ------------------------------------------------------------ bookmarks

    fun observeBookmarks(profileId: String): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeFor(profileId)

    /** Toggles the bookmark for [url]; returns `true` when now bookmarked. */
    suspend fun toggleBookmark(profileId: String, url: String, title: String?): Boolean {
        return if (bookmarkDao.existsFor(profileId, url)) {
            bookmarkDao.deleteFor(profileId, url)
            false
        } else {
            bookmarkDao.insert(
                BookmarkEntity(
                    profileId = profileId,
                    title = title?.takeIf { it.isNotBlank() } ?: url,
                    url = url
                )
            )
            true
        }
    }

    suspend fun isBookmarked(profileId: String, url: String): Boolean =
        bookmarkDao.existsFor(profileId, url)

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.deleteFor(bookmark.profileId, bookmark.url)
    }

    // -------------------------------------------------------------- history

    fun observeHistory(profileId: String): Flow<List<HistoryEntity>> =
        historyDao.observeFor(profileId)

    /** Records a visited page. Inline no-op safe (never throws). */
    suspend fun recordVisit(profileId: String, url: String, title: String?) {
        if (profileId.isBlank() || url.isBlank()) return
        try {
            historyDao.insert(
                HistoryEntity(
                    profileId = profileId,
                    url = url,
                    title = title?.takeIf { it.isNotBlank() }
                )
            )
        } catch (_: Throwable) {
            // History recording must never break browsing.
        }
    }

    suspend fun clearHistory(profileId: String) {
        historyDao.deleteAllFor(profileId)
    }

    // ------------------------------------------------- fingerprint uniqueness

    /**
     * All fingerprint hashes currently stored on profiles. Used by
     * [FingerprintGenerator] consumers to guarantee no two profiles ever
     * share the same fingerprint.
     */
    suspend fun existingFingerprintHashes(): Set<String> =
        dao.allFingerprintHashes().filterNotNull().toSet()
}
