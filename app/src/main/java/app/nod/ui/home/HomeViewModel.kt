package app.nod.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nod.data.db.PlaceEntity
import app.nod.data.db.TripEntity
import app.nod.data.model.Units
import app.nod.data.places.PlacesRepository
import app.nod.data.settings.SettingsRepository
import app.nod.trip.TripState
import app.nod.trip.TripStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val places: PlacesRepository,
    private val settings: SettingsRepository,
    holder: TripStateHolder,
) : ViewModel() {
    val saved: StateFlow<List<PlaceEntity>?> = places.saved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Recent trips, one per destination. */
    val recent: StateFlow<List<TripEntity>?> = places.recentTrips(60)
        .map { list -> list.distinctBy { "${it.name}|${(it.lat * 1000).roundToInt()}|${(it.lon * 1000).roundToInt()}" }.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trip: StateFlow<TripState> = holder.state
    val units: StateFlow<Units> = settings.settings.map { it.units }.stateIn(viewModelScope, SharingStarted.Eagerly, Units.METRIC)

    /**
     * The quiet "star Nod on GitHub" card: only after the app has proven itself (3+ arrivals, first trip
     * 2+ days ago), never mid-trip, and never again once either button was tapped. Derived purely from
     * existing flows: no timers or background work. The 2-day check is re-evaluated whenever an input emits
     * or Home is resubscribed.
     */
    val showStarPrompt: StateFlow<Boolean> = combine(
        places.arrivedCount,
        places.firstTripAt,
        settings.settings.map { it.starPromptDone }.distinctUntilChanged(),
        holder.state.map { it is TripState.Idle }.distinctUntilChanged(),
    ) { arrived, firstAt, done, idle ->
        !done && idle && arrived >= STAR_MIN_ARRIVALS &&
            firstAt != null && System.currentTimeMillis() - firstAt >= STAR_MIN_AGE_MS
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Either button on the star card answers it for good. */
    fun answerStarPrompt() = viewModelScope.launch { settings.setStarPromptDone() }

    /** Entrance animation should only play the first time Home appears. */
    var introPlayed = false

    fun delete(place: PlaceEntity) = viewModelScope.launch { places.delete(place.id) }
}

private const val STAR_MIN_ARRIVALS = 3
private const val STAR_MIN_AGE_MS = 2 * 24 * 60 * 60 * 1000L
