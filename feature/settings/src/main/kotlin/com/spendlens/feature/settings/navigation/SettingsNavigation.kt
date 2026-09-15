package com.spendlens.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.spendlens.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsGraph

@Serializable
data object SettingsRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) =
    navigate(route = SettingsGraph, navOptions = navOptions)

/** A graph of one, like Insights, so the tab stays selected if settings ever gains sub-screens. */
fun NavGraphBuilder.settingsGraph() {
    navigation<SettingsGraph>(startDestination = SettingsRoute) {
        composable<SettingsRoute> { SettingsScreen() }
    }
}
