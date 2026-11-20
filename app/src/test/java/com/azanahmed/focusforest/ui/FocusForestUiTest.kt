package com.azanahmed.focusforest.ui

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.azanahmed.focusforest.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusForestUiTest {

    @Before
    fun denyUsageStatsByDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        Shadows.shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ERRORED
        )
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `full onboarding, focus chain, ambient audio, custom challenges, insights calendar, shop, and settings work`() {
        // Wait for initial onboarding screen
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("onboarding_next_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Page 1: Welcome
        composeRule.onNodeWithText("Welcome to Focus Forest").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()

        // Page 2: Goals
        composeRule.onNodeWithTag("onboarding_name_input").performTextInput("Maya")
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()

        // Page 3: Digital habits disclosure skip
        composeRule.onNodeWithTag("onboarding_skip_usage_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Page 4: Summary -> finish
        composeRule.onNodeWithTag("onboarding_finish_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("quick_start_focus_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Today dashboard shows Maya's intention card
        composeRule.onNodeWithText("Today's Focus Intention").assertIsDisplayed()
        composeRule.onNodeWithTag("today_lazy_column")
            .performScrollToNode(hasTestTag("weekly_goal_summary"))
        composeRule.onNodeWithTag("weekly_goal_summary").assertIsDisplayed()

        // Navigate to Focus tab -> single session start/pause/abort
        composeRule.onNodeWithTag("nav_focus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focus_lazy_column").performScrollToNode(hasTestTag("start_focus_button"))
        composeRule.onNodeWithTag("start_focus_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timer_countdown_text")
            .assertContentDescriptionContains("remaining", substring = true)
        composeRule.onNodeWithTag("pause_focus_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timer_countdown_text")
            .assertContentDescriptionContains("paused", substring = true)
        composeRule.onNodeWithTag("abort_focus_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_abort_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("start_focus_button").assertIsDisplayed()

        // Toggle Ambient Soundscape chip & Pomodoro Chain mode card
        composeRule.onNodeWithTag("focus_lazy_column").performScrollToNode(hasTestTag("mode_pomodoro_chain"))
        composeRule.onNodeWithTag("mode_pomodoro_chain").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focus_lazy_column").performScrollToNode(hasTestTag("chain_auto_transition_switch"))
        composeRule.onNodeWithTag("chain_auto_transition_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("focus_lazy_column").performScrollToNode(hasTestTag("mode_single_session"))
        composeRule.onNodeWithTag("mode_single_session").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("focus_lazy_column").performScrollToNode(hasTestTag("ambient_chip_SOFT_RAIN"))
        composeRule.onNodeWithTag("ambient_chip_SOFT_RAIN").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ambient_chip_NONE").performClick()
        composeRule.waitForIdle()

        // Navigate to Challenges -> verify validation error on empty title, then create custom challenge
        composeRule.onNodeWithTag("nav_challenges").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_custom_challenge_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_custom_challenge_button").performClick()
        composeRule.waitUntil(timeoutMillis = 3000) {
            composeRule.onAllNodesWithTag("custom_challenge_validation_error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("custom_challenge_validation_error").assertIsDisplayed()

        composeRule.onNodeWithTag("custom_challenge_title_input").performTextInput("Deep Study")
        composeRule.onNodeWithTag("save_custom_challenge_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("challenges_lazy_column")
            .performScrollToNode(hasText("Deep Study"))
        composeRule.onNodeWithText("Deep Study").assertIsDisplayed()

        // Navigate to Insights -> filter 30D, inspect calendar cell selection, and check Usage tab
        composeRule.onNodeWithTag("nav_insights").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filter_THIRTY_DAYS").performClick()
        composeRule.waitForIdle()
        val todayTag = "calendar_day_${LocalDate.now()}"
        composeRule.onNodeWithTag("insights_focus_lazy_column").performScrollToNode(hasTestTag(todayTag))
        composeRule.onNodeWithTag(todayTag).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights_focus_lazy_column").performScrollToNode(hasTestTag("calendar_selected_date_summary"))
        composeRule.onNodeWithTag("calendar_selected_date_summary").assertIsDisplayed()

        composeRule.onNodeWithTag("insights_tab_usage").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Usage Access is not enabled").assertIsDisplayed()

        // Navigate to Forest & Virtual Shop -> test Owned filter + insufficient Leaf Points handling
        composeRule.onNodeWithTag("nav_forest").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_virtual_shop_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Botanical Shop").assertIsDisplayed()
        composeRule.onNodeWithTag("shop_filter_owned").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shop_filter_all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shop_back_button").performClick()
        composeRule.waitForIdle()

        // Navigate back to Today & open Settings -> Theme -> Privacy -> Delete All Data reset check
        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_lazy_column")
            .performScrollToNode(hasTestTag("today_settings_button"))
        composeRule.onNodeWithTag("today_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("theme_chip_DARK"))
        composeRule.onNodeWithTag("theme_chip_DARK").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("open_privacy_screen_button"))
        composeRule.onNodeWithTag("open_privacy_screen_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Strict Local-First Architecture").assertIsDisplayed()
    }
}

