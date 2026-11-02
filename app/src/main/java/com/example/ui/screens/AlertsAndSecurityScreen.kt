package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SatelliteEntity
import com.example.model.BatteryTelemetryState
import com.example.model.NotificationSoundMode
import com.example.model.PowerMode
import com.example.model.SecurityAuditState
import com.example.security.PassVoiceAnnouncer
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.HudBorderHairline
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OrbitalCyan
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.StarlightWhite
import com.example.ui.theme.TacticalSlateElevated
import com.example.ui.theme.TacticalSlateSurface
import com.example.ui.theme.TelemetryMuted
import com.example.viewmodel.OrbitalUiState

@Composable
fun AlertsAndSecurityScreen(
    uiState: OrbitalUiState,
    onSetPowerMode: (PowerMode) -> Unit,
    onSetNotificationSoundMode: (NotificationSoundMode) -> Unit,
    onSetAlertLeadTimeMinutes: (Int) -> Unit,
    onPreviewSatelliteVoice: (String) -> Unit,
    onToggleFlagSecure: (Activity?, Boolean) -> Unit,
    onRunCryptographicAudit: () -> Unit,
    onSyncTlesFromCelestrak: () -> Unit,
    onTriggerTestPassNotification: () -> Unit,
    onUpdateSatelliteAlertRule: (noradId: Int, isTracked: Boolean, minElevationDeg: Int, sunlitOnly: Boolean) -> Unit,
    onInspectSatellite: (Int) -> Unit,
    onOpenSecurityPopup: () -> Unit = {}
) {
    var alertSearchQuery by remember { mutableStateOf("") }

    val filteredAlertEntities = remember(uiState.catalogEntities, alertSearchQuery) {
        val raw = alertSearchQuery.trim().lowercase()
        val normalizedId = raw.removePrefix("#").removePrefix("norad").trim()
        if (raw.isEmpty()) {
            uiState.catalogEntities
        } else {
            uiState.catalogEntities.filter { entity ->
                val noradStr = entity.noradId.toString()
                entity.name.lowercase().contains(raw) ||
                    noradStr.contains(normalizedId) ||
                    "#${noradStr}".contains(raw) ||
                    entity.orbitLayer.code.lowercase().contains(raw) ||
                    entity.orbitLayer.title.lowercase().contains(raw)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onTriggerTestPassNotification()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .testTag("alerts_and_security_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Smart Overhead Pass & Voice Notification Card
        item {
            SmartVoiceAndNotificationCard(
                soundMode = uiState.notificationSoundMode,
                leadMinutes = uiState.alertLeadTimeMinutes,
                onSelectSoundMode = onSetNotificationSoundMode,
                onSelectLeadMinutes = onSetAlertLeadTimeMinutes,
                onPreviewIssVoice = { onPreviewSatelliteVoice("ISS (ZARYA)") },
                onTriggerTestNotification = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onTriggerTestPassNotification()
                    }
                },
                onOpenSecurityPopup = onOpenSecurityPopup
            )
        }

        // 2. Searchable Per-Satellite Alert Rules Filter Bar
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Satellite Voice & Pass Alert Rules",
                        style = MaterialTheme.typography.titleMedium,
                        color = StarlightWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredAlertEntities.size}/${uiState.catalogEntities.size} Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrbitalCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(OrbitalCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }

                OutlinedTextField(
                    value = alertSearchQuery,
                    onValueChange = { alertSearchQuery = it },
                    placeholder = {
                        Text(
                            text = "Filter alert rules by satellite name or NORAD ID…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TelemetryMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Alert Rules",
                            tint = OrbitalCyan
                        )
                    },
                    trailingIcon = {
                        if (alertSearchQuery.isNotBlank()) {
                            IconButton(onClick = { alertSearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Alert Filter",
                                    tint = StarlightWhite
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrbitalCyan,
                        unfocusedBorderColor = HudBorderHairline,
                        focusedContainerColor = TacticalSlateSurface,
                        unfocusedContainerColor = TacticalSlateSurface,
                        focusedTextColor = StarlightWhite,
                        unfocusedTextColor = StarlightWhite
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alert_rules_search_input")
                )
            }
        }

        // 3. Per-Satellite Smart Alert & Voice Rules
        items(
            items = filteredAlertEntities,
            key = { it.noradId }
        ) { entity ->
            SatelliteAlertRuleCard(
                entity = entity,
                leadMinutes = uiState.alertLeadTimeMinutes,
                onUpdateRule = { tracked, minEl, sunlit ->
                    onUpdateSatelliteAlertRule(entity.noradId, tracked, minEl, sunlit)
                },
                onPreviewVoice = { onPreviewSatelliteVoice(entity.name) },
                onTapInspect = { onInspectSatellite(entity.noradId) }
            )
        }
    }
}

/**
 * Discrete Pop-Up Tab Dialog for Security Vault & Adaptive Power Governor,
 * opened only on demand from the subtle header icon or settings pill rather than cluttering the main screen.
 */
@Composable
fun SecurityAndPowerPopupDialog(
    securityAudit: SecurityAuditState,
    batteryTelemetry: BatteryTelemetryState,
    isSyncingTles: Boolean,
    onSelectPowerMode: (PowerMode) -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit,
    onRunAudit: () -> Unit,
    onRotateVaultKey: () -> Unit = {},
    onSyncTles: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPopupTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Security Vault", "Power & Sync")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = TacticalSlateSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.65f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 610.dp)
                .testTag("security_power_popup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = OrbitalCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Security Vault & Power Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StarlightWhite
                            )
                            Text(
                                text = "8-Layer Defense • AES-256-GCM+AAD (Key v${securityAudit.keyRotationCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldLock
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_security_popup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Vault Popup",
                            tint = StarlightWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pop-up Tabs: [0] Security Vault | [1] Power & Sync
                TabRow(
                    selectedTabIndex = selectedPopupTab,
                    containerColor = TacticalSlateElevated,
                    contentColor = OrbitalCyan,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedPopupTab == index,
                            onClick = { selectedPopupTab = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedPopupTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedPopupTab == index) OrbitalCyan else TelemetryMuted
                                )
                            },
                            modifier = Modifier.testTag("security_popup_tab_$index")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedPopupTab == 0) {
                        SecurityVaultAndAuditCard(
                            securityAudit = securityAudit,
                            isSyncingTles = isSyncingTles,
                            onToggleFlagSecure = onToggleFlagSecure,
                            onRunAudit = onRunAudit,
                            onRotateVaultKey = onRotateVaultKey,
                            onSyncTles = onSyncTles
                        )
                    } else {
                        BatteryEfficiencyGovernorCard(
                            batteryTelemetry = batteryTelemetry,
                            onSelectPowerMode = onSelectPowerMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartVoiceAndNotificationCard(
    soundMode: NotificationSoundMode,
    leadMinutes: Int,
    onSelectSoundMode: (NotificationSoundMode) -> Unit,
    onSelectLeadMinutes: (Int) -> Unit,
    onPreviewIssVoice: () -> Unit,
    onTriggerTestNotification: () -> Unit,
    onOpenSecurityPopup: () -> Unit
) {
    val samplePhrase = PassVoiceAnnouncer.buildApproachingVoicePhrase("ISS (ZARYA)", leadMinutes)

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_voice_notification_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = SolarAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "OVERHEAD PASS SOUND & VOICE ANNOUNCER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarlightWhite
                        )
                        Text(
                            text = "Speaks in background even when app is closed",
                            style = MaterialTheme.typography.labelSmall,
                            color = SolarAmber
                        )
                    }
                }

                // Subtle compact pill to open the Security & Power Pop-Up Dialog if needed
                Surface(
                    color = TacticalSlateElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, HudBorderHairline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenSecurityPopup() }
                        .testTag("open_security_popup_from_alerts_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Open Vault & Power Popup",
                            tint = TelemetryMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "System",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "CHOOSE NOTIFICATION SOUND MODE:",
                style = MaterialTheme.typography.labelSmall,
                color = TelemetryMuted
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationSoundMode.entries.forEach { mode ->
                    val isSelected = soundMode == mode
                    val accent = if (mode == NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
                        SolarAmber
                    } else {
                        OrbitalCyan
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.16f) else TacticalSlateElevated)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accent else HudBorderHairline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectSoundMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("sound_mode_option_${mode.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (mode == NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
                                    Icons.Default.RecordVoiceOver
                                } else {
                                    Icons.Default.NotificationsActive
                                },
                                contentDescription = null,
                                tint = if (isSelected) accent else TelemetryMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StarlightWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryMuted
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected Sound Mode",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Approach Lead Time Selector (3m, 5m, 10m, 15m)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "APPROACH VOICE LEAD TIME:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 10, 15).forEach { minOpt ->
                        val active = leadMinutes == minOpt
                        Surface(
                            color = if (active) SolarAmber else TacticalSlateElevated,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (active) SolarAmber else HudBorderHairline),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectLeadMinutes(minOpt) }
                                .testTag("lead_minutes_chip_$minOpt")
                        ) {
                            Text(
                                text = "${minOpt}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) ObsidianSpace else StarlightWhite,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Spoken Voice Phrase Preview Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSpace)
                    .border(1.dp, HudBorderHairline, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "BACKGROUND VOICE ANNOUNCEMENT PREVIEW (EVEN WHEN APP CLOSED):",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryMuted,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "\"$samplePhrase\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = SolarAmber,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPreviewIssVoice,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SolarAmber,
                        contentColor = ObsidianSpace
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preview_female_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Voice Notification",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onTriggerTestNotification,
                    border = BorderStroke(1.dp, OrbitalCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitalCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_pass_alert_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Test Push + Voice",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryEfficiencyGovernorCard(
    batteryTelemetry: BatteryTelemetryState,
    onSelectPowerMode: (PowerMode) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, EmeraldLock.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_governor_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatterySaver,
                        contentDescription = null,
                        tint = EmeraldLock,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ADAPTIVE BATTERY & POWER GOVERNOR",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarlightWhite
                        )
                        Text(
                            text = "Lifecycle-gated hardware • 60s trajectory cache • 0% background drain",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLock
                        )
                    }
                }
                Text(
                    text = "LOW POWER",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLock,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(EmeraldLock.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hardware Subsystem Sleep Audit Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HardwareSleepBadge(
                    name = "CameraX",
                    isActive = batteryTelemetry.isCameraHardwareActive,
                    modifier = Modifier.weight(1f)
                )
                HardwareSleepBadge(
                    name = "Compass",
                    isActive = batteryTelemetry.isCompassSensorActive,
                    modifier = Modifier.weight(1f)
                )
                HardwareSleepBadge(
                    name = "GPS Radio",
                    isActive = batteryTelemetry.isGpsRadioActive,
                    modifier = Modifier.weight(1f)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TacticalSlateElevated)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("TICK RATE", style = MaterialTheme.typography.labelSmall, color = TelemetryMuted, fontSize = 9.sp)
                    Text(
                        text = "${batteryTelemetry.effectiveTickIntervalMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLock,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Power Mode Selector Options
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PowerMode.entries.forEach { mode ->
                    val isSelected = batteryTelemetry.activePowerMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) EmeraldLock.copy(alpha = 0.14f) else TacticalSlateElevated)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) EmeraldLock else HudBorderHairline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectPowerMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("power_mode_option_${mode.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = StarlightWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryMuted
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Power Mode",
                                tint = EmeraldLock,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareSleepBadge(
    name: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TacticalSlateElevated)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = name.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TelemetryMuted,
            fontSize = 9.sp
        )
        Text(
            text = if (isActive) "ACTIVE" else "SLEEPING",
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) SolarAmber else EmeraldLock,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SecurityVaultAndAuditCard(
    securityAudit: SecurityAuditState,
    isSyncingTles: Boolean,
    onToggleFlagSecure: (Boolean) -> Unit,
    onRunAudit: () -> Unit,
    onRotateVaultKey: () -> Unit = {},
    onSyncTles: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_vault_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = OrbitalCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "8-Layer Defense-in-Depth Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarlightWhite
                        )
                        Text(
                            text = "${securityAudit.encryptionAlgorithm} • Key Gen v${securityAudit.keyRotationCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrbitalCyan
                        )
                    }
                }

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(EmeraldLock.copy(alpha = 0.16f))
                        .border(1.dp, EmeraldLock.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${securityAudit.securityScorePercent}% HARDENED",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLock,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 8-Layer Cryptographic & Anti-Tamper Defense Matrix
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurityCheckBadge(
                    title = "1. AES-256-GCM + AAD",
                    status = "Context-Bound (v${securityAudit.keyRotationCount})",
                    modifier = Modifier.weight(1f)
                )
                SecurityCheckBadge(
                    title = "2. TLE SHA-256 + SANITY",
                    status = "${securityAudit.verifiedSatelliteCount}/${securityAudit.totalSatelliteCount} Verified",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurityCheckBadge(
                    title = "3. ALARM BROADCAST MAC",
                    status = "HmacSHA256 + Replay Guard",
                    modifier = Modifier.weight(1f)
                )
                SecurityCheckBadge(
                    title = "4. NETWORK TLS 1.3 PIN",
                    status = "SPKI Pin • No Redirect",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurityCheckBadge(
                    title = "5. ANTI-SPOOFING GPS",
                    status = "Mock Provider Rejected",
                    modifier = Modifier.weight(1f)
                )
                SecurityCheckBadge(
                    title = "6. SANDBOX PERMISSIONS",
                    status = if (securityAudit.isSandboxLockedDown) "0700 Owner-Only Lock" else "Standard Mode",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurityCheckBadge(
                    title = "7. BACKUP & D2D EXFIL",
                    status = "Blocked (Zero-Domain)",
                    modifier = Modifier.weight(1f)
                )
                SecurityCheckBadge(
                    title = "8. TAPJACKING SHIELD",
                    status = "Obscured Touch Filtered",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val auditIstTime = remember(securityAudit.lastAuditTimestampMs) {
                java.text.SimpleDateFormat("dd MMM • hh:mm:ss a 'IST'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                }.format(java.util.Date(securityAudit.lastAuditTimestampMs))
            }

            // Encrypted Observer Cipher Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianSpace)
                    .border(1.dp, HudBorderHairline, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "AAD-BOUND OBSERVER CIPHERTEXT (KEY v${securityAudit.keyRotationCount}) • AUDIT $auditIstTime:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryMuted,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = securityAudit.sampleEncryptedObserverPreview.ifBlank { "AES-256-GCM+AAD Hardware Sealed" },
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLock
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // FLAG_SECURE Anti-Spyware Screen Shield Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FLAG_SECURE Screen & Overlay Protection",
                        style = MaterialTheme.typography.titleMedium,
                        color = StarlightWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Blocks unauthorized screen recording, screenshots & malicious screen overlays",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryMuted
                    )
                }
                Switch(
                    checked = securityAudit.isFlagSecureEnabled,
                    onCheckedChange = onToggleFlagSecure,
                    modifier = Modifier.testTag("flag_secure_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Security Audit, Key Rotation & Optional CelesTrak Sync Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunAudit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrbitalCyan,
                        contentColor = ObsidianSpace
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("run_security_audit_button")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Audit Vault", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRotateVaultKey,
                    border = BorderStroke(1.dp, EmeraldLock.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldLock),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rotate_vault_key_button")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Rotate Key",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onSyncTles,
                enabled = !isSyncingTles,
                border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitalCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_celestrak_tle_button")
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSyncingTles) "Verifying Pinned HTTPS TLEs…" else "Sync & Verify TLEs via Pinned HTTPS (TLS 1.3)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SecurityCheckBadge(
    title: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TacticalSlateElevated)
            .border(1.dp, HudBorderHairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = TelemetryMuted, fontSize = 9.sp)
        Text(status, style = MaterialTheme.typography.labelSmall, color = EmeraldLock, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SatelliteAlertRuleCard(
    entity: SatelliteEntity,
    leadMinutes: Int,
    onUpdateRule: (Boolean, Int, Boolean) -> Unit,
    onPreviewVoice: () -> Unit,
    onTapInspect: () -> Unit
) {
    val spokenPhrase = PassVoiceAnnouncer.buildApproachingVoicePhrase(entity.name, leadMinutes)

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (entity.isTrackedForAlerts) entity.orbitLayer.accentColor.copy(alpha = 0.6f) else HudBorderHairline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onTapInspect() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entity.orbitLayer.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = ObsidianSpace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(entity.orbitLayer.accentColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entity.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarlightWhite
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "#${entity.noradId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrbitalCyan
                        )
                    }
                    Text(
                        text = "Voice Alert: \"$spokenPhrase\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolarAmber
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPreviewVoice() }
                            .testTag("speak_voice_rule_${entity.noradId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Preview Voice Announcement",
                                tint = SolarAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Speak",
                                style = MaterialTheme.typography.labelSmall,
                                color = SolarAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Switch(
                        checked = entity.isTrackedForAlerts,
                        onCheckedChange = { enabled ->
                            onUpdateRule(enabled, entity.minAlertElevationDeg, entity.sunlitOnlyAlert)
                        },
                        modifier = Modifier.testTag("alert_switch_${entity.noradId}")
                    )
                }
            }

            if (entity.isTrackedForAlerts) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 20, 35, 50).forEach { elThreshold ->
                            val selected = entity.minAlertElevationDeg == elThreshold
                            Surface(
                                color = if (selected) OrbitalCyan else TacticalSlateElevated,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        onUpdateRule(true, elThreshold, entity.sunlitOnlyAlert)
                                    }
                            ) {
                                Text(
                                    text = "≥${elThreshold}° El",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) ObsidianSpace else StarlightWhite,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = if (entity.sunlitOnlyAlert) SolarAmber.copy(alpha = 0.2f) else TacticalSlateElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            1.dp,
                            if (entity.sunlitOnlyAlert) SolarAmber else HudBorderHairline
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onUpdateRule(true, entity.minAlertElevationDeg, !entity.sunlitOnlyAlert)
                            }
                    ) {
                        Text(
                            text = if (entity.sunlitOnlyAlert) "Sunlit Only" else "All Passes",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entity.sunlitOnlyAlert) SolarAmber else TelemetryMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
