package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.model.OrbitLayer
import com.example.model.Point3D
import com.example.model.SatelliteLiveTelemetry
import com.example.ui.components.OrbitLayerSelectorStrip
import com.example.ui.components.WorldCountryOutlines
import com.example.ui.components.formatLat
import com.example.ui.components.formatLon
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private data class Projected3DSatNode(
    val noradId: Int,
    val screenX: Float,
    val screenY: Float,
    val zDepth: Float
)

@Composable
fun Orbit3DSimulatorScreen(
    uiState: OrbitalUiState,
    onSelectOrbitLayer: (OrbitLayer) -> Unit,
    onSelectSatellite: (Int) -> Unit,
    onToggleShowAllInLayer: () -> Unit,
    onSetTimeWarpMultiplier: (Int) -> Unit,
    onSetManualScrubMinutes: (Float) -> Unit,
    onResetTimeToNow: () -> Unit,
    onInspectSatelliteDossier: (Int) -> Unit
) {
    val selectedSat = uiState.selected3DSatelliteTelemetry
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Camera spherical rotation & zoom state (centered on Bhubaneswar, Odisha / India by default)
    var cameraYawDeg by remember { mutableFloatStateOf(-85.8f) }
    var cameraPitchDeg by remember { mutableFloatStateOf(20.3f) }
    var cameraZoom by remember { mutableFloatStateOf(uiState.selectedOrbitLayer.defaultCameraZoom) }
    var lockCameraToSelectedSat by remember { mutableStateOf(false) }

    // Smoothly adjust camera zoom when user switches Orbital Layer (e.g., LEO vs MEO vs GEO)
    LaunchedEffect(uiState.selectedOrbitLayer) {
        cameraZoom = uiState.selectedOrbitLayer.defaultCameraZoom
    }

    // Center camera on selected satellite when locked or when user picks a new satellite from dropdown
    LaunchedEffect(selectedSat?.noradId, lockCameraToSelectedSat, selectedSat?.longitudeDeg, selectedSat?.latitudeDeg) {
        if (lockCameraToSelectedSat && selectedSat != null) {
            cameraYawDeg = (-selectedSat.longitudeDeg).toFloat()
            cameraPitchDeg = selectedSat.latitudeDeg.toFloat().coerceIn(-75f, 75f)
        }
    }

    val countsByLayer = remember(uiState.liveTelemetryList) {
        val map = mutableMapOf<OrbitLayer, Int>()
        map[OrbitLayer.ALL] = uiState.liveTelemetryList.size
        OrbitLayer.entries.filter { it != OrbitLayer.ALL }.forEach { layer ->
            map[layer] = uiState.liveTelemetryList.count { it.orbitLayer == layer }
        }
        map
    }

    val satellitesInCurrentLayer = remember(uiState.liveTelemetryList, uiState.selectedOrbitLayer) {
        if (uiState.selectedOrbitLayer == OrbitLayer.ALL) {
            uiState.liveTelemetryList
        } else {
            uiState.liveTelemetryList.filter { it.orbitLayer == uiState.selectedOrbitLayer }
        }
    }

    val projectedHitNodes = remember { ArrayList<Projected3DSatNode>() }
    val shellDash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }
    val pastTrackDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }
    val nadirDash = remember { PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f) }
    val countryLabelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#88D8FF")
            textSize = 16f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            alpha = 185
        }
    }
    val timeTickPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#FFB300")
            textSize = 20f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val satNamePaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 23f
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .testTag("orbit_3d_simulator_screen")
    ) {
        // 1. Orbital Layer / Altitude Shell Selector Strip
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "3D Earth & Orbital Shells",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = StarlightWhite
            )
            Text(
                text = "${uiState.selectedOrbitLayer.title} (${uiState.selectedOrbitLayer.altitudeBandKm})",
                style = MaterialTheme.typography.labelSmall,
                color = uiState.selectedOrbitLayer.accentColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        OrbitLayerSelectorStrip(
            selectedLayer = uiState.selectedOrbitLayer,
            satelliteCountsByLayer = countsByLayer,
            onSelectLayer = onSelectOrbitLayer
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Friendly Satellite Selector Card + Camera Angle Presets
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = TacticalSlateSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.5.dp,
                            selectedSat?.orbitLayer?.accentColor ?: OrbitalCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { dropdownExpanded = true }
                            .testTag("satellite_3d_dropdown_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SatelliteAlt,
                                    contentDescription = null,
                                    tint = selectedSat?.orbitLayer?.accentColor ?: OrbitalCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (selectedSat != null) "${selectedSat.name} (#${selectedSat.noradId})" else "Select Satellite",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StarlightWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (selectedSat != null) {
                                        Text(
                                            text = "${selectedSat.orbitLayer.code} • Alt ${selectedSat.altitudeKm.roundToInt()} km • Period ${selectedSat.orbitalPeriodMinutes.roundToInt()}m",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TelemetryMuted
                                        )
                                    }
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Open Satellite Dropdown",
                                tint = StarlightWhite
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier
                            .background(TacticalSlateElevated)
                            .border(1.dp, HudBorderHairline)
                    ) {
                        satellitesInCurrentLayer.forEach { sat ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "[${sat.orbitLayer.code}] ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = sat.orbitLayer.accentColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${sat.name} (#${sat.noradId})",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = StarlightWhite,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "NORAD #${sat.noradId} • Alt ${sat.altitudeKm.roundToInt()} km • ${sat.category.displayName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TelemetryMuted
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectSatellite(sat.noradId)
                                    cameraYawDeg = (-sat.longitudeDeg).toFloat()
                                    cameraPitchDeg = sat.latitudeDeg.toFloat().coerceIn(-70f, 70f)
                                    dropdownExpanded = false
                                },
                                modifier = Modifier.testTag("dropdown_sat_item_${sat.noradId}")
                            )
                        }
                    }
                }

                // Lock Camera Button
                FilterChip(
                    selected = lockCameraToSelectedSat,
                    onClick = { lockCameraToSelectedSat = !lockCameraToSelectedSat },
                    shape = RoundedCornerShape(50),
                    label = { Text("Follow", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Lock Camera",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OrbitalCyan.copy(alpha = 0.25f),
                        selectedLabelColor = OrbitalCyan,
                        containerColor = TacticalSlateSurface,
                        labelColor = TelemetryMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick 3D Camera Angle Presets Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CameraPresetChip(
                    label = "Follow Sat",
                    active = lockCameraToSelectedSat,
                    onClick = {
                        lockCameraToSelectedSat = true
                        if (selectedSat != null) {
                            cameraYawDeg = (-selectedSat.longitudeDeg).toFloat()
                            cameraPitchDeg = selectedSat.latitudeDeg.toFloat().coerceIn(-75f, 75f)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                CameraPresetChip(
                    label = "Equatorial",
                    active = false,
                    onClick = {
                        lockCameraToSelectedSat = false
                        cameraPitchDeg = 5f
                        cameraYawDeg = 0f
                    },
                    modifier = Modifier.weight(1f)
                )
                CameraPresetChip(
                    label = "Polar Top",
                    active = false,
                    onClick = {
                        lockCameraToSelectedSat = false
                        cameraPitchDeg = 80f
                        cameraYawDeg = 0f
                    },
                    modifier = Modifier.weight(1f)
                )
                CameraPresetChip(
                    label = "My Station",
                    active = false,
                    onClick = {
                        lockCameraToSelectedSat = false
                        cameraYawDeg = (-uiState.observerStation.longitudeDeg).toFloat()
                        cameraPitchDeg = uiState.observerStation.latitudeDeg.toFloat().coerceIn(-75f, 75f)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Interactive 3D Orbital Globe Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(355.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF10244A), ObsidianSpace)
                    )
                )
                .border(1.dp, HudBorderHairline, RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (abs(pan.x) > 0.5f || abs(pan.y) > 0.5f) {
                            lockCameraToSelectedSat = false
                            cameraYawDeg = (cameraYawDeg + pan.x * 0.35f) % 360f
                            cameraPitchDeg = (cameraPitchDeg + pan.y * 0.30f).coerceIn(-82f, 82f)
                        }
                        if (zoom != 1f) {
                            cameraZoom = (cameraZoom * zoom).coerceIn(0.65f, 2.45f)
                        }
                    }
                }
                .pointerInput(satellitesInCurrentLayer, selectedSat) {
                    detectTapGestures { tapOffset ->
                        val hit = projectedHitNodes.minByOrNull {
                            hypot(it.screenX - tapOffset.x, it.screenY - tapOffset.y)
                        }
                        if (hit != null && hypot(hit.screenX - tapOffset.x, hit.screenY - tapOffset.y) <= 48.dp.toPx()) {
                            onInspectSatelliteDossier(hit.noradId)
                        }
                    }
                }
                .testTag("orbit_3d_globe_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val baseEarthRadius = minOf(size.width, size.height) * 0.27f * cameraZoom

                val yawRad = cameraYawDeg * (PI / 180.0)
                val pitchRad = cameraPitchDeg * (PI / 180.0)
                val cosYaw = cos(yawRad).toFloat()
                val sinYaw = sin(yawRad).toFloat()
                val cosPitch = cos(pitchRad).toFloat()
                val sinPitch = sin(pitchRad).toFloat()

                fun project3D(x: Float, y: Float, z: Float): Point3D {
                    val x1 = x * cosYaw + z * sinYaw
                    val z1 = -x * sinYaw + z * cosYaw
                    val y2 = y * cosPitch - z1 * sinPitch
                    val z2 = y * sinPitch + z1 * cosPitch
                    return Point3D(
                        x = cx + x1 * baseEarthRadius,
                        y = cy - y2 * baseEarthRadius,
                        z = z2
                    )
                }

                // A. Concentric 3D Orbital Layer Altitude Shells (LEO, SSO, MEO, GEO, HEO)
                val shellLayers = listOf(OrbitLayer.LEO, OrbitLayer.SSO, OrbitLayer.MEO, OrbitLayer.GEO, OrbitLayer.HEO)
                shellLayers.forEach { shell ->
                    val isHighlighted = uiState.selectedOrbitLayer == shell ||
                        (uiState.selectedOrbitLayer == OrbitLayer.ALL && selectedSat?.orbitLayer == shell)
                    val shellPath = Path()
                    val shellR = shell.normalizedShellRadius
                    val steps = 60
                    for (i in 0..steps) {
                        val angle = (i.toDouble() / steps) * 2.0 * PI
                        val sx = (shellR * cos(angle)).toFloat()
                        val sz = (shellR * sin(angle)).toFloat()
                        val sy = if (shell == OrbitLayer.SSO) (shellR * 0.35f * sin(angle * 2)).toFloat() else 0f
                        val proj = project3D(sx, sy, sz)
                        if (i == 0) shellPath.moveTo(proj.x, proj.y) else shellPath.lineTo(proj.x, proj.y)
                    }
                    drawPath(
                        path = shellPath,
                        color = shell.accentColor.copy(alpha = if (isHighlighted) 0.48f else 0.14f),
                        style = Stroke(
                            width = if (isHighlighted) 2.5f else 1.2f,
                            pathEffect = shellDash
                        )
                    )
                }

                // B. 3D Earth Sphere & Atmospheric Limb Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            OrbitalCyan.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = baseEarthRadius * 1.22f
                    ),
                    radius = baseEarthRadius * 1.22f,
                    center = Offset(cx, cy)
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A437A),
                            Color(0xFF0B2246),
                            Color(0xFF050F24)
                        ),
                        center = Offset(cx - baseEarthRadius * 0.25f, cy - baseEarthRadius * 0.25f),
                        radius = baseEarthRadius * 1.15f
                    ),
                    radius = baseEarthRadius,
                    center = Offset(cx, cy)
                )

                // C. 3D Latitude & Longitude Graticule Wireframe on Earth Sphere
                for (lat in listOf(-60, -30, 0, 30, 60)) {
                    val latR = lat * (PI / 180.0)
                    val yRing = sin(latR).toFloat()
                    val rRing = cos(latR).toFloat()
                    val latPath = Path()
                    var started = false
                    for (lon in -180..180 step 10) {
                        val lonR = lon * (PI / 180.0)
                        val px = rRing * sin(lonR).toFloat()
                        val pz = rRing * cos(lonR).toFloat()
                        val proj = project3D(px, yRing, pz)
                        if (proj.z >= 0f) {
                            if (!started) {
                                latPath.moveTo(proj.x, proj.y)
                                started = true
                            } else {
                                latPath.lineTo(proj.x, proj.y)
                            }
                        } else {
                            started = false
                        }
                    }
                    drawPath(
                        path = latPath,
                        color = if (lat == 0) OrbitalCyan.copy(alpha = 0.35f) else StarlightWhite.copy(alpha = 0.14f),
                        style = Stroke(width = if (lat == 0) 1.6f else 1f)
                    )
                }

                for (lon in -150..180 step 30) {
                    val lonR = lon * (PI / 180.0)
                    val lonPath = Path()
                    var started = false
                    for (lat in -80..80 step 10) {
                        val latR = lat * (PI / 180.0)
                        val px = (cos(latR) * sin(lonR)).toFloat()
                        val py = sin(latR).toFloat()
                        val pz = (cos(latR) * cos(lonR)).toFloat()
                        val proj = project3D(px, py, pz)
                        if (proj.z >= 0f) {
                            if (!started) {
                                lonPath.moveTo(proj.x, proj.y)
                                started = true
                            } else {
                                lonPath.lineTo(proj.x, proj.y)
                            }
                        } else {
                            started = false
                        }
                    }
                    drawPath(
                        path = lonPath,
                        color = StarlightWhite.copy(alpha = 0.13f),
                        style = Stroke(width = 1f)
                    )
                }

                // C2. 3D World Country Outlines & Continental Landmass Polygons on Earth Sphere
                WorldCountryOutlines.countries.forEach { country ->
                    val borderPath = Path()
                    val fillPath = Path()
                    var borderStarted = false
                    var visibleVertices = 0
                    var sumX = 0f
                    var sumY = 0f
                    var sumZ = 0f

                    country.coordinatesLatLon.forEachIndexed { idx, (latDeg, lonDeg) ->
                        val latR = latDeg * (PI / 180.0)
                        val lonR = lonDeg * (PI / 180.0)
                        val px = (cos(latR) * sin(lonR)).toFloat()
                        val py = sin(latR).toFloat()
                        val pz = (cos(latR) * cos(lonR)).toFloat()
                        val proj = project3D(px, py, pz)

                        if (proj.z >= -0.02f) {
                            if (!borderStarted) {
                                borderPath.moveTo(proj.x, proj.y)
                                borderStarted = true
                            } else {
                                borderPath.lineTo(proj.x, proj.y)
                            }
                            if (visibleVertices == 0) {
                                fillPath.moveTo(proj.x, proj.y)
                            } else {
                                fillPath.lineTo(proj.x, proj.y)
                            }
                            visibleVertices++
                            sumX += proj.x
                            sumY += proj.y
                            sumZ += proj.z
                        } else {
                            borderStarted = false
                        }
                    }

                    // Fill landmass when country is on the visible front hemisphere
                    if (visibleVertices >= (country.coordinatesLatLon.size * 0.75f).toInt().coerceAtLeast(3)) {
                        fillPath.close()
                        drawPath(
                            path = fillPath,
                            color = Color(0xFF174F7D).copy(alpha = 0.52f)
                        )
                    }

                    // Crisp 3D Country Outline Border
                    if (visibleVertices >= 2) {
                        drawPath(
                            path = borderPath,
                            color = OrbitalCyan.copy(alpha = 0.78f),
                            style = Stroke(width = 1.6f)
                        )
                    }

                    // Country ISO code label on front-facing countries when visible
                    if (visibleVertices >= 4) {
                        val avgZ = sumZ / visibleVertices
                        if (avgZ > 0.42f && cameraZoom >= 0.85f) {
                            val centerX = sumX / visibleVertices
                            val centerY = sumY / visibleVertices
                            drawContext.canvas.nativeCanvas.drawText(
                                country.isoCode,
                                centerX,
                                centerY + 5f,
                                countryLabelPaint
                            )
                        }
                    }
                }

                // Earth Rim Outline
                drawCircle(
                    color = OrbitalCyan.copy(alpha = 0.55f),
                    radius = baseEarthRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )

                // D. Observer Station Beacon on 3D Globe
                val obsLatRad = uiState.observerStation.latitudeDeg * (PI / 180.0)
                val obsLonRad = uiState.observerStation.longitudeDeg * (PI / 180.0)
                val obsProj = project3D(
                    (cos(obsLatRad) * sin(obsLonRad)).toFloat(),
                    sin(obsLatRad).toFloat(),
                    (cos(obsLatRad) * cos(obsLonRad)).toFloat()
                )
                if (obsProj.z >= -0.1f) {
                    drawCircle(
                        color = EmeraldLock.copy(alpha = 0.35f),
                        radius = 12f,
                        center = Offset(obsProj.x, obsProj.y)
                    )
                    drawCircle(
                        color = EmeraldLock,
                        radius = 5f,
                        center = Offset(obsProj.x, obsProj.y)
                    )
                }

                // E. Selected Satellite Full 3D Trajectory Ribbon (Past Track + Future Trajectory + Time Ticks)
                if (selectedSat != null) {
                    val pastPath = Path()
                    val futurePath = Path()
                    var pastStarted = false
                    var futureStarted = false

                    selectedSat.orbitTrajectory3D.forEach { pt ->
                        val proj = project3D(pt.x, pt.y, pt.z)
                        if (pt.timeOffsetMinutes <= 0) {
                            if (!pastStarted) {
                                pastPath.moveTo(proj.x, proj.y)
                                pastStarted = true
                            } else {
                                pastPath.lineTo(proj.x, proj.y)
                            }
                        }
                        if (pt.timeOffsetMinutes >= 0) {
                            if (!futureStarted) {
                                futurePath.moveTo(proj.x, proj.y)
                                futureStarted = true
                            } else {
                                futurePath.lineTo(proj.x, proj.y)
                            }
                        }

                        // Future trajectory time markers (+15m, +30m, +45m)
                        if (pt.timeOffsetMinutes in listOf(15, 30, 45) && proj.z > -0.3f) {
                            drawCircle(
                                color = SolarAmber,
                                radius = 4f,
                                center = Offset(proj.x, proj.y)
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "+${pt.timeOffsetMinutes}m",
                                proj.x + 8f,
                                proj.y - 6f,
                                timeTickPaint
                            )
                        }
                    }

                    // Past orbital path (dashed cyan)
                    drawPath(
                        path = pastPath,
                        color = selectedSat.orbitLayer.accentColor.copy(alpha = 0.45f),
                        style = Stroke(
                            width = 2.2f,
                            pathEffect = pastTrackDash
                        )
                    )
                    // Future trajectory path (solid bright amber)
                    drawPath(
                        path = futurePath,
                        color = SolarAmber,
                        style = Stroke(width = 3.2f)
                    )
                }

                // F. Render Satellites in Active Orbital Layer & Record Hit Targets
                val visibleSats = if (uiState.showAllLayerSatellitesIn3D) {
                    satellitesInCurrentLayer
                } else {
                    listOfNotNull(selectedSat)
                }

                projectedHitNodes.clear()

                visibleSats.forEach { sat ->
                    val proj = project3D(sat.position3D.x, sat.position3D.y, sat.position3D.z)
                    val isSelected = sat.noradId == selectedSat?.noradId
                    projectedHitNodes.add(Projected3DSatNode(sat.noradId, proj.x, proj.y, proj.z))

                    // Draw nadir line & 3D sub-satellite footprint ring on Earth for selected satellite
                    if (isSelected) {
                        val subLatRad = sat.latitudeDeg * (PI / 180.0)
                        val subLonRad = sat.longitudeDeg * (PI / 180.0)
                        val subProj = project3D(
                            (cos(subLatRad) * sin(subLonRad)).toFloat(),
                            sin(subLatRad).toFloat(),
                            (cos(subLatRad) * cos(subLonRad)).toFloat()
                        )
                        if (subProj.z >= -0.2f) {
                            drawCircle(
                                color = EmeraldLock.copy(alpha = 0.20f),
                                radius = 20f,
                                center = Offset(subProj.x, subProj.y)
                            )
                            drawCircle(
                                color = EmeraldLock.copy(alpha = 0.75f),
                                radius = 20f,
                                center = Offset(subProj.x, subProj.y),
                                style = Stroke(width = 1.6f, pathEffect = nadirDash)
                            )
                        }
                        drawLine(
                            color = sat.orbitLayer.accentColor.copy(alpha = 0.7f),
                            start = Offset(subProj.x, subProj.y),
                            end = Offset(proj.x, proj.y),
                            strokeWidth = 2f,
                            pathEffect = nadirDash
                        )
                        drawCircle(
                            color = sat.orbitLayer.accentColor.copy(alpha = 0.28f),
                            radius = 22f,
                            center = Offset(proj.x, proj.y)
                        )
                        drawCircle(
                            color = StarlightWhite,
                            radius = 9f,
                            center = Offset(proj.x, proj.y)
                        )
                        drawCircle(
                            color = sat.orbitLayer.accentColor,
                            radius = 6.5f,
                            center = Offset(proj.x, proj.y)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "${sat.name} [${sat.orbitLayer.code}]",
                            proj.x + 14f,
                            proj.y + 6f,
                            satNamePaint
                        )
                    } else {
                        val alpha = if (proj.z >= 0f) 0.9f else 0.38f
                        drawCircle(
                            color = sat.orbitLayer.accentColor.copy(alpha = alpha * 0.25f),
                            radius = 12f,
                            center = Offset(proj.x, proj.y)
                        )
                        drawCircle(
                            color = sat.orbitLayer.accentColor.copy(alpha = alpha),
                            radius = 5.5f,
                            center = Offset(proj.x, proj.y)
                        )
                    }
                }
            }

            // Top-left 3D Viewport Legend Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSpace.copy(alpha = 0.78f))
                    .border(1.dp, HudBorderHairline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "SHELL: ${uiState.selectedOrbitLayer.code} • 3D COUNTRY BORDERS • ${"%.1f".format(cameraZoom)}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = uiState.selectedOrbitLayer.accentColor
                )
                Text(
                    text = "Drag to Rotate • Pinch Zoom • Tap Sat for Specs",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryMuted,
                    fontSize = 9.sp
                )
            }

            // Bottom-right toggle for All Layer Satellites vs Single Satellite
            Surface(
                color = ObsidianSpace.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, HudBorderHairline),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleShowAllInLayer() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = OrbitalCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.showAllLayerSatellitesIn3D) {
                            "Showing All ${satellitesInCurrentLayer.size} in ${uiState.selectedOrbitLayer.code}"
                        } else {
                            "Single Satellite Focus"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = StarlightWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Time-Warp & Trajectory Scrubber Dock
        Card(
            colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HudBorderHairline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            tint = SolarAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TIME-WARP & FUTURE TRAJECTORY SCRUBBER",
                            style = MaterialTheme.typography.labelMedium,
                            color = StarlightWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = TacticalSlateElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, OrbitalCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onResetTimeToNow() }
                            .testTag("reset_time_warp_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = OrbitalCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LIVE NOW", style = MaterialTheme.typography.labelSmall, color = OrbitalCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Speed Multiplier Buttons (1x, 10x, 60x, 300x)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 10, 60, 300).forEach { mult ->
                        val active = uiState.timeWarpMultiplier == mult
                        Surface(
                            color = if (active) SolarAmber.copy(alpha = 0.24f) else TacticalSlateElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (active) SolarAmber else HudBorderHairline),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSetTimeWarpMultiplier(mult) }
                                .testTag("time_warp_${mult}x_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (mult == 1) "1x Real" else "${mult}x Warp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) SolarAmber else StarlightWhite,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "-180m",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryMuted
                    )
                    Slider(
                        value = uiState.manualScrubOffsetMinutes,
                        onValueChange = onSetManualScrubMinutes,
                        valueRange = -180f..180f,
                        colors = SliderDefaults.colors(
                            thumbColor = SolarAmber,
                            activeTrackColor = SolarAmber,
                            inactiveTrackColor = TacticalSlateElevated
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "${"%+d".format(uiState.manualScrubOffsetMinutes.roundToInt())}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolarAmber,
                        modifier = Modifier.width(42.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Real-Time Orbital Altitude Profile & Apogee/Perigee Graph Card
        if (selectedSat != null) {
            OrbitalAltitudeProfileGraphCard(
                satellite = selectedSat,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 6. Selected Satellite Live Telemetry & Tap-to-Inspect Dossier Card
        if (selectedSat != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, selectedSat.orbitLayer.accentColor.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onInspectSatelliteDossier(selectedSat.noradId) }
                    .testTag("selected_3d_satellite_telemetry_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedSat.name} • ${selectedSat.orbitLayer.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StarlightWhite
                            )
                            Text(
                                text = "Sub-Point ${formatLat(selectedSat.latitudeDeg)}, ${formatLon(selectedSat.longitudeDeg)} • Alt ${selectedSat.altitudeKm.roundToInt()} km • Speed ${"%.2f".format(selectedSat.velocityKms)} km/s",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrbitalCyan
                            )
                        }
                        Button(
                            onClick = { onInspectSatelliteDossier(selectedSat.noradId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = selectedSat.orbitLayer.accentColor,
                                contentColor = ObsidianSpace
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Specs & History", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPresetChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (active) OrbitalCyan.copy(alpha = 0.22f) else TacticalSlateSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (active) OrbitalCyan else HudBorderHairline),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) OrbitalCyan else StarlightWhite,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OrbitalAltitudeProfileGraphCard(
    satellite: SatelliteLiveTelemetry,
    modifier: Modifier = Modifier
) {
    val pts = satellite.orbitTrajectory3D
    val minAlt = remember(pts) { (pts.minOfOrNull { it.altKm } ?: satellite.perigeeKm) * 0.96 }
    val maxAlt = remember(pts) { (pts.maxOfOrNull { it.altKm } ?: satellite.apogeeKm) * 1.04 }
    val altRange = (maxAlt - minAlt).coerceAtLeast(15.0)
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }

    Card(
        colors = CardDefaults.cardColors(containerColor = TacticalSlateSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorderHairline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("orbital_altitude_profile_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = satellite.orbitLayer.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ORBITAL ALTITUDE PROFILE (-90M TO +90M)",
                        style = MaterialTheme.typography.labelMedium,
                        color = StarlightWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Perigee ${satellite.perigeeKm.roundToInt()} km • Apogee ${satellite.apogeeKm.roundToInt()} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = satellite.orbitLayer.accentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianSpace)
                    .border(1.dp, HudBorderHairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    if (pts.size < 2) return@Canvas

                    // Midline reference
                    drawLine(
                        color = HudBorderHairline,
                        start = Offset(0f, h / 2f),
                        end = Offset(w, h / 2f),
                        strokeWidth = 1f,
                        pathEffect = dash
                    )
                    // Current time vertical marker (T=0)
                    drawLine(
                        color = SolarAmber.copy(alpha = 0.65f),
                        start = Offset(w / 2f, 0f),
                        end = Offset(w / 2f, h),
                        strokeWidth = 1.5f,
                        pathEffect = dash
                    )

                    val curvePath = Path()
                    var nowX = w / 2f
                    var nowY = h / 2f

                    pts.forEachIndexed { idx, pt ->
                        val x = (idx.toFloat() / (pts.size - 1).coerceAtLeast(1)) * w
                        val normY = ((pt.altKm - minAlt) / altRange).toFloat().coerceIn(0f, 1f)
                        val y = h - normY * (h - 14f) - 7f
                        if (idx == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
                        if (pt.timeOffsetMinutes == 0) {
                            nowX = x
                            nowY = y
                        }
                    }

                    drawPath(
                        path = curvePath,
                        color = satellite.orbitLayer.accentColor,
                        style = Stroke(width = 2.6f)
                    )

                    drawCircle(
                        color = SolarAmber,
                        radius = 6f,
                        center = Offset(nowX, nowY)
                    )
                }
            }
        }
    }
}
