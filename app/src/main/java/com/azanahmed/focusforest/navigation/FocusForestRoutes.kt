package com.azanahmed.focusforest.navigation

import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object TodayRoute

@Serializable
data class FocusRoute(
    val routineId: String = "",
    val requestId: String = ""
)

@Serializable
data object RoutinesRoute

@Serializable
data object HistoryRoute

@Serializable
data object ChallengesRoute

@Serializable
data object InsightsRoute

@Serializable
data object ForestRoute

@Serializable
data object ShopRoute

@Serializable
data object SettingsRoute

@Serializable
data object PrivacyRoute
