package com.azanahmed.focusforest.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.azanahmed.focusforest.data.local.db.entities.AchievementEntity
import com.azanahmed.focusforest.data.local.db.entities.AppUsageItemEntity
import com.azanahmed.focusforest.data.local.db.entities.AppUsageSnapshotEntity
import com.azanahmed.focusforest.data.local.db.entities.ChallengeEntity
import com.azanahmed.focusforest.data.local.db.entities.ChallengeProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.ForestProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.ForestTreeEntity
import com.azanahmed.focusforest.data.local.db.entities.OwnedItemEntity
import com.azanahmed.focusforest.data.local.db.entities.RewardLedgerEntity
import com.azanahmed.focusforest.data.local.db.entities.ShopItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Query("SELECT * FROM app_usage_snapshots ORDER BY localDate DESC")
    fun observeSnapshots(): Flow<List<AppUsageSnapshotEntity>>

    @Query("SELECT * FROM app_usage_snapshots ORDER BY localDate DESC")
    suspend fun getSnapshotsOnce(): List<AppUsageSnapshotEntity>

    @Query("SELECT * FROM app_usage_items WHERE snapshotDate = :date ORDER BY foregroundMillis DESC")
    fun observeItemsForDate(date: String): Flow<List<AppUsageItemEntity>>

    @Query("SELECT * FROM app_usage_items ORDER BY snapshotDate DESC, foregroundMillis DESC")
    fun observeAllItems(): Flow<List<AppUsageItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: AppUsageSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<AppUsageItemEntity>)

    @Query("DELETE FROM app_usage_items WHERE snapshotDate = :date")
    suspend fun deleteItemsForDate(date: String)

    @Transaction
    suspend fun replaceSnapshotAndItems(snapshot: AppUsageSnapshotEntity, items: List<AppUsageItemEntity>) {
        upsertSnapshot(snapshot)
        deleteItemsForDate(snapshot.localDate)
        if (items.isNotEmpty()) {
            upsertItems(items)
        }
    }

    @Query("DELETE FROM app_usage_items")
    suspend fun clearAllItems()

    @Query("DELETE FROM app_usage_snapshots")
    suspend fun clearAllSnapshots()

    @Transaction
    suspend fun clearAllUsageData() {
        clearAllItems()
        clearAllSnapshots()
    }
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges ORDER BY createdAtMs ASC")
    fun observeChallenges(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenge_progress")
    fun observeChallengeProgress(): Flow<List<ChallengeProgressEntity>>

    @Query("SELECT * FROM challenges WHERE periodKey = :periodKey")
    suspend fun getChallengesForPeriod(periodKey: String): List<ChallengeEntity>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun getChallengeById(id: String): ChallengeEntity?

    @Query("SELECT * FROM challenges ORDER BY createdAtMs ASC")
    suspend fun getAllChallengesOnce(): List<ChallengeEntity>

    @Query("SELECT * FROM challenge_progress")
    suspend fun getAllProgressOnce(): List<ChallengeProgressEntity>

    @Query("SELECT * FROM challenge_progress WHERE challengeId = :id")
    suspend fun getProgressById(id: String): ChallengeProgressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChallengesIfAbsent(challenges: List<ChallengeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgressIfAbsent(progress: List<ChallengeProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenge(challenge: ChallengeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ChallengeProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgressList(progress: List<ChallengeProgressEntity>)

    @Query("UPDATE challenges SET isArchived = 1 WHERE id = :id")
    suspend fun archiveChallenge(id: String)

    @Query("DELETE FROM challenge_progress WHERE challengeId = :id")
    suspend fun deleteProgressById(id: String)

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun deleteChallengeById(id: String)

    @Query("DELETE FROM challenge_progress")
    suspend fun clearProgress()

    @Query("DELETE FROM challenges")
    suspend fun clearChallenges()
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAchievementsOnce(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAchievement(achievement: AchievementEntity)

    @Query("DELETE FROM achievements")
    suspend fun clear()
}

@Dao
interface ForestDao {
    @Query("SELECT * FROM forest_progress WHERE id = 1")
    fun observeForestProgress(): Flow<ForestProgressEntity?>

    @Query("SELECT * FROM forest_progress WHERE id = 1")
    suspend fun getForestProgressOnce(): ForestProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForestProgress(progress: ForestProgressEntity)

    @Query("SELECT * FROM forest_trees ORDER BY plantedAtWallMs DESC")
    fun observeTrees(): Flow<List<ForestTreeEntity>>

    @Query("SELECT * FROM forest_trees ORDER BY plantedAtWallMs DESC")
    suspend fun getTreesOnce(): List<ForestTreeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTreeIfAbsent(tree: ForestTreeEntity): Long

    @Query("DELETE FROM forest_trees")
    suspend fun clearTrees()

    @Query("DELETE FROM forest_progress")
    suspend fun clearProgress()
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop_items ORDER BY category ASC, costLeafPoints ASC")
    fun observeCatalog(): Flow<List<ShopItemEntity>>

    @Query("SELECT * FROM owned_items")
    fun observeOwnedItems(): Flow<List<OwnedItemEntity>>

    @Query("SELECT * FROM shop_items")
    suspend fun getCatalogOnce(): List<ShopItemEntity>

    @Query("SELECT * FROM owned_items")
    suspend fun getOwnedItemsOnce(): List<OwnedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogItems(items: List<ShopItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOwnedItem(ownedItem: OwnedItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOwnedIfAbsent(ownedItem: OwnedItemEntity)

    @Query("DELETE FROM owned_items")
    suspend fun clearOwned()
}

@Dao
interface RewardLedgerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: RewardLedgerEntity): Long

    @Query("SELECT * FROM reward_ledger ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<RewardLedgerEntity>>

    @Query("SELECT * FROM reward_ledger ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<RewardLedgerEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM reward_ledger WHERE rewardKey = :rewardKey)")
    suspend fun exists(rewardKey: String): Boolean

    @Query(
        "SELECT COUNT(*) FROM reward_ledger WHERE sourceType = :sourceType AND createdAtMs >= :sinceWallMs AND (xpDelta > 0 OR seedsDelta > 0)"
    )
    suspend fun countBySourceSince(sourceType: String, sinceWallMs: Long): Int

    @Query("DELETE FROM reward_ledger")
    suspend fun clear()
}
