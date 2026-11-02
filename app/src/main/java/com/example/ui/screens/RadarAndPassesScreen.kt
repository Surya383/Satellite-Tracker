package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CatalogSortOption
import com.example.model.ObserverStation
import com.example.model.OrbitLayer
import com.example.model.OverheadPassPrediction
import com.example.model.SatelliteCategory
import com.example.model.SatelliteLiveTelemetry
import com.example.ui.components.OrbitLayerIntelligenceBanner
import com.example.ui.components.OrbitLayerSelectorStrip
import com.example.ui.components.azimuthCardinal
import com.example.ui.components.formatLat
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.HudBorderHairline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OrbitalCyan
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.StarlightWhite
import com.example.ui.theme.TacticalSlateElevated
import com.example.ui.theme.TacticalSlateSurface
import com.example.ui.theme.TelemetryMuted
import com.example.viewmodel.OrbitalUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private data class RadarBlipHitTarget(
    val noradId: Int,
    val screenX: Float,
    val screenY: Float
)

private val QuickSearchPresets = listOf(
    "ISS" to "ISS #25544",
    "48274" to "Tiangong #48274",
    "20580" to "Hubble #20580",
    "43013" to "NOAA-20 #43013",
    "43873" to "GPS III #43873",
    "41866" to "GOES-16 #41866",
    "28841" to "Molniya #28841"
)

@Composable
fun RadarAndPassesScreen(
    uiState: OrbitalUiState,
    onSelectOrbitLayer: (OrbitLayer) -> Unit,
    onSelectCategoryFilter: (SatelliteCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectSortOption: (CatalogSortOption) -> Unit = {},
    onToggleCatalogListMode: (Boolean) -> Unit = {},
    onResetSearchAndFilters: () -> Unit = {},
    onToggleOnlyAboveHorizon: () -> Unit,
    onInspectSatellite: (Int) -> Unit,
    onOpenIn3D: (Int) -> Unit,
    onOpenInArSky: (Int) -> Unit,
    onSpeakSatelliteBriefing: (Int) -> Unit
) {
    val countsByLayer = remember(uiState.liveTelemetryList) {
        val map = mutableMapOf<OrbitLayer, Int>()
        map[OrbitLayer.ALL] = uiState.liveTelemetryList.size
        OrbitLayer.entries.filter { it != OrbitLayer.ALL }.forEach { layer ->
            map[layer] = uiState.liveTelemetryList.count { it.orbitLayer == layer }
        }
        map
    }

    val satellitesInSelectedLayer = remember(uiState.liveTelemetryList, uiState.selectedOrbitLayer) {
        if (uiState.selectedOrbitLayer == OrbitLayer.ALL) {
            uiState.liveTelemetryList
        } else {
            uiState.liveTelemetryList.filter { it.orbitLayer == uiState.selectedOrbitLayer }
        }
    }

    val passesInSelectedLayer = remember(uiState.upcomingPasses, uiState.selectedOrbitLayer) {
        if (uiState.selectedOrbitLayer == OrbitLayer.ALL) {
            uiState.upcomingPasses
        } else {
            uiState.upcomingPasses.filter { it.orbitLayer == uiState.selectedOrbitLayer }
        }
    }

    // Only collapse the Radar when the user explicitly toggles "Catalog List" mode;
    // Quick Find keeps the Radar visible while showing an Instant Quick-Find Spotlight right below the search bar!
    val isCompactSearchListMode = uiState.isSearchableCatalogFocused

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .testTag("radar_and_passes_screen"),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Orbital Layer Strip + Friendly View Mode Switcher
        item {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Orbital Shells & View",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = StarlightWhite
                    )

                    // Friendly Pill Switcher: Sky Radar vs Full List
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(TacticalSlateSurface)
                            .border(1.dp, HudBorderHairline, RoundedCornerShape(50))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = if (!uiState.isSearchableCatalogFocused) OrbitalCyan.copy(alpha = 0.24f) else Color.Transparent,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onToggleCatalogListMode(false) }
                                .testTag("mode_radar_and_catalog_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = if (!uiState.isSearchableCatalogFocused) OrbitalCyan else TelemetryMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sky Radar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!uiState.isSearchableCatalogFocused) StarlightWhite else TelemetryMuted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            color = if (uiState.isSearchableCatalogFocused) OrbitalCyan.copy(alpha = 0.24f) else Color.Transparent,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onToggleCatalogListMode(true) }
                                .testTag("mode_searchable_catalog_only_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    tint = if (uiState.isSearchableCatalogFocused) OrbitalCyan else TelemetryMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Catalog List",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isSearchableCatalogFocused) StarlightWhite else TelemetryMuted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OrbitLayerSelectorStrip(
                    selectedLayer = uiState.selectedOrbitLayer,
                    satelliteCountsByLayer = countsByLayer,
                    onSelectLayer = onSelectOrbitLayer
                )
            }
        }

        // 2. Friendly Quick Find & Searchable Satellite Catalog Control Card
        item {
            SearchableCatalogControlCard(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                allSatellites = uiState.liveTelemetryList,
                matchedSatellites = uiState.filteredTelemetryList,
                filteredCount = uiState.filteredTelemetryList.size,
                totalCount = uiState.liveTelemetryList.size,
                onlyAboveHorizon = uiState.onlyAboveHorizon,
                onToggleOnlyAboveHorizon = onToggleOnlyAboveHorizon,
                selectedCategory = uiState.selectedCategoryFilter,
                onSelectCategory = onSelectCategoryFilter,
                selectedSortOption = uiState.catalogSortOption,
                onSelectSortOption = onSelectSortOption,
                onInspectSatellite = onInspectSatellite,
                onOpenIn3D = onOpenIn3D,
                onOpenInArSky = onOpenInArSky,
                onSpeakSatelliteBriefing = onSpeakSatelliteBriefing,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 3. Show Radar, Countdown & Layer Overview when not in Catalog-List-Only mode
        if (!isCompactSearchListMode) {
            val nextPass = passesInSelectedLayer.firstOrNull() ?: uiState.upcomingPasses.firstOrNull()
            if (nextPass != null) {
                item {
                    NextPassMissionCountdownBanner(
                        pass = nextPass,
                        observerName = uiState.observerStation.name,
                        simulationTimeMillis = uiState.simulationTimeMillis,
                        onInspect = { onInspectSatellite(nextPass.noradId) },
                        onOpen3D = { onOpenIn3D(nextPass.noradId) },
                        onOpenAr = { onOpenInArSky(nextPass.noradId) },
                        onSpeakVoice = { onSpeakSatelliteBriefing(nextPass.noradId) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            item {
                PolarSkyDomeRadarCard(
                    satellites = satellitesInSelectedLayer,
                    selectedSat = uiState.selected3DSatelliteTelemetry,
                    observer = uiState.observerStation,
                    onTapSatelliteBlip = onInspectSatellite,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                OrbitLayerIntelligenceBanner(
                    layer = uiState.selectedOrbitLayer,
                    satellitesInLayer = satellitesInSelectedLayer,
                    selectedSatelliteNoradId = uiState.selected3DSatelliteNoradId,
                    onSelectSatellite = { /* handled with dossier inspection */ },
                    onInspectSatelliteDossier = onInspectSatellite,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = SolarAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upcoming Sky Passes (Next 18h • IST)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StarlightWhite
                            )
                        }
                        Text(
                            text = "${passesInSelectedLayer.size} Passes",
                            style = MaterialTheme.typography.labelSmall,
                            color = SolarAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (passesInSelectedLayer.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, HudBorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No high-elevation passes in this orbital shell within 18h for ${uiState.observerStation.name}. Switch to All Orbits or LEO to see active passes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TelemetryMuted,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            passesInSelectedLayer.take(4).forEach { pass ->
                                OverheadPassRowCard(
                                    pass = pass,
                                    onClick = { onInspectSatellite(pass.noradId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Chronological Next-Approach Satellite Catalog List Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) {
                            "Quick Find Matches for \"${uiState.searchQuery}\" (${uiState.filteredTelemetryList.size})"
                        } else {
                            "Satellites by Next Approach (${uiState.filteredTelemetryList.size})"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StarlightWhite
                    )
                    Text(
                        text = "Sorted by upcoming approach time over ${uiState.observerStation.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryMuted
                    )
                }
                Text(
                    text = uiState.catalogSortOption.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = OrbitalCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(OrbitalCyan.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // 5. Empty State if no satellites match the search query / filters
        if (uiState.filteredTelemetryList.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HudBorderHairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("empty_satellite_search_state")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OrbitalCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Satellites Match \"${uiState.searchQuery}\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarlightWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching by satellite name (e.g. ISS, Hubble, Tiangong, NOAA) or NORAD ID (e.g. 25544, 20580, 48274) or reset active filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TelemetryMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onResetSearchAndFilters,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OrbitalCyan,
                                contentColor = ObsidianSpace
                            ),
                            modifier = Modifier.testTag("reset_catalog_search_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Search & Filters", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // 6. Live Searchable Satellite List Items Ordered by Next Approach Timing
            itemsIndexed(
                items = uiState.filteredTelemetryList,
                key = { _, sat -> sat.noradId }
            ) { index, telemetry ->
                LiveSatelliteTelemetryCard(
                    rankIndex = index + 1,
                    telemetry = telemetry,
                    onTapInspect = { onInspectSatellite(telemetry.noradId) },
                    onTap3DSim = { onOpenIn3D(telemetry.noradId) },
                    onTapArSky = { onOpenInArSky(telemetry.noradId) },
                    onTapSpeakVoice = { onSpeakSatelliteBriefing(telemetry.noradId) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchableCatalogControlCard(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    allSatellites: List<SatelliteLiveTelemetry>,
    matchedSatellites: List<SatelliteLiveTelemetry>,
    filteredCount: Int,
    totalCount: Int,
    onlyAboveHorizon: Boolean,
    onToggleOnlyAboveHorizon: () -> Unit,
    selectedCategory: SatelliteCategory?,
    onSelectCategory: (SatelliteCategory?) -> Unit,
    selectedSortOption: CatalogSortOption,
    onSelectSortOption: (CatalogSortOption) -> Unit,
    onInspectSatellite: (Int) -> Unit,
    onOpenIn3D: (Int) -> Unit,
    onOpenInArSky: (Int) -> Unit,
    onSpeakSatelliteBriefing: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Build Quick Find chips for all satellites ordered by next approach timing so every satellite is 1 tap away
    val quickFindSatellites = remember(allSatellites) {
        allSatellites.sortedWith(
            compareByDescending<SatelliteLiveTelemetry> { it.isAboveHorizon }
                .thenBy { if (it.isAboveHorizon) -it.elevationDeg else it.nextApproachMillis.toDouble() }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (searchQuery.isNotBlank()) OrbitalCyan else HudBorderHairline
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("searchable_catalog_header_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            OrbitalCyan.copy(alpha = 0.11f),
                            TacticalSlateSurface
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = OrbitalCyan,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quick Find & Satellite Search",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StarlightWhite
                    )
                }

                Text(
                    text = "$filteredCount of $totalCount Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrbitalCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(OrbitalCyan.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Pill Search Input Field (Name, NORAD ID #25544, COSPAR, or Orbit Layer)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search satellite name (ISS, Hubble) or ID (25544)…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TelemetryMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = OrbitalCyan
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.testTag("clear_satellite_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = StarlightWhite
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrbitalCyan,
                    unfocusedBorderColor = HudBorderHairline,
                    focusedContainerColor = ObsidianSpace,
                    unfocusedContainerColor = ObsidianSpace,
                    focusedTextColor = StarlightWhite,
                    unfocusedTextColor = StarlightWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("satellite_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Pill Quick Find Chips for ALL Satellites (with Orbital Layer color dot & NORAD ID)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Find:",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrbitalCyan,
                    fontWeight = FontWeight.Bold
                )

                // "All (18)" reset pill
                val isAllSelected = searchQuery.isBlank()
                Surface(
                    color = if (isAllSelected) OrbitalCyan.copy(alpha = 0.24f) else TacticalSlateElevated,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (isAllSelected) OrbitalCyan else HudBorderHairline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSearchQueryChange("") }
                        .testTag("quick_search_chip_all")
                ) {
                    Text(
                        text = "All ($totalCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAllSelected) OrbitalCyan else StarlightWhite,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }

                if (quickFindSatellites.isNotEmpty()) {
                    quickFindSatellites.forEach { sat ->
                        val shortName = sat.name.substringBefore("(").trim()
                        val noradStr = sat.noradId.toString()
                        val isSelected = searchQuery.trim().equals(noradStr, ignoreCase = true) ||
                            searchQuery.trim().equals(shortName, ignoreCase = true) ||
                            (searchQuery.isNotBlank() && matchedSatellites.size == 1 && matchedSatellites.first().noradId == sat.noradId)

                        Surface(
                            color = if (isSelected) sat.orbitLayer.accentColor.copy(alpha = 0.24f) else TacticalSlateElevated,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) sat.orbitLayer.accentColor else HudBorderHairline
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    if (isSelected) {
                                        onSearchQueryChange("")
                                    } else {
                                        onSearchQueryChange(noradStr)
                                    }
                                }
                                .testTag("quick_search_chip_$noradStr")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (sat.isAboveHorizon) EmeraldLock else sat.orbitLayer.accentColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$shortName #${sat.noradId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StarlightWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    QuickSearchPresets.forEach { (queryValue, chipLabel) ->
                        val isSelected = searchQuery.equals(queryValue, ignoreCase = true)
                        Surface(
                            color = if (isSelected) OrbitalCyan.copy(alpha = 0.24f) else TacticalSlateElevated,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) OrbitalCyan else HudBorderHairline
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    if (isSelected) onSearchQueryChange("") else onSearchQueryChange(queryValue)
                                }
                                .testTag("quick_search_chip_$queryValue")
                        ) {
                            Text(
                                text = chipLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) OrbitalCyan else StarlightWhite,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Instant Quick-Find Spotlight Card right inside the search box when a query / Quick Find chip is active!
            if (searchQuery.isNotBlank() && matchedSatellites.isNotEmpty()) {
                val primaryMatch = matchedSatellites.first()
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ObsidianSpace,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, primaryMatch.orbitLayer.accentColor.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onInspectSatellite(primaryMatch.noradId) }
                        .testTag("quick_find_instant_result_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primaryMatch.orbitLayer.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ObsidianSpace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(primaryMatch.orbitLayer.accentColor)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${primaryMatch.name} • #${primaryMatch.noradId}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StarlightWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = primaryMatch.nextApproachStatusText.ifBlank {
                                            if (primaryMatch.isAboveHorizon) "PASSING OVERHEAD NOW" else "TRACKED IN ORBIT"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (primaryMatch.isAboveHorizon) EmeraldLock else SolarAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "Alt ${primaryMatch.altitudeKm.roundToInt()} km • El ${primaryMatch.elevationDeg.roundToInt()}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrbitalCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = TacticalSlateElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onInspectSatellite(primaryMatch.noradId) }
                            ) {
                                Text(
                                    text = "Full Specs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OrbitalCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                color = TacticalSlateElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenIn3D(primaryMatch.noradId) }
                            ) {
                                Text(
                                    text = "3D Orbit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StarlightWhite,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                color = TacticalSlateElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, EmeraldLock.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenInArSky(primaryMatch.noradId) }
                            ) {
                                Text(
                                    text = "AR Sky",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLock,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                color = TacticalSlateElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSpeakSatelliteBriefing(primaryMatch.noradId) }
                            ) {
                                Text(
                                    text = "Voice",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SolarAmber,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sort Options + Above Horizon Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = onlyAboveHorizon,
                    onClick = onToggleOnlyAboveHorizon,
                    label = {
                        Text(
                            text = "Above Horizon",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldLock.copy(alpha = 0.22f),
                        selectedLabelColor = EmeraldLock,
                        containerColor = TacticalSlateElevated,
                        labelColor = TelemetryMuted
                    ),
                    modifier = Modifier.testTag("filter_above_horizon_chip")
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    tint = TelemetryMuted,
                    modifier = Modifier.size(14.dp)
                )

                CatalogSortOption.entries.forEach { sortOpt ->
                    val active = selectedSortOption == sortOpt
                    FilterChip(
                        selected = active,
                        onClick = { onSelectSortOption(sortOpt) },
                        label = {
                            Text(
                                text = sortOpt.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SolarAmber.copy(alpha = 0.22f),
                            selectedLabelColor = SolarAmber,
                            containerColor = TacticalSlateElevated,
                            labelColor = TelemetryMuted
                        ),
                        modifier = Modifier.testTag("sort_chip_${sortOpt.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Mission Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = {
                        Text("All Missions", style = MaterialTheme.typography.labelSmall)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OrbitalCyan.copy(alpha = 0.22f),
                        selectedLabelColor = OrbitalCyan,
                        containerColor = TacticalSlateElevated,
                        labelColor = TelemetryMuted
                    )
                )
                SatelliteCategory.entries.forEach { cat ->
                    val selected = selectedCategory == cat
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCategory(cat) },
                        label = {
                            Text(cat.displayName, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrbitalCyan.copy(alpha = 0.22f),
                            selectedLabelColor = OrbitalCyan,
                            containerColor = TacticalSlateElevated,
                            labelColor = TelemetryMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NextPassMissionCountdownBanner(
    pass: OverheadPassPrediction,
    observerName: String,
    simulationTimeMillis: Long,
    onInspect: () -> Unit,
    onOpen3D: () -> Unit,
    onOpenAr: () -> Unit,
    onSpeakVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diffSeconds = ((pass.aosTimeMillis - simulationTimeMillis) / 1000L).coerceAtLeast(0L)
    val hours = diffSeconds / 3600L
    val minutes = (diffSeconds % 3600L) / 60L
    val seconds = diffSeconds % 60L
    val countdownText = if (diffSeconds == 0L) {
        "PASSING OVERHEAD NOW"
    } else if (hours > 0) {
        "T-%02dh %02dm %02ds".format(hours, minutes, seconds)
    } else {
        "T-%02dm %02ds".format(minutes, seconds)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.65f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onInspect() }
            .testTag("next_pass_countdown_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            SolarAmber.copy(alpha = 0.14f),
                            TacticalSlateSurface,
                            OrbitalCyan.copy(alpha = 0.10f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pass.orbitLayer.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianSpace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pass.orbitLayer.accentColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Next Sky Pass • ${observerName.substringBefore(",").trim()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SolarAmber,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.labelMedium,
                    color = ObsidianSpace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SolarAmber)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pass.satelliteName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StarlightWhite
                    )
                    Text(
                        text = "Peak ${pass.maxElevationDeg.roundToInt()}° • Rises ${azimuthCardinal(pass.aosAzimuthDeg)} (${pass.aosAzimuthDeg.roundToInt()}°) → Sets ${azimuthCardinal(pass.losAzimuthDeg)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryMuted
                    )
                }

                // Quick Voice Briefing, 3D & AR Pill Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSpeakVoice() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak Pass Voice Briefing",
                                tint = SolarAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Voice", style = MaterialTheme.typography.labelSmall, color = SolarAmber, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpen3D() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Open in 3D",
                                tint = OrbitalCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("3D", style = MaterialTheme.typography.labelSmall, color = StarlightWhite, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, EmeraldLock.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenAr() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = "Open in AR Sky",
                                tint = EmeraldLock,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AR", style = MaterialTheme.typography.labelSmall, color = StarlightWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolarSkyDomeRadarCard(
    satellites: List<SatelliteLiveTelemetry>,
    selectedSat: SatelliteLiveTelemetry?,
    observer: ObserverStation,
    onTapSatelliteBlip: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val aboveHorizonSats = remember(satellites) { satellites.filter { it.isAboveHorizon } }
    var showGlobalGroundTrackMap by remember { mutableStateOf(false) }
    val blipHitTargets = remember { ArrayList<RadarBlipHitTarget>() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 23f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val satTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 21f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HudBorderHairline),
        modifier = modifier.fillMaxWidth()
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
                        imageVector = if (showGlobalGroundTrackMap) Icons.Default.Map else Icons.Default.Radar,
                        contentDescription = null,
                        tint = EmeraldLock,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (showGlobalGroundTrackMap) {
                                "World Ground-Track Map"
                            } else {
                                "Live Sky Radar • ${observer.name.substringBefore(",").trim()}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarlightWhite
                        )
                        Text(
                            text = "Tap any satellite dot to view full specs & orbit details",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryMuted
                        )
                    }
                }

                // Toggle between Polar Sky Dome and Global Ground-Track Map
                Surface(
                    color = TacticalSlateElevated,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showGlobalGroundTrackMap = !showGlobalGroundTrackMap }
                        .testTag("toggle_radar_map_mode_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showGlobalGroundTrackMap) Icons.Default.Radar else Icons.Default.Map,
                            contentDescription = "Switch Radar/Map View",
                            tint = OrbitalCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showGlobalGroundTrackMap) "Sky Dome" else "World Map",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrbitalCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSpace)
                    .border(1.dp, HudBorderHairline.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .pointerInput(satellites, showGlobalGroundTrackMap) {
                        detectTapGestures { tapOffset ->
                            val hit = blipHitTargets.minByOrNull {
                                hypot(it.screenX - tapOffset.x, it.screenY - tapOffset.y)
                            }
                            if (hit != null && hypot(hit.screenX - tapOffset.x, hit.screenY - tapOffset.y) <= 44.dp.toPx()) {
                                onTapSatelliteBlip(hit.noradId)
                            }
                        }
                    }
                    .testTag("polar_sky_dome_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    blipHitTargets.clear()

                    if (!showGlobalGroundTrackMap) {
                        val cx = w / 2f
                        val cy = h / 2f
                        val maxR = minOf(cx, cy) * 0.80f

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(OrbitalCyan.copy(alpha = 0.14f), Color.Transparent),
                                center = Offset(cx, cy),
                                radius = maxR
                            ),
                            radius = maxR,
                            center = Offset(cx, cy)
                        )

                        drawCircle(
                            color = OrbitalCyan.copy(alpha = 0.45f),
                            radius = maxR,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = HudBorderHairline,
                            radius = maxR * (2f / 3f),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.2f, pathEffect = dashEffect)
                        )
                        drawCircle(
                            color = HudBorderHairline,
                            radius = maxR * (1f / 3f),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.2f, pathEffect = dashEffect)
                        )

                        drawLine(
                            color = HudBorderHairline,
                            start = Offset(cx, cy - maxR),
                            end = Offset(cx, cy + maxR),
                            strokeWidth = 1.2f
                        )
                        drawLine(
                            color = HudBorderHairline,
                            start = Offset(cx - maxR, cy),
                            end = Offset(cx + maxR, cy),
                            strokeWidth = 1.2f
                        )

                        drawContext.canvas.nativeCanvas.apply {
                            drawText("N 0°", cx, cy - maxR - 10f, labelPaint)
                            drawText("S 180°", cx, cy + maxR + 24f, labelPaint)
                            drawText("E 90°", cx + maxR + 36f, cy + 8f, labelPaint)
                            drawText("W 270°", cx - maxR - 40f, cy + 8f, labelPaint)
                        }

                        satellites.forEach { sat ->
                            val normalizedR = if (sat.isAboveHorizon) {
                                ((90.0 - sat.elevationDeg.coerceIn(0.0, 90.0)) / 90.0).toFloat() * maxR
                            } else {
                                maxR * 1.05f
                            }
                            val azRad = (sat.azimuthDeg - 90.0) * (PI / 180.0)
                            val bx = cx + normalizedR * cos(azRad).toFloat()
                            val by = cy + normalizedR * sin(azRad).toFloat()

                            blipHitTargets.add(RadarBlipHitTarget(sat.noradId, bx, by))

                            val blipColor = sat.orbitLayer.accentColor
                            if (sat.isAboveHorizon) {
                                drawCircle(
                                    color = blipColor.copy(alpha = 0.25f),
                                    radius = 18f,
                                    center = Offset(bx, by)
                                )
                                drawCircle(
                                    color = blipColor,
                                    radius = 7.5f,
                                    center = Offset(bx, by)
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    "${sat.name.take(10)} (${sat.elevationDeg.roundToInt()}°)",
                                    bx + 12f,
                                    by + 6f,
                                    satTextPaint
                                )
                            } else {
                                drawCircle(
                                    color = blipColor.copy(alpha = 0.45f),
                                    radius = 4.5f,
                                    center = Offset(bx, by)
                                )
                            }
                        }
                    } else {
                        fun lonToX(lon: Double): Float = (((lon + 180.0) / 360.0) * w).toFloat().coerceIn(0f, w)
                        fun latToY(lat: Double): Float = (((90.0 - lat) / 180.0) * h).toFloat().coerceIn(0f, h)

                        for (lat in listOf(-60, -30, 0, 30, 60)) {
                            val y = latToY(lat.toDouble())
                            drawLine(
                                color = if (lat == 0) OrbitalCyan.copy(alpha = 0.35f) else HudBorderHairline.copy(alpha = 0.45f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = if (lat == 0) 1.6f else 1f
                            )
                        }
                        for (lon in listOf(-120, -60, 0, 60, 120)) {
                            val x = lonToX(lon.toDouble())
                            drawLine(
                                color = if (lon == 0) OrbitalCyan.copy(alpha = 0.28f) else HudBorderHairline.copy(alpha = 0.45f),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1f
                            )
                        }

                        // Country Outlines on 2D World Map
                        com.example.ui.components.WorldCountryOutlines.countries.forEach { country ->
                            val cPath = Path()
                            country.coordinatesLatLon.forEachIndexed { idx, (cLat, cLon) ->
                                val cx2 = lonToX(cLon.toDouble())
                                val cy2 = latToY(cLat.toDouble())
                                if (idx == 0) cPath.moveTo(cx2, cy2) else cPath.lineTo(cx2, cy2)
                            }
                            cPath.close()
                            drawPath(
                                path = cPath,
                                color = Color(0xFF174F7D).copy(alpha = 0.40f)
                            )
                            drawPath(
                                path = cPath,
                                color = OrbitalCyan.copy(alpha = 0.55f),
                                style = Stroke(width = 1.1f)
                            )
                        }

                        val obsX = lonToX(observer.longitudeDeg)
                        val obsY = latToY(observer.latitudeDeg)
                        drawCircle(
                            color = EmeraldLock.copy(alpha = 0.25f),
                            radius = 16f,
                            center = Offset(obsX, obsY)
                        )
                        drawCircle(
                            color = EmeraldLock,
                            radius = 5.5f,
                            center = Offset(obsX, obsY)
                        )

                        val activeTrackSat = selectedSat ?: satellites.firstOrNull()
                        if (activeTrackSat != null) {
                            val trackPath = Path()
                            var prevLon: Double? = null
                            activeTrackSat.groundTrack.forEach { pt ->
                                val x = lonToX(pt.longitudeDeg)
                                val y = latToY(pt.latitudeDeg)
                                if (prevLon == null || abs(pt.longitudeDeg - (prevLon ?: 0.0)) > 90.0) {
                                    trackPath.moveTo(x, y)
                                } else {
                                    trackPath.lineTo(x, y)
                                }
                                prevLon = pt.longitudeDeg
                            }
                            drawPath(
                                path = trackPath,
                                color = SolarAmber.copy(alpha = 0.78f),
                                style = Stroke(width = 2.5f, pathEffect = dashEffect)
                            )

                            val satX = lonToX(activeTrackSat.longitudeDeg)
                            val satY = latToY(activeTrackSat.latitudeDeg)
                            val footprintPx = ((activeTrackSat.footprintRadiusKm / 20000.0) * (h * 0.65)).toFloat().coerceIn(18f, 95f)
                            drawCircle(
                                color = activeTrackSat.orbitLayer.accentColor.copy(alpha = 0.14f),
                                radius = footprintPx,
                                center = Offset(satX, satY)
                            )
                            drawCircle(
                                color = activeTrackSat.orbitLayer.accentColor.copy(alpha = 0.65f),
                                radius = footprintPx,
                                center = Offset(satX, satY),
                                style = Stroke(width = 1.6f)
                            )
                        }

                        satellites.forEach { sat ->
                            val sx = lonToX(sat.longitudeDeg)
                            val sy = latToY(sat.latitudeDeg)
                            blipHitTargets.add(RadarBlipHitTarget(sat.noradId, sx, sy))
                            val isSelected = sat.noradId == activeTrackSat?.noradId
                            drawCircle(
                                color = sat.orbitLayer.accentColor,
                                radius = if (isSelected) 7.5f else 5f,
                                center = Offset(sx, sy)
                            )
                            if (isSelected || sat.isAboveHorizon) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    sat.name.take(9),
                                    sx + 10f,
                                    sy + 5f,
                                    satTextPaint
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "${aboveHorizonSats.size} OVERHEAD NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLock,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianSpace.copy(alpha = 0.85f))
                        .border(1.dp, EmeraldLock.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun OverheadPassRowCard(
    pass: OverheadPassPrediction,
    onClick: () -> Unit
) {
    val timeFormat = remember {
        SimpleDateFormat("hh:mm a 'IST'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        }
    }
    val minutesUntilAos = ((pass.aosTimeMillis - System.currentTimeMillis()) / 60000L).coerceAtLeast(0L)

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, pass.orbitLayer.accentColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pass.orbitLayer.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianSpace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(pass.orbitLayer.accentColor)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pass.satelliteName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StarlightWhite
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Rise ${timeFormat.format(Date(pass.aosTimeMillis))} (${azimuthCardinal(pass.aosAzimuthDeg)}) • Max El ${pass.maxElevationDeg.roundToInt()}° • Duration ${pass.durationSeconds / 60}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryMuted
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (minutesUntilAos == 0L) "NOW" else "IN ${minutesUntilAos}M",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pass.isSunlitVisible) SolarAmber else OrbitalCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = pass.qualityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLock
                )
            }
        }
    }
}

@Composable
private fun LiveSatelliteTelemetryCard(
    rankIndex: Int = 1,
    telemetry: SatelliteLiveTelemetry,
    onTapInspect: () -> Unit,
    onTap3DSim: () -> Unit,
    onTapArSky: () -> Unit,
    onTapSpeakVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val approachUrgencyFraction = remember(telemetry.isAboveHorizon, telemetry.minutesUntilApproach) {
        if (telemetry.isAboveHorizon) {
            1.0f
        } else {
            val mins = telemetry.minutesUntilApproach.coerceIn(1, 360)
            (1.0f - (mins.toFloat() / 360f)).coerceIn(0.14f, 0.92f)
        }
    }
    val approachAccentColor = when {
        telemetry.isAboveHorizon -> EmeraldLock
        telemetry.minutesUntilApproach <= 30 -> SolarAmber
        else -> OrbitalCyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (telemetry.isAboveHorizon) {
                EmeraldLock.copy(alpha = 0.7f)
            } else if (telemetry.minutesUntilApproach <= 30) {
                SolarAmber.copy(alpha = 0.5f)
            } else {
                HudBorderHairline
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onTapInspect() }
            .testTag("satellite_card_${telemetry.noradId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            telemetry.orbitLayer.accentColor.copy(alpha = 0.09f),
                            TacticalSlateSurface,
                            approachAccentColor.copy(alpha = 0.07f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            // Top Next-Approach Chronological Pill Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSpace)
                    .border(1.dp, approachAccentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#$rankIndex",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = ObsidianSpace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(approachAccentColor)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = approachAccentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = telemetry.nextApproachStatusText.ifBlank {
                            if (telemetry.isAboveHorizon) {
                                "Passing Overhead Now • +${telemetry.elevationDeg.roundToInt()}° El"
                            } else {
                                "Approaching in ${telemetry.minutesUntilApproach}m"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = approachAccentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (telemetry.nextApproachMaxElDeg > 0.0) {
                    Text(
                        text = "Peak ${telemetry.nextApproachMaxElDeg.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = StarlightWhite.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Smooth Visual Approach Urgency Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TacticalSlateElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(approachUrgencyFraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(approachAccentColor)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                            .clip(RoundedCornerShape(8.dp))
                            .background(telemetry.orbitLayer.accentColor.copy(alpha = 0.2f))
                            .border(1.dp, telemetry.orbitLayer.accentColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = telemetry.orbitLayer.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = telemetry.orbitLayer.accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = telemetry.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarlightWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NORAD #${telemetry.noradId}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = OrbitalCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " • ${telemetry.category.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Overhead / Horizon Pill
                Surface(
                    color = if (telemetry.isAboveHorizon) EmeraldLock.copy(alpha = 0.18f) else TacticalSlateElevated,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        1.dp,
                        if (telemetry.isAboveHorizon) EmeraldLock else HudBorderHairline
                    )
                ) {
                    Text(
                        text = if (telemetry.isAboveHorizon) {
                            "Overhead +${telemetry.elevationDeg.roundToInt()}°"
                        } else {
                            "El ${telemetry.elevationDeg.roundToInt()}°"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (telemetry.isAboveHorizon) EmeraldLock else TelemetryMuted,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4-Column Live Telemetry Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniTelemetryCell("Altitude", "${telemetry.altitudeKm.roundToInt()} km", Modifier.weight(1f))
                MiniTelemetryCell("Speed", "${"%.2f".format(telemetry.velocityKms)} km/s", Modifier.weight(1f))
                MiniTelemetryCell("Direction", "${telemetry.azimuthDeg.roundToInt()}° ${azimuthCardinal(telemetry.azimuthDeg)}", Modifier.weight(1f))
                MiniTelemetryCell("Latitude", formatLat(telemetry.latitudeDeg), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Friendly Pill Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = OrbitalCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "View Specs",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrbitalCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onTapSpeakVoice() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak", tint = SolarAmber, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Voice", style = MaterialTheme.typography.labelSmall, color = SolarAmber, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onTap3DSim() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = OrbitalCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("3D Globe", style = MaterialTheme.typography.labelSmall, color = StarlightWhite, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, EmeraldLock.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onTapArSky() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = EmeraldLock, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AR Sky", style = MaterialTheme.typography.labelSmall, color = StarlightWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTelemetryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TacticalSlateElevated)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TelemetryMuted,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = StarlightWhite,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

