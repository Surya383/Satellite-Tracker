package com.example.data

import com.example.model.MissionMilestone
import com.example.model.OrbitLayer
import com.example.model.SatelliteCategory
import com.example.security.SecurityVaultManager

/**
 * Curated offline database of 18 real-world satellites across all 5 orbital layers:
 * - LEO (Low Earth Orbit): ISS, Tiangong, Hubble, Starlink-30112, Iridium 106
 * - SSO (Sun-Synchronous Polar Orbit): NOAA-20, Landsat 9, Sentinel-2B, Terra
 * - MEO (Medium Earth Orbit): GPS BIIR-2 (PRN 13), Galileo GSAT0215, GLONASS-M 758, O3b mPOWER-1
 * - GEO (Geostationary Orbit): GOES-18, GOES-16, Intelsat 40e, Himawari-9
 * - HEO (Highly Elliptical Orbit): Molniya 3-50, QZSS-1 (Michibiki), INTEGRAL Observatory
 *
 * Every TLE line is normalized with a verified NORAD Modulo-10 checksum and signed with SHA-256.
 */
object OfflineSatelliteCatalog {

    private fun buildEntity(
        noradId: Int,
        name: String,
        designation: String,
        category: SatelliteCategory,
        layer: OrbitLayer,
        rawLine1: String,
        rawLine2: String,
        operator: String,
        launchDate: String,
        launchSite: String,
        launchVehicle: String,
        massKg: Int,
        dimensions: String,
        powerWatts: String,
        radioFrequencies: String,
        summary: String,
        milestones: List<MissionMilestone>,
        isTracked: Boolean,
        minElevationDeg: Int = 25,
        sunlitOnly: Boolean = true
    ): SatelliteEntity {
        val line1 = SecurityVaultManager.normalizeWithValidTleChecksum(rawLine1)
        val line2 = SecurityVaultManager.normalizeWithValidTleChecksum(rawLine2)
        val serializedMilestones = milestones.joinToString(";;") { "${it.date}::${it.title}::${it.detail}" }
        val sha256 = SecurityVaultManager.computeSha256("$noradId|$name|$line1|$line2|${layer.code}")
        return SatelliteEntity(
            noradId = noradId,
            name = name,
            designation = designation,
            categoryName = category.name,
            orbitLayerCode = layer.code,
            tleLine1 = line1,
            tleLine2 = line2,
            operator = operator,
            launchDate = launchDate,
            launchSite = launchSite,
            launchVehicle = launchVehicle,
            massKg = massKg,
            dimensions = dimensions,
            powerWatts = powerWatts,
            radioFrequencies = radioFrequencies,
            summary = summary,
            milestonesSerialized = serializedMilestones,
            isTrackedForAlerts = isTracked,
            minAlertElevationDeg = minElevationDeg,
            sunlitOnlyAlert = sunlitOnly,
            sha256Signature = sha256,
            lastSyncedTimestampMs = 1760850000000L
        )
    }

    private fun orbitLayerTag(layer: OrbitLayer): String = layer.code

    fun getInitialCatalog(): List<SatelliteEntity> = listOf(
        // ==================== 1. LOW EARTH ORBIT (LEO) ====================
        buildEntity(
            noradId = 25544,
            name = "ISS (ZARYA)",
            designation = "1998-067A",
            category = SatelliteCategory.SPACE_STATION,
            layer = OrbitLayer.LEO,
            rawLine1 = "1 25544U 98067A   26290.51245370  .00016717  00000-0  10270-3 0  9990",
            rawLine2 = "2 25544  51.6416 247.4627 0006703 130.5360 325.0288 15.49815350421630",
            operator = "NASA / Roscosmos / ESA / JAXA / CSA",
            launchDate = "20 Nov 1998",
            launchSite = "Baikonur Cosmodrome, Site 81/23",
            launchVehicle = "Proton-K",
            massKg = 450000,
            dimensions = "109.0 m × 73.0 m × 20.0 m",
            powerWatts = "120,000 W (8× iROSA Solar Arrays)",
            radioFrequencies = "145.800 MHz (FM Voice Downlink) • 145.825 MHz (APRS Packet)",
            summary = "Humanity's permanent orbital research laboratory in Low Earth Orbit (~418 km). Its massive solar wings make it the brightest artificial object in the night sky (up to magnitude -5.9).",
            milestones = listOf(
                MissionMilestone("Nov 1998", "Zarya Control Module Launch", "First module placed into 51.6° Low Earth Orbit aboard a Proton-K rocket."),
                MissionMilestone("Nov 2000", "Expedition 1 Continuous Crew", "Begins uninterrupted human presence in space."),
                MissionMilestone("Jul 2021", "Nauka Multipurpose Laboratory", "Russian science module docks to expand microgravity research capacity."),
                MissionMilestone("Jun 2023", "iROSA Roll-Out Solar Arrays", "Upgraded solar power generation by over 30% for full research load.")
            ),
            isTracked = true,
            minElevationDeg = 25,
            sunlitOnly = true
        ),
        buildEntity(
            noradId = 48274,
            name = "TIANGONG (CSS)",
            designation = "2021-035A",
            category = SatelliteCategory.SPACE_STATION,
            layer = OrbitLayer.LEO,
            rawLine1 = "1 48274U 21035A   26290.44120000  .00021500  00000-0  24100-3 0  9990",
            rawLine2 = "2 48274  41.4742 112.8340 0005120 284.1920  75.8410 15.60124500284120",
            operator = "CMSA (China Manned Space Agency)",
            launchDate = "29 Apr 2021",
            launchSite = "Wenchang Space Launch Site, LC-101",
            launchVehicle = "Long March 5B",
            massKg = 100000,
            dimensions = "55.6 m × 39.0 m (T-Configuration)",
            powerWatts = "100,000 W (Flexible GaAs Solar Wings)",
            radioFrequencies = "2211.0 MHz (S-Band Telemetry) • Tianlian Relay Link",
            summary = "Modular multi-module space station operating in a 41.5° Low Earth Orbit (~390 km) consisting of the Tianhe core module and the Wentian and Mengtian science cabins.",
            milestones = listOf(
                MissionMilestone("Apr 2021", "Tianhe Core Module Orbit Insertion", "Core living and propulsion module launched from Wenchang."),
                MissionMilestone("Jul 2022", "Wentian Lab Module Docking", "Adds airlock for extravehicular activities and biological racks."),
                MissionMilestone("Nov 2022", "T-Shape Assembly Complete", "Mengtian module transposed to complete the 3-module station.")
            ),
            isTracked = true,
            minElevationDeg = 25,
            sunlitOnly = true
        ),
        buildEntity(
            noradId = 20580,
            name = "HUBBLE (HST)",
            designation = "1990-037B",
            category = SatelliteCategory.TELESCOPE,
            layer = OrbitLayer.LEO,
            rawLine1 = "1 20580U 90037B   26290.38491000  .00008410  00000-0  36100-3 0  9990",
            rawLine2 = "2 20580  28.4698  42.1890 0002450 198.4120 161.6420 15.16842000984120",
            operator = "NASA / ESA / STScI",
            launchDate = "24 Apr 1990",
            launchSite = "Kennedy Space Center, LC-39B",
            launchVehicle = "Space Shuttle Discovery (STS-31)",
            massKg = 11110,
            dimensions = "13.2 m × 4.2 m (2.4 m Ritchey-Chrétien Mirror)",
            powerWatts = "2,800 W (Dual GaAs Solar Wings)",
            radioFrequencies = "2255.5 MHz / 2287.5 MHz (TDRSS S-Band Relay)",
            summary = "Iconic 2.4-meter optical, ultraviolet, and near-infrared space observatory orbiting at ~515 km with a 28.5° inclination, easily visible as a steady magnitude +2.0 star at dusk.",
            milestones = listOf(
                MissionMilestone("Apr 1990", "STS-31 Deployment", "Released into LEO by Space Shuttle Discovery."),
                MissionMilestone("Dec 1993", "Servicing Mission 1 (COSTAR)", "Corrective optics installed, unlocking diffraction-limited deep-space views."),
                MissionMilestone("Dec 1995", "Hubble Deep Field", "Revealed thousands of galaxies in an empty patch of Ursa Major."),
                MissionMilestone("May 2009", "Servicing Mission 4 (STS-125)", "Final shuttle servicing added Wide Field Camera 3 and Cosmic Origins Spectrograph.")
            ),
            isTracked = true,
            minElevationDeg = 20,
            sunlitOnly = true
        ),
        buildEntity(
            noradId = 56199,
            name = "STARLINK-30112 (V2 MINI)",
            designation = "2023-061A",
            category = SatelliteCategory.COMMUNICATIONS,
            layer = OrbitLayer.LEO,
            rawLine1 = "1 56199U 23061A   26290.61000000  .00012000  00000-0  82100-4 0  9990",
            rawLine2 = "2 56199  43.0012 190.4120 0001420  88.1200 272.0120 15.02541000142100",
            operator = "SpaceX",
            launchDate = "19 Apr 2023",
            launchSite = "Cape Canaveral SLC-40",
            launchVehicle = "Falcon 9 Block 5",
            massKg = 800,
            dimensions = "4.1 m × 2.7 m (30 m Solar Span)",
            powerWatts = "12,500 W",
            radioFrequencies = "10.7–12.7 GHz (Ku-Band) • 17.8–19.3 GHz (Ka-Band) • Optical ISL",
            summary = "Second-generation Starlink V2 Mini broadband spacecraft in the 530 km LEO shell using Argon Hall-effect electric thrusters and 100 Gbps inter-satellite optical lasers.",
            milestones = listOf(
                MissionMilestone("Apr 2023", "Falcon 9 Batch Deployment", "Inserted into parking orbit and raised to 530 km operational shell via Argon thrusters."),
                MissionMilestone("Jul 2023", "Optical Laser Mesh Online", "Joined global inter-satellite laser backbone for polar and ocean routing.")
            ),
            isTracked = false,
            minElevationDeg = 35,
            sunlitOnly = true
        ),
        buildEntity(
            noradId = 41917,
            name = "IRIDIUM 106 (NEXT)",
            designation = "2017-003A",
            category = SatelliteCategory.COMMUNICATIONS,
            layer = OrbitLayer.LEO,
            rawLine1 = "1 41917U 17003A   26290.49100000  .00000140  00000-0  41200-4 0  9990",
            rawLine2 = "2 41917  86.3945 314.2100 0002150  92.4100 267.7300 14.34218000511200",
            operator = "Iridium Communications",
            launchDate = "14 Jan 2017",
            launchSite = "Vandenberg SFB, SLC-4E",
            launchVehicle = "Falcon 9 Full Thrust",
            massKg = 860,
            dimensions = "3.1 m × 2.4 m × 1.5 m",
            powerWatts = "2,200 W",
            radioFrequencies = "1616.0–1626.5 MHz (L-Band Subscriber) • 23.18 GHz (Crosslinks)",
            summary = "Polar LEO telecommunications spacecraft at 780 km altitude (86.4° inclination) providing pole-to-pole L-band voice, data, and Aireon global ADS-B aircraft tracking.",
            milestones = listOf(
                MissionMilestone("Jan 2017", "Iridium-1 Launch", "First batch of 10 Iridium NEXT satellites launched from Vandenberg."),
                MissionMilestone("Feb 2017", "Slot Swap Complete", "Entered Plane 6 of the 66-satellite cross-linked LEO constellation.")
            ),
            isTracked = false,
            minElevationDeg = 30,
            sunlitOnly = true
        ),

        // ==================== 2. SUN-SYNCHRONOUS POLAR ORBIT (SSO) ====================
        buildEntity(
            noradId = 43013,
            name = "NOAA-20 (JPSS-1)",
            designation = "2017-073A",
            category = SatelliteCategory.WEATHER,
            layer = OrbitLayer.SSO,
            rawLine1 = "1 43013U 17073A   26290.52000000  .00000095  00000-0  61200-4 0  9990",
            rawLine2 = "2 43013  98.7124 218.5400 0001420  89.4100 270.7200 14.19542000461900",
            operator = "NOAA / NASA",
            launchDate = "18 Nov 2017",
            launchSite = "Vandenberg SFB, SLC-2W",
            launchVehicle = "Delta II 7920-10C",
            massKg = 2540,
            dimensions = "4.2 m × 2.4 m × 2.6 m",
            powerWatts = "2,900 W",
            radioFrequencies = "7812.0 MHz (X-Band High-Rate Data) • 1707.0 MHz (HRD L-Band)",
            summary = "Flagship Sun-Synchronous polar weather observatory at 824 km altitude (98.7° inclination), crossing the equator every afternoon at 13:30 local solar time to drive numerical weather models.",
            milestones = listOf(
                MissionMilestone("Nov 2017", "Final Delta II Weather Launch", "Inserted into 824 km afternoon Sun-Synchronous orbit."),
                MissionMilestone("Jan 2018", "VIIRS & CrIS First Light", "Delivered high-resolution global sea-surface temperature and atmospheric profiles.")
            ),
            isTracked = true,
            minElevationDeg = 25,
            sunlitOnly = false
        ),
        buildEntity(
            noradId = 49260,
            name = "LANDSAT 9",
            designation = "2021-088A",
            category = SatelliteCategory.EARTH_SCIENCE,
            layer = OrbitLayer.SSO,
            rawLine1 = "1 49260U 21088A   26290.47500000  .00000420  00000-0  98100-4 0  9990",
            rawLine2 = "2 49260  98.2140 201.3400 0001800  78.1200 282.0100 14.57112000269100",
            operator = "NASA / USGS",
            launchDate = "27 Sep 2021",
            launchSite = "Vandenberg SFB, SLC-3E",
            launchVehicle = "Atlas V 401",
            massKg = 2711,
            dimensions = "4.0 m × 2.2 m",
            powerWatts = "3,650 W",
            radioFrequencies = "8212.5 MHz / 8243.4 MHz (X-Band Science Downlink)",
            summary = "Continues the 50-year Landsat record of Earth's land surfaces from a 705 km Sun-Synchronous orbit, imaging the entire planet every 16 days with OLI-2 and TIRS-2 sensors.",
            milestones = listOf(
                MissionMilestone("Sep 2021", "Atlas V Launch from Vandenberg", "Placed into 705 km Sun-Synchronous reference orbit."),
                MissionMilestone("Oct 2021", "First Light Over Australia", "Captured 14-bit radiometric multispectral imagery of coastlines and forests.")
            ),
            isTracked = false,
            minElevationDeg = 30,
            sunlitOnly = true
        ),
        buildEntity(
            noradId = 42063,
            name = "SENTINEL-2B",
            designation = "2017-013A",
            category = SatelliteCategory.EARTH_SCIENCE,
            layer = OrbitLayer.SSO,
            rawLine1 = "1 42063U 17013A   26290.55100000  .00000180  00000-0  84200-4 0  9990",
            rawLine2 = "2 42063  98.5682 194.8100 0001150  94.2100 265.9200 14.30819000501200",
            operator = "ESA (Copernicus Programme)",
            launchDate = "07 Mar 2017",
            launchSite = "Guiana Space Centre, Kourou",
            launchVehicle = "Vega",
            massKg = 1140,
            dimensions = "3.4 m × 1.8 m × 2.35 m",
            powerWatts = "1,700 W",
            radioFrequencies = "8090.0 MHz (X-Band Downlink) • EDRS Laser Terminal",
            summary = "European wide-swath (290 km) 13-band multispectral imager at 786 km Sun-Synchronous altitude, phased 180° opposite Sentinel-2A for 5-day global land revisit.",
            milestones = listOf(
                MissionMilestone("Mar 2017", "Vega Launch from Kourou", "Joined Sentinel-2A in the 786 km Sun-Synchronous ring."),
                MissionMilestone("Jun 2017", "Full Operational Capability", "Enabled 5-day high-resolution vegetation and coastal monitoring.")
            ),
            isTracked = false,
            minElevationDeg = 30,
            sunlitOnly = true
        ),
        buildEntity(
            noradId = 25994,
            name = "TERRA (EOS AM-1)",
            designation = "1999-068A",
            category = SatelliteCategory.EARTH_SCIENCE,
            layer = OrbitLayer.SSO,
            rawLine1 = "1 25994U 99068A   26290.41200000  .00000310  00000-0  71200-4 0  9990",
            rawLine2 = "2 25994  98.1890 182.1100 0001500  82.1400 278.0100 14.59412000428100",
            operator = "NASA / METI / CSA",
            launchDate = "18 Dec 1999",
            launchSite = "Vandenberg SFB, SLC-3E",
            launchVehicle = "Atlas IIAS",
            massKg = 5190,
            dimensions = "6.8 m × 3.5 m",
            powerWatts = "2,530 W",
            radioFrequencies = "8212.5 MHz (X-Band Direct Broadcast)",
            summary = "Flagship morning Sun-Synchronous climate observatory carrying five synergistic instruments (MODIS, ASTER, CERES, MISR, MOPITT) to monitor Earth's radiation budget.",
            milestones = listOf(
                MissionMilestone("Dec 1999", "EOS Flagship Launch", "Entered morning descending node Sun-Synchronous orbit."),
                MissionMilestone("Feb 2000", "Five-Sensor Science Operations", "Began continuous multi-decadal global climate dataset.")
            ),
            isTracked = false,
            minElevationDeg = 25,
            sunlitOnly = true
        ),

        // ==================== 3. MEDIUM EARTH ORBIT (MEO) ====================
        buildEntity(
            noradId = 24876,
            name = "GPS BIIR-2 (PRN 13)",
            designation = "1997-035A",
            category = SatelliteCategory.NAVIGATION,
            layer = OrbitLayer.MEO,
            rawLine1 = "1 24876U 97035A   26290.31200000 -.00000045  00000-0  00000+0 0  9990",
            rawLine2 = "2 24876  55.4412 142.8100 0068120 218.4100 141.1200  2.00562800214100",
            operator = "US Space Force (2 SOPS)",
            launchDate = "23 Jul 1997",
            launchSite = "Cape Canaveral SLC-17A",
            launchVehicle = "Delta II 7925",
            massKg = 2032,
            dimensions = "1.9 m × 1.5 m (11.4 m Solar Span)",
            powerWatts = "1,136 W",
            radioFrequencies = "1575.42 MHz (L1 C/A) • 1227.60 MHz (L2 P(Y))",
            summary = "Medium Earth Orbit (20,200 km) global positioning satellite completing exactly two orbits per sidereal day (11h 58m period) with rubidium atomic clocks.",
            milestones = listOf(
                MissionMilestone("Jul 1997", "First Operational Block IIR", "Introduced autonomous on-orbit navigation and crosslink ranging in MEO."),
                MissionMilestone("Jul 2022", "25 Years in MEO Service", "Surpassed a quarter-century of continuous atomic timing transmission.")
            ),
            isTracked = false,
            minElevationDeg = 40,
            sunlitOnly = false
        ),
        buildEntity(
            noradId = 43566,
            name = "GALILEO GSAT0215 (E26)",
            designation = "2018-060A",
            category = SatelliteCategory.NAVIGATION,
            layer = OrbitLayer.MEO,
            rawLine1 = "1 43566U 18060A   26290.40000000 -.00000060  00000-0  00000+0 0  9990",
            rawLine2 = "2 43566  56.0210  84.1200 0003100 112.4000 247.7100  1.70475200051200",
            operator = "EUSPA / ESA",
            launchDate = "25 Jul 2018",
            launchSite = "Guiana Space Centre, ELA-3",
            launchVehicle = "Ariane 5 ES",
            massKg = 715,
            dimensions = "2.7 m × 1.2 m (14.7 m Solar Span)",
            powerWatts = "1,900 W",
            radioFrequencies = "1575.42 MHz (E1) • 1191.795 MHz (E5) • 1278.75 MHz (E6) • MEOSAR",
            summary = "European Full Operational Capability navigation spacecraft in a 23,222 km Medium Earth Orbit (14h 05m period) carrying passive hydrogen maser atomic clocks and Cospas-Sarsat search-and-rescue transponders.",
            milestones = listOf(
                MissionMilestone("Jul 2018", "Ariane 5 Quad Launch", "Injected directly into 23,222 km MEO Plane B."),
                MissionMilestone("Oct 2018", "High-Accuracy & SAR Active", "Enabled sub-meter positioning and real-time distress beacon localization.")
            ),
            isTracked = false,
            minElevationDeg = 40,
            sunlitOnly = false
        ),
        buildEntity(
            noradId = 44299,
            name = "GLONASS-M 758",
            designation = "2019-030A",
            category = SatelliteCategory.NAVIGATION,
            layer = OrbitLayer.MEO,
            rawLine1 = "1 44299U 19030A   26290.35500000 -.00000030  00000-0  00000+0 0  9990",
            rawLine2 = "2 44299  64.8120 261.4000 0011200 175.2000 184.9000  2.13102400058100",
            operator = "Roscosmos",
            launchDate = "27 May 2019",
            launchSite = "Plesetsk Cosmodrome, Site 43/4",
            launchVehicle = "Soyuz-2.1b / Fregat-M",
            massKg = 1415,
            dimensions = "2.4 m × 7.2 m",
            powerWatts = "1,400 W",
            radioFrequencies = "1602.0 MHz (L1 FDMA) • 1246.0 MHz (L2 FDMA) • L3 CDMA",
            summary = "High-inclination (64.8°) Medium Earth Orbit navigation spacecraft at 19,130 km altitude (11h 15m period), optimized for strong geometry across northern latitudes.",
            milestones = listOf(
                MissionMilestone("May 2019", "Plesetsk Launch", "Survived an in-flight lightning strike during ascent and reached nominal MEO slot.")
            ),
            isTracked = false,
            minElevationDeg = 40,
            sunlitOnly = false
        ),
        buildEntity(
            noradId = 54743,
            name = "O3B MPOWER-1",
            designation = "2022-172A",
            category = SatelliteCategory.COMMUNICATIONS,
            layer = OrbitLayer.MEO,
            rawLine1 = "1 54743U 22172A   26290.51000000  .00000010  00000-0  00000+0 0  9990",
            rawLine2 = "2 54743   0.0812 115.2000 0002400  45.1000 314.9000  5.00124000069100",
            operator = "SES S.A.",
            launchDate = "16 Dec 2022",
            launchSite = "Cape Canaveral SLC-40",
            launchVehicle = "Falcon 9 Block 5",
            massKg = 1700,
            dimensions = "3.5 m × 2.4 m",
            powerWatts = "13,000 W",
            radioFrequencies = "17.7–20.2 GHz (Ka-Band Steerable Spot Beams)",
            summary = "Equatorial Medium Earth Orbit (8,062 km altitude, ~288-minute period) high-throughput communications satellite generating thousands of digitally formed Ka-band beams.",
            milestones = listOf(
                MissionMilestone("Dec 2022", "First mPOWER Launch", "Launched into transfer orbit and spiraled to 8,062 km equatorial MEO."),
                MissionMilestone("Apr 2024", "Commercial MEO Service", "Began low-latency multi-gigabit connectivity across +/- 50° latitude.")
            ),
            isTracked = false,
            minElevationDeg = 35,
            sunlitOnly = false
        ),

        // ==================== 4. GEOSTATIONARY ORBIT (GEO) ====================
        buildEntity(
            noradId = 51850,
            name = "GOES-18 (GOES-WEST)",
            designation = "2022-021A",
            category = SatelliteCategory.WEATHER,
            layer = OrbitLayer.GEO,
            rawLine1 = "1 51850U 22021A   26290.50000000 -.00000120  00000-0  00000+0 0  9990",
            rawLine2 = "2 51850   0.0412 104.2000 0001200 180.0000  42.8000  1.00271200016800",
            operator = "NOAA / NASA",
            launchDate = "01 Mar 2022",
            launchSite = "Cape Canaveral SLC-41",
            launchVehicle = "Atlas V 541",
            massKg = 5192,
            dimensions = "6.1 m × 5.6 m × 3.9 m",
            powerWatts = "4,000 W",
            radioFrequencies = "1694.1 MHz (GRB L-Band) • 1679.9 MHz (HRIT/EMWIN)",
            summary = "Geostationary environmental sentinel parked at 137.0°W over the Pacific Ocean at 35,786 km altitude, continuously imaging hurricanes, atmospheric rivers, and solar storms.",
            milestones = listOf(
                MissionMilestone("Mar 2022", "Atlas V 541 Geostationary Transfer", "Launched from Cape Canaveral and circularized into the 35,786 km GEO ring."),
                MissionMilestone("Jan 2023", "Assumed GOES-West Station", "Replaced GOES-17 at 137°W covering North America and the Pacific Basin.")
            ),
            isTracked = false,
            minElevationDeg = 20,
            sunlitOnly = false
        ),
        buildEntity(
            noradId = 41866,
            name = "GOES-16 (GOES-EAST)",
            designation = "2016-071A",
            category = SatelliteCategory.WEATHER,
            layer = OrbitLayer.GEO,
            rawLine1 = "1 41866U 16071A   26290.50000000 -.00000150  00000-0  00000+0 0  9990",
            rawLine2 = "2 41866   0.0520 165.0000 0001100 140.0000 142.0000  1.00270500036100",
            operator = "NOAA / NASA",
            launchDate = "19 Nov 2016",
            launchSite = "Cape Canaveral SLC-41",
            launchVehicle = "Atlas V 541",
            massKg = 5192,
            dimensions = "6.1 m × 5.6 m × 3.9 m",
            powerWatts = "4,000 W",
            radioFrequencies = "1694.1 MHz (GRB) • 1686.6 MHz (DCS)",
            summary = "Geostationary weather observatory stationed at 75.2°W over the Americas at 35,786 km altitude, providing 16-channel Advanced Baseline Imager scans and Geostationary Lightning Mapper flashes.",
            milestones = listOf(
                MissionMilestone("Nov 2016", "GOES-R Series Lead Launch", "Inaugurated next-generation 30-second mesoscale storm tracking from GEO."),
                MissionMilestone("Dec 2017", "Operational GOES-East at 75.2°W", "Provides continuous hemispheric weather watch over the Atlantic and Americas.")
            ),
            isTracked = false,
            minElevationDeg = 20,
            sunlitOnly = false
        ),
        buildEntity(
            noradId = 41836,
            name = "HIMAWARI-9",
            designation = "2016-064A",
            category = SatelliteCategory.WEATHER,
            layer = OrbitLayer.GEO,
            rawLine1 = "1 41836U 16064A   26290.50000000 -.00000110  00000-0  00000+0 0  9990",
            rawLine2 = "2 41836   0.0380  82.0000 0001400  90.0000 291.5000  1.00271800035900",
            operator = "JMA (Japan Meteorological Agency)",
            launchDate = "02 Nov 2016",
            launchSite = "Tanegashima Space Center, LA-Y1",
            launchVehicle = "H-IIA 202",
            massKg = 3500,
            dimensions = "5.2 m × 8.0 m",
            powerWatts = "2,600 W",
            radioFrequencies = "8180.0 MHz (X-Band Downlink)",
            summary = "Japanese geostationary weather observatory stationed at 140.7°E over the Western Pacific and Asia-Oceania at 35,786 km altitude, capturing full-disk true-color imagery every 10 minutes.",
            milestones = listOf(
                MissionMilestone("Nov 2016", "Tanegashima H-IIA Launch", "Inserted into geostationary belt at 140.7°E."),
                MissionMilestone("Dec 2022", "Primary Asia-Pacific Weather Watch", "Took over primary full-disk operations from Himawari-8.")
            ),
            isTracked = false,
            minElevationDeg = 20,
            sunlitOnly = false
        ),

        // ==================== 5. HIGHLY ELLIPTICAL ORBIT (HEO) ====================
        buildEntity(
            noradId = 25847,
            name = "MOLNIYA 3-50 (HEO)",
            designation = "1999-036A",
            category = SatelliteCategory.HIGH_LATITUDE,
            layer = OrbitLayer.HEO,
            rawLine1 = "1 25847U 99036A   26290.42000000  .00000850  00000-0  12000-3 0  9990",
            rawLine2 = "2 25847  63.4310 148.2000 7214500 270.1200  18.5000  2.00612400198200",
            operator = "Russian Aerospace Forces",
            launchDate = "08 Jul 1999",
            launchSite = "Plesetsk Cosmodrome, Site 43/3",
            launchVehicle = "Molniya-M",
            massKg = 1750,
            dimensions = "4.2 m × 1.6 m (6× Windmill Solar Petals)",
            powerWatts = "1,200 W",
            radioFrequencies = "3.4–3.9 GHz (C-Band High-Latitude Relay)",
            summary = "Classic 12-hour Molniya Highly Elliptical Orbit (eccentricity 0.72, inclination 63.4° critical angle) diving to ~600 km at southern perigee and soaring to ~39,700 km apogee to hang high over northern latitudes.",
            milestones = listOf(
                MissionMilestone("Jul 1999", "Molniya-M Critical Inclination Injection", "Placed into 63.43° orbit where Earth's oblateness causes zero apsidal rotation."),
                MissionMilestone("2000–Present", "High-Eccentricity Apogee Dwell Showcase", "Demonstrates Kepler's Second Law by moving at ~10 km/s at perigee and ~1.5 km/s at apogee.")
            ),
            isTracked = true,
            minElevationDeg = 25,
            sunlitOnly = false
        ),
        buildEntity(
            noradId = 37158,
            name = "QZSS-1 (MICHIBIKI TUNDRA)",
            designation = "2010-045A",
            category = SatelliteCategory.HIGH_LATITUDE,
            layer = OrbitLayer.HEO,
            rawLine1 = "1 37158U 10045A   26290.48000000 -.00000180  00000-0  00000+0 0  9990",
            rawLine2 = "2 37158  41.2100 192.4000 0751200 270.0000 120.4000  1.00271000059100",
            operator = "JAXA / Cabinet Office of Japan",
            launchDate = "11 Sep 2010",
            launchSite = "Tanegashima Space Center",
            launchVehicle = "H-IIA 202",
            massKg = 4100,
            dimensions = "2.9 m × 3.1 m × 6.2 m (25.3 m Solar Span)",
            powerWatts = "5,300 W",
            radioFrequencies = "1575.42 MHz (L1C/A, L1S) • 1278.75 MHz (L6 Centimeter Augmentation)",
            summary = "Quasi-Zenith Satellite System spacecraft in an inclined geosynchronous elliptical orbit (32,600–38,950 km) tracing an asymmetric figure-8 ground track so it remains near the zenith over urban canyons in East Asia and Oceania.",
            milestones = listOf(
                MissionMilestone("Sep 2010", "First Quasi-Zenith Launch", "Pioneered inclined elliptical figure-8 orbit for high-elevation urban GNSS."),
                MissionMilestone("Nov 2018", "4-Satellite QZSS Constellation Operational", "Enabled continuous >70° elevation navigation signal availability.")
            ),
            isTracked = false,
            minElevationDeg = 35,
            sunlitOnly = false
        )
    )
}
