package com.azanahmed.focusforest.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.azanahmed.focusforest.data.local.db.entities.FocusCategoryEntity
import com.azanahmed.focusforest.data.local.db.entities.FocusRoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusRoutineDao {
    @Query(
        "SELECT * FROM focus_routines WHERE isArchived = 0 " +
            "ORDER BY isPinned DESC, sortOrder ASC, name ASC"
    )
    fun observeActiveRoutines(): Flow<List<FocusRoutineEntity>>

    @Query("SELECT * FROM focus_routines ORDER BY isArchived ASC, isPinned DESC, sortOrder ASC, name ASC")
    fun observeAllRoutines(): Flow<List<FocusRoutineEntity>>

    @Query(
        "SELECT * FROM focus_routines WHERE isArchived = 0 AND isPinned = 1 " +
            "ORDER BY sortOrder ASC, name ASC LIMIT :limit"
    )
    fun observePinnedRoutines(limit: Int): Flow<List<FocusRoutineEntity>>

    @Query("SELECT * FROM focus_routines")
    suspend fun getAllRoutinesOnce(): List<FocusRoutineEntity>

    @Query("SELECT * FROM focus_routines WHERE id = :id")
    suspend fun getRoutineOnce(id: String): FocusRoutineEntity?

    @Query("SELECT COUNT(*) FROM focus_routines")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: FocusRoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutines(routines: List<FocusRoutineEntity>)

    @Query("DELETE FROM focus_routines WHERE id = :id")
    suspend fun deleteRoutineById(id: String)

    @Query("DELETE FROM focus_routines")
    suspend fun clear()
}

@Dao
interface FocusCategoryDao {
    @Query("SELECT * FROM focus_categories WHERE isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeActiveCategories(): Flow<List<FocusCategoryEntity>>

    @Query("SELECT * FROM focus_categories ORDER BY isArchived ASC, sortOrder ASC, name ASC")
    fun observeAllCategories(): Flow<List<FocusCategoryEntity>>

    @Query("SELECT * FROM focus_categories")
    suspend fun getAllCategoriesOnce(): List<FocusCategoryEntity>

    @Query("SELECT * FROM focus_categories WHERE id = :id")
    suspend fun getCategoryOnce(id: String): FocusCategoryEntity?

    @Query("SELECT COUNT(*) FROM focus_categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: FocusCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<FocusCategoryEntity>)

    @Query("DELETE FROM focus_categories")
    suspend fun clear()
}
