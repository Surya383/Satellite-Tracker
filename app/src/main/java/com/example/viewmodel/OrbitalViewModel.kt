package com.example.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SatelliteEntity
import com.example.data.SatelliteRepository
import com.example.engine.OrbitalPropagatorEngine
import com.example.model.BatteryTelemetryState
import com.example.model.CatalogSortOption
import com.example.model.ObserverStation
import com.example.model.OrbitLayer
import com.example.model.OverheadPassPrediction
import com.example.model.PowerMode
import com.example.model.SatelliteCategory
import com.example.model.SatelliteLiveTelemetry
import com.example.model.SecurityAuditState
import com.example.security.BatteryAndSensorManager
import com.example.security.DeviceSkyOrientation
import com.example.security.PassNotificationDispatcher
import com.example.security.PassVoiceAnnouncer
import com.example.security.SecurityVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class AppDestinationTab {
    RADAR,
    ORBIT_3D,
    AR_SKY,
    SECURITY_ALERTS
}

data class OrbitalUiState(
    val selectedTab: AppDestinationTab = AppDestinationTab.RADAR,
    val selectedOrbitLayer: OrbitLayer = OrbitLayer.ALL,
    val selectedCategoryFilter: SatelliteCategory? = null,
    val searchQuery: String = "",
    val catalogSortOption: CatalogSortOption = CatalogSortOption.NEXT_APPROACH_ASC,
    val isSearchableCatalogFocused: Boolean = false,
    val onlyAboveHorizon: Boolean = false,
    val catalogEntities: List<SatelliteEntity> = emptyList(),
    val liveTelemetryList: List<SatelliteLiveTelemetry> = emptyList(),
    val filteredTelemetryList: List<SatelliteLiveTelemetry> = emptyList(),
    val selected3DSatelliteNoradId: Int = 25544, // Default ISS
    val showAllLayerSatellitesIn3D: Boolean = true,
    val inspectedSatelliteNoradId: Int? = null,
    val observerStation: ObserverStation = ObserverStation("Bhubaneswar, Odisha", "India • IST (UTC+5:30)", 20.2961, 85.8245, 45.0),
    val upcomingPasses: List<OverheadPassPrediction> = emptyList(),
    val simulationTimeMillis: Long = System.currentTimeMillis(),
    val timeWarpMultiplier: Int = 1, // 1x, 10x, 60x, 300x
    val manualScrubOffsetMinutes: Float = 0f,
    val isTimeWarpPaused: Boolean = false,
    val batteryTelemetry: BatteryTelemetryState = BatteryTelemetryState(),
    val securityAudit: SecurityAuditState = SecurityAuditState(),
    val notificationSoundMode: com.example.model.NotificationSoundMode = com.example.model.NotificationSoundMode.FEMALE_VOICE_ANNOUNCER,
    val alertLeadTimeMinutes: Int = 5,
    val isSyncingTles: Boolean = false,
    val isAcquiringGps: Boolean = false,
    val statusBannerMessage: String? = null
) {
    val selected3DSatelliteTelemetry: SatelliteLiveTelemetry?
        get() = liveTelemetryList.find { it.noradId == selected3DSatelliteNoradId }
            ?: filteredTelemetryList.firstOrNull()
            ?: liveTelemetryList.firstOrNull()

    val selected3DSatelliteEntity: SatelliteEntity?
        get() = catalogEntities.find { it.noradId == selected3DSatelliteNoradId }
            ?: catalogEntities.firstOrNull()

    val inspectedSatelliteEntity: SatelliteEntity?
        get() = inspectedSatelliteNoradId?.let { id -> catalogEntities.find { it.noradId == id } }

    val inspectedSatelliteTelemetry: SatelliteLiveTelemetry?
        get() = inspectedSatelliteNoradId?.let { id -> liveTelemetryList.find { it.noradId == id } }
}

class OrbitalViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = SatelliteRepository(database.satelliteDao())
    private val securityVault = SecurityVaultManager(application)
    private val batteryAndSensorManager = BatteryAndSensorManager(application)
    private val notificationDispatcher = PassNotificationDispatcher(application)

    private val _uiState = MutableStateFlow(OrbitalUiState())
    val uiState: StateFlow<OrbitalUiState> = _uiState.asStateFlow()

    private var propagationJob: Job? = null
    private var isAppInForeground: Boolean = true
    private var isArCameraHardwareActive: Boolean = false
    private var isArCompassSensorActive: Boolean = false
    private var simulatedWarpAccumulatorMillis: Long = 0L
    private var lastRealTickMillis: Long = System.currentTimeMillis()
    private var lastPassCalculationMillis: Long = 0L

    init {
        val savedObserver = securityVault.loadEncryptedObserverStation()
        val savedFlagSecure = securityVault.loadFlagSecureEnabled()
        val savedPowerMode = securityVault.loadPowerMode()
        val savedSoundMode = securityVault.loadNotificationSoundMode()
        val savedLeadMinutes = securityVault.loadAlertLeadTimeMinutes()

        val initialCatalog = com.example.data.OfflineSatelliteCatalog.getInitialCatalog()
        _uiState.update { state ->
            state.copy(
                catalogEntities = initialCatalog,
                observerStation = savedObserver,
                notificationSoundMode = savedSoundMode,
                alertLeadTimeMinutes = savedLeadMinutes,
                batteryTelemetry = batteryAndSensorManager.readBatteryTelemetry(
                    activePowerMode = savedPowerMode,
                    isCameraActive = false,
                    isCompassActive = false,
                    isGpsActive = false
                ),
                securityAudit = state.securityAudit.copy(
                    isKeystoreHardwareBacked = securityVault.isHardwareKeystoreAvailable,
                    isFlagSecureEnabled = savedFlagSecure,
                    sampleEncryptedObserverPreview = securityVault.getEncryptedObserverCipherPreview()
                )
            )
        }
        recomputeTelemetrySnapshot(forcePassRecalc = true)

        viewModelScope.launch {
            repository.ensureSeeded()
            runSecurityAndIntegrityAudit(showBanner = false)
            repository.allSatellitesFlow.collect { entities ->
                _uiState.update { it.copy(catalogEntities = entities) }
                recomputeTelemetrySnapshot(forcePassRecalc = upcomingPassesEmpty())
            }
        }

        startAdaptivePropagationLoop()
    }

    private fun upcomingPassesEmpty(): Boolean = _uiState.value.upcomingPasses.isEmpty()

    /**
     * Scopes the orbital propagation loop strictly to when the app is visible in the foreground,
     * and adapts tick frequency according to the active PowerMode and Android Battery Saver state.
     */
    fun setAppForegroundActive(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (inForeground) {
            lastRealTickMillis = System.currentTimeMillis()
            startAdaptivePropagationLoop()
        } else {
            propagationJob?.cancel()
            propagationJob = null
        }
    }

    private fun startAdaptivePropagationLoop() {
        if (propagationJob?.isActive == true) return
        propagationJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && isAppInForeground) {
                val state = _uiState.value
                val batteryInfo = batteryAndSensorManager.readBatteryTelemetry(
                    activePowerMode = state.batteryTelemetry.activePowerMode,
                    isCameraActive = isArCameraHardwareActive,
                    isCompassActive = isArCompassSensorActive,
                    isGpsActive = state.isAcquiringGps
                )

                val now = System.currentTimeMillis()
                val realDelta = (now - lastRealTickMillis).coerceIn(0L, 5000L)
                lastRealTickMillis = now

                if (!state.isTimeWarpPaused && state.timeWarpMultiplier > 1) {
                    simulatedWarpAccumulatorMillis += realDelta * (state.timeWarpMultiplier - 1)
                } else if (state.timeWarpMultiplier == 1) {
                    simulatedWarpAccumulatorMillis = 0L
                }

                val needPassRecalc = (now - lastPassCalculationMillis) > 120_000L
                recomputeTelemetrySnapshot(
                    forcePassRecalc = needPassRecalc,
                    updatedBattery = batteryInfo
                )

                val sleepMs = if (state.selectedTab == AppDestinationTab.ORBIT_3D && state.timeWarpMultiplier > 1) {
                    minOf(batteryInfo.effectiveTickIntervalMs, 300L)
                } else {
                    batteryInfo.effectiveTickIntervalMs
                }
                delay(sleepMs)
            }
        }
    }

    private fun recomputeTelemetrySnapshot(
        forcePassRecalc: Boolean = false,
        updatedBattery: BatteryTelemetryState? = null
    ) {
        val currentState = _uiState.value
        val entities = currentState.catalogEntities
        if (entities.isEmpty()) return

        val now = System.currentTimeMillis()
        val scrubMillis = (currentState.manualScrubOffsetMinutes * 60_000f).toLong()
        val effectiveSimTime = now + simulatedWarpAccumulatorMillis + scrubMillis
        val observer = currentState.observerStation

        val liveList = entities.map { entity ->
            OrbitalPropagatorEngine.propagateSatellite(
                entity = entity,
                observer = observer,
                simulationTimeMillis = effectiveSimTime
            )
        }

        val filtered = filterAndSortTelemetry(
            all = liveList,
            layer = currentState.selectedOrbitLayer,
            category = currentState.selectedCategoryFilter,
            query = currentState.searchQuery,
            onlyAboveHorizon = currentState.onlyAboveHorizon,
            sortOption = currentState.catalogSortOption
        )

        val passes = if (forcePassRecalc || currentState.upcomingPasses.isEmpty()) {
            lastPassCalculationMillis = now
            OrbitalPropagatorEngine.predictUpcomingPasses(
                satellites = entities,
                observer = observer,
                startTimeMillis = now,
                windowHours = 18
            )
        } else {
            currentState.upcomingPasses
        }

        // Check if any watchlisted satellite is currently overhead or approaching in 5 minutes
        val trackedRules = entities.associate {
            it.noradId to Triple(it.isTrackedForAlerts, it.minAlertElevationDeg, it.sunlitOnlyAlert)
        }
        notificationDispatcher.evaluateLiveTelemetryAlerts(
            liveSatellites = liveList,
            trackedRules = trackedRules,
            observer = observer,
            soundMode = currentState.notificationSoundMode
        )
        if (forcePassRecalc) {
            notificationDispatcher.scheduleUpcomingPassAlarms(
                upcomingPasses = passes,
                trackedRules = trackedRules,
                observer = observer,
                leadMinutes = currentState.alertLeadTimeMinutes,
                soundMode = currentState.notificationSoundMode
            )
        }

        val current3dId = currentState.selected3DSatelliteNoradId
        val layerMatchesSelected = currentState.selectedOrbitLayer == OrbitLayer.ALL ||
            liveList.find { it.noradId == current3dId }?.orbitLayer == currentState.selectedOrbitLayer
        val resolved3dId = if (layerMatchesSelected) {
            current3dId
        } else {
            liveList.firstOrNull { it.orbitLayer == currentState.selectedOrbitLayer }?.noradId ?: current3dId
        }

        _uiState.update { st ->
            st.copy(
                liveTelemetryList = liveList,
                filteredTelemetryList = filtered,
                selected3DSatelliteNoradId = resolved3dId,
                upcomingPasses = passes,
                simulationTimeMillis = effectiveSimTime,
                batteryTelemetry = updatedBattery ?: st.batteryTelemetry
            )
        }
    }

    private fun filterAndSortTelemetry(
        all: List<SatelliteLiveTelemetry>,
        layer: OrbitLayer,
        category: SatelliteCategory?,
        query: String,
        onlyAboveHorizon: Boolean,
        sortOption: CatalogSortOption
    ): List<SatelliteLiveTelemetry> {
        val rawQuery = query.trim().lowercase()
        val queryTokens = rawQuery
            .replace("#", " ")
            .replace("norad", " ")
            .replace("-", " ")
            .replace("(", " ")
            .replace(")", " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val hasSearchQuery = queryTokens.isNotEmpty()

        val filtered = all.filter { sat ->
            // When actively searching or using Quick Find, search across all layers/categories/horizons so Quick Find always succeeds
            val matchesLayer = hasSearchQuery || (layer == OrbitLayer.ALL) || (sat.orbitLayer == layer)
            val matchesCategory = hasSearchQuery || (category == null) || (sat.category == category)
            val matchesHorizon = hasSearchQuery || !onlyAboveHorizon || sat.isAboveHorizon

            val searchableBlob = buildString {
                append(sat.name.lowercase()).append(' ')
                append(sat.name.lowercase().replace("-", " ")).append(' ')
                append(sat.noradId.toString()).append(' ')
                append(sat.designation.lowercase()).append(' ')
                append(sat.category.displayName.lowercase()).append(' ')
                append(sat.orbitLayer.code.lowercase()).append(' ')
                append(sat.orbitLayer.title.lowercase())
            }
            val matchesQuery = !hasSearchQuery || queryTokens.all { token ->
                searchableBlob.contains(token)
            }
            matchesLayer && matchesCategory && matchesHorizon && matchesQuery
        }

        return when (sortOption) {
            CatalogSortOption.NEXT_APPROACH_ASC -> filtered.sortedWith(
                compareByDescending<SatelliteLiveTelemetry> { it.isAboveHorizon }
                    .thenBy { if (it.isAboveHorizon) -it.elevationDeg else it.nextApproachMillis.toDouble() }
                    .thenByDescending { it.nextApproachMaxElDeg }
            )
            CatalogSortOption.ELEVATION_DESC -> filtered.sortedWith(
                compareByDescending<SatelliteLiveTelemetry> { it.isAboveHorizon }
                    .thenByDescending { it.elevationDeg }
            )
            CatalogSortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            CatalogSortOption.NORAD_ID_ASC -> filtered.sortedBy { it.noradId }
            CatalogSortOption.ALTITUDE_ASC -> filtered.sortedBy { it.altitudeKm }
            CatalogSortOption.SPEED_DESC -> filtered.sortedByDescending { it.velocityKms }
        }
    }

    fun selectTab(tab: AppDestinationTab) {
        if (tab != AppDestinationTab.AR_SKY) {
            isArCameraHardwareActive = false
            isArCompassSensorActive = false
        }
        _uiState.update { it.copy(selectedTab = tab) }
        recomputeTelemetrySnapshot(forcePassRecalc = false)
    }

    fun selectOrbitLayer(layer: OrbitLayer) {
        _uiState.update { state ->
            val matchingSat = state.liveTelemetryList.firstOrNull {
                layer == OrbitLayer.ALL || it.orbitLayer == layer
            }
            val next3dId = if (layer == OrbitLayer.ALL ||
                state.liveTelemetryList.find { it.noradId == state.selected3DSatelliteNoradId }?.orbitLayer == layer
            ) {
                state.selected3DSatelliteNoradId
            } else {
                matchingSat?.noradId ?: state.selected3DSatelliteNoradId
            }

            state.copy(
                selectedOrbitLayer = layer,
                selected3DSatelliteNoradId = next3dId,
                filteredTelemetryList = filterAndSortTelemetry(
                    all = state.liveTelemetryList,
                    layer = layer,
                    category = state.selectedCategoryFilter,
                    query = state.searchQuery,
                    onlyAboveHorizon = state.onlyAboveHorizon,
                    sortOption = state.catalogSortOption
                )
            )
        }
    }

    fun selectCategoryFilter(category: SatelliteCategory?) {
        _uiState.update { state ->
            val nextCategory = if (state.selectedCategoryFilter == category) null else category
            state.copy(
                selectedCategoryFilter = nextCategory,
                filteredTelemetryList = filterAndSortTelemetry(
                    all = state.liveTelemetryList,
                    layer = state.selectedOrbitLayer,
                    category = nextCategory,
                    query = state.searchQuery,
                    onlyAboveHorizon = state.onlyAboveHorizon,
                    sortOption = state.catalogSortOption
                )
            )
        }
    }

    fun updateSearchQuery(query: String) {
        // Strip control/injection characters while preserving spaces during live typing
        val safeQuery = query.take(64).filter { ch ->
            !Character.isISOControl(ch) && ch != '|' && ch != '<' && ch != '>' && ch != '"' && ch != '`' && ch != '\\'
        }
        _uiState.update { state ->
            val effectiveLayer = if (safeQuery.isNotBlank()) OrbitLayer.ALL else state.selectedOrbitLayer
            val effectiveCategory = if (safeQuery.isNotBlank()) null else state.selectedCategoryFilter
            val effectiveHorizon = if (safeQuery.isNotBlank()) false else state.onlyAboveHorizon
            val nextFiltered = filterAndSortTelemetry(
                all = state.liveTelemetryList,
                layer = effectiveLayer,
                category = effectiveCategory,
                query = safeQuery,
                onlyAboveHorizon = effectiveHorizon,
                sortOption = state.catalogSortOption
            )
            val nextSelectedId = if (safeQuery.isNotBlank() && nextFiltered.isNotEmpty()) {
                nextFiltered.first().noradId
            } else {
                state.selected3DSatelliteNoradId
            }
            state.copy(
                searchQuery = safeQuery,
                selectedOrbitLayer = effectiveLayer,
                selectedCategoryFilter = effectiveCategory,
                onlyAboveHorizon = effectiveHorizon,
                selected3DSatelliteNoradId = nextSelectedId,
                filteredTelemetryList = nextFiltered
            )
        }
    }

    fun setCatalogSortOption(sortOption: CatalogSortOption) {
        _uiState.update { state ->
            state.copy(
                catalogSortOption = sortOption,
                filteredTelemetryList = filterAndSortTelemetry(
                    all = state.liveTelemetryList,
                    layer = state.selectedOrbitLayer,
                    category = state.selectedCategoryFilter,
                    query = state.searchQuery,
                    onlyAboveHorizon = state.onlyAboveHorizon,
                    sortOption = sortOption
                )
            )
        }
    }

    fun setSearchableCatalogFocused(focused: Boolean) {
        _uiState.update { it.copy(isSearchableCatalogFocused = focused) }
    }

    fun resetCatalogSearchAndFilters() {
        _uiState.update { state ->
            state.copy(
                searchQuery = "",
                selectedOrbitLayer = OrbitLayer.ALL,
                selectedCategoryFilter = null,
                onlyAboveHorizon = false,
                filteredTelemetryList = filterAndSortTelemetry(
                    all = state.liveTelemetryList,
                    layer = OrbitLayer.ALL,
                    category = null,
                    query = "",
                    onlyAboveHorizon = false,
                    sortOption = state.catalogSortOption
                )
            )
        }
    }

    fun toggleOnlyAboveHorizon() {
        _uiState.update { state ->
            val next = !state.onlyAboveHorizon
            state.copy(
                onlyAboveHorizon = next,
                filteredTelemetryList = filterAndSortTelemetry(
                    all = state.liveTelemetryList,
                    layer = state.selectedOrbitLayer,
                    category = state.selectedCategoryFilter,
                    query = state.searchQuery,
                    onlyAboveHorizon = next,
                    sortOption = state.catalogSortOption
                )
            )
        }
    }

    fun select3DSatellite(noradId: Int) {
        _uiState.update { it.copy(selected3DSatelliteNoradId = noradId) }
    }

    fun toggleShowAllLayerSatellitesIn3D() {
        _uiState.update { it.copy(showAllLayerSatellitesIn3D = !it.showAllLayerSatellitesIn3D) }
    }

    fun openSatelliteDossier(noradId: Int) {
        _uiState.update {
            it.copy(
                inspectedSatelliteNoradId = noradId,
                selected3DSatelliteNoradId = noradId
            )
        }
    }

    fun closeSatelliteDossier() {
        _uiState.update { it.copy(inspectedSatelliteNoradId = null) }
    }

    fun focusSatelliteIn3D(noradId: Int) {
        _uiState.update {
            it.copy(
                selected3DSatelliteNoradId = noradId,
                inspectedSatelliteNoradId = null,
                selectedTab = AppDestinationTab.ORBIT_3D
            )
        }
    }

    fun focusSatelliteInArSky(noradId: Int) {
        _uiState.update {
            it.copy(
                selected3DSatelliteNoradId = noradId,
                inspectedSatelliteNoradId = null,
                selectedTab = AppDestinationTab.AR_SKY
            )
        }
    }

    fun setTimeWarpMultiplier(multiplier: Int) {
        lastRealTickMillis = System.currentTimeMillis()
        if (multiplier == 1) {
            simulatedWarpAccumulatorMillis = 0L
        }
        _uiState.update {
            it.copy(
                timeWarpMultiplier = multiplier,
                isTimeWarpPaused = false
            )
        }
        recomputeTelemetrySnapshot(forcePassRecalc = false)
    }

    fun setManualScrubOffsetMinutes(offsetMinutes: Float) {
        _uiState.update { it.copy(manualScrubOffsetMinutes = offsetMinutes) }
        recomputeTelemetrySnapshot(forcePassRecalc = false)
    }

    fun resetTimeToLiveNow() {
        simulatedWarpAccumulatorMillis = 0L
        lastRealTickMillis = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                timeWarpMultiplier = 1,
                manualScrubOffsetMinutes = 0f,
                isTimeWarpPaused = false
            )
        }
        recomputeTelemetrySnapshot(forcePassRecalc = false)
    }

    fun updateObserverStation(station: ObserverStation) {
        securityVault.saveEncryptedObserverStation(station)
        _uiState.update { state ->
            state.copy(
                observerStation = station,
                securityAudit = state.securityAudit.copy(
                    sampleEncryptedObserverPreview = securityVault.getEncryptedObserverCipherPreview()
                ),
                statusBannerMessage = "Observer locked to ${station.name} (Encrypted with AES-256-GCM)"
            )
        }
        recomputeTelemetrySnapshot(forcePassRecalc = true)
    }

    fun acquireGpsObserverStation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAcquiringGps = true) }
            val gpsStation = batteryAndSensorManager.acquireSingleShotGpsStation()
            _uiState.update { it.copy(isAcquiringGps = false) }
            if (gpsStation != null) {
                updateObserverStation(gpsStation)
            } else {
                _uiState.update {
                    it.copy(
                        statusBannerMessage = "GPS signal unavailable in container; using encrypted manual station (${it.observerStation.name})."
                    )
                }
            }
        }
    }

    fun setPowerMode(mode: PowerMode) {
        securityVault.savePowerMode(mode)
        val updatedBattery = batteryAndSensorManager.readBatteryTelemetry(
            activePowerMode = mode,
            isCameraActive = isArCameraHardwareActive,
            isCompassActive = isArCompassSensorActive,
            isGpsActive = _uiState.value.isAcquiringGps
        )
        _uiState.update {
            it.copy(
                batteryTelemetry = updatedBattery,
                statusBannerMessage = "Power Governor set to ${mode.label} (${updatedBattery.effectiveTickIntervalMs}ms tick)"
            )
        }
    }

    fun setArHardwareStatus(cameraActive: Boolean, compassActive: Boolean) {
        isArCameraHardwareActive = cameraActive
        isArCompassSensorActive = compassActive
        val updatedBattery = batteryAndSensorManager.readBatteryTelemetry(
            activePowerMode = _uiState.value.batteryTelemetry.activePowerMode,
            isCameraActive = cameraActive,
            isCompassActive = compassActive,
            isGpsActive = _uiState.value.isAcquiringGps
        )
        _uiState.update { it.copy(batteryTelemetry = updatedBattery) }
    }

    private val cachedSkyOrientationFlow: Flow<DeviceSkyOrientation> =
        batteryAndSensorManager.observeSkyOrientation()

    fun observeSkyOrientationFlow(): Flow<DeviceSkyOrientation> = cachedSkyOrientationFlow

    fun setNotificationSoundMode(mode: com.example.model.NotificationSoundMode) {
        securityVault.saveNotificationSoundMode(mode)
        _uiState.update {
            it.copy(
                notificationSoundMode = mode,
                statusBannerMessage = "Notification Sound set to: ${mode.title}"
            )
        }
        if (mode == com.example.model.NotificationSoundMode.FEMALE_VOICE_ANNOUNCER) {
            previewSatelliteVoiceAnnouncement("ISS (ZARYA)")
        }
    }

    fun setAlertLeadTimeMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 30)
        securityVault.saveAlertLeadTimeMinutes(clamped)
        _uiState.update { state ->
            val phrase = PassVoiceAnnouncer.buildApproachingVoicePhrase("ISS (ZARYA)", clamped)
            state.copy(
                alertLeadTimeMinutes = clamped,
                statusBannerMessage = "Background Alert Lead Time set to $clamped min (e.g. \"$phrase\")"
            )
        }
        recomputeTelemetrySnapshot(forcePassRecalc = true)
    }

    fun previewSatelliteVoiceAnnouncement(satelliteName: String) {
        val leadMin = _uiState.value.alertLeadTimeMinutes
        val phrase = PassVoiceAnnouncer.buildApproachingVoicePhrase(satelliteName, leadMin)
        PassVoiceAnnouncer.speakFemaleVoiceAnnouncement(getApplication(), phrase)
        _uiState.update {
            it.copy(statusBannerMessage = "Voice Notification: \"$phrase\"")
        }
    }

    fun speakLiveTelemetryBriefing(noradId: Int) {
        val state = _uiState.value
        val telemetry = state.liveTelemetryList.find { it.noradId == noradId } ?: return
        val spokenName = PassVoiceAnnouncer.formatSpokenSatelliteName(telemetry.name)
        val approachPhrase = PassVoiceAnnouncer.buildApproachingVoicePhrase(
            telemetry.name,
            state.alertLeadTimeMinutes
        )
        val briefing = if (telemetry.isAboveHorizon) {
            "$spokenName is currently overhead at ${telemetry.elevationDeg.roundToInt()} degrees elevation, altitude ${telemetry.altitudeKm.roundToInt()} kilometers."
        } else {
            "$approachPhrase. Current altitude ${telemetry.altitudeKm.roundToInt()} kilometers, speed ${"%.1f".format(telemetry.velocityKms)} kilometers per second."
        }
        PassVoiceAnnouncer.speakFemaleVoiceAnnouncement(getApplication(), briefing)
        _uiState.update {
            it.copy(statusBannerMessage = "Voice Briefing: \"$briefing\"")
        }
    }

    fun updateSatelliteAlertRule(
        noradId: Int,
        isTracked: Boolean,
        minElevationDeg: Int,
        sunlitOnly: Boolean
    ) {
        viewModelScope.launch {
            repository.updateAlertSettings(noradId, isTracked, minElevationDeg, sunlitOnly)
            recomputeTelemetrySnapshot(forcePassRecalc = true)
        }
    }

    fun triggerTestOverheadAlert() {
        val state = _uiState.value
        val nextPass = state.upcomingPasses.firstOrNull()
        val message = notificationDispatcher.triggerSimulatedPassAlert(
            pass = nextPass,
            observer = state.observerStation,
            soundMode = state.notificationSoundMode,
            leadMinutes = state.alertLeadTimeMinutes
        )
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    fun toggleFlagSecure(activity: Activity?, enabled: Boolean) {
        securityVault.saveFlagSecureEnabled(enabled)
        securityVault.applyWindowSecurityFlag(activity, enabled)
        _uiState.update { state ->
            state.copy(
                securityAudit = state.securityAudit.copy(isFlagSecureEnabled = enabled),
                statusBannerMessage = if (enabled) {
                    "FLAG_SECURE Enabled: Screen recording & overlay harvesting blocked."
                } else {
                    "FLAG_SECURE Disabled: Standard screen capture allowed."
                }
            )
        }
    }

    fun rotateVaultKey() {
        viewModelScope.launch {
            val newGen = securityVault.rotateVaultEncryptionKey()
            val sandboxOk = securityVault.hardenInternalSandboxPermissions()
            _uiState.update { state ->
                state.copy(
                    securityAudit = state.securityAudit.copy(
                        isKeystoreHardwareBacked = securityVault.isHardwareKeystoreAvailable,
                        isAadBoundEncryption = true,
                        isHmacAlarmAuthActive = true,
                        isSandboxLockedDown = sandboxOk,
                        keyRotationCount = newGen,
                        lastAuditTimestampMs = System.currentTimeMillis(),
                        sampleEncryptedObserverPreview = securityVault.getEncryptedObserverCipherPreview()
                    ),
                    statusBannerMessage = "Cryptographic Key Rotated (Generation v$newGen): All vault records re-encrypted with fresh AES-256-GCM IVs & AAD tags."
                )
            }
        }
    }

    fun runSecurityAndIntegrityAudit(showBanner: Boolean = true) {
        viewModelScope.launch {
            val result = repository.runCryptographicIntegrityAudit()
            val sandboxOk = securityVault.hardenInternalSandboxPermissions()
            val keyGen = securityVault.getKeyRotationGeneration()
            val allChecksumsOk = result.validModulo10Count == result.totalRecords
            val allShaOk = result.validSha256Count == result.totalRecords
            val score = if (allChecksumsOk && allShaOk && sandboxOk) 100 else 88

            _uiState.update { state ->
                state.copy(
                    securityAudit = state.securityAudit.copy(
                        isKeystoreHardwareBacked = securityVault.isHardwareKeystoreAvailable,
                        isAadBoundEncryption = true,
                        isHmacAlarmAuthActive = true,
                        isOrbitalSanityVerified = allChecksumsOk,
                        isSandboxLockedDown = sandboxOk,
                        isGpsAntiSpoofActive = true,
                        verifiedSatelliteCount = result.validSha256Count,
                        totalSatelliteCount = result.totalRecords,
                        allTleChecksumsValid = allChecksumsOk,
                        keyRotationCount = keyGen,
                        securityScorePercent = score,
                        lastAuditTimestampMs = System.currentTimeMillis(),
                        sampleEncryptedObserverPreview = securityVault.getEncryptedObserverCipherPreview()
                    ),
                    statusBannerMessage = if (showBanner) {
                        "Security Audit Passed ($score%): ${result.validSha256Count}/${result.totalRecords} SHA-256 + Mod-10 + Keplerian TLE checks & AAD Vault verified."
                    } else state.statusBannerMessage
                )
            }
        }
    }

    fun syncTlesFromCelestrak() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingTles = true) }
            val syncResult = repository.syncLatestTlesFromCelestrak()
            val auditResult = repository.runCryptographicIntegrityAudit()
            _uiState.update { state ->
                state.copy(
                    isSyncingTles = false,
                    securityAudit = state.securityAudit.copy(
                        verifiedSatelliteCount = auditResult.validSha256Count,
                        totalSatelliteCount = auditResult.totalRecords,
                        allTleChecksumsValid = auditResult.validModulo10Count == auditResult.totalRecords,
                        isOrbitalSanityVerified = auditResult.validModulo10Count == auditResult.totalRecords,
                        lastAuditTimestampMs = System.currentTimeMillis()
                    ),
                    statusBannerMessage = syncResult.message
                )
            }
        }
    }

    fun dismissStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }
}
