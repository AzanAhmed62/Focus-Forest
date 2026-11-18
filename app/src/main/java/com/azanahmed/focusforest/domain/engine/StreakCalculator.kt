package com.azanahmed.focusforest.domain.engine

import java.time.LocalDate

data class StreakResult(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val usedGraceToday: Boolean = false
)

/**
 * Recovery-friendly calendar-date consistency calculator.
 * - Active days (completed >= 1 focus session) build the streak.
 * - Marked rest days preserve continuity without penalizing the user.
 * - Up to 1 gentle grace/recovery day is tolerated per 7-day stretch so a single
 *   life interruption doesn't trigger destructive streak resets.
 * - If today has no completed session yet, yesterday's streak remains intact.
 */
object StreakCalculator {

    fun calculateStreaks(
        activeDates: Set<LocalDate>,
        restDates: Set<LocalDate>,
        today: LocalDate
    ): StreakResult {
        if (activeDates.isEmpty()) {
            return StreakResult(currentStreakDays = 0, longestStreakDays = 0)
        }

        val sortedActive = activeDates.sorted()
        val earliest = sortedActive.first()

        // Compute longest streak across history
        var longest = 0
        var running = 0
        var graceUsedRecently = false
        var daysSinceGrace = 99
        var cursor = earliest
        while (!cursor.isAfter(today)) {
            when {
                cursor in activeDates -> {
                    running++
                    daysSinceGrace++
                    if (daysSinceGrace >= 7) graceUsedRecently = false
                }
                cursor in restDates -> {
                    // Rest days bridge continuity without incrementing active count or breaking streak
                    daysSinceGrace++
                    if (daysSinceGrace >= 7) graceUsedRecently = false
                }
                else -> {
                    if (running > 0 && !graceUsedRecently && cursor != today) {
                        graceUsedRecently = true
                        daysSinceGrace = 0
                    } else if (cursor != today) {
                        running = 0
                        graceUsedRecently = false
                        daysSinceGrace = 99
                    }
                }
            }
            if (running > longest) longest = running
            cursor = cursor.plusDays(1)
        }

        // Compute current streak ending at today or yesterday (or bridged by rest/1 grace day)
        var current = 0
        var checkDate = if (today in activeDates || today in restDates) today else today.minusDays(1)
        var graceRemaining = 1
        var consecutiveSinceGrace = 0

        while (!checkDate.isBefore(earliest)) {
            when {
                checkDate in activeDates -> {
                    current++
                    consecutiveSinceGrace++
                    if (consecutiveSinceGrace >= 7) {
                        graceRemaining = 1
                    }
                    checkDate = checkDate.minusDays(1)
                }
                checkDate in restDates -> {
                    consecutiveSinceGrace++
                    checkDate = checkDate.minusDays(1)
                }
                else -> {
                    if (graceRemaining > 0 && current > 0) {
                        // Only bridge if the day immediately before the missed day was active or rest
                        val prevDay = checkDate.minusDays(1)
                        if (prevDay in activeDates || prevDay in restDates) {
                            graceRemaining--
                            consecutiveSinceGrace = 0
                            checkDate = prevDay
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
            }
        }

        return StreakResult(
            currentStreakDays = current,
            longestStreakDays = maxOf(longest, current),
            usedGraceToday = today !in activeDates && current > 0
        )
    }
}
