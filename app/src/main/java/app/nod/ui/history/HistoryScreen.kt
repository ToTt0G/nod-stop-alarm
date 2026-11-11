package app.nod.ui.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nod.R
import app.nod.data.db.TripEntity
import app.nod.data.db.TripOutcome
import app.nod.data.model.Destination
import app.nod.data.places.PlacesRepository
import app.nod.data.places.toDestination
import app.nod.ui.components.CircleIconButton
import app.nod.ui.components.KindBadge
import app.nod.ui.components.SectionLabel
import app.nod.ui.components.bounceClick
import app.nod.ui.components.rememberHaptics
import app.nod.ui.home.relativeDay
import app.nod.ui.theme.NodTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val places: PlacesRepository) : ViewModel() {
    val trips: StateFlow<List<TripEntity>?> = places.recentTrips(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun delete(id: Long) = viewModelScope.launch { places.deleteTrip(id) }
    fun clear() = viewModelScope.launch { places.clearHistory() }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Destination) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    val c = NodTheme.colors
    var confirmClear by remember { mutableStateOf(false) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, elevated = false, container = Color.Transparent)
            Spacer(Modifier.weight(1f))
            if (!trips.isNullOrEmpty()) {
                CircleIconButton(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_history), { confirmClear = true }, elevated = false, container = Color.Transparent)
            }
        }
        Text(stringResource(R.string.history), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 12.dp))

        val list = trips
        if (list != null && list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.history_empty_text), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        } else if (list != null) {
            val groups = list.groupBy { relativeDayKey(it.startedAt) }
            LazyColumn(contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
                groups.forEach { (_, items) ->
                    item(key = "h${items.first().id}") {
                        SectionLabel(relativeDay(items.first().startedAt), Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp).animateItem())
                    }
                    items(items, key = { it.id }) { t ->
                        SwipeRow(t, onOpen = { onOpen(t.toDestination()) }, onDelete = { vm.delete(t.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_text)) },
            confirmButton = { TextButton({ vm.clear(); confirmClear = false }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = c.card,
        )
    }
}

private fun relativeDayKey(ms: Long): Long = ms / 86_400_000L

@Composable
private fun SwipeRow(t: TripEntity, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { haptics.confirm(); onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            val p by animateFloatAsState(if (state.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.6f, label = "s")
            Box(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f)).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.remove), tint = Color.White, modifier = Modifier.graphicsLayer { scaleX = p; scaleY = p })
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).bounceClick(pressedScale = 0.98f, onClick = onOpen).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindBadge(t.kind)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val mins = ((t.endedAt - t.startedAt) / 60_000L).coerceAtLeast(0)
                Text(
                    listOf(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t.startedAt)),
                        stringResource(if (t.outcome == TripOutcome.ARRIVED) R.string.outcome_arrived else R.string.outcome_ended),
                        stringResource(R.string.minutes_short, mins),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            if (t.outcome == TripOutcome.ARRIVED) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.zone))
            }
        }
    }
}
