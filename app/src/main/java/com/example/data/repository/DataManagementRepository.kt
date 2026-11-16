package com.example.data.repository

import android.content.Context
import android.content.Intent
import com.example.core.time.ClockProvider
import com.example.core.util.CsvExporter
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataManagementRepository(
    private val db: FocusForestDatabase,
    private val prefs: UserPreferencesDataStore,
    private val clockProvider: ClockProvider
) {
    suspend fun exportFocusSessionsCsvIntent(context: Context): Intent = withContext(Dispatchers.IO) {
        val sessions = db.focusSessionDao().getAllSessionsOnce().map { it.toDomain() }
        val csv = CsvExporter.buildFocusSessionsCsv(sessions, clockProvider.zoneId())
        val date = clockProvider.todayLocalDate()
        val uri = CsvExporter.writeCsvToCacheAndGetUri(
            context = context,
            fileName = "focus_forest_sessions_$date.csv",
            csvContent = csv
        )
        CsvExporter.buildShareCsvIntent(uri, "Focus Forest Sessions ($date)")
    }

    suspend fun exportDailyStatsCsvIntent(context: Context): Intent = withContext(Dispatchers.IO) {
        val stats = db.dailyStatsDao().getAllDailyStatsOnce().map { it.toDomain() }
        val csv = CsvExporter.buildDailyStatsCsv(stats)
        val date = clockProvider.todayLocalDate()
        val uri = CsvExporter.writeCsvToCacheAndGetUri(
            context = context,
            fileName = "focus_forest_daily_stats_$date.csv",
            csvContent = csv
        )
        CsvExporter.buildShareCsvIntent(uri, "Focus Forest Daily Stats ($date)")
    }

    suspend fun deleteAllLocalData() {
        db.clearAllUserData()
        prefs.clearAll()
    }
}
