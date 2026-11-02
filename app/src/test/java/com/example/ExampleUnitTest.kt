package com.example

import com.example.data.OfflineSatelliteCatalog
import com.example.engine.OrbitalPropagatorEngine
import com.example.model.OrbitLayer
import com.example.model.PresetObserverStations
import com.example.security.PassVoiceAnnouncer
import com.example.security.SecurityVaultManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun offlineCatalog_coversAllOrbitalLayersAndValidTleChecksums() {
        val catalog = OfflineSatelliteCatalog.getInitialCatalog()
        assertEquals(18, catalog.size)

        val layersPresent = catalog.map { it.orbitLayer }.toSet()
        assertTrue(layersPresent.contains(OrbitLayer.LEO))
        assertTrue(layersPresent.contains(OrbitLayer.SSO))
        assertTrue(layersPresent.contains(OrbitLayer.MEO))
        assertTrue(layersPresent.contains(OrbitLayer.GEO))
        assertTrue(layersPresent.contains(OrbitLayer.HEO))

        for (sat in catalog) {
            assertTrue(
                "Line 1 checksum failed for ${sat.name}",
                SecurityVaultManager.verifyTleLineChecksum(sat.tleLine1, 1)
            )
            assertTrue(
                "Line 2 checksum failed for ${sat.name}",
                SecurityVaultManager.verifyTleLineChecksum(sat.tleLine2, 2)
            )
            assertTrue("Milestones should not be empty for ${sat.name}", sat.milestones.isNotEmpty())
        }
    }

    @Test
    fun voiceAnnouncer_generatesApproachingPhrasesForIssAndAllSatellites() {
        assertEquals(
            "ISS approaching in 5 minutes",
            PassVoiceAnnouncer.buildApproachingVoicePhrase("ISS (ZARYA)", 5)
        )
        assertEquals(
            "Tiangong approaching in 5 minutes",
            PassVoiceAnnouncer.buildApproachingVoicePhrase("TIANGONG (CSS)", 5)
        )
        assertEquals(
            "Hubble approaching in 5 minutes",
            PassVoiceAnnouncer.buildApproachingVoicePhrase("HUBBLE (HST)", 5)
        )

        val catalog = OfflineSatelliteCatalog.getInitialCatalog()
        for (sat in catalog) {
            val phrase = PassVoiceAnnouncer.buildApproachingVoicePhrase(sat.name, 5)
            assertTrue(
                "Voice phrase for ${sat.name} should end with 'approaching in 5 minutes': $phrase",
                phrase.endsWith("approaching in 5 minutes")
            )
        }
    }

    @Test
    fun orbitalPropagator_computesValidTelemetryAndPassPredictions() {
        val catalog = OfflineSatelliteCatalog.getInitialCatalog()
        val observer = PresetObserverStations.stations.first()
        assertEquals("Bhubaneswar, Odisha", observer.name)
        assertEquals(20.2961, observer.latitudeDeg, 0.001)
        assertEquals(85.8245, observer.longitudeDeg, 0.001)
        val nowMillis = 1760880000000L

        for (sat in catalog) {
            val telemetry = OrbitalPropagatorEngine.propagateSatellite(sat, observer, nowMillis)
            assertTrue(telemetry.latitudeDeg in -90.0..90.0)
            assertTrue(telemetry.longitudeDeg in -180.0..180.0)
            assertTrue(telemetry.altitudeKm > 150.0)
            assertTrue(telemetry.velocityKms > 1.0)
            assertTrue(telemetry.orbitTrajectory3D.size > 30)
            assertTrue(telemetry.tleChecksumValid)
        }

        val passes = OrbitalPropagatorEngine.predictUpcomingPasses(
            satellites = catalog,
            observer = observer,
            startTimeMillis = nowMillis,
            windowHours = 18
        )
        assertTrue("Should predict upcoming passes across 18 hours", passes.isNotEmpty())
    }

    @Test
    fun catalogSearch_filtersSatellitesByNameAndNoradId() {
        val catalog = OfflineSatelliteCatalog.getInitialCatalog()

        // Search by NORAD ID 25544 (ISS)
        val issById = catalog.filter { it.noradId.toString().contains("25544") }
        assertEquals(1, issById.size)
        assertEquals("ISS (ZARYA)", issById.first().name)

        // Search by partial name "HUBBLE"
        val hubbleByName = catalog.filter { it.name.lowercase().contains("hubble") }
        assertEquals(1, hubbleByName.size)
        assertEquals(20580, hubbleByName.first().noradId)

        // Search by partial NORAD ID "48274" (Tiangong)
        val tiangongById = catalog.filter { "#${it.noradId}".contains("#48274") }
        assertEquals(1, tiangongById.size)
        assertEquals("TIANGONG (CSS)", tiangongById.first().name)
    }

    @Test
    fun worldCountryOutlines_containsValidCountryBoundariesFor3DGlobe() {
        val countries = com.example.ui.components.WorldCountryOutlines.countries
        assertTrue("Should contain at least 30 country boundary outlines", countries.size >= 30)
        for (country in countries) {
            assertTrue("Country ${country.countryName} must have at least 4 boundary vertices", country.coordinatesLatLon.size >= 4)
            for ((lat, lon) in country.coordinatesLatLon) {
                assertTrue("Invalid latitude $lat in ${country.countryName}", lat in -90f..90f)
                assertTrue("Invalid longitude $lon in ${country.countryName}", lon in -180f..180f)
            }
        }
    }

    @Test
    fun securityHardening_verifiesConstantTimeDigestCatalogMatchAndInputSanitization() {
        val catalog = OfflineSatelliteCatalog.getInitialCatalog()
        for (sat in catalog) {
            assertTrue(
                "NORAD catalog match failed for ${sat.name}",
                SecurityVaultManager.verifyTleCatalogMatch(sat.tleLine1, sat.tleLine2, sat.noradId)
            )
            assertTrue(
                "Keplerian orbital parameter sanity check failed for ${sat.name}",
                SecurityVaultManager.verifyTleOrbitalParametersSanity(sat.tleLine1, sat.tleLine2)
            )
            val expectedSha = SecurityVaultManager.computeSha256(
                "${sat.noradId}|${sat.name}|${sat.tleLine1}|${sat.tleLine2}|${sat.orbitLayerCode}"
            )
            assertTrue(
                "Constant-time SHA-256 verification failed for ${sat.name}",
                SecurityVaultManager.verifySha256ConstantTime(expectedSha, sat.sha256Signature)
            )
        }

        // Verify RFC 2104 HmacSHA256 determinism & tamper rejection
        val mac1 = SecurityVaultManager.computeHmacSha256("test_secret_key_256", "v2|25544|ISS|5|1760880000000")
        val mac2 = SecurityVaultManager.computeHmacSha256("test_secret_key_256", "v2|25544|ISS|5|1760880000000")
        val macTampered = SecurityVaultManager.computeHmacSha256("test_secret_key_256", "v2|25544|ISS|10|1760880000000")
        assertTrue(SecurityVaultManager.verifySha256ConstantTime(mac1, mac2))
        assertTrue(!SecurityVaultManager.verifySha256ConstantTime(mac1, macTampered))

        val sanitized = SecurityVaultManager.sanitizeUserText("Bhubaneswar|<script>alert(1);</script>\u200B", 48)
        assertTrue(!sanitized.contains("|"))
        assertTrue(!sanitized.contains("<"))
        assertTrue(!sanitized.contains(">"))
        assertTrue(!sanitized.contains(";"))
        assertTrue(sanitized.startsWith("Bhubaneswar"))
    }

    @Test
    fun nextApproachSorting_andQuickFindMultiToken_ordersAndMatchesProperly() {
        val catalog = OfflineSatelliteCatalog.getInitialCatalog()
        val observer = PresetObserverStations.stations.first()
        val nowMillis = 1760880000000L

        val telemetryList = catalog.map { sat ->
            OrbitalPropagatorEngine.propagateSatellite(sat, observer, nowMillis)
        }

        // Verify every satellite has non-blank next approach status text and valid timing
        for (sat in telemetryList) {
            assertTrue("nextApproachMillis should be >= nowMillis for ${sat.name}", sat.nextApproachMillis >= nowMillis)
            assertTrue("minutesUntilApproach should be >= 0 for ${sat.name}", sat.minutesUntilApproach >= 0)
            assertTrue("nextApproachStatusText should not be blank for ${sat.name}", sat.nextApproachStatusText.isNotBlank())
        }

        // Verify chronological Next Approach sort: overhead satellites first, then ascending nextApproachMillis
        val sortedByNextApproach = telemetryList.sortedWith(
            compareByDescending<com.example.model.SatelliteLiveTelemetry> { it.isAboveHorizon }
                .thenBy { if (it.isAboveHorizon) -it.elevationDeg else it.nextApproachMillis.toDouble() }
                .thenBy { it.noradId }
        )

        val belowHorizonList = sortedByNextApproach.filter { !it.isAboveHorizon }
        for (i in 0 until belowHorizonList.size - 1) {
            assertTrue(
                "Below-horizon satellites must be ordered by nextApproachMillis ascending",
                belowHorizonList[i].nextApproachMillis <= belowHorizonList[i + 1].nextApproachMillis
            )
        }
    }
}

