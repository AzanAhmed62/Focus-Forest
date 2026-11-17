package com.example.data.local.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val primaryIntention: String,
    val dailyFocusGoalMinutes: Int,
    val lifetimeXp: Int,
    val currentLevel: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val equippedAvatarId: String,
    val equippedThemeId: String,
    val createdAtMs: Long
)

@Entity(
    tableName = "focus_sessions",
    indices = [Index(value = ["localDate"]), Index(value = ["status"])]
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val sessionType: String,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val status: String,
    val startedAtWallMs: Long,
    val endedAtWallMs: Long?,
    val localDate: String,
    val xpAwarded: Int,
    val seedsAwarded: Int,
    val intentionLabel: String,
    val rewardGranted: Boolean
)

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val localDate: String,
    val focusMinutes: Int,
    val completedSessions: Int,
    val goalMinutes: Int,
    val goalMet: Boolean,
    val xpEarned: Int,
    val seedsEarned: Int,
    val isRestDay: Boolean
)

@Entity(tableName = "app_usage_snapshots")
data class AppUsageSnapshotEntity(
    @PrimaryKey val localDate: String,
    val totalObservedMinutes: Int,
    val monitoredAppsMinutes: Int,
    val hasUsableData: Boolean,
    val capturedAtWallMs: Long,
    @ColumnInfo(defaultValue = "COMPLETE")
    val completeness: String = "COMPLETE",
    @ColumnInfo(defaultValue = "0")
    val totalObservedMillis: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val monitoredAppsMillis: Long = 0L
)

@Entity(
    tableName = "app_usage_items",
    primaryKeys = ["snapshotDate", "packageName"],
    indices = [Index(value = ["snapshotDate"])]
)
data class AppUsageItemEntity(
    val snapshotDate: String,
    val packageName: String,
    val appName: String,
    val foregroundMillis: Long,
    val foregroundMinutes: Int,
    val isMonitored: Boolean
)

@Entity(
    tableName = "challenges",
    indices = [Index(value = ["periodKey"]), Index(value = ["period"])]
)
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val iconType: String,
    val period: String,
    val metricType: String,
    val targetValue: Int,
    val rewardXp: Int,
    val rewardSeeds: Int,
    val periodKey: String,
    val createdAtMs: Long,
    val targetPackageName: String?,
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false
)

@Entity(tableName = "challenge_progress")
data class ChallengeProgressEntity(
    @PrimaryKey val challengeId: String,
    val currentProgress: Int,
    val status: String,
    val completedAtMs: Long?,
    val rewardClaimed: Boolean
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val type: String,
    val unlocked: Boolean,
    val unlockedAtMs: Long?
)

@Entity(tableName = "forest_progress")
data class ForestProgressEntity(
    @PrimaryKey val id: Int = 1,
    val totalSeeds: Int,
    val spendableLeafPoints: Int,
    val forestLevel: Int,
    val totalTreesPlanted: Int,
    val equippedEnvironmentId: String,
    val equippedDecorationsCsv: String
)

@Entity(tableName = "forest_trees")
data class ForestTreeEntity(
    @PrimaryKey val id: String,
    val species: String,
    val plantedDate: String,
    val focusMinutes: Int,
    val plantedAtWallMs: Long
)

@Entity(tableName = "shop_items")
data class ShopItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val costLeafPoints: Int,
    val previewColorHex: Long
)

@Entity(tableName = "owned_items")
data class OwnedItemEntity(
    @PrimaryKey val itemId: String,
    val acquiredAtMs: Long,
    val isEquipped: Boolean
)

@Entity(tableName = "reward_ledger")
data class RewardLedgerEntity(
    @PrimaryKey val rewardKey: String,
    val sourceType: String,
    val sourceId: String,
    val xpDelta: Int,
    val seedsDelta: Int,
    val createdAtMs: Long
)
