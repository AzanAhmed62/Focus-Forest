package com.example.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.core.time.ClockProvider
import com.example.core.util.CsvExporter
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import com.example.notifications.FocusWorkers
import com.example.platform.audio.AmbientAudioController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DataManagementRepository(
    private val db: FocusForestDatabase,
    private val prefs: UserPreferencesDataStore,
    private val clockProvider: ClockProvider,
    private val appContext: Context? = null,
    private val usageRepository: UsageRepository? = null,
    private val ambientAudioController: AmbientAudioController? = null
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
        ambientAudioController?.stop()
        appContext?.let { ctx ->
            FocusWorkers.cancelAll(ctx)
            runCatching { NotificationManagerCompat.from(ctx).cancelAll() }
            withContext(Dispatchers.IO) {
                runCatching { File(ctx.cacheDir, "exports").deleteRecursively() }
            }
        }
        db.clearAllUserData()
        prefs.clearAll()
        usageRepository?.resetInMemoryCache()
    }
}
