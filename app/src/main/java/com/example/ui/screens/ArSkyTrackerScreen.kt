package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.OrbitLayer
import com.example.security.DeviceSkyOrientation
import com.example.ui.components.OrbitLayerSelectorStrip
import com.example.ui.components.azimuthCardinal
import com.example.ui.theme.CoralAlert
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
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private data class ArReticleHitTarget(
    val noradId: Int,
    val screenX: Float,
    val screenY: Float
)

@Composable
fun ArSkyTrackerScreen(
    uiState: OrbitalUiState,
    orientationFlow: Flow<DeviceSkyOrientation>,
    onSelectOrbitLayer: (OrbitLayer) -> Unit,
    onSelectTargetSatellite: (Int) -> Unit = {},
    onInspectSatelliteDossier: (Int) -> Unit,
    onSpeakSatelliteBriefing: (Int) -> Unit,
    onUpdateArHardwareStatus: (cameraActive: Boolean, compassActive: Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var useLiveCameraFeed by remember { mutableStateOf(hasCameraPermission) }
    var nightVisionRedMode by remember { mutableStateOf(false) }

    // Stabilized Target Lock keeps the cross-point locked on the target satellite by default
    // so compass/emulator noise never makes the cross-point jump.
    var stabilizedTargetLock by remember { mutableStateOf(true) }
    var useCompassSensor by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        useLiveCameraFeed = granted
    }

    val sensorOrientation by orientationFlow.collectAsStateWithLifecycle(
        initialValue = DeviceSkyOrientation(180f, 35f, 0f, hasHardwareSensor = false)
    )

    val lockedTargetSat = uiState.selected3DSatelliteTelemetry

    // Manual pan state in degrees
    var manualLookAzimuth by remember {
        mutableFloatStateOf(lockedTargetSat?.azimuthDeg?.toFloat() ?: 180f)
    }
    var manualLookElevation by remember {
        mutableFloatStateOf(lockedTargetSat?.elevationDeg?.toFloat()?.coerceIn(-45f, 88f) ?: 35f)
    }

    // When target lock is enabled or user switches target satellite, smoothly align look direction
    LaunchedEffect(lockedTargetSat?.noradId, stabilizedTargetLock) {
        if (stabilizedTargetLock && lockedTargetSat != null) {
            manualLookAzimuth = lockedTargetSat.azimuthDeg.toFloat()
            manualLookElevation = lockedTargetSat.elevationDeg.toFloat().coerceIn(-45f, 88f)
        }
    }

    val rawTargetAzimuth = when {
        stabilizedTargetLock && lockedTargetSat != null -> lockedTargetSat.azimuthDeg.toFloat()
        useCompassSensor && sensorOrientation.hasHardwareSensor ->
            ((sensorOrientation.azimuthDeg + manualLookAzimuth) % 360f + 360f) % 360f
        else -> ((manualLookAzimuth % 360f) + 360f) % 360f
    }

    val rawTargetElevation = when {
        stabilizedTargetLock && lockedTargetSat != null ->
            lockedTargetSat.elevationDeg.toFloat().coerceIn(-45f, 88f)
        useCompassSensor && sensorOrientation.hasHardwareSensor ->
            (sensorOrientation.pitchElevationDeg + manualLookElevation).coerceIn(-45f, 88f)
        else -> manualLookElevation.coerceIn(-45f, 88f)
    }

    // Continuous unwrapped azimuth for smooth 0°/360° interpolation without wrap-around jumps
    var continuousAzimuthTarget by remember { mutableFloatStateOf(rawTargetAzimuth) }
    LaunchedEffect(rawTargetAzimuth) {
        val delta = angularDeltaDeg(rawTargetAzimuth, ((continuousAzimuthTarget % 360f) + 360f) % 360f)
        if (abs(delta) > 0.15f) {
            continuousAzimuthTarget += delta
        }
    }

    val animatedContinuousAzimuth by animateFloatAsState(
        targetValue = continuousAzimuthTarget,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "ar_smooth_azimuth"
    )
    val effectiveLookElevation by animateFloatAsState(
        targetValue = rawTargetElevation,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "ar_smooth_elevation"
    )
    val effectiveLookAzimuth = ((animatedContinuousAzimuth % 360f) + 360f) % 360f

    // Report active hardware state to Battery Governor and release immediately on tab exit
    LaunchedEffect(useLiveCameraFeed, hasCameraPermission, useCompassSensor, sensorOrientation.hasHardwareSensor) {
        onUpdateArHardwareStatus(
            useLiveCameraFeed && hasCameraPermission,
            useCompassSensor && sensorOrientation.hasHardwareSensor
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onUpdateArHardwareStatus(false, false)
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

    val satellitesInSkyFilter = remember(uiState.liveTelemetryList, uiState.selectedOrbitLayer) {
        if (uiState.selectedOrbitLayer == OrbitLayer.ALL) {
            uiState.liveTelemetryList
        } else {
            uiState.liveTelemetryList.filter { it.orbitLayer == uiState.selectedOrbitLayer }
        }
    }

    // Plain non-state list holder so updating hit targets in Canvas onDraw NEVER triggers recomposition
    val reticleHitTargets = remember { ArrayList<ArReticleHitTarget>() }

    val gridDash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }
    val trackDash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) }
    val guideDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
    val gridPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#8FA3C7")
            textSize = 22f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val cardinalPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#00E5FF")
            textSize = 28f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val satLabelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val satSubPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#00E5FF")
            textSize = 20f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val primaryHudAccent = if (nightVisionRedMode) CoralAlert else OrbitalCyan

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .testTag("ar_sky_tracker_screen")
    ) {
        // 1. Orbital Layer Filter Strip for AR Sky Overlay
        OrbitLayerSelectorStrip(
            selectedLayer = uiState.selectedOrbitLayer,
            satelliteCountsByLayer = countsByLayer,
            onSelectLayer = onSelectOrbitLayer
        )

        // 2. Quick Target Switcher Strip (Tap any satellite to lock the AR crosshair onto it)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LOCK TARGET:",
                style = MaterialTheme.typography.labelSmall,
                color = TelemetryMuted,
                fontWeight = FontWeight.Bold
            )
            satellitesInSkyFilter.forEach { sat ->
                val isSelected = sat.noradId == lockedTargetSat?.noradId
                Surface(
                    color = if (isSelected) sat.orbitLayer.accentColor.copy(alpha = 0.24f) else TacticalSlateSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) sat.orbitLayer.accentColor else HudBorderHairline
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onSelectTargetSatellite(sat.noradId)
                            stabilizedTargetLock = true
                            manualLookAzimuth = sat.azimuthDeg.toFloat()
                            manualLookElevation = sat.elevationDeg.toFloat().coerceIn(-45f, 88f)
                        }
                        .testTag("ar_quick_target_chip_${sat.noradId}")
                ) {
                    Text(
                        text = "${sat.name.substringBefore(" ")} #${sat.noradId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) sat.orbitLayer.accentColor else StarlightWhite,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 3. Full-Bleed Stabilized AR Night-Sky Viewfinder + HUD Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    1.dp,
                    if (nightVisionRedMode) CoralAlert.copy(alpha = 0.65f) else HudBorderHairline,
                    RoundedCornerShape(18.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Dragging switches to smooth manual pan and unlocks auto-center
                        if (stabilizedTargetLock) {
                            stabilizedTargetLock = false
                            useCompassSensor = false
                        }
                        manualLookAzimuth = ((manualLookAzimuth - dragAmount.x * 0.18f) % 360f + 360f) % 360f
                        manualLookElevation = (manualLookElevation + dragAmount.y * 0.16f).coerceIn(-45f, 88f)
                    }
                }
                .pointerInput(satellitesInSkyFilter) {
                    detectTapGestures { tapOffset ->
                        val hit = reticleHitTargets.minByOrNull {
                            hypot(it.screenX - tapOffset.x, it.screenY - tapOffset.y)
                        }
                        if (hit != null && hypot(hit.screenX - tapOffset.x, hit.screenY - tapOffset.y) <= 52.dp.toPx()) {
                            onInspectSatelliteDossier(hit.noradId)
                        }
                    }
                }
                .testTag("ar_sky_viewfinder_box")
        ) {
            // Optional Live CameraX Preview
            if (useLiveCameraFeed && hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview
                                )
                            } catch (_: Exception) {
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        try {
                            ProcessCameraProvider.getInstance(view.context).get().unbindAll()
                        } catch (_: Exception) {
                        }
                    }
                )
            }

            // Virtual Starfield + Stabilized Augmented Reality Sky Tracking Canvas Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val fovHorizDeg = 68f
                val fovVertDeg = 56f

                cardinalPaint.color = if (nightVisionRedMode) {
                    android.graphics.Color.parseColor("#FF5252")
                } else {
                    android.graphics.Color.parseColor("#00E5FF")
                }
                satSubPaint.color = cardinalPaint.color

                if (!useLiveCameraFeed || !hasCameraPermission) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = if (nightVisionRedMode) {
                                listOf(Color(0xFF0A0204), Color(0xFF1A0508), Color(0xFF26070C))
                            } else {
                                listOf(Color(0xFF030712), Color(0xFF09152B), Color(0xFF0E2240))
                            }
                        )
                    )
                    // Procedural starfield background
                    for (starIdx in 0 until 65) {
                        val starAz = (starIdx * 57.3f) % 360f
                        val starEl = ((starIdx * 31.7f) % 85f) + 2f
                        val dAz = angularDeltaDeg(starAz, effectiveLookAzimuth)
                        val dEl = starEl - effectiveLookElevation
                        if (abs(dAz) <= fovHorizDeg / 2f + 10f && abs(dEl) <= fovVertDeg / 2f + 10f) {
                            val sx = cx + (dAz / (fovHorizDeg / 2f)) * (w / 2f)
                            val sy = cy - (dEl / (fovVertDeg / 2f)) * (h / 2f)
                            val starAlpha = if (starIdx % 4 == 0) 0.75f else 0.35f
                            drawCircle(
                                color = (if (nightVisionRedMode) CoralAlert else StarlightWhite).copy(alpha = starAlpha),
                                radius = if (starIdx % 5 == 0) 2.6f else 1.5f,
                                center = Offset(sx, sy)
                            )
                        }
                    }
                } else {
                    drawRect(color = ObsidianSpace.copy(alpha = 0.28f))
                }

                // 1. Sliding Top Compass Tape HUD (every 15° tick + Cardinal Markers)
                val tapeY = 128f
                drawLine(
                    color = primaryHudAccent.copy(alpha = 0.45f),
                    start = Offset(32f, tapeY),
                    end = Offset(w - 32f, tapeY),
                    strokeWidth = 1.5f
                )
                for (azStep in 0 until 360 step 15) {
                    val dAz = angularDeltaDeg(azStep.toFloat(), effectiveLookAzimuth)
                    if (abs(dAz) <= fovHorizDeg / 2f) {
                        val tx = cx + (dAz / (fovHorizDeg / 2f)) * (w / 2f - 32f)
                        val isCardinal = azStep % 45 == 0
                        drawLine(
                            color = primaryHudAccent.copy(alpha = if (isCardinal) 0.9f else 0.45f),
                            start = Offset(tx, tapeY - if (isCardinal) 10f else 5f),
                            end = Offset(tx, tapeY + if (isCardinal) 10f else 5f),
                            strokeWidth = if (isCardinal) 2.2f else 1.2f
                        )
                    }
                }

                // 2. Elevation Grid Lines (-15°, 0° Horizon, +30°, +60°)
                for (elevLine in listOf(-15, 0, 15, 30, 45, 60, 75)) {
                    val dEl = elevLine - effectiveLookElevation
                    if (abs(dEl) <= fovVertDeg / 2f + 8f) {
                        val lineY = cy - (dEl / (fovVertDeg / 2f)) * (h / 2f)
                        val isHorizon = elevLine == 0
                        drawLine(
                            color = if (isHorizon) {
                                (if (nightVisionRedMode) CoralAlert else EmeraldLock).copy(alpha = 0.75f)
                            } else {
                                HudBorderHairline.copy(alpha = 0.55f)
                            },
                            start = Offset(0f, lineY),
                            end = Offset(w, lineY),
                            strokeWidth = if (isHorizon) 2.6f else 1.2f,
                            pathEffect = if (isHorizon) null else gridDash
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            if (isHorizon) "HORIZON 0°" else "${elevLine}° EL",
                            18f,
                            lineY - 8f,
                            gridPaint
                        )
                    }
                }

                // 3. Cardinal & Intercardinal Compass Markers along the Horizon
                val cardinals = listOf(
                    0f to "N",
                    45f to "NE",
                    90f to "E",
                    135f to "SE",
                    180f to "S",
                    225f to "SW",
                    270f to "W",
                    315f to "NW"
                )
                val horizonY = (cy - ((0f - effectiveLookElevation) / (fovVertDeg / 2f)) * (h / 2f))
                    .coerceIn(150f, h - 45f)

                cardinals.forEach { (cAz, label) ->
                    val dAz = angularDeltaDeg(cAz, effectiveLookAzimuth)
                    if (abs(dAz) <= fovHorizDeg / 2f + 5f) {
                        val xPos = cx + (dAz / (fovHorizDeg / 2f)) * (w / 2f)
                        drawCircle(
                            color = primaryHudAccent,
                            radius = 5f,
                            center = Offset(xPos, horizonY)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            xPos,
                            horizonY - 14f,
                            cardinalPaint
                        )
                    }
                }

                // 4. Project Satellites & Future Sky Trajectories into AR Viewport
                reticleHitTargets.clear()

                satellitesInSkyFilter.forEach { sat ->
                    val dAz = angularDeltaDeg(sat.azimuthDeg.toFloat(), effectiveLookAzimuth)
                    val dEl = sat.elevationDeg.toFloat() - effectiveLookElevation

                    if (abs(dAz) <= fovHorizDeg / 2f + 14f &&
                        abs(dEl) <= fovVertDeg / 2f + 14f
                    ) {
                        val sx = cx + (dAz / (fovHorizDeg / 2f)) * (w / 2f)
                        val sy = cy - (dEl / (fovVertDeg / 2f)) * (h / 2f)
                        reticleHitTargets.add(ArReticleHitTarget(sat.noradId, sx, sy))

                        val isLocked = sat.noradId == lockedTargetSat?.noradId
                        val color = if (nightVisionRedMode) CoralAlert else sat.orbitLayer.accentColor

                        // Draw projected future sky trajectory vector arc across the sky
                        val skyTrackPath = Path()
                        skyTrackPath.moveTo(sx, sy)
                        val headingRad = ((sat.inclinationDeg - 20.0) * PI / 180.0).toFloat()
                        val trackLen = if (sat.orbitLayer == OrbitLayer.GEO) 12f else 95f
                        val endX = sx + cos(headingRad) * trackLen
                        val endY = sy - sin(headingRad) * trackLen
                        skyTrackPath.lineTo(endX, endY)
                        drawPath(
                            path = skyTrackPath,
                            color = color.copy(alpha = 0.75f),
                            style = Stroke(
                                width = if (isLocked) 3.5f else 2.2f,
                                pathEffect = trackDash
                            )
                        )

                        // Draw Stabilized Tactical Target Reticle & Corner Brackets
                        val reticleR = if (isLocked) 30f else 20f
                        drawCircle(
                            color = color.copy(alpha = if (isLocked) 0.26f else 0.18f),
                            radius = reticleR * 1.35f,
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = color,
                            radius = reticleR,
                            center = Offset(sx, sy),
                            style = Stroke(width = if (isLocked) 3.2f else 2f)
                        )
                        if (isLocked) {
                            // Stabilized precision cross-point ticks inside target lock ring
                            drawLine(
                                color = SolarAmber,
                                start = Offset(sx - reticleR - 8f, sy),
                                end = Offset(sx - reticleR + 6f, sy),
                                strokeWidth = 2.4f
                            )
                            drawLine(
                                color = SolarAmber,
                                start = Offset(sx + reticleR - 6f, sy),
                                end = Offset(sx + reticleR + 8f, sy),
                                strokeWidth = 2.4f
                            )
                            drawLine(
                                color = SolarAmber,
                                start = Offset(sx, sy - reticleR - 8f),
                                end = Offset(sx, sy - reticleR + 6f),
                                strokeWidth = 2.4f
                            )
                            drawLine(
                                color = SolarAmber,
                                start = Offset(sx, sy + reticleR - 6f),
                                end = Offset(sx, sy + reticleR + 8f),
                                strokeWidth = 2.4f
                            )
                        }
                        drawCircle(
                            color = StarlightWhite,
                            radius = 5f,
                            center = Offset(sx, sy)
                        )

                        drawContext.canvas.nativeCanvas.apply {
                            drawText(
                                "${sat.name} (#${sat.noradId})",
                                sx + reticleR + 12f,
                                sy - 4f,
                                satLabelPaint
                            )
                            drawText(
                                "AZ ${sat.azimuthDeg.roundToInt()}° • EL ${sat.elevationDeg.roundToInt()}° • ${sat.altitudeKm.roundToInt()}km",
                                sx + reticleR + 12f,
                                sy + 20f,
                                satSubPaint
                            )
                        }
                    }
                }

                // 5. Center Boresight HUD Cross-Point & Smooth Off-Screen Target Pointer
                drawCircle(
                    color = primaryHudAccent.copy(alpha = 0.65f),
                    radius = 36f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.8f)
                )
                drawLine(primaryHudAccent, Offset(cx - 16f, cy), Offset(cx + 16f, cy), strokeWidth = 2f)
                drawLine(primaryHudAccent, Offset(cx, cy - 16f), Offset(cx, cy + 16f), strokeWidth = 2f)

                if (lockedTargetSat != null) {
                    val dAz = angularDeltaDeg(lockedTargetSat.azimuthDeg.toFloat(), effectiveLookAzimuth)
                    val dEl = lockedTargetSat.elevationDeg.toFloat() - effectiveLookElevation
                    val angularDist = hypot(dAz, dEl)

                    // Only show the directional guide pointer when panning away from the target (> 8 degrees)
                    if (angularDist > 8f) {
                        val angle = atan2(-dEl, dAz)
                        val guideR = minOf(w, h) * 0.33f
                        val gx = cx + cos(angle) * guideR
                        val gy = cy + sin(angle) * guideR
                        drawLine(
                            color = SolarAmber.copy(alpha = 0.78f),
                            start = Offset(cx + cos(angle) * 40f, cy + sin(angle) * 40f),
                            end = Offset(gx, gy),
                            strokeWidth = 2.8f,
                            pathEffect = guideDash
                        )
                        drawCircle(
                            color = SolarAmber,
                            radius = 9f,
                            center = Offset(gx, gy)
                        )
                    }
                }
            }

            // Top HUD Bar inside AR Viewfinder (Live Compass Heading + Stabilized Lock + Compass/Camera Toggles)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSpace.copy(alpha = 0.86f))
                        .border(1.dp, HudBorderHairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "AZ ${effectiveLookAzimuth.roundToInt()}° (${azimuthCardinal(effectiveLookAzimuth.toDouble())}) • EL ${"%+d".format(effectiveLookElevation.roundToInt())}°",
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryHudAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            stabilizedTargetLock -> "STABILIZED TARGET LOCK • Drag to Pan"
                            useCompassSensor && sensorOrientation.hasHardwareSensor -> "Gyro/Compass + Deadband Filter"
                            else -> "Manual Touch Sky Pan (Zero Jitter)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stabilizedTargetLock) EmeraldLock else TelemetryMuted,
                        fontSize = 9.sp
                    )
                }

                // Stabilized Lock, Gyro/Compass, Night Red Vision & Camera Toggle Chips
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(
                        selected = stabilizedTargetLock,
                        onClick = {
                            stabilizedTargetLock = !stabilizedTargetLock
                            if (stabilizedTargetLock && lockedTargetSat != null) {
                                manualLookAzimuth = lockedTargetSat.azimuthDeg.toFloat()
                                manualLookElevation = lockedTargetSat.elevationDeg.toFloat().coerceIn(-45f, 88f)
                            }
                        },
                        label = {
                            Text(
                                text = if (stabilizedTargetLock) "Locked" else "Free Pan",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = "Toggle Stabilized Target Lock",
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldLock.copy(alpha = 0.25f),
                            selectedLabelColor = EmeraldLock,
                            containerColor = ObsidianSpace.copy(alpha = 0.86f),
                            labelColor = TelemetryMuted
                        ),
                        modifier = Modifier.testTag("ar_stabilized_lock_chip")
                    )

                    if (sensorOrientation.hasHardwareSensor) {
                        FilterChip(
                            selected = useCompassSensor && !stabilizedTargetLock,
                            onClick = {
                                useCompassSensor = !useCompassSensor
                                if (useCompassSensor) {
                                    stabilizedTargetLock = false
                                    manualLookAzimuth = 0f
                                    manualLookElevation = 0f
                                }
                            },
                            label = {
                                Text(
                                    text = "Gyro",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = "Toggle Gyro Compass",
                                    modifier = Modifier.size(13.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrbitalCyan.copy(alpha = 0.25f),
                                selectedLabelColor = OrbitalCyan,
                                containerColor = ObsidianSpace.copy(alpha = 0.86f),
                                labelColor = TelemetryMuted
                            )
                        )
                    }

                    FilterChip(
                        selected = nightVisionRedMode,
                        onClick = { nightVisionRedMode = !nightVisionRedMode },
                        label = {
                            Text(
                                text = if (nightVisionRedMode) "Red" else "Cyan",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Toggle Night Vision HUD",
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralAlert.copy(alpha = 0.25f),
                            selectedLabelColor = CoralAlert,
                            containerColor = ObsidianSpace.copy(alpha = 0.86f),
                            labelColor = OrbitalCyan
                        )
                    )

                    FilterChip(
                        selected = useLiveCameraFeed && hasCameraPermission,
                        onClick = {
                            if (!hasCameraPermission) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                useLiveCameraFeed = !useLiveCameraFeed
                            }
                        },
                        label = {
                            Text(
                                text = if (useLiveCameraFeed && hasCameraPermission) "Cam" else "Sky",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (useLiveCameraFeed && hasCameraPermission) {
                                    Icons.Default.CameraAlt
                                } else {
                                    Icons.Default.BatterySaver
                                },
                                contentDescription = "Toggle AR Camera",
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrbitalCyan.copy(alpha = 0.25f),
                            selectedLabelColor = StarlightWhite,
                            containerColor = ObsidianSpace.copy(alpha = 0.86f),
                            labelColor = EmeraldLock
                        ),
                        modifier = Modifier.testTag("ar_camera_toggle_chip")
                    )
                }
            }

            // Bottom Target Lock, Voice Briefing & Stabilized Re-Center Bar inside AR Viewfinder
            if (lockedTargetSat != null) {
                Surface(
                    color = ObsidianSpace.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, lockedTargetSat.orbitLayer.accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onInspectSatelliteDossier(lockedTargetSat.noradId) }
                        .testTag("ar_locked_target_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TARGET LOCK: ${lockedTargetSat.name} (#${lockedTargetSat.noradId})",
                                style = MaterialTheme.typography.labelMedium,
                                color = StarlightWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Az ${lockedTargetSat.azimuthDeg.roundToInt()}° (${azimuthCardinal(lockedTargetSat.azimuthDeg)}) • El ${lockedTargetSat.elevationDeg.roundToInt()}° • Alt ${lockedTargetSat.altitudeKm.roundToInt()} km",
                                style = MaterialTheme.typography.labelSmall,
                                color = lockedTargetSat.orbitLayer.accentColor
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Speak Female Voice Briefing Button
                            Surface(
                                color = TacticalSlateElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SolarAmber),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSpeakSatelliteBriefing(lockedTargetSat.noradId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Speak Satellite Briefing",
                                        tint = SolarAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "VOICE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SolarAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Stabilized Center & Lock on Target Button
                            Surface(
                                color = if (stabilizedTargetLock) EmeraldLock.copy(alpha = 0.22f) else TacticalSlateElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (stabilizedTargetLock) EmeraldLock else OrbitalCyan),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        stabilizedTargetLock = true
                                        manualLookAzimuth = lockedTargetSat.azimuthDeg.toFloat()
                                        manualLookElevation = lockedTargetSat.elevationDeg.toFloat().coerceIn(-45f, 88f)
                                    }
                                    .testTag("ar_center_on_target_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CenterFocusStrong,
                                        contentDescription = "Lock Crosshair on Satellite",
                                        tint = if (stabilizedTargetLock) EmeraldLock else OrbitalCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (stabilizedTargetLock) "LOCKED" else "AIM SKY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (stabilizedTargetLock) EmeraldLock else OrbitalCyan,
                                        fontWeight = FontWeight.Bold
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

private fun angularDeltaDeg(targetAz: Float, currentAz: Float): Float {
    var diff = (targetAz - currentAz) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}
