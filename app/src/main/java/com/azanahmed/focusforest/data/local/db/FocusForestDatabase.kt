package com.azanahmed.focusforest.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.azanahmed.focusforest.data.local.db.dao.AchievementDao
import com.azanahmed.focusforest.data.local.db.dao.ChallengeDao
import com.azanahmed.focusforest.data.local.db.dao.DailyStatsDao
import com.azanahmed.focusforest.data.local.db.dao.FocusSessionDao
import com.azanahmed.focusforest.data.local.db.dao.ForestDao
import com.azanahmed.focusforest.data.local.db.dao.RewardLedgerDao
import com.azanahmed.focusforest.data.local.db.dao.ShopDao
import com.azanahmed.focusforest.data.local.db.dao.UsageDao
import com.azanahmed.focusforest.data.local.db.dao.UserProfileDao
import com.azanahmed.focusforest.data.local.db.entities.AchievementEntity
import com.azanahmed.focusforest.data.local.db.entities.AppUsageItemEntity
import com.azanahmed.focusforest.data.local.db.entities.AppUsageSnapshotEntity
import com.azanahmed.focusforest.data.local.db.entities.ChallengeEntity
import com.azanahmed.focusforest.data.local.db.entities.ChallengeProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.DailyStatsEntity
import com.azanahmed.focusforest.data.local.db.entities.FocusSessionEntity
import com.azanahmed.focusforest.data.local.db.entities.ForestProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.ForestTreeEntity
import com.azanahmed.focusforest.data.local.db.entities.OwnedItemEntity
import com.azanahmed.focusforest.data.local.db.entities.RewardLedgerEntity
import com.azanahmed.focusforest.data.local.db.entities.ShopItemEntity
import com.azanahmed.focusforest.data.local.db.entities.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        FocusSessionEntity::class,
        DailyStatsEntity::class,
        AppUsageSnapshotEntity::class,
        AppUsageItemEntity::class,
        ChallengeEntity::class,
        ChallengeProgressEntity::class,
        AchievementEntity::class,
        ForestProgressEntity::class,
        ForestTreeEntity::class,
        ShopItemEntity::class,
        OwnedItemEntity::class,
        RewardLedgerEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class FocusForestDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun usageDao(): UsageDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun achievementDao(): AchievementDao
    abstract fun forestDao(): ForestDao
    abstract fun shopDao(): ShopDao
    abstract fun rewardLedgerDao(): RewardLedgerDao

    suspend fun clearAllUserData() {
        withTransaction {
            focusSessionDao().clear()
            dailyStatsDao().clear()
            usageDao().clearAllUsageData()
            challengeDao().clearProgress()
            challengeDao().clearChallenges()
            achievementDao().clear()
            forestDao().clearTrees()
            forestDao().clearProgress()
            shopDao().clearOwned()
            rewardLedgerDao().clear()
            userProfileDao().clear()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: FocusForestDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_usage_snapshots ADD COLUMN completeness TEXT NOT NULL DEFAULT 'COMPLETE'"
                )
                db.execSQL(
                    "ALTER TABLE app_usage_snapshots ADD COLUMN totalObservedMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE app_usage_snapshots ADD COLUMN monitoredAppsMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE challenges ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reward_ledger (
                        rewardKey TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        xpDelta INTEGER NOT NULL,
                        seedsDelta INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        PRIMARY KEY(rewardKey)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): FocusForestDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusForestDatabase::class.java,
                    "focus_forest.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
