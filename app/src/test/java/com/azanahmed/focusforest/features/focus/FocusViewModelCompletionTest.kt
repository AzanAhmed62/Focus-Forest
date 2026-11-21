package com.azanahmed.focusforest.features.focus

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

private class ViewModelTestClock(
    var wallMs: Long = 1_795_030_000_000L,
    var elapsedMs: Long = 100_000L,
    var boot: Int = 1
) : ClockProvider {
    override fun nowWallClockMs(): Long = wallMs
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun bootCount(): Int = boot
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

private class EmptyUsageStatsGateway : UsageStatsGateway {
    override fun checkUsagePermission(): UsagePermissionStatus = UsagePermissionStatus.DENIED
    override fun getLaunchableApps(): List<LaunchableAppInfo> = emptyList()
    override fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>
    ): List<AppUsageSummary> = emptyList()
}

private class SilentAmbientAudioController : AmbientAudioController {
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val activePreset: StateFlow<AmbientSoundPreset> = MutableStateFlow(AmbientSoundPreset.NONE).asStateFlow()
    override val playbackState: StateFlow<AmbientPlaybackState> = MutableStateFlow(AmbientPlaybackState.IDLE).asStateFlow()

    override fun startOrUpdate(preset: AmbientSoundPreset, volume: Float) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun pause() = Unit
    override fun stop() = Unit
}

/**
 * Regression coverage for a completed ticker cancelling the coroutine that performs its own save.
 * Before the fix, completeSession() cancelled tickerJob from inside tickerJob, Room/DataStore work
 * was cancelled, and the UI reported "saving failed (StandaloneCoroutine was cancelled)".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusViewModelCompletionTest {

    private lateinit var context: Context
    private lateinit var db: FocusForestDatabase
    private lateinit var prefs: UserPreferencesDataStore
    private lateinit var prefsFile: File
    private lateinit var clock: ViewModelTestClock

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FocusForestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsFile = File(context.filesDir, "vm_prefs_${System.nanoTime()}.preferences_pb")
        prefs = UserPreferencesDataStore(PreferenceDataStoreFactory.create(produceFile = { prefsFile }))
        clock = ViewModelTestClock()
    }

    @After
    fun tearDown() {
        db.close()
        prefsFile.delete()
    }

    @Test
    fun `ticker completion saves without a cancellation error`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runBlocking {
                val container = AppContainer(
                    context = context,
                    clockProvider = clock,
                    database = db,
                    preferencesDataStore = prefs,
                    usageStatsGateway = EmptyUsageStatsGateway(),
                    ambientAudioController = SilentAmbientAudioController()
                )
                val viewModel = FocusViewModel(container, context)
                dispatcher.scheduler.runCurrent()

                // Initialization reads persisted timer/chain state asynchronously. Wait for the
                // view model to be ready instead of racing the first DataStore emission.
                val readyDeadline = System.currentTimeMillis() + 10_000L
                while (!viewModel.uiState.value.isReady && System.currentTimeMillis() < readyDeadline) {
                    dispatcher.scheduler.runCurrent()
                    Thread.sleep(10L)
                }
                assertTrue("View model should become ready", viewModel.uiState.value.isReady)

                viewModel.selectSessionType(SessionType.SHORT_BREAK)
                viewModel.startSession()
                dispatcher.scheduler.runCurrent()
                assertNotNull("Break session should start", viewModel.uiState.value.activeTimer)

                // Move past the 5-minute break and let the ticker's one-second delay elapse.
                clock.wallMs += 301_000L
                clock.elapsedMs += 301_000L
                dispatcher.scheduler.advanceTimeBy(1_100L)

                val deadline = System.currentTimeMillis() + 10_000L
                while (viewModel.uiState.value.completionOutcome == null &&
                    viewModel.uiState.value.completionErrorMessage == null &&
                    System.currentTimeMillis() < deadline
                ) {
                    dispatcher.scheduler.runCurrent()
                    Thread.sleep(25L)
                }

                val state = viewModel.uiState.value
                assertNull(state.completionErrorMessage, state.completionErrorMessage)
                assertNotNull("Ticker completion should produce an outcome", state.completionOutcome)
                assertNull("Completed timer should be cleared", state.activeTimer)
                assertEquals(1, db.focusSessionDao().getAllSessionsOnce().count { it.status == "COMPLETED" })
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
