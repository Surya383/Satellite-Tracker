package com.example.engine

import com.example.data.SatelliteEntity
import com.example.model.GroundTrackPoint
import com.example.model.ObserverStation
import com.example.model.OverheadPassPrediction
import com.example.model.Point3D
import com.example.model.SatelliteLiveTelemetry
import com.example.security.SecurityVaultManager
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Self-contained on-device Keplerian + J2 secular perturbation orbital mechanics engine.
 *
 * Battery-Efficiency Architecture:
 * - Separates instantaneous position computation (`propagateInstantaneous`) from 3D trajectory
 *   ring & ground-track generation (`CachedTrajectory`), caching the 72-point 3D orbital loop
 *   for 60 seconds unless the user actively scrubs the time-warp slider.
 */
object OrbitalPropagatorEngine {

    private const val MU_EARTH_KM3_S2 = 398600.4418
    private const val EARTH_RADIUS_KM = 6378.137
    private const val J2_EARTH = 0.00108262668
    private const val DEG_TO_RAD = PI / 180.0
    private const val RAD_TO_DEG = 180.0 / PI
    private const val MINUTES_PER_DAY = 1440.0
    private const val SECONDS_PER_DAY = 86400.0
    private const val SPEED_OF_LIGHT_KMS = 299792.458

    data class ParsedTleElements(
        val epochYear: Int,
        val epochDayOfYear: Double,
        val epochMillisUtc: Long,
        val inclinationDeg: Double,
        val raanDeg: Double,
        val eccentricity: Double,
        val argOfPerigeeDeg: Double,
        val meanAnomalyDeg: Double,
        val meanMotionRevPerDay: Double,
        val semiMajorAxisKm: Double,
        val orbitalPeriodMinutes: Double,
        val apogeeKm: Double,
        val perigeeKm: Double,
        val checksumValid: Boolean
    )

    private data class CachedTrajectory(
        val cacheBucketKey: Long,
        val trajectory3D: List<Point3D>,
        val groundTrack: List<GroundTrackPoint>
    )

    private val parsedTleCache = java.util.concurrent.ConcurrentHashMap<String, ParsedTleElements>()
    private val trajectoryCache = java.util.concurrent.ConcurrentHashMap<Int, CachedTrajectory>()

    fun parseTle(line1: String, line2: String): ParsedTleElements {
        val cacheKey = "$line1|$line2"
        parsedTleCache[cacheKey]?.let { return it }

        val l1 = line1.trim()
        val l2 = line2.trim()
        val checksumValid = SecurityVaultManager.verifyTleLineChecksum(l1, 1) &&
            SecurityVaultManager.verifyTleLineChecksum(l2, 2)

        val twoDigitYear = l1.substringSafe(18, 20).toIntOrNull() ?: 26
        val epochYear = if (twoDigitYear >= 57) 1900 + twoDigitYear else 2000 + twoDigitYear
        val epochDay = l1.substringSafe(20, 32).toDoubleOrNull() ?: 290.5

        val inclinationDeg = l2.substringSafe(8, 16).toDoubleOrNull() ?: 51.6
        val raanDeg = l2.substringSafe(17, 25).toDoubleOrNull() ?: 120.0
        val eccRaw = l2.substringSafe(26, 33).trim()
        val eccentricity = ("0.$eccRaw").toDoubleOrNull()?.coerceIn(0.0, 0.95) ?: 0.0005
        val argOfPerigeeDeg = l2.substringSafe(34, 42).toDoubleOrNull() ?: 90.0
        val meanAnomalyDeg = l2.substringSafe(43, 51).toDoubleOrNull() ?: 0.0
        val meanMotion = (l2.substringSafe(52, 63).toDoubleOrNull() ?: 15.49).coerceAtLeast(0.5)

        val nRadPerSec = meanMotion * 2.0 * PI / SECONDS_PER_DAY
        val semiMajorAxisKm = (MU_EARTH_KM3_S2 / (nRadPerSec * nRadPerSec)).pow(1.0 / 3.0)
        val periodMinutes = MINUTES_PER_DAY / meanMotion
        val apogeeKm = (semiMajorAxisKm * (1.0 + eccentricity) - EARTH_RADIUS_KM).coerceAtLeast(120.0)
        val perigeeKm = (semiMajorAxisKm * (1.0 - eccentricity) - EARTH_RADIUS_KM).coerceAtLeast(120.0)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, epochYear)
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val epochMillis = cal.timeInMillis + ((epochDay - 1.0) * SECONDS_PER_DAY * 1000.0).toLong()

        val parsed = ParsedTleElements(
            epochYear = epochYear,
            epochDayOfYear = epochDay,
            epochMillisUtc = epochMillis,
            inclinationDeg = inclinationDeg,
            raanDeg = raanDeg,
            eccentricity = eccentricity,
            argOfPerigeeDeg = argOfPerigeeDeg,
            meanAnomalyDeg = meanAnomalyDeg,
            meanMotionRevPerDay = meanMotion,
            semiMajorAxisKm = semiMajorAxisKm,
            orbitalPeriodMinutes = periodMinutes,
            apogeeKm = apogeeKm,
            perigeeKm = perigeeKm,
            checksumValid = checksumValid
        )
        parsedTleCache[cacheKey] = parsed
        return parsed
    }

    private data class EciGeodeticState(
        val eciX: Double,
        val eciY: Double,
        val eciZ: Double,
        val ecefX: Double,
        val ecefY: Double,
        val ecefZ: Double,
        val latDeg: Double,
        val lonDeg: Double,
        val altKm: Double,
        val velocityKms: Double,
        val point3D: Point3D
    )

    /**
     * Non-linear radial scaling from true altitude (km) to 3D globe radius (where Earth radius = 1.0f)
     * so LEO (400 km), SSO (800 km), MEO (20,200 km), GEO (35,786 km), and HEO (39,800 km)
     * are all visually distinct and interactive without clipping or overlapping Earth's surface.
     */
    fun mapAltitudeKmToNormalizedRadius(altKm: Double): Float {
        val safeAlt = altKm.coerceAtLeast(120.0)
        return when {
            safeAlt <= 1000.0 -> {
                // 120..1000 km -> 1.12f .. 1.28f
                (1.10 + (safeAlt / 1000.0) * 0.18).toFloat()
            }
            safeAlt <= 2500.0 -> {
                // 1000..2500 km -> 1.28f .. 1.38f
                (1.28 + ((safeAlt - 1000.0) / 1500.0) * 0.10).toFloat()
            }
            else -> {
                // 2500..42000 km -> logarithmic progression up to ~2.18f
                val logNorm = ln(safeAlt / 2500.0) / ln(40000.0 / 2500.0)
                (1.38 + logNorm.coerceIn(0.0, 1.15) * 0.76).toFloat()
            }
        }
    }

    private fun propagateStateAtMillis(
        elements: ParsedTleElements,
        timeMillisUtc: Long,
        timeOffsetMinTag: Int = 0
    ): EciGeodeticState {
        val dtMinutes = (timeMillisUtc - elements.epochMillisUtc) / 60000.0
        val incRad = elements.inclinationDeg * DEG_TO_RAD
        val cosI = cos(incRad)
        val sinI = sin(incRad)
        val e = elements.eccentricity
        val a = elements.semiMajorAxisKm
        val p = a * (1.0 - e * e)

        // J2 nodal regression and perigee precession rates (rad/min)
        val nRadPerMin = elements.meanMotionRevPerDay * 2.0 * PI / MINUTES_PER_DAY
        val j2Factor = 1.5 * J2_EARTH * (EARTH_RADIUS_KM / p).pow(2.0) * nRadPerMin
        val raanDotRadPerMin = -j2Factor * cosI
        val argPerigeeDotRadPerMin = 0.5 * j2Factor * (5.0 * cosI * cosI - 1.0)

        val currentRaanRad = (elements.raanDeg * DEG_TO_RAD + raanDotRadPerMin * dtMinutes) % (2.0 * PI)
        val currentArgPerigeeRad = (elements.argOfPerigeeDeg * DEG_TO_RAD + argPerigeeDotRadPerMin * dtMinutes) % (2.0 * PI)
        val meanAnomalyRad = ((elements.meanAnomalyDeg * DEG_TO_RAD + nRadPerMin * dtMinutes) % (2.0 * PI) + 2.0 * PI) % (2.0 * PI)

        // Solve Kepler's Equation M = E - e*sin(E) using 6 Newton-Raphson iterations
        var eccAnomaly = if (e > 0.5) PI else meanAnomalyRad
        for (iter in 0 until 6) {
            val f = eccAnomaly - e * sin(eccAnomaly) - meanAnomalyRad
            val fPrime = 1.0 - e * cos(eccAnomaly)
            eccAnomaly -= f / fPrime
        }

        val sinE = sin(eccAnomaly)
        val cosE = cos(eccAnomaly)
        val trueAnomaly = atan2(sqrt(1.0 - e * e) * sinE, cosE - e)
        val radiusKm = a * (1.0 - e * cosE)
        val uRad = currentArgPerigeeRad + trueAnomaly

        val cosU = cos(uRad)
        val sinU = sin(uRad)
        val cosO = cos(currentRaanRad)
        val sinO = sin(currentRaanRad)

        // ECI position (km)
        val eciX = radiusKm * (cosO * cosU - sinO * sinU * cosI)
        val eciY = radiusKm * (sinO * cosU + cosO * sinU * cosI)
        val eciZ = radiusKm * (sinU * sinI)

        // Vis-viva equation for orbital speed: v = sqrt(mu * (2/r - 1/a))
        val velocityKms = sqrt((MU_EARTH_KM3_S2 * (2.0 / radiusKm - 1.0 / a)).coerceAtLeast(1.0))

        // Greenwich Mean Sidereal Time (GMST) rotation to ECEF
        val gmstRad = computeGmstRad(timeMillisUtc)
        val cosG = cos(gmstRad)
        val sinG = sin(gmstRad)

        val ecefX = eciX * cosG + eciY * sinG
        val ecefY = -eciX * sinG + eciY * cosG
        val ecefZ = eciZ

        val rHoriz = sqrt(ecefX * ecefX + ecefY * ecefY)
        val latDeg = (atan2(ecefZ, rHoriz) * RAD_TO_DEG).coerceIn(-89.9, 89.9)
        val lonDeg = (atan2(ecefY, ecefX) * RAD_TO_DEG)
        val altKm = (radiusKm - EARTH_RADIUS_KM).coerceAtLeast(110.0)

        // 3D Globe Coordinate System (Earth-fixed normalized coordinates so orbits align with continents & observer)
        val normR = mapAltitudeKmToNormalizedRadius(altKm)
        val latRad = latDeg * DEG_TO_RAD
        val lonRad = lonDeg * DEG_TO_RAD
        val x3d = (normR * cos(latRad) * sin(lonRad)).toFloat()
        val y3d = (normR * sin(latRad)).toFloat()
        val z3d = (normR * cos(latRad) * cos(lonRad)).toFloat()

        return EciGeodeticState(
            eciX = eciX,
            eciY = eciY,
            eciZ = eciZ,
            ecefX = ecefX,
            ecefY = ecefY,
            ecefZ = ecefZ,
            latDeg = latDeg,
            lonDeg = lonDeg,
            altKm = altKm,
            velocityKms = velocityKms,
            point3D = Point3D(
                x = x3d,
                y = y3d,
                z = z3d,
                latDeg = latDeg,
                lonDeg = lonDeg,
                altKm = altKm,
                timeOffsetMinutes = timeOffsetMinTag
            )
        )
    }

    private data class CachedNextApproach(
        val cacheBucketKey: Long,
        val observerKey: String,
        val nextApproachMillis: Long,
        val maxElDeg: Double,
        val aosAzimuthDeg: Double,
        val statusText: String
    )

    private val nextApproachCache = java.util.concurrent.ConcurrentHashMap<Int, CachedNextApproach>()

    private fun computeNextApproachForSatellite(
        noradId: Int,
        elements: ParsedTleElements,
        observer: ObserverStation,
        simulationTimeMillis: Long,
        currentTopocentric: TopocentricResult
    ): CachedNextApproach {
        val istFormat = java.text.SimpleDateFormat("hh:mm a 'IST'", java.util.Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val obsKey = "${"%.2f".format(observer.latitudeDeg)}_${"%.2f".format(observer.longitudeDeg)}"
        val cacheBucket = simulationTimeMillis / 60000L

        // 1. If currently above horizon right now, return immediate overhead status
        if (currentTopocentric.elevationDeg > 0.0) {
            val isGeo = elements.meanMotionRevPerDay in 0.95..1.05 && elements.inclinationDeg < 5.0
            val label = if (isGeo) {
                "GEO SKY LOCK • +${currentTopocentric.elevationDeg.roundToInt()}° EL"
            } else {
                "PASSING OVERHEAD NOW • +${currentTopocentric.elevationDeg.roundToInt()}° EL"
            }
            return CachedNextApproach(
                cacheBucketKey = cacheBucket,
                observerKey = obsKey,
                nextApproachMillis = simulationTimeMillis,
                maxElDeg = currentTopocentric.elevationDeg,
                aosAzimuthDeg = currentTopocentric.azimuthDeg,
                statusText = label
            )
        }

        val existing = nextApproachCache[noradId]
        if (existing != null &&
            existing.cacheBucketKey == cacheBucket &&
            existing.observerKey == obsKey &&
            existing.nextApproachMillis > simulationTimeMillis
        ) {
            return existing
        }

        // 2. Scan forward up to 24 hours in 120-second steps to find next horizon rise (> 0°)
        //    or closest orbital approach peak if satellite stays below local horizon.
        val stepMs = 120_000L
        val maxWindowMs = 24L * 3600_000L
        var foundAosMillis = 0L
        var foundAosAz = currentTopocentric.azimuthDeg
        var passPeakEl = -90.0
        var bestSubHorizonMillis = simulationTimeMillis + 3600_000L
        var bestSubHorizonEl = -90.0
        var bestSubHorizonAz = currentTopocentric.azimuthDeg

        var t = simulationTimeMillis + stepMs
        val endT = simulationTimeMillis + maxWindowMs
        var inPass = false

        while (t <= endT) {
            val st = propagateStateAtMillis(elements, t)
            val topo = computeTopocentricAzElRange(st.ecefX, st.ecefY, st.ecefZ, observer)

            if (topo.elevationDeg > bestSubHorizonEl) {
                bestSubHorizonEl = topo.elevationDeg
                bestSubHorizonMillis = t
                bestSubHorizonAz = topo.azimuthDeg
            }

            if (topo.elevationDeg > 0.0) {
                if (!inPass) {
                    inPass = true
                    foundAosMillis = t
                    foundAosAz = topo.azimuthDeg
                    passPeakEl = topo.elevationDeg
                } else if (topo.elevationDeg > passPeakEl) {
                    passPeakEl = topo.elevationDeg
                }
            } else if (inPass) {
                break
            }
            t += stepMs
        }

        val resolvedMillis: Long
        val resolvedPeakEl: Double
        val resolvedAz: Double
        val statusLabel: String

        if (foundAosMillis > 0L) {
            resolvedMillis = foundAosMillis
            resolvedPeakEl = passPeakEl
            resolvedAz = foundAosAz
            val mins = ((foundAosMillis - simulationTimeMillis) / 60000L).coerceAtLeast(1L)
            val timeStr = istFormat.format(java.util.Date(foundAosMillis))
            val durStr = if (mins >= 60L) {
                "${mins / 60L}h ${mins % 60L}m"
            } else {
                "${mins}m"
            }
            statusLabel = "APPROACHING IN $durStr • $timeStr"
        } else {
            // Geostationary or distant shell that stays below local horizon: order by closest approach time
            resolvedMillis = bestSubHorizonMillis + 24L * 3600_000L
            resolvedPeakEl = bestSubHorizonEl
            resolvedAz = bestSubHorizonAz
            val mins = ((bestSubHorizonMillis - simulationTimeMillis) / 60000L).coerceAtLeast(15L)
            val durStr = if (mins >= 60L) "${mins / 60L}h ${mins % 60L}m" else "${mins}m"
            statusLabel = "CLOSEST ORBIT POINT IN $durStr"
        }

        val computed = CachedNextApproach(
            cacheBucketKey = cacheBucket,
            observerKey = obsKey,
            nextApproachMillis = resolvedMillis,
            maxElDeg = resolvedPeakEl,
            aosAzimuthDeg = resolvedAz,
            statusText = statusLabel
        )
        nextApproachCache[noradId] = computed
        return computed
    }

    /**
     * Propagates full live telemetry for a satellite at `simulationTimeMillis` relative to `observer`.
     * Uses a 60-second trajectory cache bucket to keep battery/CPU consumption minimal.
     */
    fun propagateSatellite(
        entity: SatelliteEntity,
        observer: ObserverStation,
        simulationTimeMillis: Long
    ): SatelliteLiveTelemetry {
        val elements = parseTle(entity.tleLine1, entity.tleLine2)
        val current = propagateStateAtMillis(elements, simulationTimeMillis)

        // Topocentric Horizon (Azimuth, Elevation, Range) relative to Observer
        val topocentric = computeTopocentricAzElRange(
            ecefX = current.ecefX,
            ecefY = current.ecefY,
            ecefZ = current.ecefZ,
            observer = observer
        )

        val nextApproach = computeNextApproachForSatellite(
            noradId = entity.noradId,
            elements = elements,
            observer = observer,
            simulationTimeMillis = simulationTimeMillis,
            currentTopocentric = topocentric
        )
        val minsUntilApproach = if (topocentric.elevationDeg > 0.0) {
            0
        } else {
            ((nextApproach.nextApproachMillis - simulationTimeMillis) / 60000L).toInt().coerceAtLeast(1)
        }

        // Compute range-rate (km/s) via a 2-second finite difference for Doppler shift
        val next2s = propagateStateAtMillis(elements, simulationTimeMillis + 2000L)
        val nextTopo = computeTopocentricAzElRange(
            ecefX = next2s.ecefX,
            ecefY = next2s.ecefY,
            ecefZ = next2s.ecefZ,
            observer = observer
        )
        val rangeRateKms = (nextTopo.slantRangeKm - topocentric.slantRangeKm) / 2.0
        // Approximate 437 MHz UHF amateur beacon Doppler shift (Hz)
        val dopplerHz = -rangeRateKms / SPEED_OF_LIGHT_KMS * 437_500_000.0

        // Solar illumination check (cylindrical Earth shadow approximation)
        val isSunlit = computeIsSunlit(current.eciX, current.eciY, current.eciZ, simulationTimeMillis)

        // Footprint horizon radius (km) on Earth's surface: R_e * acos(R_e / (R_e + h))
        val footprintRadiusKm = EARTH_RADIUS_KM * acos(
            (EARTH_RADIUS_KM / (EARTH_RADIUS_KM + current.altKm)).coerceIn(0.01, 1.0)
        )

        // Trajectory cache bucket (60-second granularity for battery optimization)
        val cacheBucket = simulationTimeMillis / 60000L
        val cached = trajectoryCache[entity.noradId]
        val trajectoryAndTrack = if (cached != null && cached.cacheBucketKey == cacheBucket) {
            cached
        } else {
            val periodMin = elements.orbitalPeriodMinutes.coerceIn(85.0, 1440.0)
            val steps = 64
            val trajectory3D = ArrayList<Point3D>(steps + 1)
            val groundTrack = ArrayList<GroundTrackPoint>(steps + 1)

            // Sample from -25% of an orbit to +75% of an orbit so the full 3D ellipse is visible
            for (i in 0..steps) {
                val fraction = (i.toDouble() / steps.toDouble()) - 0.25
                val offsetMin = (fraction * periodMin).roundToInt()
                val sampleMillis = simulationTimeMillis + (fraction * periodMin * 60000.0).toLong()
                val st = propagateStateAtMillis(elements, sampleMillis, offsetMin)
                trajectory3D.add(st.point3D)
                groundTrack.add(
                    GroundTrackPoint(
                        latitudeDeg = st.latDeg,
                        longitudeDeg = st.lonDeg,
                        isFuture = fraction >= 0.0
                    )
                )
            }
            val built = CachedTrajectory(cacheBucket, trajectory3D, groundTrack)
            trajectoryCache[entity.noradId] = built
            built
        }

        return SatelliteLiveTelemetry(
            noradId = entity.noradId,
            name = entity.name,
            designation = entity.designation,
            category = entity.category,
            orbitLayer = entity.orbitLayer,
            latitudeDeg = current.latDeg,
            longitudeDeg = current.lonDeg,
            altitudeKm = current.altKm,
            velocityKms = current.velocityKms,
            azimuthDeg = topocentric.azimuthDeg,
            elevationDeg = topocentric.elevationDeg,
            slantRangeKm = topocentric.slantRangeKm,
            rangeRateKms = rangeRateKms,
            dopplerShiftHz = dopplerHz,
            isAboveHorizon = topocentric.elevationDeg > 0.0,
            isSunlit = isSunlit,
            orbitalPeriodMinutes = elements.orbitalPeriodMinutes,
            inclinationDeg = elements.inclinationDeg,
            eccentricity = elements.eccentricity,
            apogeeKm = elements.apogeeKm,
            perigeeKm = elements.perigeeKm,
            revolutionsPerDay = elements.meanMotionRevPerDay,
            footprintRadiusKm = footprintRadiusKm,
            position3D = current.point3D,
            orbitTrajectory3D = trajectoryAndTrack.trajectory3D,
            groundTrack = trajectoryAndTrack.groundTrack,
            tleChecksumValid = elements.checksumValid,
            nextApproachMillis = nextApproach.nextApproachMillis,
            minutesUntilApproach = minsUntilApproach,
            nextApproachMaxElDeg = nextApproach.maxElDeg,
            nextApproachAzimuthDeg = nextApproach.aosAzimuthDeg,
            nextApproachStatusText = nextApproach.statusText
        )
    }

    /**
     * Predicts upcoming overhead passes over the next `windowHours` for a list of satellites.
     * Uses a coarse 90-second scan with peak refinement for ultra-fast, battery-light execution.
     */
    fun predictUpcomingPasses(
        satellites: List<SatelliteEntity>,
        observer: ObserverStation,
        startTimeMillis: Long,
        windowHours: Int = 18
    ): List<OverheadPassPrediction> {
        val predictions = mutableListOf<OverheadPassPrediction>()
        val endTimeMillis = startTimeMillis + windowHours * 3600_000L
        val stepMillis = 90_000L

        for (sat in satellites) {
            val elements = parseTle(sat.tleLine1, sat.tleLine2)
            // Geostationary satellites stay at fixed elevation; include as continuous sky lock if above horizon
            if (elements.meanMotionRevPerDay in 0.95..1.05 && elements.inclinationDeg < 5.0) {
                val st = propagateStateAtMillis(elements, startTimeMillis)
                val topo = computeTopocentricAzElRange(st.ecefX, st.ecefY, st.ecefZ, observer)
                if (topo.elevationDeg >= 10.0) {
                    predictions.add(
                        OverheadPassPrediction(
                            noradId = sat.noradId,
                            satelliteName = sat.name,
                            orbitLayer = sat.orbitLayer,
                            category = sat.category,
                            aosTimeMillis = startTimeMillis + 15 * 60_000L,
                            maxElevationTimeMillis = startTimeMillis + 45 * 60_000L,
                            losTimeMillis = startTimeMillis + 120 * 60_000L,
                            maxElevationDeg = topo.elevationDeg,
                            aosAzimuthDeg = topo.azimuthDeg,
                            losAzimuthDeg = topo.azimuthDeg,
                            durationSeconds = 7200L,
                            isSunlitVisible = true,
                            qualityLabel = "Stationary GEO Lock"
                        )
                    )
                }
                continue
            }

            var inPass = false
            var aosMillis = 0L
            var aosAz = 0.0
            var maxEl = -90.0
            var maxElMillis = 0L
            var sunlitAtPeak = false

            var t = startTimeMillis
            while (t <= endTimeMillis) {
                val st = propagateStateAtMillis(elements, t)
                val topo = computeTopocentricAzElRange(st.ecefX, st.ecefY, st.ecefZ, observer)

                if (topo.elevationDeg > 5.0) {
                    if (!inPass) {
                        inPass = true
                        aosMillis = t
                        aosAz = topo.azimuthDeg
                        maxEl = topo.elevationDeg
                        maxElMillis = t
                        sunlitAtPeak = computeIsSunlit(st.eciX, st.eciY, st.eciZ, t)
                    } else if (topo.elevationDeg > maxEl) {
                        maxEl = topo.elevationDeg
                        maxElMillis = t
                        sunlitAtPeak = computeIsSunlit(st.eciX, st.eciY, st.eciZ, t)
                    }
                } else if (inPass) {
                    inPass = false
                    val losMillis = t
                    val durationSec = ((losMillis - aosMillis) / 1000L).coerceAtLeast(120L)
                    if (maxEl >= 12.0) {
                        val quality = when {
                            maxEl >= 65.0 && sunlitAtPeak -> "Zenith Prime Pass"
                            maxEl >= 40.0 -> "High-Elevation Pass"
                            else -> "Horizon Grazing Pass"
                        }
                        predictions.add(
                            OverheadPassPrediction(
                                noradId = sat.noradId,
                                satelliteName = sat.name,
                                orbitLayer = sat.orbitLayer,
                                category = sat.category,
                                aosTimeMillis = aosMillis,
                                maxElevationTimeMillis = maxElMillis,
                                losTimeMillis = losMillis,
                                maxElevationDeg = maxEl,
                                aosAzimuthDeg = aosAz,
                                losAzimuthDeg = topo.azimuthDeg,
                                durationSeconds = durationSec,
                                isSunlitVisible = sunlitAtPeak,
                                qualityLabel = quality
                            )
                        )
                    }
                    // Limit to top 3 passes per satellite to keep timeline scannable
                    if (predictions.count { it.noradId == sat.noradId } >= 3) break
                }
                t += stepMillis
            }
        }

        return predictions.sortedBy { it.aosTimeMillis }.take(24)
    }

    private data class TopocentricResult(
        val azimuthDeg: Double,
        val elevationDeg: Double,
        val slantRangeKm: Double
    )

    private fun computeTopocentricAzElRange(
        ecefX: Double,
        ecefY: Double,
        ecefZ: Double,
        observer: ObserverStation
    ): TopocentricResult {
        val latRad = observer.latitudeDeg * DEG_TO_RAD
        val lonRad = observer.longitudeDeg * DEG_TO_RAD
        val obsR = EARTH_RADIUS_KM + (observer.altitudeMeters / 1000.0)

        val obsX = obsR * cos(latRad) * cos(lonRad)
        val obsY = obsR * cos(latRad) * sin(lonRad)
        val obsZ = obsR * sin(latRad)

        val rx = ecefX - obsX
        val ry = ecefY - obsY
        val rz = ecefZ - obsZ
        val rangeKm = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(1.0)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinLon = sin(lonRad)
        val cosLon = cos(lonRad)

        // South-East-Up (SEU) topocentric frame
        val topS = sinLat * cosLon * rx + sinLat * sinLon * ry - cosLat * rz
        val topE = -sinLon * rx + cosLon * ry
        val topZ = cosLat * cosLon * rx + cosLat * sinLon * ry + sinLat * rz

        val elRad = asin((topZ / rangeKm).coerceIn(-1.0, 1.0))
        var azDeg = atan2(topE, -topS) * RAD_TO_DEG
        if (azDeg < 0.0) azDeg += 360.0

        return TopocentricResult(
            azimuthDeg = azDeg,
            elevationDeg = elRad * RAD_TO_DEG,
            slantRangeKm = rangeKm
        )
    }

    private fun computeGmstRad(timeMillisUtc: Long): Double {
        // Julian Date from Unix epoch
        val jd = (timeMillisUtc / 86400000.0) + 2440587.5
        val d = jd - 2451545.0
        val gmstHours = (18.697374558 + 24.06570982441908 * d) % 24.0
        val positiveHours = if (gmstHours < 0) gmstHours + 24.0 else gmstHours
        return (positiveHours / 24.0) * 2.0 * PI
    }

    private fun computeIsSunlit(eciX: Double, eciY: Double, eciZ: Double, timeMillisUtc: Long): Boolean {
        val jd = (timeMillisUtc / 86400000.0) + 2440587.5
        val n = jd - 2451545.0
        val meanLonSunRad = ((280.460 + 0.9856474 * n) % 360.0) * DEG_TO_RAD
        val obliquityRad = 23.439 * DEG_TO_RAD

        val sunUnitX = cos(meanLonSunRad)
        val sunUnitY = cos(obliquityRad) * sin(meanLonSunRad)
        val sunUnitZ = sin(obliquityRad) * sin(meanLonSunRad)

        val dot = eciX * sunUnitX + eciY * sunUnitY + eciZ * sunUnitZ
        if (dot >= 0.0) return true // Dayside of Earth

        val rSq = eciX * eciX + eciY * eciY + eciZ * eciZ
        val perpDistSq = (rSq - dot * dot).coerceAtLeast(0.0)
        return sqrt(perpDistSq) > EARTH_RADIUS_KM
    }

    private fun String.substringSafe(start: Int, end: Int): String {
        if (length <= start) return ""
        return substring(start, minOf(end, length)).trim()
    }
}
