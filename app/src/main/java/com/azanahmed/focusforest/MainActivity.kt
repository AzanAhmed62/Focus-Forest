package com.azanahmed.focusforest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.domain.model.ForestThemeSkin
import com.azanahmed.focusforest.domain.model.ThemePreference
import com.azanahmed.focusforest.navigation.FocusForestNavHost
import com.azanahmed.focusforest.ui.theme.FocusForestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as FocusForestApplication).container

        setContent {
            val onboardingCompleted by appContainer.preferencesDataStore.onboardingCompleted
                .collectAsStateWithLifecycle(initialValue = null)
            val themePreference by appContainer.preferencesDataStore.themePreference
                .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)
            val profile by appContainer.focusRepository.userProfile
                .collectAsStateWithLifecycle(initialValue = null)

            FocusForestTheme(
                themePreference = themePreference,
                equippedThemeId = profile?.equippedThemeId ?: ForestThemeSkin.CLASSIC_SAGE.id
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val completed = onboardingCompleted
                    if (completed == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        FocusForestNavHost(
                            container = appContainer,
                            appContext = applicationContext,
                            onboardingCompleted = completed
                        )
                    }
                }
            }
        }
    }
}
