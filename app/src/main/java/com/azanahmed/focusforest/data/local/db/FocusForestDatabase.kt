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
import com.azanahmed.focusforest.data.local.db.dao.FocusCategoryDao
import com.azanahmed.focusforest.data.local.db.dao.FocusRoutineDao
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
import com.azanahmed.focusforest.data.local.db.entities.FocusCategoryEntity
import com.azanahmed.focusforest.data.local.db.entities.FocusRoutineEntity
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
        RewardLedgerEntity::class,
        FocusCategoryEntity::class,
        FocusRoutineEntity::class
    ],
    version = 3,
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
    abstract fun focusCategoryDao(): FocusCategoryDao
    abstract fun focusRoutineDao(): FocusRoutineDao

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
            focusRoutineDao().clear()
            focusCategoryDao().clear()
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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Session context is additive and defaults to empty for existing history.
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN routineId TEXT")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN routineNameSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN routineSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN categoryId TEXT")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN categoryNameSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN reflection TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_routineId ON focus_sessions (routineId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_categoryId ON focus_sessions (categoryId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_categories (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        isArchived INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        isBuiltIn INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_focus_categories_isArchived ON focus_categories (isArchived)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_routines (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        focusMinutes INTEGER NOT NULL,
                        shortBreakMinutes INTEGER NOT NULL,
                        longBreakMinutes INTEGER NOT NULL,
                        longBreakFrequency INTEGER NOT NULL,
                        totalFocusSessions INTEGER NOT NULL,
                        autoTransition INTEGER NOT NULL,
                        ambientPreset TEXT NOT NULL,
                        defaultCategoryId TEXT,
                        isPinned INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        isBuiltIn INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        lastUsedAtMs INTEGER,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_routines_isArchived ON focus_routines (isArchived)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_routines_isPinned ON focus_routines (isPinned)")

                // Documented system starter categories. Existing sessions keep empty snapshots;
                // no historical meaning is invented by the migration.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO focus_categories
                        (id, name, isArchived, sortOrder, isBuiltIn, createdAtMs)
                    VALUES
                        ('cat_study', 'Study', 0, 0, 1, 0),
                        ('cat_coding', 'Coding', 0, 1, 1, 0),
                        ('cat_reading', 'Reading', 0, 2, 1, 0),
                        ('cat_work', 'Work', 0, 3, 1, 0),
                        ('cat_personal', 'Personal', 0, 4, 1, 0),
                        ('cat_exercise', 'Exercise', 0, 5, 1, 0)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO focus_routines
                        (id, name, description, focusMinutes, shortBreakMinutes, longBreakMinutes,
                         longBreakFrequency, totalFocusSessions, autoTransition, ambientPreset,
                         defaultCategoryId, isPinned, sortOrder, isArchived, isBuiltIn, createdAtMs,
                         lastUsedAtMs)
                    VALUES
                        ('routine_classic', 'Classic Focus', 'A familiar four-round Pomodoro rhythm.',
                         25, 5, 15, 4, 4, 0, 'NONE', NULL, 1, 0, 0, 1, 0, NULL),
                        ('routine_extended', 'Extended Focus', 'Two longer focus blocks with generous breaks.',
                         50, 10, 20, 2, 2, 0, 'NONE', 'cat_work', 1, 1, 0, 1, 0, NULL),
                        ('routine_deep_work', 'Deep Work', 'One long, uninterrupted block.',
                         90, 10, 20, 2, 1, 0, 'BROWN_NOISE', 'cat_work', 0, 2, 0, 1, 0, NULL),
                        ('routine_reading', 'Reading', 'Two calm reading blocks.',
                         30, 5, 15, 2, 2, 0, 'SOFT_RAIN', 'cat_reading', 0, 3, 0, 1, 0, NULL),
                        ('routine_study', 'Study', 'Three focused study rounds.',
                         25, 5, 15, 3, 3, 0, 'FOREST_WIND', 'cat_study', 0, 4, 0, 1, 0, NULL),
                        ('routine_coding', 'Coding', 'Three deep implementation blocks.',
                         45, 5, 20, 3, 3, 0, 'BROWN_NOISE', 'cat_coding', 0, 5, 0, 1, 0, NULL)
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
