package com.azanahmed.focusforest.features.forest

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.platform.audio.AmbientAudioController
import com.azanahmed.focusforest.platform.audio.AmbientPlaybackState
import com.azanahmed.focusforest.platform.usage.UsageStatsGateway
import com.azanahmed.focusforest.ui.theme.FocusForestTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

private class ForestTestClock : ClockProvider {
    override fun nowWallClockMs(): Long = 1_795_030_000_000L
    override fun elapsedRealtimeMs(): Long = 100_000L
    override fun bootCount(): Int = 1
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

private class ForestUsageGateway : UsageStatsGateway {
    override fun checkUsagePermission(): UsagePermissionStatus = UsagePermissionStatus.DENIED
    override fun getLaunchableApps(): List<LaunchableAppInfo> = emptyList()
    override fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>
    ): List<AppUsageSummary> = emptyList()
}

private class ForestAudioController : AmbientAudioController {
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
class ForestScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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
        prefsFile = File(context.filesDir, "forest_ui_prefs_${System.nanoTime()}.preferences_pb")
        prefs = UserPreferencesDataStore(
            PreferenceDataStoreFactory.create(produceFile = { prefsFile })
        )
        container = AppContainer(
            context = context,
            clockProvider = ForestTestClock(),
            database = db,
            preferencesDataStore = prefs,
            usageStatsGateway = ForestUsageGateway(),
            ambientAudioController = ForestAudioController()
        )
        runBlocking {
            container.shopAndForestRepository.ensureCatalogSeeded()
        }
    }

    @After
    fun tearDown() {
        db.close()
        prefsFile.delete()
    }

    @Test
    fun `empty forest explains planting and exposes a non-visual summary`() {
        val viewModel = ForestViewModel(container)
        composeRule.setContent {
            FocusForestTheme {
                ForestScreen(
                    viewModel = viewModel,
                    onOpenShop = {},
                    onOpenHistory = {},
                    onStartFocus = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("forest_start_focus_button")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("forest_canvas")
            .assertContentDescriptionContains("0 trees planted", substring = true)
        composeRule.onNodeWithTag("forest_accessibility_summary")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("forest_lazy_column")
            .performScrollToNode(hasTestTag("forest_start_first_focus"))
        composeRule.onNodeWithTag("forest_start_first_focus").assertIsDisplayed()
        composeRule.onNodeWithTag("forest_lazy_column")
            .performScrollToNode(hasTestTag("forest_stage_progress"))
        composeRule.onNodeWithTag("forest_stage_progress").assertIsDisplayed()
    }

    @Test
    fun `owned environment can be equipped while locked items open the shop`() {
        var openedShop = false
        val viewModel = ForestViewModel(container)
        composeRule.setContent {
            FocusForestTheme {
                ForestScreen(
                    viewModel = viewModel,
                    onOpenShop = { openedShop = true },
                    onOpenHistory = {},
                    onStartFocus = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.environments.isNotEmpty()
        }
        composeRule.onNodeWithTag("forest_lazy_column")
            .performScrollToNode(hasTestTag("forest_environment_env_misty_alpine"))
        composeRule.onNodeWithTag("forest_environment_env_misty_alpine").performClick()
        assertTrue("Locked environments must route to the shop", openedShop)

        composeRule.onNodeWithTag("forest_environment_env_emerald_clearing").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("forest_lazy_column")
            .performScrollToNode(hasTestTag("forest_decoration_deco_stone_lantern"))
        composeRule.onNodeWithTag("forest_decoration_deco_stone_lantern").assertIsDisplayed()
    }
}
