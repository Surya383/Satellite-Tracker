package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SatelliteDao {
    @Query("SELECT * FROM satellites ORDER BY isTrackedForAlerts DESC, noradId ASC")
    fun observeAllSatellites(): Flow<List<SatelliteEntity>>

    @Query("SELECT * FROM satellites ORDER BY noradId ASC")
    suspend fun getAllSatellitesOnce(): List<SatelliteEntity>

    @Query("SELECT COUNT(*) FROM satellites")
    suspend fun getSatelliteCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(satellites: List<SatelliteEntity>)

    @Query(
        """
        UPDATE satellites 
        SET isTrackedForAlerts = :isTracked, 
            minAlertElevationDeg = :minElevationDeg, 
            sunlitOnlyAlert = :sunlitOnly 
        WHERE noradId = :noradId
        """
    )
    suspend fun updateAlertRules(
        noradId: Int,
        isTracked: Boolean,
        minElevationDeg: Int,
        sunlitOnly: Boolean
    )

    @Query(
        """
        UPDATE satellites 
        SET tleLine1 = :line1, 
            tleLine2 = :line2, 
            sha256Signature = :sha256, 
            lastSyncedTimestampMs = :timestampMs 
        WHERE noradId = :noradId
        """
    )
    suspend fun updateSatelliteTle(
        noradId: Int,
        line1: String,
        line2: String,
        sha256: String,
        timestampMs: Long
    )
}
