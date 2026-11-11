package app.nod

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import app.nod.alarm.AlarmActivity
import app.nod.data.location.LocationRepository
import app.nod.data.model.TripPlan
import app.nod.data.places.PlacesRepository
import app.nod.data.places.toDestination
import app.nod.data.settings.Settings
import app.nod.data.settings.SettingsRepository
import app.nod.trip.TripController
import app.nod.trip.TripNotifications
import app.nod.trip.TripPhase
import app.nod.trip.TripState
import app.nod.trip.TripStateHolder
import app.nod.ui.nav.NodNavHost
import app.nod.ui.nav.PendingNav
import app.nod.ui.theme.NodTheme
import app.nod.ui.theme.isNodDark
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val controller: TripController,
    private val places: PlacesRepository,
    private val location: LocationRepository,
    private val holder: TripStateHolder,
) : ViewModel() {
    val settings: StateFlow<Settings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val trip = holder.state
    val pending = MutableStateFlow<PendingNav?>(null)

    init {
        viewModelScope.launch {
            settingsRepo.activePlan()?.let { controller.resume(it) }
        }
    }

    /** One-tap start from a widget or launcher shortcut. Falls back to the zone screen if we can't. */
    fun startPlace(id: Long) = viewModelScope.launch {
        if (holder.state.value is TripState.Active) { pending.value = PendingNav.Trip; return@launch }
        val p = places.get(id) ?: return@launch
        if (!location.hasPermission() || settingsRepo.current().onboarded.not()) {
            pending.value = PendingNav.Place(id); return@launch
        }
        val s = settingsRepo.current()
        controller.start(TripPlan(p.toDestination(), p.triggerMode, p.minutesBefore, p.alertStyle, s.headsUp, p.id))
        pending.value = PendingNav.Trip
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        handleIntent(intent)

        // Bring up the alarm when it fires while Nod is open.
        lifecycleScope.launch {
            vm.trip.map { (it as? TripState.Active)?.let { a -> a.phase == TripPhase.ALARM && !a.silenced } == true }
                .distinctUntilChanged()
                .collect { ringing ->
                    if (ringing) startActivity(Intent(this@MainActivity, AlarmActivity::class.java))
                }
        }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            val dark = isNodDark(s.theme)
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            NodTheme(dark = dark) {
                NodNavHost(
                    onboarded = s.onboarded,
                    dark = dark,
                    pending = vm.pending,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.getBooleanExtra(TripNotifications.EXTRA_OPEN_TRIP, false) -> vm.pending.value = PendingNav.Trip
            intent.hasExtra(EXTRA_START_PLACE) -> vm.startPlace(intent.getLongExtra(EXTRA_START_PLACE, -1))
            intent.getBooleanExtra(EXTRA_OPEN_SEARCH, false) -> vm.pending.value = PendingNav.Search
        }
    }

    companion object {
        const val EXTRA_START_PLACE = "start_place"
        const val EXTRA_OPEN_SEARCH = "open_search"
    }
}
