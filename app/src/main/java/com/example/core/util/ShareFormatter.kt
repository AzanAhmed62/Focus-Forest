package com.example.core.util

import android.content.Intent
import com.example.domain.model.FocusSession

object ShareFormatter {
    /**
     * Generates a privacy-safe summary for Android share sheets.
     * Deliberately excludes any Android app usage names, durations, or device inventories.
     */
    fun formatSessionShareText(
        session: FocusSession,
        currentStreakDays: Int,
        forestLevel: Int
    ): String {
        val labelSuffix = if (session.intentionLabel.isNotBlank()) {
            " on \"${session.intentionLabel}\""
        } else {
            ""
        }
        return buildString {
            append("Just completed a ${session.completedMinutes}-minute ${session.sessionType.displayName} session$labelSuffix in Focus Forest! ")
            if (currentStreakDays > 0) {
                append("${currentStreakDays}-day consistency streak • ")
            }
            append("Forest Level $forestLevel (+${session.seedsAwarded} seeds planted).")
        }
    }

    fun buildTextShareIntent(text: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
}
