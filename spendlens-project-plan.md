# SpendLens — Offline-First Receipt & Expense Tracker

**A native Android (Kotlin) portfolio project.**
Purpose: demonstrate production-grade native Android engineering — architecture, testing, performance, CI — not feature count.

---

## 1. Positioning

| | |
|---|---|
| **Why this app** | Every hard Android skill (camera, on-device ML, offline sync, background work, paging, widgets, security) falls out of the requirements naturally. No library is bolted on for show. |
| **What it proves** | You can design a multi-module codebase, reason about offline-first data, test it, measure it, and ship it. |
| **What reviewers check first** | README → architecture diagram → tests → commit history. In that order. Build accordingly. |
| **Anti-goal** | Feature bloat. A polished 6-screen app with real tests beats a 20-screen app with none. |

### The elevator pitch (put this in the README)

> SpendLens captures receipts with the camera, extracts merchant/amount/date with on-device ML Kit OCR, stores everything in Room as the single source of truth, and syncs opportunistically with WorkManager. It works fully offline and resolves conflicts on reconnect. Multi-module, ~85% domain/data test coverage, Baseline Profile cuts cold start from XXXms to XXXms.

Fill the XXX in with numbers you actually measured. That sentence is what gets you the interview.

---

## 2. Feature scope

### Must ship (v1.0)
- [ ] Capture receipt via camera → on-device OCR → editable extracted fields
- [ ] Manual expense entry (no camera path)
- [ ] Expense list with search, filter by category/date, infinite scroll
- [ ] Expense detail + edit + delete (with undo)
- [ ] Categories with budgets; monthly insights screen with charts
- [ ] Offline-first: every action works with airplane mode on
- [ ] Background sync with conflict resolution + sync status surfaced in UI
- [ ] App lock via biometric prompt
- [ ] Light/dark + dynamic color (Material You)

### Should ship (v1.1)
- [ ] Glance home-screen widget: this month's spend + quick-capture shortcut
- [ ] Budget-threshold notifications (local, then FCM-driven)
- [ ] CSV / PDF export via Storage Access Framework
- [ ] Recurring expense detection

### Explicitly out of scope
Multi-currency FX, bank account linking, receipt sharing, social features, tablet-optimised layouts (unless you finish early — then adaptive layouts are a nice bonus).

---

## 3. Tech stack

| Concern | Choice | Why it's here |
|---|---|---|
| UI | Jetpack Compose, Material 3 | Current standard; Views are legacy for new work |
| Navigation | Navigation Compose (type-safe routes) | Serializable route objects, no string keys |
| Architecture | MVVM + UDF, `StateFlow`, `sealed interface` UI state | The pattern every Android interview asks about |
| DI | Hilt | More commonly interviewed than Koin |
| Async | Coroutines, Flow, structured concurrency | Non-negotiable |
| Networking | Retrofit + OkHttp + kotlinx.serialization | `Authenticator` for token refresh, logging interceptor |
| Local DB | Room (KSP) + Paging 3 `RemoteMediator` | Source of truth; paging from DB, not network |
| Prefs | DataStore (Proto) | Typed settings, no SharedPreferences |
| Background | WorkManager | Constrained + expedited work, chained requests |
| Camera | CameraX | Lifecycle-aware, far less boilerplate than Camera2 |
| On-device ML | ML Kit Text Recognition v2 | Runs offline, no API key, no cost |
| Images | Coil (Compose) | Async loading, disk cache |
| Security | AndroidX Biometric, EncryptedSharedPreferences / SQLCipher | Real requirement for a finance app |
| Widget | Glance | Compose-style widgets, still rare in portfolios |
| Charts | Vico (or hand-rolled Compose Canvas) | Hand-rolled is a stronger signal if you have time |
| Build | Gradle Kotlin DSL, version catalogs, **convention plugins** | Disproportionately strong signal — very few candidates do this |

### Testing stack
| Layer | Tools |
|---|---|
| Unit / domain | JUnit, MockK, kotlinx-coroutines-test, Turbine (Flow assertions) |
| Data | Room in-memory DAO tests, MockWebServer for API contracts |
| UI | Compose UI tests (`createAndroidComposeRule`), semantics-based assertions |
| Screenshot | Paparazzi (JVM, no emulator — fast in CI) |
| Performance | Macrobenchmark module + Baseline Profile generator |

---

## 4. Module structure

Model it on Google's `nowinandroid`. Do **not** put everything in `:app`.

```
SpendLens/
├── build-logic/                  ← convention plugins (this is the flex)
│   └── convention/
│       ├── AndroidApplicationConventionPlugin.kt
│       ├── AndroidLibraryConventionPlugin.kt
│       ├── AndroidComposeConventionPlugin.kt
│       └── AndroidHiltConventionPlugin.kt
├── app/                          ← navigation host, Application class, DI wiring only
├── core/
│   ├── common/                   ← Result wrapper, dispatchers, extensions
│   ├── designsystem/             ← theme, typography, reusable composables
│   ├── model/                    ← domain models (pure Kotlin, no Android deps)
│   ├── database/                 ← Room entities, DAOs, migrations
│   ├── network/                  ← Retrofit services, DTOs, mappers
│   ├── datastore/                ← Proto DataStore
│   ├── data/                     ← repositories (the only layer both DB and network touch)
│   └── testing/                  ← shared fakes, test rules
├── feature/
│   ├── capture/                  ← CameraX + OCR flow
│   ├── expenses/                 ← list + detail + edit
│   ├── insights/                 ← charts, budgets
│   └── settings/
├── widget/                       ← Glance
├── sync/                         ← WorkManager workers
└── benchmark/                    ← Macrobenchmark + Baseline Profile
```

**Dependency rule:** `feature:*` → `core:data` → `core:{database,network}`. Features never depend on each other. `core:model` depends on nothing.

---

## 5. Data model sketch

```kotlin
// core:model — pure Kotlin
data class Expense(
    val id: String,              // UUID generated client-side (offline-first requirement)
    val merchant: String,
    val amountMinor: Long,       // never use Double for money
    val currency: String,
    val occurredAt: Instant,
    val categoryId: String,
    val note: String?,
    val receiptImagePath: String?,
    val syncState: SyncState,    // PENDING, SYNCED, CONFLICT
    val updatedAt: Instant,      // for last-write-wins conflict resolution
    val isDeleted: Boolean       // soft delete — tombstones sync, hard deletes don't
)
```

Three decisions here you should be able to defend in an interview: **client-generated IDs**, **money as minor units in `Long`**, and **soft deletes with tombstones**. Write down *why* for each.

### Backend
Don't build a full backend — it's not what's being assessed. Options, cheapest first:
1. **Supabase / Firebase Firestore** — real sync endpoint, minimal work
2. **Ktor server** on Railway/Render — ~200 lines, and shows Kotlin backend range
3. **json-server / Mockoon** on a free host — enough to prove the sync layer works

---

## 6. Design system — `core:designsystem`

This module depends on nothing but Compose. No Hilt, no repositories, no `core:model`. That's what makes it Paparazzi-testable on the JVM with no emulator.

```
core/designsystem/src/main/kotlin/…/designsystem/
├── theme/
│   ├── Color.kt          ← M3 schemes + semantic colors
│   ├── Type.kt           ← typography + tabular-figure amount styles
│   ├── Shape.kt
│   ├── Dimens.kt         ← spacing scale
│   └── Theme.kt          ← SpendLensTheme
├── component/            ← SpendLensButton, AmountText, CategoryChip, SyncBadge…
└── preview/              ← multipreview annotations, preview parameter providers
```

### 6.1 Color schemes

Generate the real tonal palettes with [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/) from a seed colour — don't hand-pick 30 values. Seed used below: `#006A6B` (teal; reads as "money" without being literal green).

```kotlin
// core:designsystem — theme/Color.kt
package com.spendlens.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF6FC),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    // …surfaceContainer{Low,High,Highest}, inverse*, scrim — paste the rest from the generator
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF4CDADE),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF6FF6FC),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
)
```

### 6.2 Semantic colours M3 doesn't give you

`ColorScheme` has no "over budget", "sync pending", or "category 7". Bolting those on as loose top-level `val`s is the common mistake — they then don't respond to dark mode. Extend the theme with a `CompositionLocal` instead.

**This is worth doing precisely because it's the thing most portfolio apps get wrong**, and it's a clean 2-minute interview answer.

```kotlin
// core:designsystem — theme/Color.kt (continued)
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SemanticColors(
    val budgetUnder: Color,
    val budgetNear: Color,      // ≥80% of budget
    val budgetOver: Color,
    val syncPending: Color,
    val syncSynced: Color,
    val syncConflict: Color,
    val onSemanticContainer: Color,
    // ImmutableList, not List — List<T> is an unstable type to the Compose
    // compiler and would defeat skipping on every composable that takes it.
    val categoryPalette: ImmutableList<Color>,
)

internal val LightSemanticColors = SemanticColors(
    budgetUnder = Color(0xFF16651F),
    budgetNear = Color(0xFF8A5000),
    budgetOver = Color(0xFFBA1A1A),
    syncPending = Color(0xFF8A5000),
    syncSynced = Color(0xFF16651F),
    syncConflict = Color(0xFFBA1A1A),
    onSemanticContainer = Color(0xFF191C1C),
    categoryPalette = persistentListOf(
        Color(0xFF00696D), Color(0xFF7B4E7F), Color(0xFF8A5000),
        Color(0xFF16651F), Color(0xFF4B607C), Color(0xFFA03D3D),
    ),
)

internal val DarkSemanticColors = SemanticColors(
    budgetUnder = Color(0xFF7FDA85),
    budgetNear = Color(0xFFFFB95C),
    budgetOver = Color(0xFFFFB4AB),
    syncPending = Color(0xFFFFB95C),
    syncSynced = Color(0xFF7FDA85),
    syncConflict = Color(0xFFFFB4AB),
    onSemanticContainer = Color(0xFFE0E3E2),
    categoryPalette = persistentListOf(
        Color(0xFF4CDADE), Color(0xFFEBB4EE), Color(0xFFFFB95C),
        Color(0xFF7FDA85), Color(0xFFB3C8E8), Color(0xFFFFB4AB),
    ),
)

// staticCompositionLocalOf, not compositionLocalOf: this value changes only on a
// full theme switch, so we want the cheaper no-tracking variant.
val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
```

**Deliberate decision to write down in `DECISIONS.md`:** semantic colours are *not* derived from dynamic colour. If the user's wallpaper is red, "under budget" must not turn red. Material You reskins the chrome; the meaning-carrying colours stay fixed. Never encode state in colour alone anyway — pair every one with an icon or text label, for colour-blind users and TalkBack both.

### 6.3 Typography

```kotlin
// core:designsystem — theme/Type.kt
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val SpendLensTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    // …the rest, or leave defaults
)

/**
 * Money uses tabular (monospaced) figures so digits align in a column and the
 * total doesn't jitter as it animates. `tnum` is an OpenType feature — this is a
 * one-line detail that makes a list of amounts look professionally typeset.
 */
@Immutable
data class AmountTextStyles(
    val large: TextStyle,
    val medium: TextStyle,
    val small: TextStyle,
)

internal val SpendLensAmountStyles = AmountTextStyles(
    large = SpendLensTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
    medium = SpendLensTypography.titleMedium.copy(fontFeatureSettings = "tnum"),
    small = SpendLensTypography.labelSmall.copy(fontFeatureSettings = "tnum"),
)

val LocalAmountTextStyles = staticCompositionLocalOf { SpendLensAmountStyles }
```

Do **not** set fixed `dp` heights on anything containing text — the accessibility pass in Phase 5 tests at 200% font scale and fixed heights clip.

### 6.4 The theme composable

```kotlin
// core:designsystem — theme/Theme.kt
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

@Composable
fun SpendLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default in previews/screenshot tests so Paparazzi output is deterministic.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalAmountTextStyles provides SpendLensAmountStyles,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpendLensTypography,
            shapes = SpendLensShapes,
            content = content,
        )
    }
}

/** Call sites read `MaterialTheme.semantic.budgetOver` — same ergonomics as the M3 roles. */
val MaterialTheme.semantic: SemanticColors
    @Composable @ReadOnlyComposable get() = LocalSemanticColors.current

val MaterialTheme.amounts: AmountTextStyles
    @Composable @ReadOnlyComposable get() = LocalAmountTextStyles.current
```

### 6.5 Theme preference, wired to Proto DataStore

The theme is user state, so it belongs in `core:datastore` and flows up through `core:data` — not a global `var`. `:app` holds it until the first value arrives, so the app never flashes the wrong theme on cold start.

```kotlin
// core:model
enum class ThemeBrand { DEFAULT, DYNAMIC }
enum class DarkThemeConfig { FOLLOW_SYSTEM, LIGHT, DARK }
data class UserSettings(val darkThemeConfig: DarkThemeConfig, val useDynamicColor: Boolean)

// app — MainActivityViewModel
sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val settings: UserSettings) : MainUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    userSettingsRepository: UserSettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = userSettingsRepository.settings
        .map(MainUiState::Success)
        .stateIn(
            scope = viewModelScope,
            initialValue = MainUiState.Loading,
            // 5s, not Eagerly: survives a config change without restarting collection,
            // but stops collecting when the app is backgrounded.
            started = SharingStarted.WhileSubscribed(5_000),
        )
}
```

```kotlin
// app — MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var uiState: MainUiState by mutableStateOf(MainUiState.Loading)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.onEach { uiState = it }.collect()
            }
        }
        // Hold the splash until settings load — no theme flash.
        splashScreen.setKeepOnScreenCondition { uiState is MainUiState.Loading }

        setContent {
            val darkTheme = shouldUseDarkTheme(uiState)

            // API 35 enforces edge-to-edge and deprecates window.statusBarColor.
            // enableEdgeToEdge with matching scrims is the supported path; the
            // DisposableEffect re-applies it when the theme flips at runtime.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT, Color.TRANSPARENT
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LightScrim, DarkScrim
                    ) { darkTheme },
                )
                onDispose {}
            }

            SpendLensTheme(
                darkTheme = darkTheme,
                dynamicColor = shouldUseDynamicColor(uiState),
            ) {
                SpendLensApp()
            }
        }
    }
}

@Composable
private fun shouldUseDarkTheme(uiState: MainUiState): Boolean = when (uiState) {
    MainUiState.Loading -> isSystemInDarkTheme()
    is MainUiState.Success -> when (uiState.settings.darkThemeConfig) {
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
    }
}

/** Matches the framework's own 3-button-nav scrims. */
private val LightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
```

Because you're edge-to-edge, every screen must consume insets itself — `Scaffold` handles most of it, but `LazyColumn`s need `contentPadding = paddingValues` (not `Modifier.padding`, which would clip the scroll under the bar instead of letting content scroll beneath it).

### 6.6 Previews and screenshot tests

One multipreview annotation, used everywhere — this is what feeds Paparazzi in Phase 5.

```kotlin
// core:designsystem — preview/Previews.kt
@Preview(name = "light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class ThemePreviews

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f)
annotation class FullPreviews

@ThemePreviews
@Composable
private fun ExpenseRowPreview(
    @PreviewParameter(ExpensePreviewParameterProvider::class) expense: Expense,
) {
    SpendLensTheme { ExpenseRow(expense = expense, onClick = {}) }
}
```

Also available for free: `@PreviewLightDark`, `@PreviewFontScale`, `@PreviewScreenSizes`, `@PreviewDynamicColors`.

---

## 7. Navigation — type-safe, module-aware

Navigation Compose 2.8+ takes `@Serializable` route types instead of string routes. Use them; string routes with `"detail/{id}"` templates are the old way and an interviewer will notice.

> Aware of **Navigation 3** (backstack-as-state, `NavDisplay`)? Worth a sentence in `DECISIONS.md` explaining why you chose stable Nav Compose for this build. Knowing the trade-off reads better than using the newest thing.

### 7.1 Routes live in the feature module that owns the screen

```kotlin
// feature:expenses — navigation/ExpensesNavigation.kt
import kotlinx.serialization.Serializable

@Serializable data object ExpensesGraph          // nested graph
@Serializable data object ExpenseListRoute
@Serializable data class ExpenseDetailRoute(val expenseId: String)
@Serializable data class ExpenseEditRoute(val expenseId: String? = null) // null = new
```

Requires the `kotlin("plugin.serialization")` plugin — add it to your `AndroidFeatureConventionPlugin` so every feature module gets it without repetition.

### 7.2 Each feature exposes its own graph — and never imports another feature

The §4 dependency rule says features can't depend on each other. So how does the expense list open the camera? **It doesn't navigate — it reports an event.** The feature exposes a lambda; `:app` is the only module that knows both sides and wires them together.

```kotlin
// feature:expenses — navigation/ExpensesNavigation.kt (continued)

fun NavController.navigateToExpenses(navOptions: NavOptions? = null) =
    navigate(route = ExpensesGraph, navOptions = navOptions)

fun NavGraphBuilder.expensesGraph(
    onExpenseClick: (String) -> Unit,
    onEditExpense: (String?) -> Unit,
    onNavigateToCapture: () -> Unit,   // :app decides this means feature:capture
    onBack: () -> Unit,
) {
    navigation<ExpensesGraph>(startDestination = ExpenseListRoute) {
        composable<ExpenseListRoute> {
            ExpenseListRoute(
                onExpenseClick = onExpenseClick,
                onAddManualClick = { onEditExpense(null) },
                onScanReceiptClick = onNavigateToCapture,
            )
        }
        composable<ExpenseDetailRoute> {
            ExpenseDetailRoute(onEditClick = onEditExpense, onBack = onBack)
        }
        composable<ExpenseEditRoute> {
            ExpenseEditRoute(onSaved = onBack, onCancel = onBack)
        }
    }
}
```

### 7.3 Reading arguments — in the ViewModel, not the composable

```kotlin
// feature:expenses — ExpenseDetailViewModel.kt
@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    expenseRepository: ExpenseRepository,
) : ViewModel() {

    // Typed, no string keys, no manual null handling.
    private val route: ExpenseDetailRoute = savedStateHandle.toRoute()

    val uiState: StateFlow<ExpenseDetailUiState> =
        expenseRepository.observeExpense(route.expenseId)
            .map { expense ->
                if (expense == null) ExpenseDetailUiState.NotFound
                else ExpenseDetailUiState.Success(expense)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseDetailUiState.Loading)
}
```

### 7.4 The host, in `:app`

```kotlin
// app — navigation/SpendLensNavHost.kt
@Composable
fun SpendLensNavHost(
    appState: SpendLensAppState,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController
    NavHost(
        navController = navController,
        startDestination = ExpensesGraph,
        modifier = modifier,
    ) {
        expensesGraph(
            onExpenseClick = { id -> navController.navigate(ExpenseDetailRoute(id)) },
            onEditExpense = { id -> navController.navigate(ExpenseEditRoute(id)) },
            onNavigateToCapture = { navController.navigateToCapture() },  // cross-feature, resolved here
            onBack = navController::popBackStack,
        )
        captureGraph(
            onCaptureComplete = { draftId ->
                navController.navigate(ExpenseEditRoute(draftId)) {
                    // Don't leave the camera on the back stack behind the review screen.
                    popUpTo<CaptureRoute> { inclusive = true }
                }
            },
            onBack = navController::popBackStack,
        )
        insightsGraph(onCategoryClick = { /* … */ })
        settingsGraph(onBack = navController::popBackStack)
    }
}
```

### 7.5 Bottom bar / top-level destinations

```kotlin
// app — navigation/TopLevelDestination.kt
enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val labelRes: Int,
    val route: KClass<*>,
) {
    EXPENSES(Icons.Filled.Receipt, Icons.Outlined.Receipt, R.string.expenses, ExpensesGraph::class),
    INSIGHTS(Icons.Filled.PieChart, Icons.Outlined.PieChart, R.string.insights, InsightsGraph::class),
    SETTINGS(Icons.Filled.Settings, Icons.Outlined.Settings, R.string.settings, SettingsGraph::class),
}

// app — SpendLensAppState.kt
@Stable
class SpendLensAppState(val navController: NavHostController) {

    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() = TopLevelDestination.entries.firstOrNull { dest ->
            currentDestination?.hierarchy?.any { it.hasRoute(dest.route) } == true
        }

    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        val options = navOptions {
            // Standard bottom-nav semantics: single instance, state preserved per tab.
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.EXPENSES -> navController.navigateToExpenses(options)
            TopLevelDestination.INSIGHTS -> navController.navigateToInsights(options)
            TopLevelDestination.SETTINGS -> navController.navigateToSettings(options)
        }
    }
}
```

Note `hierarchy` — it matches the *graph*, so the Expenses tab stays selected on the detail screen. Forgetting it is the classic bottom-bar bug.

### 7.6 Deep links — free once routes are typed

Needed anyway for the Glance widget's quick-capture shortcut and for budget notifications.

```kotlin
composable<CaptureRoute>(
    deepLinks = listOf(navDeepLink<CaptureRoute>(basePath = "spendlens://capture")),
) { CaptureRoute(...) }
```

Test with:
`adb shell am start -a android.intent.action.VIEW -d "spendlens://capture"`

---

## 8. Screen anatomy — the pattern to repeat

Every screen splits in two: a **stateful route** that owns the ViewModel, and a **stateless screen** that takes data and emits lambdas. The stateless half is what previews and Paparazzi render, and what Compose UI tests drive.

```kotlin
// feature:expenses — ExpenseListScreen.kt

sealed interface ExpenseListUiState {
    data object Loading : ExpenseListUiState
    data class Empty(val hasActiveFilters: Boolean) : ExpenseListUiState
    data class Success(
        val expenses: ImmutableList<ExpenseUiModel>,
        val monthTotal: String,
        val syncStatus: SyncStatus,
    ) : ExpenseListUiState
    data class Error(val message: UiText) : ExpenseListUiState
}

@Composable
fun ExpenseListRoute(
    onExpenseClick: (String) -> Unit,
    onAddManualClick: () -> Unit,
    onScanReceiptClick: () -> Unit,
    viewModel: ExpenseListViewModel = hiltViewModel(),
) {
    // collectAsStateWithLifecycle, never collectAsState — the latter keeps
    // collecting while the app is backgrounded.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExpenseListScreen(
        uiState = uiState,
        onExpenseClick = onExpenseClick,
        onAddManualClick = onAddManualClick,
        onScanReceiptClick = onScanReceiptClick,
        onDeleteExpense = viewModel::deleteExpense,
        onUndoDelete = viewModel::undoDelete,
    )
}

@Composable
internal fun ExpenseListScreen(
    uiState: ExpenseListUiState,
    onExpenseClick: (String) -> Unit,
    /* …lambdas… */
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { /* … */ },
        floatingActionButton = { /* … */ },
    ) { padding ->
        when (uiState) {
            ExpenseListUiState.Loading -> LoadingIndicator(Modifier.padding(padding))
            is ExpenseListUiState.Empty -> EmptyState(
                hasFilters = uiState.hasActiveFilters,
                modifier = Modifier.padding(padding),
            )
            is ExpenseListUiState.Error -> ErrorState(uiState.message, Modifier.padding(padding))
            is ExpenseListUiState.Success -> LazyColumn(
                contentPadding = padding,   // contentPadding, not Modifier.padding
            ) {
                items(
                    items = uiState.expenses,
                    key = { it.id },        // stable keys — required for correct
                ) { expense ->              // animations and scroll restoration
                    ExpenseRow(
                        expense = expense,
                        onClick = { onExpenseClick(expense.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
```

Rules that hold across every screen:

- **`Modifier` is always the first optional parameter**, defaulted, and applied to the root — never consumed internally.
- **Hoist state, pass lambdas down.** No ViewModel below the route composable.
- **`sealed interface` for UI state**, so `when` is exhaustive and there is no `isLoading && data == null` ambiguity.
- **Strings never come from the ViewModel as `String`** — use a `UiText` wrapper (`@StringRes` id + args) so tests don't need a `Context` and translations still work.
- **Every state has a screen.** Loading, empty, empty-because-filtered, error, success. Phase 4 says "no blank screens"; this is how you honour it.

### Semantic colour at the call site

```kotlin
@Composable
fun BudgetBar(spent: Long, budget: Long, modifier: Modifier = Modifier) {
    val fraction = (spent.toFloat() / budget).coerceIn(0f, 1f)
    val status = when {
        spent > budget -> BudgetStatus.OVER
        fraction >= 0.8f -> BudgetStatus.NEAR
        else -> BudgetStatus.UNDER
    }
    val color = when (status) {
        BudgetStatus.OVER -> MaterialTheme.semantic.budgetOver
        BudgetStatus.NEAR -> MaterialTheme.semantic.budgetNear
        BudgetStatus.UNDER -> MaterialTheme.semantic.budgetUnder
    }

    LinearProgressIndicator(
        progress = { fraction },
        color = color,
        modifier = modifier.semantics {
            // Colour alone never carries meaning — TalkBack gets the words.
            contentDescription = "Spent $spent of $budget, ${status.name.lowercase()} budget"
        },
    )
}
```

---

## 9. Roadmap

Ship a working app at the end of every phase. Never leave `main` broken.

### Phase 0 — Foundation (3–4 days)
- [ ] Create project: Empty Compose Activity, Kotlin DSL, min SDK 26, target latest
- [ ] Set up `libs.versions.toml` version catalog
- [ ] Build `build-logic/` with 3–4 convention plugins
- [ ] Create the empty module skeleton, wire dependency rules
- [ ] Hilt setup + `Application` class
- [ ] ktlint + detekt configured and passing
- [ ] GitHub repo, `.gitignore`, first CI workflow (lint + build)

**Outcome:** an empty app that builds, lints, and CIs cleanly. Boring, and the most valuable phase.

### Phase 1 — Core data + list (1 week)
- [ ] `core:model` domain classes
- [ ] Room entities, DAOs, `TypeConverters`, first migration
- [ ] Repository in `core:data` exposing `Flow<List<Expense>>`
- [ ] **`core:designsystem` per §6:** colour schemes, semantic colours, typography, `SpendLensTheme`, edge-to-edge `MainActivity`
- [ ] **Type-safe navigation skeleton per §7:** routes, per-feature graphs, `NavHost`, bottom bar
- [ ] Manual expense entry screen
- [ ] Expense list screen with Compose
- [ ] **Tests:** DAO tests (in-memory Room), repository tests with fakes, ViewModel tests with Turbine

**Outcome:** a working offline expense tracker. Already usable.

### Phase 2 — Camera + OCR (1 week)
- [ ] CameraX preview + capture, runtime permission handling (including denial paths)
- [ ] ML Kit text recognition on the captured image
- [ ] Parser: extract merchant / total / date from raw OCR blocks — heuristics, and write tests with real sample text
- [ ] Review-and-correct screen before saving
- [ ] Store image to app-internal storage, thumbnail with Coil

**Outcome:** the demo moment. This is your README GIF.

### Phase 3 — Networking + sync (1.5 weeks)
- [ ] Retrofit service, DTOs, mappers, auth interceptor
- [ ] Repository writes locally first, marks `PENDING`
- [ ] WorkManager sync worker: upload pending, pull remote, resolve by `updatedAt`
- [ ] Retry/backoff policy, network constraint, expedited work for user-triggered sync
- [ ] Sync status in UI (pending badge, last-synced timestamp)
- [ ] Paging 3 with `RemoteMediator`
- [ ] **Tests:** MockWebServer contract tests, `WorkManagerTestInitHelper` for worker tests

**Outcome:** the hardest and most interview-valuable part. Document the conflict strategy.

### Phase 4 — Insights + polish (1 week)
- [ ] Category budgets, monthly aggregation queries
- [ ] Charts (Vico or Canvas)
- [ ] Biometric app lock, encrypted storage
- [ ] Material You dynamic color, dark theme
- [ ] Empty / loading / error states everywhere — no blank screens
- [ ] Meaningful Compose animations (shared element on list→detail)

### Phase 5 — The differentiators (1 week)
- [ ] Macrobenchmark module, measure cold start
- [ ] Generate Baseline Profile, **re-measure, record before/after numbers**
- [ ] Accessibility pass: TalkBack navigation, content descriptions, 48dp touch targets, dynamic type
- [ ] Paparazzi screenshot tests for the design system
- [ ] R8/ProGuard rules, verify release build works
- [ ] Glance widget

### Phase 6 — Ship it (3–4 days)
- [ ] README: pitch, GIF, architecture diagram (Excalidraw/Mermaid), stack table, **decisions & trade-offs** section, perf numbers, test coverage
- [ ] Signed release APK attached to a GitHub Release
- [ ] Play Store internal testing track (or at minimum a downloadable APK)
- [ ] CI badge, screenshots in `/docs`
- [ ] Add to resume with two concrete metrics

**Total: 6–8 weeks of consistent evenings.** Slower is fine. Half-finished is not.

---

## 10. How to start — first three days

**Day 1 — environment and skeleton**
1. Android Studio, latest stable. Create new project → Empty Activity → Compose.
2. Immediately convert to version catalog: move every dependency into `gradle/libs.versions.toml`.
3. Create `build-logic/convention` as an included build in `settings.gradle.kts`.
4. Write one convention plugin (`AndroidLibraryConventionPlugin`) that sets compileSdk, minSdk, Java/Kotlin target. Apply it to a throwaway `:core:common` module.
5. Commit. First commit should be `chore: project scaffolding with convention plugins`.

**Day 2 — modules and DI**
1. Create every module folder from section 4, each with a minimal `build.gradle.kts` applying the right convention plugin.
2. Add Hilt: application-level setup, `@HiltAndroidApp`, one `@Module` in `:core:common` providing dispatchers.
3. Verify `./gradlew build` passes from a clean clone.

**Day 3 — CI and first vertical slice**
1. GitHub Actions workflow: checkout → JDK 17 → `./gradlew detekt ktlintCheck testDebugUnitTest assembleDebug`.
2. Create the `Expense` model in `:core:model` and one Room entity + DAO in `:core:database`.
3. Write your first test — a DAO insert/read test. Watch it pass in CI.

After that, follow Phase 1. The rule for the whole project: **every phase ends with green CI and a tagged commit.**

### Learning as you go
If Compose or Hilt are new, don't pre-study for two weeks. Build Phase 1 badly, then refactor. The official codelabs (Compose Basics, Navigation, Room+Flow) are 2–3 hours each and enough to start. Read `android/nowinandroid` source when stuck on structure.

---

## 11. Interview talking points to harvest

Keep a `DECISIONS.md` as you go. By the end you want crisp answers to:

- Why is Room the source of truth instead of the network?
- How do you resolve a conflict when the same expense is edited offline on two devices?
- Why client-generated UUIDs instead of server IDs?
- How does `RemoteMediator` differ from a plain `PagingSource`?
- What does a Baseline Profile actually do, and what did it buy you?
- How do you test a `Flow` that emits over time? (Turbine)
- Why convention plugins instead of `subprojects {}` in the root build file?
- What breaks if you make a Compose state holder mutable and pass it down?
- How do you handle permanent camera-permission denial?
- Two feature modules can't import each other — so how does the expense list open the camera? (§7.2)
- Why put semantic colours in a `CompositionLocal` instead of top-level vals or extra `ColorScheme` fields?
- Why are semantic colours excluded from Material You dynamic colour?
- Why `staticCompositionLocalOf` rather than `compositionLocalOf` for the theme?
- Why does `List<T>` in a UI state class hurt performance, and what do you use instead?
- How do you keep the bottom-bar tab selected on a detail screen? (`NavDestination.hierarchy`)
- What's recomposition, and how did you find and fix an unnecessary one? (Layout Inspector / recomposition counts)

Each of these is a 2-minute answer that separates you from candidates who followed a tutorial.

---

## 12. Resume line (write it at the end, with real numbers)

> **SpendLens — Offline-First Expense Tracker** | *Kotlin, Jetpack Compose, Room, WorkManager, CameraX, ML Kit, Hilt*
> - Built a multi-module (14 modules) native Android app with offline-first architecture; Room as single source of truth with WorkManager-based conflict-resolving sync.
> - Implemented on-device receipt OCR with CameraX and ML Kit, extracting merchant/amount/date with no network dependency.
> - Reduced cold-start time from XXXms to XXXms via Baseline Profiles, verified with Macrobenchmark; XX% test coverage across data and domain layers, enforced in GitHub Actions CI.

No invented percentages. Only numbers you can point at a script and reproduce.
