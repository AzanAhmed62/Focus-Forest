package com.azanahmed.focusforest.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.azanahmed.focusforest.data.local.db.entities.DailyStatsEntity
import com.azanahmed.focusforest.data.local.db.entities.FocusSessionEntity
import com.azanahmed.focusforest.data.local.db.entities.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAtWallMs DESC")
    fun observeAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtWallMs DESC")
    suspend fun getAllSessionsOnce(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE localDate = :localDate ORDER BY startedAtWallMs DESC")
    fun observeSessionsForDate(localDate: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): FocusSessionEntity?

    @Query("SELECT id FROM focus_sessions WHERE rewardGranted = 1")
    suspend fun getRewardedSessionIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions")
    suspend fun clear()
}

@Dao
interface DailyStatsDao {
    @Query("SELECT * FROM daily_stats ORDER BY localDate DESC")
    fun observeAllDailyStats(): Flow<List<DailyStatsEntity>>

    @Query("SELECT * FROM daily_stats ORDER BY localDate DESC")
    suspend fun getAllDailyStatsOnce(): List<DailyStatsEntity>

    @Query("SELECT * FROM daily_stats WHERE localDate = :localDate")
    fun observeDailyStatsForDate(localDate: String): Flow<DailyStatsEntity?>

    @Query("SELECT * FROM daily_stats WHERE localDate = :localDate")
    suspend fun getDailyStatsForDateOnce(localDate: String): DailyStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStats(stats: DailyStatsEntity)

    @Query("DELETE FROM daily_stats")
    suspend fun clear()
}
