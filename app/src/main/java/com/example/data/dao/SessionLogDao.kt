package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.SessionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs ORDER BY startedAt DESC LIMIT 50")
    fun getRecentSessions(): Flow<List<SessionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionLogEntity): Long

    @Query("UPDATE session_logs SET endedAt = :endedAt, bytesTransferred = :bytes, terminationReason = :reason WHERE sessionId = :sessionId")
    suspend fun closeSession(sessionId: String, endedAt: Long, bytes: Long, reason: String)

    @Query("DELETE FROM session_logs")
    suspend fun clearLogs()
}
