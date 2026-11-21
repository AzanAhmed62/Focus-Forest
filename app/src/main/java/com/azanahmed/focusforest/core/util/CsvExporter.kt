package com.azanahmed.focusforest.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.azanahmed.focusforest.domain.model.DailyStats
import com.azanahmed.focusforest.domain.model.FocusSession
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun buildFocusSessionsCsv(
        sessions: List<FocusSession>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val sb = StringBuilder()
        sb.appendLine(
            "id,date,session_type,planned_minutes,completed_minutes,status,xp_awarded," +
                "seeds_awarded,label,started_at,ended_at,routine_id,routine_name," +
                "routine_snapshot,category_id,category_name,note,reflection"
        )
        sessions.forEach { s ->
            val started = Instant.ofEpochMilli(s.startedAtWallMs).atZone(zoneId).format(isoFormatter)
            val ended = s.endedAtWallMs?.let {
                Instant.ofEpochMilli(it).atZone(zoneId).format(isoFormatter)
            } ?: ""
            sb.appendLine(
                listOf(
                    escape(s.id),
                    escape(s.localDate),
                    escape(s.sessionType.name),
                    s.plannedMinutes.toString(),
                    s.completedMinutes.toString(),
                    escape(s.status.name),
                    s.xpAwarded.toString(),
                    s.seedsAwarded.toString(),
                    escape(s.intentionLabel),
                    escape(started),
                    escape(ended),
                    escape(s.routineId.orEmpty()),
                    escape(s.routineNameSnapshot),
                    escape(s.routineSnapshot),
                    escape(s.categoryId.orEmpty()),
                    escape(s.categoryNameSnapshot),
                    escape(s.note),
                    escape(s.reflection?.name.orEmpty())
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun buildDailyStatsCsv(stats: List<DailyStats>): String {
        val sb = StringBuilder()
        sb.appendLine("date,focus_minutes,completed_sessions,goal_minutes,goal_met,xp_earned,seeds_earned,is_rest_day")
        stats.forEach { d ->
            sb.appendLine(
                listOf(
                    escape(d.localDate),
                    d.focusMinutes.toString(),
                    d.completedSessions.toString(),
                    d.goalMinutes.toString(),
                    d.goalMet.toString(),
                    d.xpEarned.toString(),
                    d.seedsEarned.toString(),
                    d.isRestDay.toString()
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun writeCsvToCacheAndGetUri(
        context: Context,
        fileName: String,
        csvContent: String
    ): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        file.writeText(csvContent, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun buildShareCsvIntent(uri: Uri, title: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun escape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
