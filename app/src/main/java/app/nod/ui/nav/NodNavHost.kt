package app.nod.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.nod.data.model.Destination
import app.nod.ui.history.HistoryScreen
import app.nod.ui.home.HomeScreen
import app.nod.ui.onboarding.OnboardingScreen
import app.nod.ui.search.SearchScreen
import app.nod.ui.settings.SettingsScreen
import app.nod.ui.trip.TripScreen
import app.nod.ui.zone.ZoneScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data class ZoneRoute(val destination: String? = null, val placeId: Long = -1L)
@Serializable data object TripRoute
@Serializable data object SettingsRoute
@Serializable data object HistoryRoute

sealed interface PendingNav {
    data object Trip : PendingNav
    data object Search : PendingNav
    data class Place(val id: Long) : PendingNav
}

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private val navJson = Json { ignoreUnknownKeys = true }
fun Destination.toRoute(placeId: Long = -1L) = ZoneRoute(navJson.encodeToString(Destination.serializer(), this), placeId)
fun ZoneRoute.destinationOrNull(): Destination? =
    destination?.let { runCatching { navJson.decodeFromString(Destination.serializer(), it) }.getOrNull() }

private const val DUR = 380
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    slideInHorizontally(tween(DUR)) { it / 5 } + fadeIn(tween(DUR))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    slideOutHorizontally(tween(DUR)) { -it / 8 } + fadeOut(tween(DUR / 2))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(tween(DUR)) { -it / 8 } + fadeIn(tween(DUR))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(tween(DUR)) { it / 5 } + fadeOut(tween(DUR / 2))

@Composable
fun NodNavHost(onboarded: Boolean, dark: Boolean, pending: MutableStateFlow<PendingNav?>) {
    val nav = rememberNavController()
    val pend by pending.collectAsStateWithLifecycle()
    LaunchedEffect(pend, onboarded) {
        val p = pend ?: return@LaunchedEffect
        if (!onboarded) return@LaunchedEffect
        when (p) {
            PendingNav.Trip -> nav.navigate(TripRoute) { launchSingleTop = true }
            PendingNav.Search -> nav.navigate(SearchRoute) { launchSingleTop = true }
            is PendingNav.Place -> nav.navigate(ZoneRoute(placeId = p.id))
        }
        pending.value = null
    }

    SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = nav,
                startDestination = if (onboarded) HomeRoute else OnboardingRoute,
                enterTransition = { enter() },
                exitTransition = { exit() },
                popEnterTransition = { popEnter() },
                popExitTransition = { popExit() },
            ) {
                composable<OnboardingRoute>(
                    exitTransition = { fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.04f) },
                ) {
                    OnboardingScreen(onDone = {
                        nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                    })
                }
                composable<HomeRoute>(
                    enterTransition = { fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.98f) },
                ) {
                    Scoped(this) {
                        HomeScreen(
                            onSearch = { nav.navigate(SearchRoute) },
                            onPickOnMap = { nav.navigate(ZoneRoute()) },
                            onOpen = { d, id -> nav.navigate(d.toRoute(id)) },
                            onTrip = { nav.navigate(TripRoute) { launchSingleTop = true } },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onHistory = { nav.navigate(HistoryRoute) },
                        )
                    }
                }
                composable<SearchRoute>(
                    enterTransition = { fadeIn(tween(300)) },
                    exitTransition = { fadeOut(tween(250)) },
                    popExitTransition = { fadeOut(tween(250)) },
                ) {
                    Scoped(this) {
                        SearchScreen(
                            onBack = { nav.popBackStack() },
                            onPick = { d, id -> nav.navigate(d.toRoute(id)) { popUpTo(SearchRoute) { inclusive = true } } },
                            onPickOnMap = { nav.navigate(ZoneRoute()) { popUpTo(SearchRoute) { inclusive = true } } },
                        )
                    }
                }
                composable<ZoneRoute> {
                    ZoneScreen(
                        dark = dark,
                        onBack = { nav.popBackStack() },
                        onSearch = { nav.navigate(SearchRoute) { popUpTo(HomeRoute) } },
                        onStarted = { nav.navigate(TripRoute) { popUpTo(HomeRoute) } },
                    )
                }
                composable<TripRoute>(
                    enterTransition = { fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.94f) },
                ) {
                    TripScreen(dark = dark, onClose = { backToHome(nav) })
                }
                composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable<HistoryRoute> {
                    HistoryScreen(onBack = { nav.popBackStack() }, onOpen = { d -> nav.navigate(d.toRoute()) })
                }
            }
        }
    }
}

private fun backToHome(nav: NavHostController) {
    if (!nav.popBackStack(HomeRoute, inclusive = false)) {
        nav.navigate(HomeRoute) { popUpTo(0) }
    }
}

@Composable
private fun Scoped(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimScope provides scope) { content() }
}

/** Shared-element helper that degrades gracefully when no transition scope is around. */
@Composable
fun Modifier.sharedBounds(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimScope.current ?: return this
    return with(shared) {
        this@sharedBounds.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
