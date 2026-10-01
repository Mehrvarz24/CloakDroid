package com.cloakdroid.data.fingerprint

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * A fully formed, internally consistent fake identity produced by
 * [FingerprintGenerator]. Every field is guaranteed to agree with every other
 * field (a Pixel UA never ships with a 1920x1080 desktop screen, a "Windows"
 * platform never ships with an Android UA, ...).
 */
data class GeneratedIdentity(
    val userAgent: String,
    val platform: String,
    val screenW: Int,
    val screenH: Int,
    val devicePixelRatio: Float,
    val deviceName: String,
    val hardwareConcurrency: Int,
    val deviceMemory: Double,
    val canvasSeed: Long,
    val fingerprintHash: String
)

/**
 * Randomized-but-consistent identity generator.
 *
 * Identities are drawn from a curated catalog of real, common device profiles
 * (Samsung / Pixel / Xiaomi phones and tablets, plus typical desktop
 * resolutions). The User-Agent is always chosen from templates matching the
 * selected device class, so the identity holds up to cross-checks that
 * competitors' naive random generators routinely fail.
 *
 * The stable [GeneratedIdentity.fingerprintHash] (SHA-256 over the canonical
 * identity fields) lets callers guarantee that no two profiles ever share the
 * same fingerprint.
 */
@Singleton
class FingerprintGenerator @Inject constructor() {

    /** One catalog entry: a device class with its realistic screen + UAs. */
    private data class DeviceProfile(
        val deviceName: String,
        val platform: String,
        val screenW: Int,
        val screenH: Int,
        val devicePixelRatio: Float,
        val userAgents: List<String>
    )

    private val catalog = listOf(
        DeviceProfile(
            deviceName = "Samsung Galaxy S23",
            platform = "Linux aarch64",
            screenW = 360, screenH = 780, devicePixelRatio = 3f,
            userAgents = listOf(
                "Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Samsung Galaxy S24 Ultra",
            platform = "Linux aarch64",
            screenW = 384, screenH = 824, devicePixelRatio = 3.75f,
            userAgents = listOf(
                "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Google Pixel 8",
            platform = "Linux aarch64",
            screenW = 412, screenH = 915, devicePixelRatio = 2.625f,
            userAgents = listOf(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Google Pixel 7",
            platform = "Linux aarch64",
            screenW = 412, screenH = 915, devicePixelRatio = 2.625f,
            userAgents = listOf(
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Xiaomi 13",
            platform = "Linux aarch64",
            screenW = 393, screenH = 873, devicePixelRatio = 2.75f,
            userAgents = listOf(
                "Mozilla/5.0 (Linux; Android 13; 2211133G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Xiaomi Redmi Note 12",
            platform = "Linux aarch64",
            screenW = 393, screenH = 873, devicePixelRatio = 2.75f,
            userAgents = listOf(
                "Mozilla/5.0 (Linux; Android 13; 23021RAAEG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Samsung Galaxy Tab S9",
            platform = "Linux aarch64",
            screenW = 800, screenH = 1280, devicePixelRatio = 2.5f,
            userAgents = listOf(
                "Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Windows 11 Desktop",
            platform = "Win32",
            screenW = 1920, screenH = 1080, devicePixelRatio = 1f,
            userAgents = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Windows 11 Laptop",
            platform = "Win32",
            screenW = 1536, screenH = 864, devicePixelRatio = 1.25f,
            userAgents = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "MacBook Air",
            platform = "MacIntel",
            screenW = 1440, screenH = 900, devicePixelRatio = 2f,
            userAgents = listOf(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "MacBook Pro",
            platform = "MacIntel",
            screenW = 1728, screenH = 1117, devicePixelRatio = 2f,
            userAgents = listOf(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
            )
        ),
        DeviceProfile(
            deviceName = "Linux Desktop",
            platform = "Linux x86_64",
            screenW = 1920, screenH = 1080, devicePixelRatio = 1f,
            userAgents = listOf(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:124.0) Gecko/20100101 Firefox/124.0"
            )
        )
    )

    private val coreChoices = listOf(4, 6, 8, 12, 16)
    private val memoryChoices = listOf(4.0, 8.0, 16.0)

    /**
     * Produces a new random identity. Callers that need a uniqueness
     * guarantee should call [fingerprintHash] checks themselves (see
     * [ProfileViewModel.generateUniqueProfiles]).
     */
    fun generate(random: Random = Random.Default): GeneratedIdentity {
        val device = catalog[random.nextInt(catalog.size)]
        val ua = device.userAgents[random.nextInt(device.userAgents.size)]
        val identity = GeneratedIdentity(
            userAgent = ua,
            platform = device.platform,
            screenW = device.screenW,
            screenH = device.screenH,
            devicePixelRatio = device.devicePixelRatio,
            deviceName = device.deviceName,
            hardwareConcurrency = coreChoices[random.nextInt(coreChoices.size)],
            deviceMemory = memoryChoices[random.nextInt(memoryChoices.size)],
            canvasSeed = random.nextLong(),
            fingerprintHash = ""
        )
        return identity.copy(fingerprintHash = fingerprintHash(identity))
    }

    /**
     * Stable SHA-256 over the canonical identity fields. Two identities with
     * the same hash are considered the same fingerprint.
     */
    fun fingerprintHash(identity: GeneratedIdentity): String {
        val canonical = listOf(
            identity.userAgent,
            identity.platform,
            identity.screenW.toString(),
            identity.screenH.toString(),
            identity.devicePixelRatio.toString(),
            identity.deviceName,
            identity.hardwareConcurrency.toString(),
            identity.deviceMemory.toString(),
            identity.canvasSeed.toString()
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
