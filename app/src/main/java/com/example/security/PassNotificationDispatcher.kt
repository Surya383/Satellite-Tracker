package com.example.security

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.NotificationSoundMode
import com.example.model.ObserverStation
import com.example.model.OverheadPassPrediction
import com.example.model.SatelliteLiveTelemetry
import kotlin.math.roundToInt

/**
 * Dispatches smart overhead pass notifications with selectable notification sound modes:
 * 1. System Default Notification Sound (`CHANNEL_ID_SYSTEM_DEFAULT`)
 * 2. Human-Like Female Voice Announcer (`CHANNEL_ID_VOICE_ANNOUNCER` + on-device TTS speaking
 *    e.g. "ISS approaching in 5 minutes" for all tracked satellites, even when the app is closed).
 */
class PassNotificationDispatcher(private val context: Context) {

    companion object {
        const val CHANNEL_ID_SYSTEM_DEFAULT = "aether_orbit_passes_default_sound"
        const val CHANNEL_ID_VOICE_ANNOUNCER = "aether_orbit_passes_female_voice"
    }

    private val appContext = context.applicationContext
    private val notifiedPassKeys = HashSet<String>()
    private val scheduledAlarmKeys = HashSet<String>()

    init {
        ensureNotificationChannels()
    }

    fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            // 1. System Default Chime Channel
            val defaultChannel = NotificationChannel(
                CHANNEL_ID_SYSTEM_DEFAULT,
                appContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }

            // 2. Female Voice Announcer Channel (silent chime so TTS voice speaks clearly without overlap)
            val voiceChannel = NotificationChannel(
                CHANNEL_ID_VOICE_ANNOUNCER,
                "Satellite Voice Announcer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Spoken female voice announcements when tracked satellites approach in 5 minutes."
                setSound(null, null)
                enableVibration(true)
            }

            manager?.createNotificationChannel(defaultChannel)
            manager?.createNotificationChannel(voiceChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun dispatchOverheadAlert(
        title: String,
        body: String,
        notificationId: Int,
        soundMode: NotificationSoundMode
    ): Boolean {
        if (!hasNotificationPermission()) return false
        return try {
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = if (soundMode == NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
                CHANNEL_ID_VOICE_ANNOUNCER
            } else {
                CHANNEL_ID_SYSTEM_DEFAULT
            }

            val builder = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (soundMode == NotificationSoundMode.SYSTEM_DEFAULT) {
                val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                builder.setSound(defaultSoundUri)
            } else {
                builder.setSilent(true)
            }

            NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Schedules zero-drain AlarmManager wakeups for upcoming passes of all tracked satellites
     * at `pass.aosTimeMillis - (leadMinutes * 60_000L)` so the user receives the notification
     * and spoken female voice ("<Satellite> approaching in 5 minutes") even when the app is closed.
     */
    fun scheduleUpcomingPassAlarms(
        upcomingPasses: List<OverheadPassPrediction>,
        trackedRules: Map<Int, Triple<Boolean, Int, Boolean>>,
        observer: ObserverStation,
        leadMinutes: Int,
        soundMode: NotificationSoundMode
    ) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val leadMillis = leadMinutes.coerceIn(1, 30) * 60_000L

        for (pass in upcomingPasses) {
            val rule = trackedRules[pass.noradId] ?: continue
            val (isTracked, minEl, sunlitOnly) = rule
            if (!isTracked) continue
            if (pass.maxElevationDeg < minEl) continue
            if (sunlitOnly && !pass.isSunlitVisible) continue

            val triggerAtMillis = pass.aosTimeMillis - leadMillis
            val minutesUntilAos = ((pass.aosTimeMillis - now) / 60_000L).toInt()

            // If the pass is already within the 5-minute approach window right now, announce immediately once
            if (minutesUntilAos in 1..leadMinutes) {
                val immediateKey = "imm_${pass.noradId}_${pass.aosTimeMillis / 600_000L}"
                if (notifiedPassKeys.add(immediateKey)) {
                    val voicePhrase = PassVoiceAnnouncer.buildApproachingVoicePhrase(pass.satelliteName, minutesUntilAos)
                    dispatchOverheadAlert(
                        title = "$voicePhrase [${pass.orbitLayer.code}]",
                        body = "${pass.satelliteName} approaching ${observer.name} in $minutesUntilAos min • Max El ${pass.maxElevationDeg.roundToInt()}°.",
                        notificationId = 30000 + pass.noradId,
                        soundMode = soundMode
                    )
                    if (soundMode == NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
                        PassVoiceAnnouncer.speakFemaleVoiceAnnouncement(appContext, voicePhrase)
                    }
                }
            } else if (triggerAtMillis > now + 5_000L) {
                // Schedule background AlarmManager wakeup so it fires even if the app is closed
                val alarmKey = "alm_${pass.noradId}_${pass.aosTimeMillis / 600_000L}_$leadMinutes"
                if (scheduledAlarmKeys.add(alarmKey)) {
                    val safeSatName = SecurityVaultManager.sanitizeUserText(pass.satelliteName, 48)
                    val safeLead = leadMinutes.coerceIn(1, 30)
                    val authToken = SecurityVaultManager(appContext)
                        .computeAlarmIntentToken(pass.noradId, safeSatName, safeLead)
                    val intent = Intent(appContext, PassAlertAlarmReceiver::class.java).apply {
                        action = PassAlertAlarmReceiver.ACTION_PASS_APPROACHING_ALARM
                        setPackage(appContext.packageName)
                        putExtra(PassAlertAlarmReceiver.EXTRA_NORAD_ID, pass.noradId)
                        putExtra(PassAlertAlarmReceiver.EXTRA_SAT_NAME, safeSatName)
                        putExtra(PassAlertAlarmReceiver.EXTRA_ORBIT_LAYER, pass.orbitLayer.code)
                        putExtra(PassAlertAlarmReceiver.EXTRA_MAX_EL_DEG, pass.maxElevationDeg.roundToInt())
                        putExtra(PassAlertAlarmReceiver.EXTRA_AOS_AZ_DEG, pass.aosAzimuthDeg.roundToInt())
                        putExtra(PassAlertAlarmReceiver.EXTRA_LEAD_MINUTES, safeLead)
                        putExtra(PassAlertAlarmReceiver.EXTRA_OBSERVER_NAME, observer.name)
                        putExtra(PassAlertAlarmReceiver.EXTRA_AUTH_TOKEN, authToken)
                    }
                    val requestCode = 40000 + pass.noradId + (pass.aosTimeMillis / 600_000L).toInt()
                    val pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun evaluateLiveTelemetryAlerts(
        liveSatellites: List<SatelliteLiveTelemetry>,
        trackedRules: Map<Int, Triple<Boolean, Int, Boolean>>,
        observer: ObserverStation,
        soundMode: NotificationSoundMode
    ) {
        if (!hasNotificationPermission()) return
        val hourBucket = System.currentTimeMillis() / 3600_000L

        for (sat in liveSatellites) {
            val rule = trackedRules[sat.noradId] ?: continue
            val (isTracked, minEl, sunlitOnly) = rule
            if (!isTracked) continue
            if (sat.elevationDeg >= minEl && (!sunlitOnly || sat.isSunlit)) {
                val dedupeKey = "${sat.noradId}_$hourBucket"
                if (notifiedPassKeys.add(dedupeKey)) {
                    val azCardinal = azimuthToCardinal(sat.azimuthDeg)
                    val voicePhrase = PassVoiceAnnouncer.buildApproachingVoicePhrase(sat.name, 5)
                    dispatchOverheadAlert(
                        title = "$voicePhrase (${sat.orbitLayer.code})",
                        body = "${sat.name} overhead ${observer.name} at ${sat.elevationDeg.roundToInt()}° elevation (Azimuth ${sat.azimuthDeg.roundToInt()}° $azCardinal).",
                        notificationId = 10000 + sat.noradId,
                        soundMode = soundMode
                    )
                    if (soundMode == NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
                        PassVoiceAnnouncer.speakFemaleVoiceAnnouncement(appContext, voicePhrase)
                    }
                }
            }
        }
    }

    fun triggerSimulatedPassAlert(
        pass: OverheadPassPrediction?,
        observer: ObserverStation,
        soundMode: NotificationSoundMode,
        leadMinutes: Int
    ): String {
        val targetName = pass?.satelliteName ?: "ISS (ZARYA)"
        val layerCode = pass?.orbitLayer?.code ?: "LEO"
        val maxEl = pass?.maxElevationDeg?.roundToInt() ?: 68
        val az = pass?.aosAzimuthDeg?.roundToInt() ?: 242
        val cardinal = azimuthToCardinal(az.toDouble())
        val voicePhrase = PassVoiceAnnouncer.buildApproachingVoicePhrase(targetName, leadMinutes)

        val delivered = dispatchOverheadAlert(
            title = "$voicePhrase • [$layerCode]",
            body = "$targetName approaching ${observer.name} in $leadMinutes minutes • Max El $maxEl° • Approach $az° ($cardinal).",
            notificationId = 99001,
            soundMode = soundMode
        )

        if (soundMode == NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
            PassVoiceAnnouncer.speakFemaleVoiceAnnouncement(appContext, voicePhrase)
        }

        return if (soundMode == NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
            "Voice Notification: \"$voicePhrase\" ${if (delivered) "+ Push Notification sent" else "(Grant Notification Permission for status bar banner)"}."
        } else {
            if (delivered) {
                "System Default Notification sent: \"$voicePhrase\" over ${observer.name}."
            } else {
                "Alert prepared for $targetName (Grant Notification Permission to show in status bar)."
            }
        }
    }

    private fun azimuthToCardinal(azDeg: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((azDeg + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
        return dirs[idx]
    }
}
