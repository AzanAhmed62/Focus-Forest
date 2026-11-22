package com.azanahmed.focusforest.features.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.data.local.datastore.UserPreferencesDataStore
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import com.azanahmed.focusforest.domain.model.AppUsageSummary
import com.azanahmed.focusforest.domain.model.LaunchableAppInfo
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.platform.audio.AmbientAudioController
import com.azanahmed.focusforest.platform.audio.AmbientPlaybackState
import com.azanahmed.focusforest.platform.usage.UsageStatsGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

private class SettingsTestClock : ClockProvider {
    override fun nowWallClockMs(): Long = 1_795_030_000_000L
    override fun elapsedRealtimeMs(): Long = 100_000L
    override fun bootCount(): Int = 1
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

private class SettingsUsageGateway : UsageStatsGateway {
    override fun checkUsagePermission(): UsagePermissionStatus = UsagePermissionStatus.DENIED
    override fun getLaunchableApps(): List<LaunchableAppInfo> = emptyList()
    override fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>
    ): List<AppUsageSummary> = emptyList()
}

private class SettingsAudioController : AmbientAudioController {
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val activePreset: StateFlow<AmbientSoundPreset> =
        MutableStateFlow(AmbientSoundPreset.NONE).asStateFlow()
    override val playbackState: StateFlow<AmbientPlaybackState> =
        MutableStateFlow(AmbientPlaybackState.IDLE).asStateFlow()

    override fun startOrUpdate(preset: AmbientSoundPreset, volume: Float) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun pause() = Unit
    override fun stop() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var db: FocusForestDatabase
    private lateinit var prefs: UserPreferencesDataStore
    private lateinit var prefsFile: File
    private lateinit var container: AppContainer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FocusForestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsFile = File(context.filesDir, "settings_prefs_${System.nanoTime()}.preferences_pb")
        prefs = UserPreferencesDataStore(
            PreferenceDataStoreFactory.create(produceFile = { prefsFile })
        )
        container = AppContainer(
            context = context,
            clockProvider = SettingsTestClock(),
            database = db,
            preferencesDataStore = prefs,
            usageStatsGateway = SettingsUsageGateway(),
            ambientAudioController = SettingsAudioController()
        )
    }

    @After
    fun tearDown() {
        db.close()
        prefsFile.delete()
    }

    @Test
    fun `settings default session persists and rejects break selections`() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = SettingsViewModel(container, context)

            viewModel.setDefaultSession(SessionType.DEEP_WORK, 50)
            assertTrue(
                awaitPreference(dispatcher) {
                    prefs.defaultSessionType.first() == SessionType.DEEP_WORK
                }
            )
            assertEquals(50, prefs.defaultSessionMinutes.first())

            viewModel.setDefaultSession(SessionType.SHORT_BREAK, 5)
            assertTrue(
                awaitPreference(dispatcher) {
                    prefs.defaultSessionType.first() == SessionType.POMODORO
                }
            )
            assertEquals(5, prefs.defaultSessionMinutes.first())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ambient setters update one choice without clobbering the others`() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            prefs.setAmbientPreferences(
                preset = AmbientSoundPreset.SOFT_RAIN,
                volume = 0.35f,
                playWithSession = false
            )
            val viewModel = SettingsViewModel(container, context)

            viewModel.setAmbientVolume(0.8f)
            assertTrue(awaitPreference(dispatcher) { prefs.ambientVolume.first() == 0.8f })
            assertEquals(AmbientSoundPreset.SOFT_RAIN, prefs.ambientPreset.first())
            assertFalse(prefs.ambientPlayWithSession.first())

            viewModel.setAmbientPlayWithSession(true)
            assertTrue(awaitPreference(dispatcher) { prefs.ambientPlayWithSession.first() })
            assertEquals(AmbientSoundPreset.SOFT_RAIN, prefs.ambientPreset.first())
            assertEquals(0.8f, prefs.ambientVolume.first())

            viewModel.setAmbientPreset(AmbientSoundPreset.OCEAN_WAVES)
            assertTrue(
                awaitPreference(dispatcher) {
                    prefs.ambientPreset.first() == AmbientSoundPreset.OCEAN_WAVES
                }
            )
            assertEquals(0.8f, prefs.ambientVolume.first())
            assertTrue(prefs.ambientPlayWithSession.first())
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * DataStore writes complete on its own IO scope, so advance the ViewModel dispatcher and
     * poll the persisted value instead of assuming the write finished in the same tick.
     */
    private suspend fun awaitPreference(
        dispatcher: TestDispatcher,
        predicate: suspend () -> Boolean
    ): Boolean {
        val deadline = System.currentTimeMillis() + 15_000L
        while (System.currentTimeMillis() < deadline) {
            dispatcher.scheduler.runCurrent()
            if (predicate()) return true
            Thread.sleep(10L)
        }
        return predicate()
    }
}
