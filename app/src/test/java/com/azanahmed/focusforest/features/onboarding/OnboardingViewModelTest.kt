package com.azanahmed.focusforest.features.onboarding

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
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
import com.azanahmed.focusforest.domain.model.UserIntention
import com.azanahmed.focusforest.platform.audio.AmbientAudioController
import com.azanahmed.focusforest.platform.audio.AmbientPlaybackState
import com.azanahmed.focusforest.platform.usage.UsageStatsGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

private class OnboardingTestClock : ClockProvider {
    override fun nowWallClockMs(): Long = 1_795_030_000_000L
    override fun elapsedRealtimeMs(): Long = 100_000L
    override fun bootCount(): Int = 1
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

private class OnboardingUsageGateway : UsageStatsGateway {
    override fun checkUsagePermission(): UsagePermissionStatus = UsagePermissionStatus.DENIED
    override fun getLaunchableApps(): List<LaunchableAppInfo> = emptyList()
    override fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>
    ): List<AppUsageSummary> = emptyList()
}

private class OnboardingAudioController : AmbientAudioController {
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
class OnboardingViewModelTest {

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
        prefsFile = File(context.filesDir, "onboarding_prefs_${System.nanoTime()}.preferences_pb")
        prefs = UserPreferencesDataStore(
            PreferenceDataStoreFactory.create(produceFile = { prefsFile })
        )
        container = AppContainer(
            context = context,
            clockProvider = OnboardingTestClock(),
            database = db,
            preferencesDataStore = prefs,
            usageStatsGateway = OnboardingUsageGateway(),
            ambientAudioController = OnboardingAudioController()
        )
    }

    @After
    fun tearDown() {
        db.close()
        prefsFile.delete()
    }

    @Test
    fun `saved state restores an in-progress onboarding page and choices`() {
        val handle = SavedStateHandle(
            mapOf(
                "onboarding_page" to 1,
                "onboarding_name" to "Maya",
                "onboarding_daily_goal" to 75,
                "onboarding_intention" to UserIntention.WORK.name,
                "onboarding_session_type" to SessionType.CUSTOM.name,
                "onboarding_session_minutes" to 40
            )
        )

        val viewModel = OnboardingViewModel(container, handle)

        assertEquals(1, viewModel.uiState.value.currentPage)
        assertEquals("Maya", viewModel.uiState.value.displayName)
        assertEquals(75, viewModel.uiState.value.dailyGoalMinutes)
        assertEquals(UserIntention.WORK, viewModel.uiState.value.selectedIntention)
        assertEquals(SessionType.CUSTOM, viewModel.uiState.value.defaultSessionType)
        assertEquals(40, viewModel.uiState.value.plannedDefaultMinutes)

        viewModel.updateDailyGoal(90)
        assertEquals(90, handle.get<Int>("onboarding_daily_goal"))
    }

    @Test
    fun `skip saves the setup locally and completes onboarding without usage access`() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = OnboardingViewModel(container, SavedStateHandle())
            viewModel.updateDisplayName("Maya")
            viewModel.updateDailyGoal(75)
            viewModel.selectIntention(UserIntention.WORK)
            viewModel.selectDefaultSession(SessionType.CUSTOM)
            viewModel.updateDefaultSessionMinutes(40)

            var finished = false
            viewModel.skipOnboarding { finished = true }

            val deadline = System.currentTimeMillis() + 15_000L
            while (!finished && System.currentTimeMillis() < deadline) {
                dispatcher.scheduler.runCurrent()
                Thread.sleep(10L)
            }

            assertTrue("Skipping onboarding must finish without a permission prompt", finished)
            assertTrue("Onboarding completion must be persisted", prefs.onboardingCompleted.first())
            assertEquals(SessionType.CUSTOM, prefs.defaultSessionType.first())
            assertEquals(40, prefs.defaultSessionMinutes.first())
            assertEquals(75, prefs.dailyGoalMinutes.first())

            val profile = container.focusRepository.userProfile.first()
            assertEquals("Maya", profile?.displayName)
            assertEquals(UserIntention.WORK, profile?.primaryIntention)
            assertEquals(75, profile?.dailyFocusGoalMinutes)

            // The starter catalog and routines are seeded before the completion callback runs.
            assertTrue(container.shopAndForestRepository.shopItems.first().isNotEmpty())
            assertTrue(container.focusContextRepository.activeRoutines.first().isNotEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `break session types cannot become the default focus selection`() {
        val viewModel = OnboardingViewModel(container, SavedStateHandle())
        viewModel.selectDefaultSession(SessionType.SHORT_BREAK)

        assertEquals(SessionType.POMODORO, viewModel.uiState.value.defaultSessionType)
    }
}
