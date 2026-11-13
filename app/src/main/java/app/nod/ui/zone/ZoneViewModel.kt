package app.nod.ui.zone

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.nod.core.Geo
import app.nod.core.GeoPoint
import app.nod.data.location.LocationRepository
import app.nod.data.model.AlertStyle
import app.nod.data.model.Destination
import app.nod.data.model.PlaceKind
import app.nod.data.model.TriggerMode
import app.nod.data.model.TripPlan
import app.nod.data.model.Units
import app.nod.data.places.PlacesRepository
import app.nod.data.places.toDestination
import app.nod.data.search.SearchRepository
import app.nod.data.settings.SettingsRepository
import app.nod.trip.TripController
import app.nod.ui.nav.ZoneRoute
import app.nod.ui.nav.destinationOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZoneUi(
    val ready: Boolean = false,
    val name: String = "",
    val subtitle: String = "",
    val kind: PlaceKind = PlaceKind.PLACE,
    val center: GeoPoint = FALLBACK,
    val radiusM: Int = PlaceKind.PLACE.defaultRadiusM,
    val resolving: Boolean = false,
    val isPin: Boolean = false,
    val trigger: TriggerMode = TriggerMode.DISTANCE,
    val minutes: Int = 3,
    val alertStyle: AlertStyle = AlertStyle.ALARM,
    val savedId: Long? = null,
    val units: Units = Units.METRIC,
    val headsUp: Boolean = true,
    /** Bumped whenever the camera should jump to [center] (initial load, "my location"). */
    val cameraTick: Int = 0,
) {
    companion object {
        /** Stockholm Centralstation: a friendly default when we have no idea where you are. */
        val FALLBACK = GeoPoint(59.3303, 18.0581)
    }
}

@HiltViewModel
class ZoneViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val places: PlacesRepository,
    private val search: SearchRepository,
    private val location: LocationRepository,
    private val settings: SettingsRepository,
    private val controller: TripController,
) : ViewModel() {
    private val route = savedState.toRoute<ZoneRoute>()
    private val _ui = MutableStateFlow(ZoneUi())
    val ui: StateFlow<ZoneUi> = _ui.asStateFlow()
    private var reverseJob: Job? = null
    private val permissionTick = MutableStateFlow(0)

    /**
     * Where you are, for the dot on the map. Location updates run only while the screen collects
     * this (it stops a few seconds after the app leaves the foreground).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val me: StateFlow<GeoPoint?> = permissionTick
        .flatMapLatest { location.live() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), location.lastKnown)

    /** A permission prompt came back: start the live dot if it was granted. */
    fun onPermissionResult() = permissionTick.update { it + 1 }

    init {
        viewModelScope.launch {
            val s = settings.current()
            _ui.update { it.copy(units = s.units, trigger = s.triggerMode, minutes = s.minutesBefore, alertStyle = s.alertStyle, headsUp = s.headsUp) }

            val saved = route.placeId.takeIf { it > 0 }?.let { places.get(it) }
            val dest = saved?.toDestination() ?: route.destinationOrNull()
            if (dest != null) {
                val existing = saved ?: places.findSaved(dest.lat, dest.lon)
                _ui.update {
                    it.copy(
                        ready = true, name = dest.name, subtitle = dest.subtitle, kind = dest.kind,
                        center = GeoPoint(dest.lat, dest.lon), radiusM = existing?.radiusM ?: dest.radiusM,
                        savedId = existing?.id,
                        trigger = existing?.triggerMode ?: it.trigger,
                        minutes = existing?.minutesBefore ?: it.minutes,
                        alertStyle = existing?.alertStyle ?: it.alertStyle,
                        cameraTick = it.cameraTick + 1,
                    )
                }
            } else {
                val here = location.current() ?: location.lastKnown
                val c = here ?: ZoneUi.FALLBACK
                _ui.update { it.copy(ready = true, center = c, isPin = true, cameraTick = it.cameraTick + 1) }
                resolve(c)
            }
        }
    }

    /** The map stopped moving: the pin now marks a new spot. */
    fun onCameraIdle(center: GeoPoint) {
        val cur = _ui.value
        if (!cur.ready) return
        if (Geo.distance(center, cur.center) < 15) {
            // A nudge: same place (name, saved link), but use exactly the point under the pin.
            if (center != cur.center) _ui.update { it.copy(center = center) }
            return
        }
        _ui.update { it.copy(center = center, isPin = true, savedId = null) }
        resolve(center)
    }

    private fun resolve(p: GeoPoint) {
        reverseJob?.cancel()
        _ui.update { it.copy(resolving = true) }
        reverseJob = viewModelScope.launch {
            delay(250)
            val d = runCatching { search.reverse(p.lat, p.lon) }.getOrNull()
            _ui.update {
                it.copy(
                    resolving = false,
                    name = d?.name ?: "",
                    subtitle = d?.subtitle ?: "",
                    kind = d?.kind?.takeIf { k -> k != PlaceKind.CITY } ?: PlaceKind.PLACE,
                )
            }
        }
    }

    fun locateMe() = viewModelScope.launch {
        val here = location.current() ?: return@launch
        _ui.update { it.copy(center = here, isPin = true, savedId = null, cameraTick = it.cameraTick + 1) }
        resolve(here)
    }

    fun setRadius(m: Int) = _ui.update { it.copy(radiusM = m) }
    fun setTrigger(t: TriggerMode) {
        _ui.update { it.copy(trigger = t) }
        viewModelScope.launch { settings.setTrigger(t) }
    }
    fun setMinutes(m: Int) {
        _ui.update { it.copy(minutes = m) }
        viewModelScope.launch { settings.setMinutes(m) }
    }
    fun setAlertStyle(s: AlertStyle) = _ui.update { it.copy(alertStyle = s) }

    fun toggleSaved(fallbackName: String) = viewModelScope.launch {
        val u = _ui.value
        if (u.savedId != null) {
            places.delete(u.savedId)
            _ui.update { it.copy(savedId = null) }
        } else {
            val id = places.save(plan(fallbackName))
            _ui.update { it.copy(savedId = id) }
        }
    }

    private fun plan(fallbackName: String): TripPlan {
        val u = _ui.value
        return TripPlan(
            destination = Destination(
                name = u.name.ifBlank { fallbackName },
                subtitle = u.subtitle,
                lat = u.center.lat,
                lon = u.center.lon,
                kind = u.kind,
                radiusM = u.radiusM,
            ),
            triggerMode = u.trigger,
            minutesBefore = u.minutes,
            alertStyle = u.alertStyle,
            headsUp = u.headsUp,
            savedPlaceId = u.savedId,
        )
    }

    fun start(fallbackName: String) {
        val p = plan(fallbackName)
        // Remember tweaks made to a saved place.
        p.savedPlaceId?.let { id -> viewModelScope.launch { places.save(p, id) } }
        controller.start(p)
    }
}
