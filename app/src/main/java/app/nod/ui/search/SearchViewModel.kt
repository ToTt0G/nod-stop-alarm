package app.nod.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nod.core.GeoPoint
import app.nod.data.db.PlaceEntity
import app.nod.data.location.LocationRepository
import app.nod.data.model.Units
import app.nod.data.places.PlacesRepository
import app.nod.data.search.PlaceResult
import app.nod.data.search.SearchFilter
import app.nod.data.search.SearchRepository
import app.nod.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUi {
    data object Idle : SearchUi
    data object Loading : SearchUi
    data class Results(val items: List<PlaceResult>) : SearchUi
    data object Empty : SearchUi
    data object Error : SearchUi
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
    private val location: LocationRepository,
    places: PlacesRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(SearchFilter.ALL)
    private val retry = MutableStateFlow(0)
    private var near: GeoPoint? = location.lastKnown

    val units: StateFlow<Units> = settings.settings.map { it.units }.stateIn(viewModelScope, SharingStarted.Eagerly, Units.METRIC)
    val saved: StateFlow<List<PlaceEntity>> = places.saved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ui: StateFlow<SearchUi> = combine(query, filter, retry) { q, f, _ -> q.trim() to f }
        .debounce { (q, _) -> if (q.length < 2) 0L else 280L }
        .flatMapLatest { (q, f) ->
            flow {
                if (q.length < 2) { emit(SearchUi.Idle); return@flow }
                emit(SearchUi.Loading)
                val r = runCatching { search.search(q, f, near?.lat, near?.lon) }
                emit(
                    r.fold(
                        onSuccess = { if (it.isEmpty()) SearchUi.Empty else SearchUi.Results(it) },
                        onFailure = { SearchUi.Error },
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi.Idle)

    init {
        viewModelScope.launch { location.current()?.let { near = it } }
    }

    fun retry() { retry.value++ }
}
