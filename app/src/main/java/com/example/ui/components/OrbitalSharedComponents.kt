package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SatelliteEntity
import com.example.model.BatteryTelemetryState
import com.example.model.ObserverStation
import com.example.model.OrbitLayer
import com.example.model.PresetObserverStations
import com.example.model.SatelliteLiveTelemetry
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.HudBorderHairline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OrbitalCyan
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.StarlightWhite
import com.example.ui.theme.TacticalCardHighlight
import com.example.ui.theme.TacticalSlateElevated
import com.example.ui.theme.TacticalSlateSurface
import com.example.ui.theme.TelemetryMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TopMissionControlHeader(
    observerStation: ObserverStation,
    batteryTelemetry: BatteryTelemetryState,
    isEncryptedVaultActive: Boolean,
    simulationTimeMillis: Long,
    timeWarpMultiplier: Int,
    onOpenObserverPicker: () -> Unit,
    onOpenSecurityPowerTab: () -> Unit
) {
    val istFormat = remember {
        SimpleDateFormat("dd MMM • hh:mm:ss a 'IST'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    Surface(
        color = ObsidianSpace,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = HudBorderHairline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF0B1530),
                            Color(0xFF131F42),
                            Color(0xFF0A1228)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        OrbitalCyan.copy(alpha = 0.25f),
                                        Color(0xFF1E293B)
                                    )
                                )
                            )
                            .border(1.dp, OrbitalCyan.copy(alpha = 0.65f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                            contentDescription = "Satellite Tracker Icon",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Satellite Tracker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = StarlightWhite
                            )
                            if (timeWarpMultiplier > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${timeWarpMultiplier}x Warp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ObsidianSpace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(SolarAmber)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldLock)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = istFormat.format(Date(simulationTimeMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = OrbitalCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Friendly Pill Buttons for Station Picker & Security Vault
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenObserverPicker() }
                            .testTag("observer_station_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (observerStation.isGpsDerived) Icons.Default.GpsFixed else Icons.Default.LocationOn,
                                contentDescription = "Observer Station",
                                tint = if (observerStation.isGpsDerived) EmeraldLock else OrbitalCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = observerStation.name.substringBefore(",").trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = StarlightWhite,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (isEncryptedVaultActive) EmeraldLock.copy(alpha = 0.55f) else HudBorderHairline
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenSecurityPowerTab() }
                            .testTag("top_security_power_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isEncryptedVaultActive) Icons.Default.Lock else Icons.Default.Shield,
                                contentDescription = "Open System Vault & Power Popup",
                                tint = if (isEncryptedVaultActive) EmeraldLock else TelemetryMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Vault",
                                style = MaterialTheme.typography.labelSmall,
                                color = StarlightWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Orbital Layer Strip allowing the user to filter by and explore Earth's orbital shells:
 * ALL, LEO, SSO, MEO, GEO, and HEO.
 */
@Composable
fun OrbitLayerSelectorStrip(
    selectedLayer: OrbitLayer,
    satelliteCountsByLayer: Map<OrbitLayer, Int>,
    onSelectLayer: (OrbitLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrbitLayer.entries.forEach { layer ->
            val isSelected = selectedLayer == layer
            val count = satelliteCountsByLayer[layer] ?: 0
            Surface(
                color = if (isSelected) layer.accentColor.copy(alpha = 0.22f) else TacticalSlateSurface,
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) layer.accentColor else HudBorderHairline
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelectLayer(layer) }
                    .testTag("orbit_layer_chip_${layer.code}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(layer.accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (layer == OrbitLayer.ALL) "All Orbits ($count)" else "${layer.code} ($count)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) StarlightWhite else TelemetryMuted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Compact, friendly Orbital Shell Summary Card with one-tap satellite pills.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrbitLayerIntelligenceBanner(
    layer: OrbitLayer,
    satellitesInLayer: List<SatelliteLiveTelemetry>,
    selectedSatelliteNoradId: Int,
    onSelectSatellite: (Int) -> Unit,
    onInspectSatelliteDossier: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedDetails by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, layer.accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { expandedDetails = !expandedDetails }
            .testTag("orbit_layer_intelligence_card")
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
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = layer.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = layer.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarlightWhite
                        )
                        Text(
                            text = "${layer.altitudeBandKm} • ${layer.typicalPeriod} period • ${satellitesInLayer.size} satellites",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryMuted
                        )
                    }
                }
                Text(
                    text = if (expandedDetails) "Hide Info" else "Shell Info",
                    style = MaterialTheme.typography.labelSmall,
                    color = layer.accentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(layer.accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            AnimatedVisibility(visible = expandedDetails) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = layer.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TelemetryMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryMetricChip(
                            label = "Orbit Period",
                            value = layer.typicalPeriod,
                            accent = layer.accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricChip(
                            label = "Typical Speed",
                            value = layer.typicalSpeedKms,
                            accent = layer.accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricChip(
                            label = "Tracked",
                            value = "${satellitesInLayer.size} Sats",
                            accent = layer.accentColor,
                            modifier = Modifier.weight(0.85f)
                        )
                    }

                    if (satellitesInLayer.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            satellitesInLayer.forEach { sat ->
                                val isActive = sat.noradId == selectedSatelliteNoradId
                                Surface(
                                    color = if (isActive) layer.accentColor.copy(alpha = 0.25f) else TacticalSlateElevated,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isActive) layer.accentColor else HudBorderHairline
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            onSelectSatellite(sat.noradId)
                                            onInspectSatelliteDossier(sat.noradId)
                                        }
                                        .testTag("layer_sat_pill_${sat.noradId}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (sat.isAboveHorizon) EmeraldLock else layer.accentColor)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "${sat.name.substringBefore("(").trim()} (${sat.altitudeKm.roundToInt()} km)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StarlightWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryMetricChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TacticalSlateElevated)
            .border(1.dp, HudBorderHairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TelemetryMuted,
            fontSize = 9.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Interactive Modal Bottom Sheet displaying comprehensive technical specifications,
 * live orbital telemetry, NORAD Two-Line Elements, and chronological mission history
 * whenever the user taps any satellite in the Radar, 3D Simulator, or AR Sky view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteDossierBottomSheet(
    entity: SatelliteEntity,
    telemetry: SatelliteLiveTelemetry,
    onDismiss: () -> Unit,
    onOpenIn3DSimulator: (Int) -> Unit,
    onOpenInArSky: (Int) -> Unit,
    onSpeakBriefing: (Int) -> Unit = {},
    onTogglePassAlert: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedDossierTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Live Telemetry", "Specs & Radio", "Mission History", "Orbital TLE")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TacticalSlateSurface,
        contentColor = StarlightWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .testTag("satellite_dossier_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NORAD #${entity.noradId} • ${entity.designation}",
                            style = MaterialTheme.typography.labelMedium,
                            color = OrbitalCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entity.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = StarlightWhite
                    )
                    Text(
                        text = "${entity.operator} • ${entity.category.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TelemetryMuted
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Dossier",
                        tint = TelemetryMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visibility & Sunlit Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadgePill(
                    text = if (telemetry.isAboveHorizon) {
                        "ABOVE HORIZON (${telemetry.elevationDeg.roundToInt()}° EL)"
                    } else {
                        "BELOW HORIZON (${telemetry.elevationDeg.roundToInt()}° EL)"
                    },
                    color = if (telemetry.isAboveHorizon) EmeraldLock else TelemetryMuted
                )
                StatusBadgePill(
                    text = if (telemetry.isSunlit) "SUNLIT IN ORBIT" else "EARTH ECLIPSE",
                    color = if (telemetry.isSunlit) SolarAmber else OrbitalCyan
                )
                if (telemetry.tleChecksumValid) {
                    StatusBadgePill(
                        text = "SHA-256 VERIFIED",
                        color = EmeraldLock
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = entity.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = StarlightWhite.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: 3D Simulator, AR Sky Lock, Female Voice Briefing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpenIn3DSimulator(entity.noradId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrbitalCyan,
                        contentColor = ObsidianSpace
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dossier_view_3d_button")
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("3D Sim", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onOpenInArSky(entity.noradId) },
                    border = BorderStroke(1.dp, OrbitalCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitalCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dossier_view_ar_button")
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AR Sky", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onSpeakBriefing(entity.noradId) },
                    border = BorderStroke(1.dp, SolarAmber),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SolarAmber),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dossier_speak_briefing_button")
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Speak", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedDossierTab,
                containerColor = TacticalSlateElevated,
                contentColor = OrbitalCyan,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedDossierTab == index,
                        onClick = { selectedDossierTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedDossierTab) {
                0 -> {
                    // Live Telemetry Grid
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("SUB-SAT LATITUDE", formatLat(telemetry.latitudeDeg), Modifier.weight(1f))
                            DossierKeyValueCard("SUB-SAT LONGITUDE", formatLon(telemetry.longitudeDeg), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("ORBITAL ALTITUDE", "${"%.1f".format(telemetry.altitudeKm)} km (${telemetry.orbitLayer.code})", Modifier.weight(1f))
                            DossierKeyValueCard("ORBITAL VELOCITY", "${"%.2f".format(telemetry.velocityKms)} km/s", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("LOCAL AZIMUTH", "${"%.1f".format(telemetry.azimuthDeg)}° (${azimuthCardinal(telemetry.azimuthDeg)})", Modifier.weight(1f))
                            DossierKeyValueCard("LOCAL ELEVATION", "${"%.1f".format(telemetry.elevationDeg)}°", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("SLANT RANGE", "${telemetry.slantRangeKm.roundToInt()} km", Modifier.weight(1f))
                            DossierKeyValueCard("DOPPLER (437 MHz)", "${"%+d".format(telemetry.dopplerShiftHz.roundToInt())} Hz", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("APOGEE × PERIGEE", "${telemetry.apogeeKm.roundToInt()} × ${telemetry.perigeeKm.roundToInt()} km", Modifier.weight(1f))
                            DossierKeyValueCard("ORBITAL PERIOD", "${"%.1f".format(telemetry.orbitalPeriodMinutes)} min", Modifier.weight(1f))
                        }
                    }
                }
                1 -> {
                    // Technical Specs & Radio
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DossierKeyValueCard("LAUNCH DATE & SITE", "${entity.launchDate} • ${entity.launchSite}", Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("LAUNCH VEHICLE", entity.launchVehicle, Modifier.weight(1f))
                            DossierKeyValueCard("LAUNCH MASS", "${entity.massKg} kg", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("DIMENSIONS", entity.dimensions, Modifier.weight(1f))
                            DossierKeyValueCard("ELECTRICAL POWER", entity.powerWatts, Modifier.weight(1f))
                        }
                        DossierKeyValueCard("RADIO / DOWNLINK TELEMETRY", entity.radioFrequencies, Modifier.fillMaxWidth())
                        DossierKeyValueCard("ORBITAL LAYER REGIME", "${entity.orbitLayer.title} (${entity.orbitLayer.altitudeBandKm})", Modifier.fillMaxWidth())
                    }
                }
                2 -> {
                    // Mission History Timeline
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entity.milestones.forEach { milestone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TacticalSlateElevated)
                                    .border(1.dp, HudBorderHairline, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = milestone.date,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OrbitalCyan,
                                    modifier = Modifier.width(78.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = milestone.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = StarlightWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = milestone.detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TelemetryMuted
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Orbital Elements & TLE Block
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("INCLINATION", "${"%.4f".format(telemetry.inclinationDeg)}°", Modifier.weight(1f))
                            DossierKeyValueCard("ECCENTRICITY", "${"%.6f".format(telemetry.eccentricity)}", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DossierKeyValueCard("MEAN MOTION", "${"%.4f".format(telemetry.revolutionsPerDay)} rev/day", Modifier.weight(1f))
                            DossierKeyValueCard("FOOTPRINT RADIUS", "${telemetry.footprintRadiusKm.roundToInt()} km", Modifier.weight(1f))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianSpace)
                                .border(1.dp, OrbitalCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "NORAD TWO-LINE ELEMENT SET (MODULO-10 VERIFIED)",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrbitalCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = entity.tleLine1,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = StarlightWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entity.tleLine2,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = StarlightWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "SHA-256 Digest: ${entity.sha256Signature.take(32)}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldLock
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = HudBorderHairline)
            Spacer(modifier = Modifier.height(10.dp))

            // Overhead Pass Alert Toggle inside Dossier
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (entity.isTrackedForAlerts) SolarAmber else TelemetryMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Overhead Pass Alert",
                            style = MaterialTheme.typography.titleMedium,
                            color = StarlightWhite
                        )
                        Text(
                            text = "Notify when elevation exceeds ${entity.minAlertElevationDeg}°",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TelemetryMuted
                        )
                    }
                }
                Switch(
                    checked = entity.isTrackedForAlerts,
                    onCheckedChange = onTogglePassAlert
                )
            }
        }
    }
}

@Composable
private fun StatusBadgePill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun DossierKeyValueCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TacticalSlateElevated)
            .border(1.dp, HudBorderHairline, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TelemetryMuted
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = StarlightWhite,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Dialog for choosing an Observer Station (24 Preset Cities, Custom Lat/Lon/Alt input, or optional
 * single-shot GPS coordinate lock). All coordinates are stored encrypted via AndroidKeyStore AES-256-GCM.
 */
@Composable
fun ObserverStationPickerDialog(
    currentStation: ObserverStation,
    isAcquiringGps: Boolean,
    onSelectStation: (ObserverStation) -> Unit,
    onRequestSingleShotGps: () -> Unit,
    onDismiss: () -> Unit
) {
    var customName by remember { mutableStateOf(currentStation.name) }
    var customLat by remember { mutableStateOf(currentStation.latitudeDeg.toString()) }
    var customLon by remember { mutableStateOf(currentStation.longitudeDeg.toString()) }
    var customAlt by remember { mutableStateOf(currentStation.altitudeMeters.roundToInt().toString()) }
    var showCustomEntry by remember { mutableStateOf(false) }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            onRequestSingleShotGps()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TacticalSlateSurface,
        titleContentColor = StarlightWhite,
        textContentColor = StarlightWhite,
        title = {
            Column {
                Text(
                    text = "Observer Station & Privacy Mode",
                    style = MaterialTheme.typography.titleLarge,
                    color = StarlightWhite
                )
                Text(
                    text = "Encrypted locally with AES-256-GCM • Never sent to any server",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLock
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Optional Single-Shot GPS Button
                Button(
                    onClick = {
                        gpsPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrbitalCyan.copy(alpha = 0.2f),
                        contentColor = OrbitalCyan
                    ),
                    border = BorderStroke(1.dp, OrbitalCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAcquiringGps) "Acquiring Single-Shot Fix…" else "One-Tap GPS Lock (Zero Background Drain)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showCustomEntry) "CUSTOM COORDINATES" else "ZERO-PERMISSION PRESET STATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryMuted
                    )
                    TextButton(onClick = { showCustomEntry = !showCustomEntry }) {
                        Text(
                            text = if (showCustomEntry) "Preset Cities" else "Custom Lat/Lon",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrbitalCyan
                        )
                    }
                }

                if (showCustomEntry) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Station Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customLat,
                                onValueChange = { customLat = it },
                                label = { Text("Lat (-90..90)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customLon,
                                onValueChange = { customLon = it },
                                label = { Text("Lon (-180..180)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = customAlt,
                            onValueChange = { customAlt = it },
                            label = { Text("Altitude (meters)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val lat = customLat.toDoubleOrNull()?.coerceIn(-90.0, 90.0) ?: 20.2961
                                val lon = customLon.toDoubleOrNull()?.coerceIn(-180.0, 180.0) ?: 85.8245
                                val alt = customAlt.toDoubleOrNull()?.coerceIn(-400.0, 9000.0) ?: 45.0
                                val safeName = com.example.security.SecurityVaultManager.sanitizeUserText(customName, 48)
                                    .ifBlank { "Custom Station" }
                                onSelectStation(
                                    ObserverStation(
                                        name = safeName,
                                        region = "India • IST (UTC+5:30)",
                                        latitudeDeg = lat,
                                        longitudeDeg = lon,
                                        altitudeMeters = alt,
                                        isGpsDerived = false
                                    )
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OrbitalCyan,
                                contentColor = ObsidianSpace
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Encrypted Coordinates", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(PresetObserverStations.stations) { station ->
                            val isSelected = station.name == currentStation.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) TacticalCardHighlight else TacticalSlateElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) OrbitalCyan else HudBorderHairline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onSelectStation(station)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = station.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = StarlightWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${station.region} • ${formatLat(station.latitudeDeg)}, ${formatLon(station.longitudeDeg)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelemetryMuted
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = OrbitalCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TelemetryMuted)
            }
        }
    )
}

fun formatLat(lat: Double): String {
    val dir = if (lat >= 0) "N" else "S"
    return "${"%.2f".format(abs(lat))}°$dir"
}

fun formatLon(lon: Double): String {
    val dir = if (lon >= 0) "E" else "W"
    return "${"%.2f".format(abs(lon))}°$dir"
}

fun azimuthCardinal(azDeg: Double): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((azDeg + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
    return dirs[idx]
}
