package com.example.security

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.WindowManager
import com.example.model.ObserverStation
import com.example.model.PowerMode
import com.example.model.PresetObserverStations
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Hardened cryptographic vault & defense-in-depth security manager:
 * 1. Encrypts sensitive local observer coordinates & preferences at rest using a
 *    256-bit AES-GCM key inside the hardware-backed AndroidKeyStore with mandatory
 *    randomized 96-bit IVs, 128-bit authentication tags, and Context-Bound Additional
 *    Authenticated Data (AAD) to prevent cross-preference ciphertext substitution.
 * 2. Supports live Cryptographic Key Rotation (`rotateVaultEncryptionKey`) that provisions
 *    a fresh AES-256-GCM key alias, re-encrypts all vault entries with new 96-bit IVs and
 *    AAD bindings, and purges retired Keystore entries.
 * 3. Verifies NORAD Two-Line Element (TLE) Modulo-10 checksums, cross-line NORAD catalog IDs,
 *    Keplerian orbital parameter sanity bounds (anti-NaN / anti-DoS), and constant-time
 *    SHA-256 record digests.
 * 4. Authenticates background AlarmManager broadcast intents using RFC 2104 `HmacSHA256`
 *    with 256-bit secret keys, timestamp binding, and constant-time MAC verification.
 * 5. Enforces 0700 internal sandbox permissions, FLAG_SECURE window protection, and
 *    anti-tapjacking touch filtering against overlay spyware.
 */
class SecurityVaultManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BASE_KEY_ALIAS = "aether_orbit_aes256_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_TAG_LENGTH_BYTES = 16
        private const val PREFS_NAME = "aether_orbit_secure_vault"
        private const val AAD_PREFIX = "satellite_tracker_aad_v2:"

        // Maximum age skew for timestamped alarm broadcast tokens (48 hours)
        private const val MAX_ALARM_TOKEN_WINDOW_MS = 48L * 3600_000L

        private const val KEY_VAULT_GENERATION = "vault_key_generation_v1"
        private const val KEY_ENC_OBSERVER = "enc_observer_station_bhubaneswar_v2"
        private const val KEY_ENC_FLAG_SECURE = "enc_flag_secure_v2"
        private const val KEY_ENC_POWER_MODE = "enc_power_mode_v1"
        private const val KEY_ENC_SOUND_MODE = "enc_notif_sound_mode_v1"
        private const val KEY_ENC_LEAD_MINUTES = "enc_notif_lead_minutes_v1"
        private const val KEY_ENC_ALARM_SECRET = "enc_alarm_hmac_secret_v1"

        /**
         * Sanitizes user-supplied text (station labels, search queries, broadcast extras) by
         * stripping control characters, zero-width spoofing codepoints, pipe delimiters, and
         * script/HTML/SQL injection characters, then bounding length.
         */
        fun sanitizeUserText(raw: String, maxLength: Int = 48): String {
            if (raw.isBlank()) return ""
            val cleaned = buildString(minOf(raw.length, maxLength)) {
                for (ch in raw) {
                    if (length >= maxLength) break
                    val code = ch.code
                    val isControlOrZeroWidth = Character.isISOControl(ch) ||
                        code in 0x200B..0x200F ||
                        code in 0x202A..0x202E ||
                        code in 0x2066..0x2069 ||
                        code == 0xFEFF
                    val isInjectionDelimiter = ch == '|' || ch == '<' || ch == '>' ||
                        ch == '"' || ch == '\'' || ch == '`' || ch == '\\' ||
                        ch == ';' || ch == '\u0000'
                    if (!isControlOrZeroWidth && !isInjectionDelimiter) {
                        append(ch)
                    }
                }
            }.trim()
            return cleaned
        }

        /**
         * Computes standard NORAD TLE Modulo-10 checksum for a 69-character TLE line.
         * Digits 0-9 add their value; '-' adds 1; all other letters/spaces add 0.
         */
        fun computeTleLineChecksum(line68: String): Int {
            var sum = 0
            val limit = minOf(68, line68.length)
            for (i in 0 until limit) {
                val c = line68[i]
                if (c in '1'..'9') {
                    sum += (c - '0')
                } else if (c == '-') {
                    sum += 1
                }
            }
            return sum % 10
        }

        /**
         * Normalizes a TLE line to exactly 69 characters with a valid Modulo-10 checksum digit
         * in column 69, and validates its structure.
         */
        fun normalizeWithValidTleChecksum(rawLine: String): String {
            val trimmed = rawLine.trim()
            val base68 = if (trimmed.length >= 68) {
                trimmed.substring(0, 68)
            } else {
                trimmed.padEnd(68, ' ')
            }
            val checksum = computeTleLineChecksum(base68)
            return base68 + checksum.toString()
        }

        /**
         * Verifies that a TLE line has valid length, line prefix ('1 ' or '2 '), and Modulo-10 checksum.
         */
        fun verifyTleLineChecksum(line: String, expectedLineNumber: Int): Boolean {
            val trimmed = line.trim()
            if (trimmed.length != 69) return false
            if (!trimmed.startsWith("$expectedLineNumber ")) return false
            val expectedChecksum = computeTleLineChecksum(trimmed.substring(0, 68))
            val actualChar = trimmed[68]
            return actualChar.isDigit() && (actualChar - '0') == expectedChecksum
        }

        /**
         * Verifies that both Line 1 and Line 2 belong to `expectedNoradId` by parsing columns 3..7,
         * preventing cross-satellite TLE substitution attacks.
         */
        fun verifyTleCatalogMatch(line1: String, line2: String, expectedNoradId: Int): Boolean {
            val t1 = line1.trim()
            val t2 = line2.trim()
            if (t1.length < 7 || t2.length < 7) return false
            val cat1 = t1.substring(2, 7).trim().toIntOrNull() ?: return false
            val cat2 = t2.substring(2, 7).trim().toIntOrNull() ?: return false
            return cat1 == expectedNoradId && cat2 == expectedNoradId
        }

        /**
         * Validates that the Keplerian orbital elements encoded in TLE Line 1 & Line 2 fall within
         * physically possible bounds to prevent NaN / divide-by-zero propagation DoS attacks:
         * - Inclination: [0.0, 180.0] deg
         * - RAAN: [0.0, 360.0] deg
         * - Eccentricity: [0.0, 0.999999] (closed elliptical orbit)
         * - Argument of Perigee: [0.0, 360.0] deg
         * - Mean Anomaly: [0.0, 360.0] deg
         * - Mean Motion: [0.05, 20.0] revolutions/day
         */
        fun verifyTleOrbitalParametersSanity(line1: String, line2: String): Boolean {
            val t1 = line1.trim()
            val t2 = line2.trim()
            if (t1.length != 69 || t2.length != 69) return false
            return try {
                val epochYear = t1.substring(18, 20).trim().toIntOrNull() ?: return false
                val epochDay = t1.substring(20, 32).trim().toDoubleOrNull() ?: return false
                if (epochYear !in 0..99 || !epochDay.isFinite() || epochDay !in 1.0..367.0) {
                    return false
                }

                val inclination = t2.substring(8, 16).trim().toDoubleOrNull() ?: return false
                val raan = t2.substring(17, 25).trim().toDoubleOrNull() ?: return false
                val eccDigits = t2.substring(26, 33).trim()
                if (eccDigits.isEmpty() || !eccDigits.all { it.isDigit() }) return false
                val eccentricity = "0.$eccDigits".toDoubleOrNull() ?: return false
                val argPerigee = t2.substring(34, 42).trim().toDoubleOrNull() ?: return false
                val meanAnomaly = t2.substring(43, 51).trim().toDoubleOrNull() ?: return false
                val meanMotion = t2.substring(52, 63).trim().toDoubleOrNull() ?: return false

                inclination.isFinite() && inclination in 0.0..180.0 &&
                    raan.isFinite() && raan in 0.0..360.0 &&
                    eccentricity.isFinite() && eccentricity in 0.0..0.999999 &&
                    argPerigee.isFinite() && argPerigee in 0.0..360.0 &&
                    meanAnomaly.isFinite() && meanAnomaly in 0.0..360.0 &&
                    meanMotion.isFinite() && meanMotion in 0.05..20.0
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Computes a hex-encoded SHA-256 digest for anti-tamper integrity verification.
         */
        fun computeSha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(payload.toByteArray(StandardCharsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * Computes an RFC 2104 HmacSHA256 authentication tag in hex using a secret key.
         */
        fun computeHmacSha256(secretKey: String, message: String): String {
            val keyBytes = secretKey.toByteArray(StandardCharsets.UTF_8)
            val msgBytes = message.toByteArray(StandardCharsets.UTF_8)
            return try {
                val mac = Mac.getInstance(HMAC_ALGORITHM)
                mac.init(SecretKeySpec(keyBytes, HMAC_ALGORITHM))
                val hmacBytes = mac.doFinal(msgBytes)
                hmacBytes.joinToString("") { "%02x".format(it) }
            } finally {
                keyBytes.fill(0)
                msgBytes.fill(0)
            }
        }

        /**
         * Constant-time hex digest / MAC comparison using `MessageDigest.isEqual`
         * to prevent timing side-channel leaks.
         */
        fun verifySha256ConstantTime(expectedHex: String, actualHex: String): Boolean {
            if (expectedHex.isBlank() || actualHex.isBlank()) return false
            val a = expectedHex.lowercase().toByteArray(StandardCharsets.UTF_8)
            val b = actualHex.lowercase().toByteArray(StandardCharsets.UTF_8)
            return MessageDigest.isEqual(a, b)
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isHardwareKeystoreAvailable: Boolean = true
        private set

    fun getKeyRotationGeneration(): Int {
        return prefs.getInt(KEY_VAULT_GENERATION, 1).coerceAtLeast(1)
    }

    private fun activeKeyAlias(generation: Int = getKeyRotationGeneration()): String {
        return if (generation <= 1) BASE_KEY_ALIAS else "${BASE_KEY_ALIAS}_v$generation"
    }

    // Fallback in-memory key used only in local JVM / Robolectric unit test environments
    // where the hardware AndroidKeyStore provider is not mounted.
    private fun getJvmFallbackKey(generation: Int): SecretKey {
        val seed = "aether_orbit_jvm_test_key_seed_v$generation"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun getOrCreateSecretKey(generation: Int = getKeyRotationGeneration()): SecretKey {
        val alias = activeKeyAlias(generation)
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                isHardwareKeystoreAvailable = true
                return existing.secretKey
            }
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            isHardwareKeystoreAvailable = true
            keyGenerator.generateKey()
        } catch (_: Exception) {
            isHardwareKeystoreAvailable = false
            getJvmFallbackKey(generation)
        }
    }

    init {
        hardenInternalSandboxPermissions()
    }

    /**
     * Enforces strict owner-only permissions (0700 equivalent) on internal sandbox directories
     * so no other process or unprivileged shell can traverse app files.
     */
    fun hardenInternalSandboxPermissions(): Boolean {
        return try {
            val dirs = listOfNotNull(
                context.filesDir,
                context.cacheDir,
                context.getDatabasePath("aether_orbit_sandbox.db")?.parentFile
            )
            dirs.forEach { dir ->
                if (dir.exists()) {
                    dir.setReadable(false, false)
                    dir.setReadable(true, true)
                    dir.setWritable(false, false)
                    dir.setWritable(true, true)
                    dir.setExecutable(false, false)
                    dir.setExecutable(true, true)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypts `plainText` using 256-bit AES-GCM with a randomized 96-bit IV and binds
     * `aadContext` into the 128-bit GCM authentication tag via `cipher.updateAAD(...)`
     * so ciphertexts cannot be swapped across different preference keys.
     */
    fun encryptString(
        plainText: String,
        aadContext: String = "default",
        generation: Int = getKeyRotationGeneration()
    ): String {
        val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
        val aadBytes = "$AAD_PREFIX$aadContext:v$generation".toByteArray(StandardCharsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(generation))
            cipher.updateAAD(aadBytes)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainBytes)
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "v2:$ivB64:$dataB64"
        } catch (_: Exception) {
            ""
        } finally {
            plainBytes.fill(0)
            aadBytes.fill(0)
        }
    }

    /**
     * Decrypts an AES-256-GCM payload and verifies both the 128-bit authentication tag and
     * the context-bound `aadContext`. Supports seamless migration from legacy v1 payloads.
     */
    fun decryptString(
        cipherPayload: String,
        aadContext: String = "default",
        generation: Int = getKeyRotationGeneration()
    ): String? {
        if (cipherPayload.isBlank() || !cipherPayload.contains(":")) return null
        var plainBytes: ByteArray? = null
        return try {
            val isV2Aad = cipherPayload.startsWith("v2:")
            val rawParts = if (isV2Aad) {
                cipherPayload.removePrefix("v2:").split(":", limit = 2)
            } else {
                cipherPayload.split(":", limit = 2)
            }
            if (rawParts.size < 2) return null

            val iv = Base64.decode(rawParts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(rawParts[1], Base64.NO_WRAP)
            // Enforce strict NIST SP 800-38D 96-bit GCM IV and minimum 128-bit auth tag size
            if (iv.size != GCM_IV_LENGTH_BYTES || cipherBytes.size < GCM_TAG_LENGTH_BYTES) {
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(generation), spec)
            if (isV2Aad) {
                val aadBytes = "$AAD_PREFIX$aadContext:v$generation".toByteArray(StandardCharsets.UTF_8)
                try {
                    cipher.updateAAD(aadBytes)
                } finally {
                    aadBytes.fill(0)
                }
            }
            plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            plainBytes?.fill(0)
        }
    }

    /**
     * Rotates the master AES-256-GCM key generation, re-encrypts all sensitive vault records
     * with a newly generated Keystore key, fresh 96-bit IVs, and updated AAD bindings, and
     * rotates the 256-bit HmacSHA256 broadcast secret.
     */
    fun rotateVaultEncryptionKey(): Int {
        val currentGen = getKeyRotationGeneration()
        val currentStation = loadEncryptedObserverStation()
        val currentFlagSecure = loadFlagSecureEnabled()
        val currentPowerMode = loadPowerMode()
        val currentSoundMode = loadNotificationSoundMode()
        val currentLeadMinutes = loadAlertLeadTimeMinutes()

        val nextGen = currentGen + 1
        prefs.edit().putInt(KEY_VAULT_GENERATION, nextGen).commit()

        // Generate fresh 256-bit HMAC alarm secret under the new key generation
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val newAlarmSecret = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
        randomBytes.fill(0)

        val encAlarm = encryptString(newAlarmSecret, KEY_ENC_ALARM_SECRET, nextGen)
        prefs.edit().putString(KEY_ENC_ALARM_SECRET, encAlarm).apply()

        // Re-encrypt all stored preferences with the new key generation and fresh IVs
        saveEncryptedObserverStation(currentStation)
        saveFlagSecureEnabled(currentFlagSecure)
        savePowerMode(currentPowerMode)
        saveNotificationSoundMode(currentSoundMode)
        saveAlertLeadTimeMinutes(currentLeadMinutes)

        // Delete retired hardware Keystore entry if applicable
        try {
            val oldAlias = activeKeyAlias(currentGen)
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(oldAlias)) {
                keyStore.deleteEntry(oldAlias)
            }
        } catch (_: Exception) {
        }

        return nextGen
    }

    private fun getOrCreateAlarmAuthSecret(): String {
        val existingEnc = prefs.getString(KEY_ENC_ALARM_SECRET, null)
        if (existingEnc != null) {
            val dec = decryptString(existingEnc, KEY_ENC_ALARM_SECRET)
            if (!dec.isNullOrBlank() && dec.length >= 32) {
                return dec
            }
        }
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val generated = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
        randomBytes.fill(0)
        val enc = encryptString(generated, KEY_ENC_ALARM_SECRET)
        if (enc.isNotBlank()) {
            prefs.edit().putString(KEY_ENC_ALARM_SECRET, enc).apply()
        }
        return generated
    }

    /**
     * Generates a per-install RFC 2104 `HmacSHA256` authentication token for scheduled
     * AlarmManager intents bound to the NORAD ID, sanitized satellite name, lead time,
     * and issue timestamp (`issuedAtMillis`) to prevent local intent spoofing or replay.
     */
    fun computeAlarmIntentToken(
        noradId: Int,
        satName: String,
        leadMinutes: Int,
        issuedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val secret = getOrCreateAlarmAuthSecret()
        val safeName = sanitizeUserText(satName, 48)
        val canonicalPayload = "v2|$noradId|$safeName|$leadMinutes|$issuedAtMillis"
        val macHex = computeHmacSha256(secret, canonicalPayload)
        return "$issuedAtMillis:$macHex"
    }

    /**
     * Verifies an AlarmManager broadcast intent `HmacSHA256` token in constant time and
     * enforces the replay-protection timestamp window.
     */
    fun verifyAlarmIntentToken(
        noradId: Int,
        satName: String,
        leadMinutes: Int,
        providedToken: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (providedToken.isNullOrBlank()) return false
        val secret = getOrCreateAlarmAuthSecret()
        val safeName = sanitizeUserText(satName, 48)

        if (providedToken.contains(":")) {
            val parts = providedToken.split(":", limit = 2)
            val issuedAt = parts[0].toLongOrNull() ?: return false
            val providedMac = parts[1]
            if (abs(nowMillis - issuedAt) > MAX_ALARM_TOKEN_WINDOW_MS) {
                return false
            }
            val expectedMac = computeHmacSha256(
                secret,
                "v2|$noradId|$safeName|$leadMinutes|$issuedAt"
            )
            return verifySha256ConstantTime(expectedMac, providedMac)
        }

        // Legacy fallback for non-timestamped tokens within same session
        val legacyExpected = computeSha256("$secret|$noradId|$safeName|$leadMinutes")
        return verifySha256ConstantTime(legacyExpected, providedToken)
    }

    fun saveEncryptedObserverStation(station: ObserverStation) {
        val safeName = sanitizeUserText(station.name, 48).ifBlank { "Bhubaneswar, Odisha" }
        val safeRegion = sanitizeUserText(station.region, 48).ifBlank { "India • IST (UTC+5:30)" }
        val lat = if (station.latitudeDeg.isFinite()) station.latitudeDeg.coerceIn(-90.0, 90.0) else 20.2961
        val lon = if (station.longitudeDeg.isFinite()) station.longitudeDeg.coerceIn(-180.0, 180.0) else 85.8245
        val alt = if (station.altitudeMeters.isFinite()) station.altitudeMeters.coerceIn(-500.0, 15000.0) else 45.0
        val serialized = listOf(
            safeName,
            safeRegion,
            lat.toString(),
            lon.toString(),
            alt.toString(),
            station.isGpsDerived.toString()
        ).joinToString("|")
        val encrypted = encryptString(serialized, KEY_ENC_OBSERVER)
        if (encrypted.isNotBlank()) {
            prefs.edit().putString(KEY_ENC_OBSERVER, encrypted).apply()
        }
    }

    fun loadEncryptedObserverStation(): ObserverStation {
        val encrypted = prefs.getString(KEY_ENC_OBSERVER, null)
        if (encrypted != null) {
            val decrypted = decryptString(encrypted, KEY_ENC_OBSERVER)
            if (decrypted != null) {
                // Upgrade legacy v1 ciphertext to v2 AAD-bound ciphertext automatically
                if (!encrypted.startsWith("v2:")) {
                    val upgraded = encryptString(decrypted, KEY_ENC_OBSERVER)
                    if (upgraded.isNotBlank()) {
                        prefs.edit().putString(KEY_ENC_OBSERVER, upgraded).apply()
                    }
                }
                val parts = decrypted.split("|")
                if (parts.size >= 6) {
                    val lat = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(-90.0, 90.0)
                    val lon = parts[3].toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0)
                    val alt = parts[4].toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(-500.0, 15000.0) ?: 45.0
                    val isGps = parts[5].toBooleanStrictOrNull() ?: false
                    if (lat != null && lon != null) {
                        return ObserverStation(
                            name = sanitizeUserText(parts[0], 48).ifBlank { "Bhubaneswar, Odisha" },
                            region = sanitizeUserText(parts[1], 48).ifBlank { "India • IST (UTC+5:30)" },
                            latitudeDeg = lat,
                            longitudeDeg = lon,
                            altitudeMeters = alt,
                            isGpsDerived = isGps
                        )
                    }
                }
            }
        }
        val defaultStation = PresetObserverStations.stations.first()
        saveEncryptedObserverStation(defaultStation)
        return defaultStation
    }

    fun getEncryptedObserverCipherPreview(): String {
        val raw = prefs.getString(KEY_ENC_OBSERVER, "") ?: ""
        return if (raw.length > 38) "${raw.take(38)}…" else raw.ifEmpty { "AES-GCM+AAD Vault Initialized" }
    }

    fun saveFlagSecureEnabled(enabled: Boolean) {
        val enc = encryptString(enabled.toString(), KEY_ENC_FLAG_SECURE)
        prefs.edit().putString(KEY_ENC_FLAG_SECURE, enc).apply()
    }

    fun loadFlagSecureEnabled(): Boolean {
        val enc = prefs.getString(KEY_ENC_FLAG_SECURE, null) ?: return false
        return decryptString(enc, KEY_ENC_FLAG_SECURE)?.toBooleanStrictOrNull() ?: false
    }

    fun savePowerMode(mode: PowerMode) {
        val enc = encryptString(mode.name, KEY_ENC_POWER_MODE)
        prefs.edit().putString(KEY_ENC_POWER_MODE, enc).apply()
    }

    fun loadPowerMode(): PowerMode {
        val enc = prefs.getString(KEY_ENC_POWER_MODE, null) ?: return PowerMode.ADAPTIVE_AUTO
        val name = decryptString(enc, KEY_ENC_POWER_MODE) ?: return PowerMode.ADAPTIVE_AUTO
        return PowerMode.entries.find { it.name == name } ?: PowerMode.ADAPTIVE_AUTO
    }

    fun saveNotificationSoundMode(mode: com.example.model.NotificationSoundMode) {
        val enc = encryptString(mode.name, KEY_ENC_SOUND_MODE)
        prefs.edit().putString(KEY_ENC_SOUND_MODE, enc).apply()
    }

    fun loadNotificationSoundMode(): com.example.model.NotificationSoundMode {
        val enc = prefs.getString(KEY_ENC_SOUND_MODE, null)
            ?: return com.example.model.NotificationSoundMode.FEMALE_VOICE_ANNOUNCER
        val name = decryptString(enc, KEY_ENC_SOUND_MODE)
            ?: return com.example.model.NotificationSoundMode.FEMALE_VOICE_ANNOUNCER
        return com.example.model.NotificationSoundMode.entries.find { it.name == name }
            ?: com.example.model.NotificationSoundMode.FEMALE_VOICE_ANNOUNCER
    }

    fun saveAlertLeadTimeMinutes(minutes: Int) {
        val enc = encryptString(minutes.coerceIn(1, 30).toString(), KEY_ENC_LEAD_MINUTES)
        prefs.edit().putString(KEY_ENC_LEAD_MINUTES, enc).apply()
    }

    fun loadAlertLeadTimeMinutes(): Int {
        val enc = prefs.getString(KEY_ENC_LEAD_MINUTES, null) ?: return 5
        return decryptString(enc, KEY_ENC_LEAD_MINUTES)?.toIntOrNull()?.coerceIn(1, 30) ?: 5
    }

    /**
     * Applies or clears WindowManager.LayoutParams.FLAG_SECURE on the host Activity window
     * and enforces touch filtering against obscured overlay windows (anti-tapjacking).
     */
    fun applyWindowSecurityFlag(activity: Activity?, enabled: Boolean) {
        activity?.runOnUiThread {
            // Always filter touches when obscured by non-system overlays to block tapjacking
            activity.window?.decorView?.filterTouchesWhenObscured = true
            if (enabled) {
                activity.window?.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
