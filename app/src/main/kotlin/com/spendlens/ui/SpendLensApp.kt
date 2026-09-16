package com.spendlens.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.spendlens.core.designsystem.animation.LocalSharedTransitionScope
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
            // Hidden on destinations outside every tab's graph — today, that means capture. A tab
            // bar under a viewfinder is a way to abandon a scan with one stray thumb. Detail and
            // edit sit inside the Expenses graph, so they keep the bar with Expenses selected.
            if (currentTopLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == currentTopLevel,
                            onClick = { appState.navigateTo(destination) },
                            // Labels only. Three short words are unambiguous, and an icon that
                            // does not mean the same thing to everyone is worse than none.
                            icon = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Shared elements have to be able to draw above every destination, so the layout wraps the whole
        // NavHost. Features read the scope from a composition local rather than taking it as a parameter.
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                SpendLensNavHost(
                    navController = appState.navController,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
