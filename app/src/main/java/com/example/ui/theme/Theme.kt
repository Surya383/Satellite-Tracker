package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AetherOrbitDarkColorScheme = darkColorScheme(
    primary = OrbitalCyan,
    onPrimary = ObsidianSpace,
    primaryContainer = OrbitalCyanDim,
    onPrimaryContainer = StarlightWhite,
    secondary = SolarAmber,
    onSecondary = ObsidianSpace,
    secondaryContainer = Color(0xFF3B2B06),
    onSecondaryContainer = SolarAmber,
    tertiary = EmeraldLock,
    onTertiary = ObsidianSpace,
    background = ObsidianSpace,
    onBackground = StarlightWhite,
    surface = TacticalSlateSurface,
    onSurface = StarlightWhite,
    surfaceVariant = TacticalSlateElevated,
    onSurfaceVariant = TelemetryMuted,
    outline = HudBorderHairline,
    error = CoralAlert,
    onError = ObsidianSpace
)

private val AetherOrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun MyApplicationTheme(
    oledPowerSaver: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = if (oledPowerSaver) {
        AetherOrbitDarkColorScheme.copy(
            background = ObsidianSpace,
            surface = TacticalSlateSurface
        )
    } else {
        AetherOrbitDarkColorScheme.copy(
            background = DeepSpaceBackground,
            surface = TacticalSlateElevated
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = AetherOrbitShapes,
        content = content
    )
}
