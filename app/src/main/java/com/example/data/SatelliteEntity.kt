package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.MissionMilestone
import com.example.model.OrbitLayer
import com.example.model.SatelliteCategory

@Entity(tableName = "satellites")
data class SatelliteEntity(
    @PrimaryKey val noradId: Int,
    val name: String,
    val designation: String,
    val categoryName: String,
    val orbitLayerCode: String,
    val tleLine1: String,
    val tleLine2: String,
    val operator: String,
    val launchDate: String,
    val launchSite: String,
    val launchVehicle: String,
    val massKg: Int,
    val dimensions: String,
    val powerWatts: String,
    val radioFrequencies: String,
    val summary: String,
    val milestonesSerialized: String,
    val isTrackedForAlerts: Boolean,
    val minAlertElevationDeg: Int,
    val sunlitOnlyAlert: Boolean,
    val sha256Signature: String,
    val lastSyncedTimestampMs: Long
) {
    val orbitLayer: OrbitLayer
        get() = OrbitLayer.entries.find { it.code == orbitLayerCode } ?: OrbitLayer.LEO

    val category: SatelliteCategory
        get() = SatelliteCategory.entries.find { it.name == categoryName }
            ?: SatelliteCategory.EARTH_SCIENCE

    val milestones: List<MissionMilestone>
        get() = milestonesSerialized.split(";;").mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size >= 3) {
                MissionMilestone(date = parts[0], title = parts[1], detail = parts[2])
            } else null
        }
}
