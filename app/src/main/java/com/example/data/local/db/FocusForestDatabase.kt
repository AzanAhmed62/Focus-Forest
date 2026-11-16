package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.data.local.db.dao.AchievementDao
import com.example.data.local.db.dao.ChallengeDao
import com.example.data.local.db.dao.DailyStatsDao
import com.example.data.local.db.dao.FocusSessionDao
import com.example.data.local.db.dao.ForestDao
import com.example.data.local.db.dao.ShopDao
import com.example.data.local.db.dao.UsageDao
import com.example.data.local.db.dao.UserProfileDao
import com.example.data.local.db.entities.AchievementEntity
import com.example.data.local.db.entities.AppUsageItemEntity
import com.example.data.local.db.entities.AppUsageSnapshotEntity
import com.example.data.local.db.entities.ChallengeEntity
import com.example.data.local.db.entities.ChallengeProgressEntity
import com.example.data.local.db.entities.DailyStatsEntity
import com.example.data.local.db.entities.FocusSessionEntity
import com.example.data.local.db.entities.ForestProgressEntity
import com.example.data.local.db.entities.ForestTreeEntity
import com.example.data.local.db.entities.OwnedItemEntity
import com.example.data.local.db.entities.ShopItemEntity
import com.example.data.local.db.entities.UserProfileEntity

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
        OwnedItemEntity::class
    ],
    version = 1,
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
            userProfileDao().clear()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: FocusForestDatabase? = null

        fun getInstance(context: Context): FocusForestDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusForestDatabase::class.java,
                    "focus_forest.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
