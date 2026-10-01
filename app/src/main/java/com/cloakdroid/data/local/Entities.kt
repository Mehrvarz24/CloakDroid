package com.cloakdroid.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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
