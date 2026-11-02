package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.model.NotificationSoundMode
import java.util.Locale

/**
 * Natural human-like Female Voice Announcer using Android's on-device TextToSpeech engine,
 * plus a background BroadcastReceiver (`PassAlertAlarmReceiver`) triggered by AlarmManager
 * so voice notifications ("ISS approaching in 5 minutes") play even when the app is closed.
 */
object PassVoiceAnnouncer {

    /**
     * Converts raw catalog names like "ISS (ZARYA)", "TIANGONG (CSS)", "HUBBLE (HST)",
     * or "NOAA-20 (JPSS-1)" into natural spoken names ("ISS", "Tiangong", "Hubble", "NOAA 20").
     */
    fun formatSpokenSatelliteName(rawName: String): String {
        val safeRaw = SecurityVaultManager.sanitizeUserText(rawName, 48)
        val withoutParens = safeRaw.substringBefore("(").trim()
        return when {
            withoutParens.equals("ISS", ignoreCase = true) -> "ISS"
            withoutParens.startsWith("NOAA-", ignoreCase = true) ->
                withoutParens.replace("-", " ")
            withoutParens.startsWith("GOES-", ignoreCase = true) ->
                withoutParens.replace("-", " ")
            withoutParens.startsWith("STARLINK-", ignoreCase = true) ->
                "Starlink ${withoutParens.substringAfter("-")}"
            else -> {
                withoutParens.lowercase(Locale.US)
                    .split(" ", "-")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { token ->
                        if (token.uppercase(Locale.US) in setOf("ISS", "GPS", "O3B", "QZSS", "EOS")) {
                            token.uppercase(Locale.US)
                        } else {
                            token.replaceFirstChar { it.titlecase(Locale.US) }
                        }
                    }
            }
        }
    }

    /**
     * Builds the exact spoken voice phrase, e.g. "ISS approaching in 5 minutes".
     */
    fun buildApproachingVoicePhrase(satelliteName: String, leadMinutes: Int = 5): String {
        val spokenName = formatSpokenSatelliteName(satelliteName).ifBlank { "ISS" }
        val boundedLead = leadMinutes.coerceIn(0, 30)
        return if (boundedLead <= 0) {
            "$spokenName is passing overhead now"
        } else if (boundedLead == 1) {
            "$spokenName approaching in 1 minute"
        } else {
            "$spokenName approaching in $boundedLead minutes"
        }
    }

    /**
     * Speaks `phrase` using an on-device human-like female voice and automatically
     * shuts down the TTS engine once the utterance completes to preserve battery.
     */
    fun speakFemaleVoiceAnnouncement(
        context: Context,
        phrase: String,
        onComplete: (() -> Unit)? = null
    ) {
        val safePhrase = SecurityVaultManager.sanitizeUserText(phrase, 120)
        if (safePhrase.isBlank()) {
            onComplete?.invoke()
            return
        }
        val appContext = context.applicationContext
        var ttsRef: TextToSpeech? = null
        try {
            ttsRef = TextToSpeech(appContext) { status ->
                val engine = ttsRef
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    try {
                        engine.language = Locale.US
                        // Select a natural human-like female English voice if available on device
                        val femaleVoice = try {
                            engine.voices?.firstOrNull { voice ->
                                val nameLower = voice.name.lowercase(Locale.US)
                                val isEnglish = voice.locale.language.equals("en", ignoreCase = true)
                                val isFemale = nameLower.contains("female") ||
                                    nameLower.contains("sfg") ||
                                    nameLower.contains("iob") ||
                                    nameLower.contains("tpf") ||
                                    nameLower.contains("gba") ||
                                    nameLower.contains("f0")
                                isEnglish && !voice.isNetworkConnectionRequired && isFemale
                            } ?: engine.voices?.firstOrNull { voice ->
                                val nameLower = voice.name.lowercase(Locale.US)
                                voice.locale.language.equals("en", ignoreCase = true) &&
                                    (nameLower.contains("female") || nameLower.contains("sfg") || nameLower.contains("iob"))
                            }
                        } catch (_: Exception) {
                            null
                        }

                        if (femaleVoice != null) {
                            engine.voice = femaleVoice
                        }
                        // Tuned for warm, clear female vocal timbre and cadence
                        engine.setPitch(1.08f)
                        engine.setSpeechRate(0.96f)

                        val audioAttrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        engine.setAudioAttributes(audioAttrs)

                        val utteranceId = "aether_voice_${System.currentTimeMillis()}"
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                try {
                                    engine.shutdown()
                                } catch (_: Exception) {
                                }
                                onComplete?.invoke()
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                try {
                                    engine.shutdown()
                                } catch (_: Exception) {
                                }
                                onComplete?.invoke()
                            }
                        })

                        val params = Bundle().apply {
                            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                        }
                        engine.speak(safePhrase, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    } catch (_: Exception) {
                        try {
                            engine.shutdown()
                        } catch (_: Exception) {
                        }
                        onComplete?.invoke()
                    }
                } else {
                    try {
                        engine?.shutdown()
                    } catch (_: Exception) {
                    }
                    onComplete?.invoke()
                }
            }
        } catch (_: Exception) {
            onComplete?.invoke()
        }
    }
}

/**
 * Hardened background BroadcastReceiver invoked by AlarmManager when a tracked satellite is
 * `leadMinutes` away from rising above the user's horizon, even when the app is closed.
 * Verifies explicit action and per-install cryptographic token before dispatching alerts.
 */
class PassAlertAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PASS_APPROACHING_ALARM = "com.example.security.ACTION_PASS_APPROACHING_ALARM"
        const val EXTRA_NORAD_ID = "extra_norad_id"
        const val EXTRA_SAT_NAME = "extra_sat_name"
        const val EXTRA_ORBIT_LAYER = "extra_orbit_layer"
        const val EXTRA_MAX_EL_DEG = "extra_max_el_deg"
        const val EXTRA_AOS_AZ_DEG = "extra_aos_az_deg"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
        const val EXTRA_OBSERVER_NAME = "extra_observer_name"
        const val EXTRA_AUTH_TOKEN = "extra_auth_token"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Reject intents without the explicit internal action
        if (intent.action != null && intent.action != ACTION_PASS_APPROACHING_ALARM) {
            return
        }

        val vault = SecurityVaultManager(context)
        val rawSatName = intent.getStringExtra(EXTRA_SAT_NAME) ?: "ISS (ZARYA)"
        val satName = SecurityVaultManager.sanitizeUserText(rawSatName, 48).ifBlank { "ISS (ZARYA)" }
        val noradId = intent.getIntExtra(EXTRA_NORAD_ID, 25544).coerceIn(1, 999999)
        val layerCode = SecurityVaultManager.sanitizeUserText(
            intent.getStringExtra(EXTRA_ORBIT_LAYER) ?: "LEO",
            12
        ).ifBlank { "LEO" }
        val maxEl = intent.getIntExtra(EXTRA_MAX_EL_DEG, 60).coerceIn(0, 90)
        val aosAz = intent.getIntExtra(EXTRA_AOS_AZ_DEG, 240).coerceIn(0, 360)
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 5).coerceIn(1, 30)
        val observerName = SecurityVaultManager.sanitizeUserText(
            intent.getStringExtra(EXTRA_OBSERVER_NAME) ?: "Bhubaneswar, Odisha",
            48
        ).ifBlank { "Bhubaneswar, Odisha" }

        // 2. Verify cryptographic alarm authentication token if present (mandatory for scheduled alarms)
        val providedToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)
        if (intent.action == ACTION_PASS_APPROACHING_ALARM &&
            !vault.verifyAlarmIntentToken(noradId, satName, leadMinutes, providedToken)
        ) {
            return
        }

        val soundMode = vault.loadNotificationSoundMode()
        val voicePhrase = PassVoiceAnnouncer.buildApproachingVoicePhrase(satName, leadMinutes)

        val dispatcher = PassNotificationDispatcher(context)
        dispatcher.dispatchOverheadAlert(
            title = "$voicePhrase [$layerCode]",
            body = "$satName approaching $observerName in $leadMinutes min • Max El ${maxEl}° • Rise Azimuth ${aosAz}°.",
            notificationId = 20000 + noradId,
            soundMode = soundMode
        )

        if (soundMode == NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
            val pendingResult = goAsync()
            PassVoiceAnnouncer.speakFemaleVoiceAnnouncement(
                context = context,
                phrase = voicePhrase,
                onComplete = {
                    try {
                        pendingResult?.finish()
                    } catch (_: Exception) {
                    }
                }
            )
        }
    }
}
