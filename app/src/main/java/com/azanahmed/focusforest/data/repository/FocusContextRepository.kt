package com.azanahmed.focusforest.data.repository

import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import com.azanahmed.focusforest.domain.model.ChainConfig
import com.azanahmed.focusforest.domain.model.FocusCategory
import com.azanahmed.focusforest.domain.model.FocusRoutine
import com.azanahmed.focusforest.domain.model.validation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Local-only authority for reusable focus routines and session categories.
 *
 * Routines are templates, never live timers. Starting one copies its configuration into the
 * existing chain/timer snapshots; editing or deleting the template afterwards cannot mutate an
 * active session or completed history.
 */
class FocusContextRepository(
    private val db: FocusForestDatabase,
    private val clockProvider: ClockProvider
) {
    private val routineDao = db.focusRoutineDao()
    private val categoryDao = db.focusCategoryDao()

    val activeRoutines: Flow<List<FocusRoutine>> = routineDao.observeActiveRoutines().map { list ->
        list.map { it.toDomain() }
    }

    val allRoutines: Flow<List<FocusRoutine>> = routineDao.observeAllRoutines().map { list ->
        list.map { it.toDomain() }
    }

    /** Bounded pinned list used by Today for one-tap starts. */
    val pinnedRoutines: Flow<List<FocusRoutine>> = routineDao.observePinnedRoutines(3).map { list ->
        list.map { it.toDomain() }
    }

    val activeCategories: Flow<List<FocusCategory>> = categoryDao.observeActiveCategories().map { list ->
        list.map { it.toDomain() }
    }

    val allCategories: Flow<List<FocusCategory>> = categoryDao.observeAllCategories().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getRoutineOnce(id: String): FocusRoutine? =
        routineDao.getRoutineOnce(id)?.toDomain()

    suspend fun getCategoryOnce(id: String): FocusCategory? =
        categoryDao.getCategoryOnce(id)?.toDomain()

    suspend fun createRoutine(draft: FocusRoutine): Result<FocusRoutine> = runCatching {
        val validation = draft.validation()
        require(validation.isValid) { validation.firstError() ?: "Invalid routine." }
        val existing = routineDao.getAllRoutinesOnce().map { it.toDomain() }
        val name = draft.name.trim()
        require(existing.none { it.name.equals(name, ignoreCase = true) }) {
            "A routine with that name already exists."
        }
        val routine = draft.copy(
            id = "routine_${UUID.randomUUID()}",
            name = name,
            description = draft.description.trim(),
            isBuiltIn = false,
            isArchived = false,
            isPinned = draft.isPinned,
            sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            createdAtMs = clockProvider.nowWallClockMs(),
            lastUsedAtMs = null
        )
        routineDao.upsertRoutine(routine.toEntity())
        routine
    }

    /** Updates only user-editable fields; pin/archive/order/creation stay under dedicated actions. */
    suspend fun updateRoutine(updated: FocusRoutine): Result<FocusRoutine> = runCatching {
        val validation = updated.validation()
        require(validation.isValid) { validation.firstError() ?: "Invalid routine." }
        val existing = routineDao.getRoutineOnce(updated.id)?.toDomain()
            ?: error("Routine not found.")
        val name = updated.name.trim()
        val all = routineDao.getAllRoutinesOnce().map { it.toDomain() }
        require(all.none { it.id != existing.id && it.name.equals(name, ignoreCase = true) }) {
            "A routine with that name already exists."
        }
        val merged = existing.copy(
            name = name,
            description = updated.description.trim(),
            config = updated.config,
            ambientPreset = updated.ambientPreset,
            defaultCategoryId = updated.defaultCategoryId
        )
        routineDao.upsertRoutine(merged.toEntity())
        merged
    }

    suspend fun duplicateRoutine(id: String): Result<FocusRoutine> = runCatching {
        val source = routineDao.getRoutineOnce(id)?.toDomain() ?: error("Routine not found.")
        val all = routineDao.getAllRoutinesOnce().map { it.toDomain() }
        val baseName = "${source.name} copy".take(40)
        var candidate = baseName
        var suffix = 2
        while (all.any { it.name.equals(candidate, ignoreCase = true) }) {
            candidate = "${baseName.take(36)} $suffix"
            suffix++
        }
        val copy = source.copy(
            id = "routine_${UUID.randomUUID()}",
            name = candidate,
            isPinned = false,
            isArchived = false,
            isBuiltIn = false,
            sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            createdAtMs = clockProvider.nowWallClockMs(),
            lastUsedAtMs = null
        )
        routineDao.upsertRoutine(copy.toEntity())
        copy
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val routine = routineDao.getRoutineOnce(id)?.toDomain() ?: return
        if (routine.isArchived && pinned) return
        routineDao.upsertRoutine(routine.copy(isPinned = pinned).toEntity())
    }

    suspend fun archiveRoutine(id: String, archived: Boolean) {
        val routine = routineDao.getRoutineOnce(id)?.toDomain() ?: return
        val updated = routine.copy(
            isArchived = archived,
            isPinned = if (archived) false else routine.isPinned
        )
        routineDao.upsertRoutine(updated.toEntity())
    }

    suspend fun deleteRoutine(id: String) {
        routineDao.deleteRoutineById(id)
    }

    /** Swaps ordering with the adjacent active routine. [direction] is -1 for up, +1 for down. */
    suspend fun moveRoutine(id: String, direction: Int) {
        val routines = routineDao.getAllRoutinesOnce()
            .map { it.toDomain() }
            .filter { !it.isArchived }
            .sortedWith(compareBy<FocusRoutine> { it.sortOrder }.thenBy { it.name })
        val index = routines.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + direction).coerceIn(0, routines.lastIndex)
        if (target == index) return
        val current = routines[index]
        val other = routines[target]
        routineDao.upsertRoutine(current.copy(sortOrder = other.sortOrder).toEntity())
        routineDao.upsertRoutine(other.copy(sortOrder = current.sortOrder).toEntity())
    }

    suspend fun markRoutineUsed(id: String) {
        val routine = routineDao.getRoutineOnce(id)?.toDomain() ?: return
        routineDao.upsertRoutine(routine.copy(lastUsedAtMs = clockProvider.nowWallClockMs()).toEntity())
    }

    suspend fun createCategory(name: String): Result<FocusCategory> = runCatching {
        val trimmed = validateCategoryName(name)
        val all = categoryDao.getAllCategoriesOnce().map { it.toDomain() }
        require(all.none { it.name.equals(trimmed, ignoreCase = true) }) {
            "A category with that name already exists."
        }
        val category = FocusCategory(
            id = "cat_${UUID.randomUUID()}",
            name = trimmed,
            sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            isBuiltIn = false,
            createdAtMs = clockProvider.nowWallClockMs()
        )
        categoryDao.upsertCategory(category.toEntity())
        category
    }

    suspend fun renameCategory(id: String, name: String): Result<FocusCategory> = runCatching {
        val trimmed = validateCategoryName(name)
        val existing = categoryDao.getCategoryOnce(id)?.toDomain() ?: error("Category not found.")
        val all = categoryDao.getAllCategoriesOnce().map { it.toDomain() }
        require(all.none { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
            "A category with that name already exists."
        }
        val updated = existing.copy(name = trimmed)
        categoryDao.upsertCategory(updated.toEntity())
        updated
    }

    suspend fun archiveCategory(id: String, archived: Boolean) {
        val category = categoryDao.getCategoryOnce(id)?.toDomain() ?: return
        categoryDao.upsertCategory(category.copy(isArchived = archived).toEntity())
    }

    /**
     * Seeds the documented starter templates. Called after onboarding and by the v2→v3
     * migration, never on ordinary app starts, so a user who deletes every routine does not
     * get them silently recreated.
     */
    suspend fun seedDefaultsIfEmpty() {
        val now = clockProvider.nowWallClockMs()
        if (categoryDao.count() == 0) {
            categoryDao.upsertCategories(DefaultFocusContext.categories(now).map { it.toEntity() })
        }
        if (routineDao.count() == 0) {
            routineDao.upsertRoutines(DefaultFocusContext.routines(now).map { it.toEntity() })
        }
    }

    private fun validateCategoryName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Give the category a name." }
        require(trimmed.length <= 30) { "Category names can be at most 30 characters." }
        return trimmed
    }
}

/**
 * Documented starter content. These are conveniences, not prescriptions; every field is
 * editable and every routine can be archived or deleted.
 */
object DefaultFocusContext {
    const val CLASSIC_ROUTINE_ID = "routine_classic"
    const val EXTENDED_ROUTINE_ID = "routine_extended"
    const val DEEP_WORK_ROUTINE_ID = "routine_deep_work"
    const val READING_ROUTINE_ID = "routine_reading"
    const val STUDY_ROUTINE_ID = "routine_study"
    const val CODING_ROUTINE_ID = "routine_coding"

    fun categories(nowMs: Long): List<FocusCategory> = listOf(
        FocusCategory("cat_study", "Study", sortOrder = 0, isBuiltIn = true, createdAtMs = nowMs),
        FocusCategory("cat_coding", "Coding", sortOrder = 1, isBuiltIn = true, createdAtMs = nowMs),
        FocusCategory("cat_reading", "Reading", sortOrder = 2, isBuiltIn = true, createdAtMs = nowMs),
        FocusCategory("cat_work", "Work", sortOrder = 3, isBuiltIn = true, createdAtMs = nowMs),
        FocusCategory("cat_personal", "Personal", sortOrder = 4, isBuiltIn = true, createdAtMs = nowMs),
        FocusCategory("cat_exercise", "Exercise", sortOrder = 5, isBuiltIn = true, createdAtMs = nowMs)
    )

    fun routines(nowMs: Long): List<FocusRoutine> = listOf(
        FocusRoutine(
            id = CLASSIC_ROUTINE_ID,
            name = "Classic Focus",
            description = "A familiar four-round Pomodoro rhythm.",
            config = ChainConfig(
                totalFocusSessions = 4,
                focusMinutes = 25,
                shortBreakMinutes = 5,
                longBreakMinutes = 15,
                longBreakFrequency = 4,
                autoTransition = false
            ),
            isPinned = true,
            sortOrder = 0,
            isBuiltIn = true,
            createdAtMs = nowMs
        ),
        FocusRoutine(
            id = EXTENDED_ROUTINE_ID,
            name = "Extended Focus",
            description = "Two longer focus blocks with generous breaks.",
            config = ChainConfig(
                totalFocusSessions = 2,
                focusMinutes = 50,
                shortBreakMinutes = 10,
                longBreakMinutes = 20,
                longBreakFrequency = 2,
                autoTransition = false
            ),
            defaultCategoryId = "cat_work",
            isPinned = true,
            sortOrder = 1,
            isBuiltIn = true,
            createdAtMs = nowMs
        ),
        FocusRoutine(
            id = DEEP_WORK_ROUTINE_ID,
            name = "Deep Work",
            description = "One long, uninterrupted block.",
            config = ChainConfig(
                totalFocusSessions = 1,
                focusMinutes = 90,
                shortBreakMinutes = 10,
                longBreakMinutes = 20,
                longBreakFrequency = 2,
                autoTransition = false
            ),
            ambientPreset = AmbientSoundPreset.BROWN_NOISE,
            defaultCategoryId = "cat_work",
            sortOrder = 2,
            isBuiltIn = true,
            createdAtMs = nowMs
        ),
        FocusRoutine(
            id = READING_ROUTINE_ID,
            name = "Reading",
            description = "Two calm reading blocks.",
            config = ChainConfig(
                totalFocusSessions = 2,
                focusMinutes = 30,
                shortBreakMinutes = 5,
                longBreakMinutes = 15,
                longBreakFrequency = 2,
                autoTransition = false
            ),
            ambientPreset = AmbientSoundPreset.SOFT_RAIN,
            defaultCategoryId = "cat_reading",
            sortOrder = 3,
            isBuiltIn = true,
            createdAtMs = nowMs
        ),
        FocusRoutine(
            id = STUDY_ROUTINE_ID,
            name = "Study",
            description = "Three focused study rounds.",
            config = ChainConfig(
                totalFocusSessions = 3,
                focusMinutes = 25,
                shortBreakMinutes = 5,
                longBreakMinutes = 15,
                longBreakFrequency = 3,
                autoTransition = false
            ),
            ambientPreset = AmbientSoundPreset.FOREST_WIND,
            defaultCategoryId = "cat_study",
            sortOrder = 4,
            isBuiltIn = true,
            createdAtMs = nowMs
        ),
        FocusRoutine(
            id = CODING_ROUTINE_ID,
            name = "Coding",
            description = "Three deep implementation blocks.",
            config = ChainConfig(
                totalFocusSessions = 3,
                focusMinutes = 45,
                shortBreakMinutes = 5,
                longBreakMinutes = 20,
                longBreakFrequency = 3,
                autoTransition = false
            ),
            ambientPreset = AmbientSoundPreset.BROWN_NOISE,
            defaultCategoryId = "cat_coding",
            sortOrder = 5,
            isBuiltIn = true,
            createdAtMs = nowMs
        )
    )
}
