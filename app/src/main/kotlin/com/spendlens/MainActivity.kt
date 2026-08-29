package com.spendlens

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.feature.expenses.list.ExpenseListRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Hardcoded for now. Once core:datastore exists this comes from the user's setting,
            // held behind a splash-screen condition so the app never flashes the wrong theme.
            val darkTheme = isSystemInDarkTheme()

            // API 35 enforces edge-to-edge and deprecates window.statusBarColor, so this is the
            // supported way to style the system bars. The DisposableEffect re-applies it when the
            // theme flips at runtime, which a one-shot call in onCreate would miss.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LightScrim,
                        DarkScrim,
                    ) { darkTheme },
                )
                onDispose {}
            }

            SpendLensTheme(darkTheme = darkTheme) {
                SpendLensApp()
            }
        }
    }
}

/**
 * Placeholder host. This becomes the NavHost from §7 once there is a second screen to navigate to;
 * the empty lambdas below are the call sites that will grow into `navController.navigate(...)`.
 */
@Composable
private fun SpendLensApp() {
    ExpenseListRoute(
        onExpenseClick = {},
        onAddExpenseClick = {},
        modifier = Modifier.fillMaxSize(),
    )
}

/** Matches the scrims the framework draws behind 3-button navigation. */
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
