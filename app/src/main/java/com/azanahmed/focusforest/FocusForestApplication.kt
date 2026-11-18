package com.azanahmed.focusforest

import android.app.Application
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.notifications.FocusWorkers
import com.azanahmed.focusforest.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FocusForestApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
        appScope.launch {
            runCatching {
                val notifPrefs = container.preferencesDataStore.notificationPreferences.first()
                FocusWorkers.syncAllSchedules(this@FocusForestApplication, notifPrefs)
            }
        }
    }
}
