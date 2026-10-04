package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.AuthorizedDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthorizedDeviceDao {
    @Query("SELECT * FROM authorized_devices WHERE isRevoked = 0 ORDER BY pairedAt DESC")
    fun getActiveDevices(): Flow<List<AuthorizedDeviceEntity>>

    @Query("SELECT * FROM authorized_devices ORDER BY pairedAt DESC")
    fun getAllDevices(): Flow<List<AuthorizedDeviceEntity>>

    @Query("SELECT * FROM authorized_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): AuthorizedDeviceEntity?

    @Query("SELECT * FROM authorized_devices WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun getDeviceByFingerprint(fingerprint: String): AuthorizedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: AuthorizedDeviceEntity)

    @Query("UPDATE authorized_devices SET isRevoked = 1 WHERE deviceId = :deviceId")
    suspend fun revokeDevice(deviceId: String)

    @Query("UPDATE authorized_devices SET lastConnectedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastConnected(deviceId: String, timestamp: Long)

    @Query("DELETE FROM authorized_devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    @Query("SELECT COUNT(*) FROM authorized_devices WHERE isRevoked = 0")
    fun getActiveDeviceCount(): Flow<Int>
}
