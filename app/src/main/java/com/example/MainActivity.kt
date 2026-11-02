package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.SecurityVaultManager
import com.example.ui.components.ObserverStationPickerDialog
import com.example.ui.components.SatelliteDossierBottomSheet
import com.example.ui.components.TopMissionControlHeader
import com.example.ui.screens.AlertsAndSecurityScreen
import com.example.ui.screens.ArSkyTrackerScreen
import com.example.ui.screens.Orbit3DSimulatorScreen
import com.example.ui.screens.RadarAndPassesScreen
import com.example.ui.screens.SecurityAndPowerPopupDialog
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.HudBorderHairline
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OrbitalCyan
import com.example.ui.theme.StarlightWhite
import com.example.ui.theme.TacticalSlateElevated
import com.example.ui.theme.TacticalSlateSurface
import com.example.ui.theme.TelemetryMuted
import com.example.viewmodel.AppDestinationTab
import com.example.viewmodel.OrbitalViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Clear FLAG_SECURE by default so the browser Streaming Emulator can capture frames
        val vault = SecurityVaultManager(this)
        vault.applyWindowSecurityFlag(this, vault.loadFlagSecureEnabled())

        setContent {
            val orbitalViewModel: OrbitalViewModel = viewModel()
            val uiState by orbitalViewModel.uiState.collectAsStateWithLifecycle()

            MyApplicationTheme(
                oledPowerSaver = uiState.batteryTelemetry.activePowerMode.oledPureBlack
            ) {
                AetherOrbitApp(orbitalViewModel = orbitalViewModel)
            }
        }
    }
}

@Composable
fun AetherOrbitApp(
    orbitalViewModel: OrbitalViewModel = viewModel()
) {
    val uiState by orbitalViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context as? Activity

    var showObserverDialog by remember { mutableStateOf(false) }
    var showSecurityPowerPopup by remember { mutableStateOf(false) }

    // Pause orbital propagation loop automatically when app enters background for 0% idle battery drain
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    orbitalViewModel.setAppForegroundActive(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    orbitalViewModel.setAppForegroundActive(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle system back button when on secondary tabs to return to Radar home tab
    if (uiState.selectedTab != AppDestinationTab.RADAR) {
        BackHandler {
            orbitalViewModel.selectTab(AppDestinationTab.RADAR)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = ObsidianSpace,
        topBar = {
            Column {
                TopMissionControlHeader(
                    observerStation = uiState.observerStation,
                    batteryTelemetry = uiState.batteryTelemetry,
                    isEncryptedVaultActive = uiState.securityAudit.allTleChecksumsValid,
                    simulationTimeMillis = uiState.simulationTimeMillis,
                    timeWarpMultiplier = uiState.timeWarpMultiplier,
                    onOpenObserverPicker = { showObserverDialog = true },
                    onOpenSecurityPowerTab = {
                        showSecurityPowerPopup = true
                    }
                )

                // Dismissible status banner for audits, GPS locks, and simulated alerts
                AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                    uiState.statusBannerMessage?.let { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSlateElevated)
                                .border(1.dp, EmeraldLock.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldLock,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { orbitalViewModel.dismissStatusBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Banner",
                                    tint = TelemetryMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = TacticalSlateSurface,
                contentColor = StarlightWhite,
                modifier = Modifier.border(1.dp, HudBorderHairline)
            ) {
                NavigationBarItem(
                    selected = uiState.selectedTab == AppDestinationTab.RADAR,
                    onClick = { orbitalViewModel.selectTab(AppDestinationTab.RADAR) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == AppDestinationTab.RADAR) {
                                Icons.Filled.Radar
                            } else {
                                Icons.Outlined.Radar
                            },
                            contentDescription = stringResource(R.string.nav_radar)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_radar),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianSpace,
                        selectedTextColor = OrbitalCyan,
                        indicatorColor = OrbitalCyan,
                        unselectedIconColor = TelemetryMuted,
                        unselectedTextColor = TelemetryMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_radar")
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == AppDestinationTab.ORBIT_3D,
                    onClick = { orbitalViewModel.selectTab(AppDestinationTab.ORBIT_3D) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == AppDestinationTab.ORBIT_3D) {
                                Icons.Filled.Public
                            } else {
                                Icons.Outlined.Public
                            },
                            contentDescription = stringResource(R.string.nav_orbit_3d)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_orbit_3d),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianSpace,
                        selectedTextColor = OrbitalCyan,
                        indicatorColor = OrbitalCyan,
                        unselectedIconColor = TelemetryMuted,
                        unselectedTextColor = TelemetryMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_orbit_3d")
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == AppDestinationTab.AR_SKY,
                    onClick = { orbitalViewModel.selectTab(AppDestinationTab.AR_SKY) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == AppDestinationTab.AR_SKY) {
                                Icons.Filled.Explore
                            } else {
                                Icons.Outlined.Explore
                            },
                            contentDescription = stringResource(R.string.nav_ar_sky)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_ar_sky),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianSpace,
                        selectedTextColor = OrbitalCyan,
                        indicatorColor = OrbitalCyan,
                        unselectedIconColor = TelemetryMuted,
                        unselectedTextColor = TelemetryMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_ar_sky")
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == AppDestinationTab.SECURITY_ALERTS,
                    onClick = { orbitalViewModel.selectTab(AppDestinationTab.SECURITY_ALERTS) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == AppDestinationTab.SECURITY_ALERTS) {
                                Icons.Filled.NotificationsActive
                            } else {
                                Icons.Outlined.Notifications
                            },
                            contentDescription = stringResource(R.string.nav_security_alerts)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_security_alerts),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianSpace,
                        selectedTextColor = OrbitalCyan,
                        indicatorColor = OrbitalCyan,
                        unselectedIconColor = TelemetryMuted,
                        unselectedTextColor = TelemetryMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_security_alerts")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                AppDestinationTab.RADAR -> {
                    RadarAndPassesScreen(
                        uiState = uiState,
                        onSelectOrbitLayer = orbitalViewModel::selectOrbitLayer,
                        onSelectCategoryFilter = orbitalViewModel::selectCategoryFilter,
                        onSearchQueryChange = orbitalViewModel::updateSearchQuery,
                        onSelectSortOption = orbitalViewModel::setCatalogSortOption,
                        onToggleCatalogListMode = orbitalViewModel::setSearchableCatalogFocused,
                        onResetSearchAndFilters = orbitalViewModel::resetCatalogSearchAndFilters,
                        onToggleOnlyAboveHorizon = orbitalViewModel::toggleOnlyAboveHorizon,
                        onInspectSatellite = orbitalViewModel::openSatelliteDossier,
                        onOpenIn3D = orbitalViewModel::focusSatelliteIn3D,
                        onOpenInArSky = orbitalViewModel::focusSatelliteInArSky,
                        onSpeakSatelliteBriefing = orbitalViewModel::speakLiveTelemetryBriefing
                    )
                }

                AppDestinationTab.ORBIT_3D -> {
                    Orbit3DSimulatorScreen(
                        uiState = uiState,
                        onSelectOrbitLayer = orbitalViewModel::selectOrbitLayer,
                        onSelectSatellite = orbitalViewModel::select3DSatellite,
                        onToggleShowAllInLayer = orbitalViewModel::toggleShowAllLayerSatellitesIn3D,
                        onSetTimeWarpMultiplier = orbitalViewModel::setTimeWarpMultiplier,
                        onSetManualScrubMinutes = orbitalViewModel::setManualScrubOffsetMinutes,
                        onResetTimeToNow = orbitalViewModel::resetTimeToLiveNow,
                        onInspectSatelliteDossier = orbitalViewModel::openSatelliteDossier
                    )
                }

                AppDestinationTab.AR_SKY -> {
                    ArSkyTrackerScreen(
                        uiState = uiState,
                        orientationFlow = orbitalViewModel.observeSkyOrientationFlow(),
                        onSelectOrbitLayer = orbitalViewModel::selectOrbitLayer,
                        onSelectTargetSatellite = orbitalViewModel::select3DSatellite,
                        onInspectSatelliteDossier = orbitalViewModel::openSatelliteDossier,
                        onSpeakSatelliteBriefing = orbitalViewModel::speakLiveTelemetryBriefing,
                        onUpdateArHardwareStatus = orbitalViewModel::setArHardwareStatus
                    )
                }

                AppDestinationTab.SECURITY_ALERTS -> {
                    AlertsAndSecurityScreen(
                        uiState = uiState,
                        onSetPowerMode = orbitalViewModel::setPowerMode,
                        onSetNotificationSoundMode = orbitalViewModel::setNotificationSoundMode,
                        onSetAlertLeadTimeMinutes = orbitalViewModel::setAlertLeadTimeMinutes,
                        onPreviewSatelliteVoice = orbitalViewModel::previewSatelliteVoiceAnnouncement,
                        onToggleFlagSecure = orbitalViewModel::toggleFlagSecure,
                        onRunCryptographicAudit = { orbitalViewModel.runSecurityAndIntegrityAudit(showBanner = true) },
                        onSyncTlesFromCelestrak = orbitalViewModel::syncTlesFromCelestrak,
                        onTriggerTestPassNotification = orbitalViewModel::triggerTestOverheadAlert,
                        onUpdateSatelliteAlertRule = orbitalViewModel::updateSatelliteAlertRule,
                        onInspectSatellite = orbitalViewModel::openSatelliteDossier,
                        onOpenSecurityPopup = { showSecurityPowerPopup = true }
                    )
                }
            }
        }
    }

    // Discrete Pop-Up Tab Dialog for Security Vault & Power Governor
    if (showSecurityPowerPopup) {
        SecurityAndPowerPopupDialog(
            securityAudit = uiState.securityAudit,
            batteryTelemetry = uiState.batteryTelemetry,
            isSyncingTles = uiState.isSyncingTles,
            onSelectPowerMode = orbitalViewModel::setPowerMode,
            onToggleFlagSecure = { enabled -> orbitalViewModel.toggleFlagSecure(activity, enabled) },
            onRunAudit = { orbitalViewModel.runSecurityAndIntegrityAudit(showBanner = true) },
            onRotateVaultKey = orbitalViewModel::rotateVaultKey,
            onSyncTles = orbitalViewModel::syncTlesFromCelestrak,
            onDismiss = { showSecurityPowerPopup = false }
        )
    }

    // Tap-to-Inspect Satellite Technical Specs & Mission History Modal Sheet
    val inspectedEntity = uiState.inspectedSatelliteEntity
    val inspectedTelemetry = uiState.inspectedSatelliteTelemetry
    if (inspectedEntity != null && inspectedTelemetry != null) {
        SatelliteDossierBottomSheet(
            entity = inspectedEntity,
            telemetry = inspectedTelemetry,
            onDismiss = orbitalViewModel::closeSatelliteDossier,
            onOpenIn3DSimulator = orbitalViewModel::focusSatelliteIn3D,
            onOpenInArSky = orbitalViewModel::focusSatelliteInArSky,
            onSpeakBriefing = orbitalViewModel::speakLiveTelemetryBriefing,
            onTogglePassAlert = { enabled ->
                orbitalViewModel.updateSatelliteAlertRule(
                    noradId = inspectedEntity.noradId,
                    isTracked = enabled,
                    minElevationDeg = inspectedEntity.minAlertElevationDeg,
                    sunlitOnly = inspectedEntity.sunlitOnlyAlert
                )
            }
        )
    }

    // Observer Station Picker Dialog
    if (showObserverDialog) {
        ObserverStationPickerDialog(
            currentStation = uiState.observerStation,
            isAcquiringGps = uiState.isAcquiringGps,
            onSelectStation = orbitalViewModel::updateObserverStation,
            onRequestSingleShotGps = orbitalViewModel::acquireGpsObserverStation,
            onDismiss = { showObserverDialog = false }
        )
    }
}
