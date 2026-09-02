package com.spendlens.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.spendlens.navigation.SpendLensAppState
import com.spendlens.navigation.SpendLensNavHost
import com.spendlens.navigation.TopLevelDestination
import com.spendlens.navigation.rememberSpendLensAppState

@Composable
fun SpendLensApp(
    modifier: Modifier = Modifier,
    appState: SpendLensAppState = rememberSpendLensAppState(),
) {
    val currentTopLevel = appState.currentTopLevelDestination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == currentTopLevel,
                        onClick = { appState.navigateTo(destination) },
                        // No icons yet — labels alone are unambiguous with two tabs, and a wrong
                        // icon is worse than none. Icons land with the settings tab.
                        icon = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        SpendLensNavHost(
            navController = appState.navController,
            modifier = Modifier.padding(padding),
        )
    }
}
