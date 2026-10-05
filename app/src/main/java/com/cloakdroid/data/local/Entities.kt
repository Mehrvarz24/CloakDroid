package com.cloakdroid.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Per-profile WebRTC policy.
 *
 * DISABLED  - RTCPeerConnection is removed entirely (strongest, default).
 * PROXY_ONLY- A best-effort page-level candidate policy. Native WebRTC
 *             routing is not guaranteed and must be verified on-device.
 * FULL      - WebRTC is left untouched.
 */
enum class WebRtcPolicy {
    DISABLED,
    PROXY_ONLY,
    FULL;

    companion object {
        fun from(value: String?): WebRtcPolicy =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DISABLED
    }
}

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "tag_color")
    val tagColor: String,

    @ColumnInfo(name = "user_agent")
    val userAgent: String,

    /** One of: SOCKS5 | HTTP | HTTPS | DIRECT */
    @ColumnInfo(name = "proxy_type")
    val proxyType: String = "DIRECT",

    @ColumnInfo(name = "proxy_host")
    val proxyHost: String? = null,

    @ColumnInfo(name = "proxy_port")
    val proxyPort: Int? = null,

    @ColumnInfo(name = "proxy_username")
    val proxyUsername: String? = null,

    @ColumnInfo(name = "proxy_password")
    val proxyPassword: String? = null,

    @ColumnInfo(name = "auto_sync_geolocation")
    val autoSyncGeolocation: Boolean = false,

    @ColumnInfo(name = "spoof_lat")
    val spoofLat: Double? = null,

    @ColumnInfo(name = "spoof_lon")
    val spoofLon: Double? = null,

    @ColumnInfo(name = "webrtc_enabled")
    val webrtcEnabled: Boolean = false,

    /**
     * WebRTC policy: DISABLED (default) removes RTCPeerConnection entirely,
     * PROXY_ONLY applies a best-effort page-level candidate policy; native
     * WebRTC routing is not guaranteed. FULL leaves it untouched.
     */
    @ColumnInfo(name = "webrtc_policy")
    val webrtcPolicy: String = WebRtcPolicy.DISABLED.name,

    @ColumnInfo(name = "screen_w")
    val screenW: Int? = null,

    @ColumnInfo(name = "screen_h")
    val screenH: Int? = null,

    @ColumnInfo(name = "device_pixel_ratio")
    val devicePixelRatio: Float? = null,

    @ColumnInfo(name = "device_name")
    val deviceName: String? = null,

    /** Stable SHA-256 over the identity fields; unique across profiles. */
    @ColumnInfo(name = "fingerprint_hash")
    val fingerprintHash: String? = null,

    @ColumnInfo(name = "canvas_noise")
    val canvasNoise: Boolean = false,

    @ColumnInfo(name = "audio_noise")
    val audioNoise: Boolean = false,

    @ColumnInfo(name = "timezone_id")
    val timezoneId: String,

    @ColumnInfo(name = "locale_tag")
    val localeTag: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
)

@Entity(
    tableName = "proxy_test_results",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["profile_id", "tested_at"])
    ]
)
data class ProxyTestResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "success")
    val success: Boolean,

    @ColumnInfo(name = "latency_ms")
    val latencyMs: Long,

    @ColumnInfo(name = "public_ip")
    val publicIp: String? = null,

    @ColumnInfo(name = "country_code")
    val countryCode: String? = null,

    @ColumnInfo(name = "city")
    val city: String? = null,

    @ColumnInfo(name = "isp")
    val isp: String? = null,

    @ColumnInfo(name = "tested_at")
    val testedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["profile_id", "created_at"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["profile_id", "visited_at"])
    ]
)
data class HistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "visited_at")
    val visitedAt: Long = System.currentTimeMillis(),
)
