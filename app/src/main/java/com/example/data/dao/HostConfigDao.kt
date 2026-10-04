package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.HostConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostConfigDao {
    @Query("SELECT * FROM host_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<HostConfigEntity?>

    @Query("SELECT * FROM host_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): HostConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: HostConfigEntity)

    @Query("UPDATE host_config SET pairingNonce = :nonce, pairingNonceExpiresAt = :expiresAt WHERE id = 1")
    suspend fun updatePairingNonce(nonce: String, expiresAt: Long)
}
