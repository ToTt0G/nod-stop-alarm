package app.nod.trip

import app.nod.data.model.TripPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class TripPhase { LOCATING, TRACKING, HEADS_UP, ALARM }

sealed interface TripState {
    data object Idle : TripState
    data class Active(
        val plan: TripPlan,
        val phase: TripPhase = TripPhase.LOCATING,
        val live: LiveStats? = null,
        /** Set when the alarm sound was silenced with a volume key but not yet dismissed. */
        val silenced: Boolean = false,
    ) : TripState
}

/** Single source of truth for the running journey, shared by the service, UI, tile and alarm screen. */
@Singleton
class TripStateHolder @Inject constructor() {
    private val _state = MutableStateFlow<TripState>(TripState.Idle)
    val state: StateFlow<TripState> = _state.asStateFlow()

    fun set(state: TripState) { _state.value = state }
    fun update(f: (TripState) -> TripState) { _state.value = f(_state.value) }
}
