package com.example.ui

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

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
    fun `full onboarding to dashboard navigation and timer interaction works`() {
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

        // Navigate to Focus tab & start/pause/abort timer
        composeRule.onNodeWithTag("nav_focus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focus_lazy_column").performScrollToNode(hasTestTag("start_focus_button"))
        composeRule.onNodeWithTag("start_focus_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pause_focus_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("abort_focus_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_abort_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("start_focus_button").assertIsDisplayed()

        // Navigate to Challenges & create custom challenge
        composeRule.onNodeWithTag("nav_challenges").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_custom_challenge_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("custom_challenge_title_input").performTextInput("Deep Study")
        composeRule.onNodeWithTag("save_custom_challenge_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Deep Study").assertIsDisplayed()

        // Navigate to Insights (Focus & Usage tabs)
        composeRule.onNodeWithTag("nav_insights").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights_tab_usage").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Usage Access is not enabled").assertIsDisplayed()

        // Navigate to Forest & Virtual Shop
        composeRule.onNodeWithTag("nav_forest").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_virtual_shop_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Botanical Shop").assertIsDisplayed()
        composeRule.onNodeWithTag("shop_back_button").performClick()
        composeRule.waitForIdle()

        // Navigate back to Today & open Settings -> Theme -> Privacy
        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.waitForIdle()
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
