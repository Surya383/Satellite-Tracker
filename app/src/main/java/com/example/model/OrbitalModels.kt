package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LayerAllBlue
import com.example.ui.theme.LayerGeoMagenta
import com.example.ui.theme.LayerHeoCoral
import com.example.ui.theme.LayerLeoCyan
import com.example.ui.theme.LayerMeoAmber
import com.example.ui.theme.LayerSsoEmerald

/**
 * Represents the orbital altitude layer / shell around Earth.
 * Allows users to filter satellites by orbital layer, inspect layer physics,
 * and visualize concentric 3D altitude shells in the simulator.
 */
enum class OrbitLayer(
    val code: String,
    val title: String,
    val altitudeBandKm: String,
    val minAltKm: Double,
    val maxAltKm: Double,
    val typicalPeriod: String,
    val typicalSpeedKms: String,
    val description: String,
    val primaryUseCases: String,
    val accentColor: Color,
    val defaultCameraZoom: Float,
    val normalizedShellRadius: Float // Relative to Earth radius = 1.0f in 3D non-linear projection
) {
    ALL(
        code = "ALL",
        title = "All Orbital Layers",
        altitudeBandKm = "160 – 40,000 km",
        minAltKm = 160.0,
        maxAltKm = 40000.0,
        typicalPeriod = "90m – 24h",
        typicalSpeedKms = "3.0 – 7.8 km/s",
        description = "Complete multi-shell view from low-altitude space stations up to the geostationary belt and elliptical deep loops.",
        primaryUseCases = "Human spaceflight, Earth imaging, GNSS navigation, global weather & comms",
        accentColor = LayerAllBlue,
        defaultCameraZoom = 1.15f,
        normalizedShellRadius = 1.45f
    ),
    LEO(
        code = "LEO",
        title = "Low Earth Orbit (LEO)",
        altitudeBandKm = "160 – 2,000 km",
        minAltKm = 160.0,
        maxAltKm = 2000.0,
        typicalPeriod = "88 – 127 min",
        typicalSpeedKms = "7.4 – 7.8 km/s",
        description = "Close to Earth's surface with rapid ~90-minute orbital periods and low signal latency. Home to crewed space stations and broadband mega-constellations.",
        primaryUseCases = "Crewed Space Stations (ISS, Tiangong), Space Telescopes, Low-Latency Broadband",
        accentColor = LayerLeoCyan,
        defaultCameraZoom = 1.65f,
        normalizedShellRadius = 1.16f
    ),
    SSO(
        code = "SSO",
        title = "Sun-Synchronous Orbit (SSO)",
        altitudeBandKm = "600 – 850 km",
        minAltKm = 600.0,
        maxAltKm = 850.0,
        typicalPeriod = "96 – 102 min",
        typicalSpeedKms = "7.4 – 7.5 km/s",
        description = "A specialized near-polar LEO shell (~98° inclination) where the orbital plane precesses 1°/day so satellites cross the equator at the exact same local solar time.",
        primaryUseCases = "Consistent-illumination Earth Observation, Climate Science, Polar Weather (NOAA, Landsat, Sentinel)",
        accentColor = LayerSsoEmerald,
        defaultCameraZoom = 1.55f,
        normalizedShellRadius = 1.26f
    ),
    MEO(
        code = "MEO",
        title = "Medium Earth Orbit (MEO)",
        altitudeBandKm = "2,000 – 35,786 km",
        minAltKm = 2000.0,
        maxAltKm = 35000.0,
        typicalPeriod = "2 – 14 hours",
        typicalSpeedKms = "3.6 – 4.9 km/s",
        description = "Mid-altitude region between the inner and outer Van Allen belts. Ideal for global navigation satellite systems (GNSS) with wide continental footprints.",
        primaryUseCases = "Global Positioning & Navigation (GPS III, Galileo, GLONASS), Mid-tier Trunk Comms",
        accentColor = LayerMeoAmber,
        defaultCameraZoom = 1.05f,
        normalizedShellRadius = 1.62f
    ),
    GEO(
        code = "GEO",
        title = "Geostationary Orbit (GEO)",
        altitudeBandKm = "35,786 km",
        minAltKm = 35500.0,
        maxAltKm = 36100.0,
        typicalPeriod = "23h 56m 04s",
        typicalSpeedKms = "3.07 km/s",
        description = "Circular equatorial orbit where a satellite's orbital period matches Earth's sidereal rotation, appearing motionless at a fixed sky point from the ground.",
        primaryUseCases = "Hemispheric Weather Watch (GOES, Himawari), Broadcast TV, High-Throughput Telecom",
        accentColor = LayerGeoMagenta,
        defaultCameraZoom = 0.85f,
        normalizedShellRadius = 2.05f
    ),
    HEO(
        code = "HEO",
        title = "Highly Elliptical Orbit (HEO)",
        altitudeBandKm = "500 – 39,800 km",
        minAltKm = 500.0,
        maxAltKm = 40000.0,
        typicalPeriod = "12 – 24 hours",
        typicalSpeedKms = "1.5 – 10.0 km/s",
        description = "High-eccentricity orbits (such as Molniya at 63.4° inclination or Tundra) that whip quickly past perigee and dwell slowly near apogee over high latitudes.",
        primaryUseCases = "Arctic/High-Latitude Communications, Deep-Space Astrophysics Observatories",
        accentColor = LayerHeoCoral,
        defaultCameraZoom = 0.88f,
        normalizedShellRadius = 2.15f
    );
}

enum class SatelliteCategory(val displayName: String) {
    SPACE_STATION("Crewed Station"),
    TELESCOPE("Space Observatory"),
    WEATHER("Weather & Climate"),
    EARTH_SCIENCE("Earth Imaging"),
    COMMUNICATIONS("Communications"),
    NAVIGATION("GNSS Navigation"),
    HIGH_LATITUDE("High-Latitude / HEO")
}

enum class CatalogSortOption(val label: String) {
    NEXT_APPROACH_ASC("Approaching Next"),
    ELEVATION_DESC("Overhead First"),
    NAME_ASC("Name (A–Z)"),
    NORAD_ID_ASC("NORAD ID (#)"),
    ALTITUDE_ASC("Altitude (km)"),
    SPEED_DESC("Speed (km/s)")
}

data class MissionMilestone(
    val date: String,
    val title: String,
    val detail: String
)

data class ObserverStation(
    val name: String,
    val region: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMeters: Double = 25.0,
    val isGpsDerived: Boolean = false
)

enum class PowerMode(
    val label: String,
    val subtitle: String,
    val tickIntervalMs: Long,
    val oledPureBlack: Boolean
) {
    ECO_SAVER(
        label = "Eco Saver (1 Hz)",
        subtitle = "Minimal CPU wakeups, 60s cached orbital loops, OLED pure-black boost",
        tickIntervalMs = 1500L,
        oledPureBlack = true
    ),
    ADAPTIVE_AUTO(
        label = "Adaptive Auto",
        subtitle = "1 Hz normal tick; auto-throttles when Android Battery Saver or <20% battery",
        tickIntervalMs = 1000L,
        oledPureBlack = true
    ),
    PERFORMANCE(
        label = "High-FPS Telemetry",
        subtitle = "Fast 250ms orbital interpolation for active 3D scrubbing & sky tracking",
        tickIntervalMs = 250L,
        oledPureBlack = false
    )
}

enum class NotificationSoundMode(
    val title: String,
    val subtitle: String
) {
    FEMALE_VOICE_ANNOUNCER(
        title = "Voice Notification",
        subtitle = "Speaks \"ISS approaching in 5 minutes\" (for all tracked satellites) even when app is closed"
    ),
    SYSTEM_DEFAULT(
        title = "System Default Notification Sound",
        subtitle = "Plays standard Android system notification chime without voice speech"
    )
}

data class BatteryTelemetryState(
    val batteryPercent: Int = 88,
    val isCharging: Boolean = false,
    val isSystemPowerSaveMode: Boolean = false,
    val activePowerMode: PowerMode = PowerMode.ADAPTIVE_AUTO,
    val effectiveTickIntervalMs: Long = 1000L,
    val isCameraHardwareActive: Boolean = false,
    val isCompassSensorActive: Boolean = false,
    val isGpsRadioActive: Boolean = false,
    val estimatedHourlyDrainPercent: Float = 1.4f
)

data class SecurityAuditState(
    val isKeystoreHardwareBacked: Boolean = true,
    val encryptionAlgorithm: String = "AES-256-GCM + Context AAD (AndroidKeyStore)",
    val isAadBoundEncryption: Boolean = true,
    val isHmacAlarmAuthActive: Boolean = true,
    val isOrbitalSanityVerified: Boolean = true,
    val isSandboxLockedDown: Boolean = true,
    val isGpsAntiSpoofActive: Boolean = true,
    val verifiedSatelliteCount: Int = 18,
    val totalSatelliteCount: Int = 18,
    val allTleChecksumsValid: Boolean = true,
    val isFlagSecureEnabled: Boolean = false,
    val isAdbBackupDisabled: Boolean = true,
    val isCleartextTrafficBlocked: Boolean = true,
    val keyRotationCount: Int = 1,
    val securityScorePercent: Int = 100,
    val lastAuditTimestampMs: Long = System.currentTimeMillis(),
    val sampleEncryptedObserverPreview: String = ""
)

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val latDeg: Double = 0.0,
    val lonDeg: Double = 0.0,
    val altKm: Double = 0.0,
    val timeOffsetMinutes: Int = 0
)

data class GroundTrackPoint(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val isFuture: Boolean
)

data class SatelliteLiveTelemetry(
    val noradId: Int,
    val name: String,
    val designation: String,
    val category: SatelliteCategory,
    val orbitLayer: OrbitLayer,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeKm: Double,
    val velocityKms: Double,
    val azimuthDeg: Double,
    val elevationDeg: Double,
    val slantRangeKm: Double,
    val rangeRateKms: Double,
    val dopplerShiftHz: Double,
    val isAboveHorizon: Boolean,
    val isSunlit: Boolean,
    val orbitalPeriodMinutes: Double,
    val inclinationDeg: Double,
    val eccentricity: Double,
    val apogeeKm: Double,
    val perigeeKm: Double,
    val revolutionsPerDay: Double,
    val footprintRadiusKm: Double,
    val position3D: Point3D,
    val orbitTrajectory3D: List<Point3D>,
    val groundTrack: List<GroundTrackPoint>,
    val tleChecksumValid: Boolean,
    val nextApproachMillis: Long = 0L,
    val minutesUntilApproach: Int = 0,
    val nextApproachMaxElDeg: Double = 0.0,
    val nextApproachAzimuthDeg: Double = 0.0,
    val nextApproachStatusText: String = ""
)

data class OverheadPassPrediction(
    val noradId: Int,
    val satelliteName: String,
    val orbitLayer: OrbitLayer,
    val category: SatelliteCategory,
    val aosTimeMillis: Long,
    val maxElevationTimeMillis: Long,
    val losTimeMillis: Long,
    val maxElevationDeg: Double,
    val aosAzimuthDeg: Double,
    val losAzimuthDeg: Double,
    val durationSeconds: Long,
    val isSunlitVisible: Boolean,
    val qualityLabel: String
)

object PresetObserverStations {
    val stations = listOf(
        ObserverStation("Bhubaneswar, Odisha", "India • IST (UTC+5:30)", 20.2961, 85.8245, 45.0),
        ObserverStation("Bengaluru (ISRO HQ)", "India • IST (UTC+5:30)", 12.9716, 77.5946, 920.0),
        ObserverStation("New Delhi", "India • IST (UTC+5:30)", 28.6139, 77.2090, 216.0),
        ObserverStation("Mumbai", "India • IST (UTC+5:30)", 19.0760, 72.8777, 14.0),
        ObserverStation("San Francisco", "USA • Pacific", 37.7749, -122.4194, 16.0),
        ObserverStation("New York City", "USA • Atlantic", 40.7128, -74.0060, 10.0),
        ObserverStation("Kennedy Space Center", "Cape Canaveral, USA", 28.5729, -80.6490, 3.0),
        ObserverStation("London (Greenwich)", "United Kingdom", 51.4769, -0.0005, 46.0),
        ObserverStation("Paris", "France", 48.8566, 2.3522, 35.0),
        ObserverStation("Berlin", "Germany", 52.5200, 13.4050, 34.0),
        ObserverStation("Tokyo", "Japan", 35.6762, 139.6503, 40.0),
        ObserverStation("Singapore", "Southeast Asia", 1.3521, 103.8198, 15.0),
        ObserverStation("Sydney Observatory", "Australia", -33.8599, 151.2048, 39.0),
        ObserverStation("Kourou Spaceport", "French Guiana", 5.2360, -52.7686, 12.0),
        ObserverStation("Reykjavik", "Iceland • High Lat", 64.1466, -21.9426, 14.0),
        ObserverStation("Tromso", "Norway • Arctic", 69.6492, 18.9553, 10.0),
        ObserverStation("Atacama (ALMA)", "Chile • Andes", -23.0234, -67.7538, 5058.0),
        ObserverStation("Mauna Kea", "Hawaii, USA", 19.8207, -155.4681, 4205.0),
        ObserverStation("Zurich", "Switzerland", 47.3769, 8.5417, 408.0),
        ObserverStation("Toronto", "Canada", 43.6532, -79.3832, 76.0),
        ObserverStation("Sao Paulo", "Brazil", -23.5505, -46.6333, 760.0),
        ObserverStation("Cairo", "Egypt", 30.0444, 31.2357, 23.0),
        ObserverStation("Nairobi", "Kenya • Equatorial", -1.2921, 36.8219, 1795.0),
        ObserverStation("Seoul", "South Korea", 37.5665, 126.9780, 38.0)
    )
}
