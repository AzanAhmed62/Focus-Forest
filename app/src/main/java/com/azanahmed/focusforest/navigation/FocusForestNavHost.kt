package com.azanahmed.focusforest.navigation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.features.challenges.ChallengesScreen
import com.azanahmed.focusforest.features.challenges.ChallengesViewModel
import com.azanahmed.focusforest.features.focus.FocusScreen
import com.azanahmed.focusforest.features.focus.FocusViewModel
import com.azanahmed.focusforest.features.forest.ForestScreen
import com.azanahmed.focusforest.features.forest.ForestViewModel
import com.azanahmed.focusforest.features.home.TodayScreen
import com.azanahmed.focusforest.features.home.TodayViewModel
import com.azanahmed.focusforest.features.insights.InsightsScreen
import com.azanahmed.focusforest.features.insights.InsightsViewModel
import com.azanahmed.focusforest.features.onboarding.OnboardingScreen
import com.azanahmed.focusforest.features.onboarding.OnboardingViewModel
import com.azanahmed.focusforest.features.routines.RoutinesScreen
import com.azanahmed.focusforest.features.routines.RoutinesViewModel
import com.azanahmed.focusforest.features.settings.PrivacyScreen
import com.azanahmed.focusforest.features.settings.SettingsScreen
import com.azanahmed.focusforest.features.settings.SettingsViewModel
import com.azanahmed.focusforest.features.shop.ShopScreen
import com.azanahmed.focusforest.features.shop.ShopViewModel

private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(TodayRoute, "Today", Icons.Rounded.WbSunny, Icons.Outlined.WbSunny, "nav_today"),
    TopLevelDestination(FocusRoute(), "Focus", Icons.Rounded.Timer, Icons.Outlined.Timer, "nav_focus"),
    TopLevelDestination(ChallengesRoute, "Challenges", Icons.Rounded.Flag, Icons.Outlined.Flag, "nav_challenges"),
    TopLevelDestination(InsightsRoute, "Insights", Icons.Rounded.Insights, Icons.Outlined.Insights, "nav_insights"),
    TopLevelDestination(ForestRoute, "Forest", Icons.Rounded.Forest, Icons.Outlined.Forest, "nav_forest")
)

private fun NavDestination?.isTopLevelRoute(route: Any): Boolean {
    return when (route) {
        is TodayRoute -> this?.hasRoute<TodayRoute>() == true
        is FocusRoute -> this?.hasRoute<FocusRoute>() == true
        is ChallengesRoute -> this?.hasRoute<ChallengesRoute>() == true
        is InsightsRoute -> this?.hasRoute<InsightsRoute>() == true
        is ForestRoute -> this?.hasRoute<ForestRoute>() == true
        else -> false
    }
}

@Composable
fun FocusForestNavHost(
    container: AppContainer,
    appContext: Context,
    onboardingCompleted: Boolean,
    navController: NavHostController = rememberNavController()
) {
    val startDestination: Any = if (onboardingCompleted) TodayRoute else OnboardingRoute
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentDest = backStackEntry?.destination

    val showChrome = topLevelDestinations.any { currentDest.isTopLevelRoute(it.route) }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showChrome && !useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDest.isTopLevelRoute(dest.route)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useRail) {
                    NavigationRail {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDest.isTopLevelRoute(dest.route)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable<OnboardingRoute> {
                            val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory(container))
                            OnboardingScreen(
                                viewModel = vm,
                                onCompleted = {
                                    navController.navigate(TodayRoute) {
                                        popUpTo<OnboardingRoute> { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<TodayRoute> {
                            val vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory(container))
                            TodayScreen(
                                viewModel = vm,
                                onQuickStartFocus = { navigateTopLevel(FocusRoute()) },
                                onOpenChallenges = { navigateTopLevel(ChallengesRoute) },
                                onOpenInsights = { navigateTopLevel(InsightsRoute) },
                                onOpenForest = { navigateTopLevel(ForestRoute) },
                                onOpenSettings = { navController.navigate(SettingsRoute) }
                            )
                        }

                        composable<FocusRoute> {
                            val vm: FocusViewModel = viewModel(factory = FocusViewModel.Factory(container, appContext))
                            FocusScreen(viewModel = vm, onOpenRoutines = { navController.navigate(RoutinesRoute) })
                        }

                        composable<RoutinesRoute> {
                            val vm: RoutinesViewModel = viewModel(factory = RoutinesViewModel.Factory(container))
                            RoutinesScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() },
                                onStartRoutine = { routineId ->
                                    navController.navigate(
                                        FocusRoute(
                                            routineId = routineId,
                                            requestId = java.util.UUID.randomUUID().toString()
                                        )
                                    )
                                }
                            )
                        }

                        composable<ChallengesRoute> {
                            val vm: ChallengesViewModel = viewModel(factory = ChallengesViewModel.Factory(container))
                            ChallengesScreen(viewModel = vm)
                        }

                        composable<InsightsRoute> {
                            val vm: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory(container))
                            InsightsScreen(viewModel = vm)
                        }

                        composable<ForestRoute> {
                            val vm: ForestViewModel = viewModel(factory = ForestViewModel.Factory(container))
                            ForestScreen(
                                viewModel = vm,
                                onOpenShop = { navController.navigate(ShopRoute) }
                            )
                        }

                        composable<ShopRoute> {
                            val vm: ShopViewModel = viewModel(factory = ShopViewModel.Factory(container))
                            ShopScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<SettingsRoute> {
                            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container, appContext))
                            SettingsScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() },
                                onOpenPrivacy = { navController.navigate(PrivacyRoute) },
                                onDataDeletedResetToOnboarding = {
                                    navController.navigate(OnboardingRoute) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<PrivacyRoute> {
                            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container, appContext))
                            PrivacyScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
