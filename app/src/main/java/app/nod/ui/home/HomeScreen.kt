package app.nod.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nod.R
import app.nod.core.Format
import app.nod.data.db.PlaceEntity
import app.nod.data.db.TripEntity
import app.nod.data.db.TripOutcome
import app.nod.data.model.Destination
import app.nod.data.model.Units
import app.nod.data.places.toDestination
import app.nod.trip.TripPhase
import app.nod.trip.TripState
import app.nod.ui.components.CircleIconButton
import app.nod.ui.components.KindBadge
import app.nod.ui.components.LiveDot
import app.nod.ui.components.NodButton
import app.nod.ui.components.NodMark
import app.nod.ui.components.RollingText
import app.nod.ui.components.SectionLabel
import app.nod.ui.components.bounceClick
import app.nod.ui.components.enter
import app.nod.ui.components.pressable
import app.nod.ui.components.rememberHaptics
import app.nod.ui.components.StatusBarScrim
import app.nod.ui.nav.sharedBounds
import app.nod.ui.theme.NodMotion
import app.nod.ui.theme.NodTheme
import java.util.Calendar

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onPickOnMap: () -> Unit,
    onOpen: (Destination, Long) -> Unit,
    onTrip: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val saved by vm.saved.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val showStar by vm.showStarPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val play = remember { !vm.introPlayed }
    DisposableEffect(Unit) { onDispose { vm.introPlayed = true } }
    var confirmDelete by remember { mutableStateOf<PlaceEntity?>(null) }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val loaded = saved != null && recent != null
    // Startup metric (time to full display): the screen is complete once lists are loaded.
    ReportDrawnWhen { loaded }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(top = top + 8.dp, bottom = bottom + 32.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).enter(0, play),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NodMark(Modifier.size(34.dp), color = NodTheme.colors.cta.takeIf { !NodTheme.colors.isDark } ?: NodTheme.colors.accent, breathe = true)
                    Spacer(Modifier.width(6.dp))
                    Text("nod", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(Icons.Rounded.History, stringResource(R.string.history), onHistory, elevated = false, size = 44.dp, container = Color.Transparent)
                    CircleIconButton(Icons.Rounded.Settings, stringResource(R.string.settings), onSettings, elevated = false, size = 44.dp, container = Color.Transparent)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 36.dp).enter(1, play)) {
                    Text(stringResource(greeting()), style = MaterialTheme.typography.bodyLarge, color = NodTheme.colors.muted)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            item {
                SearchPill(onSearch, onPickOnMap, Modifier.padding(horizontal = 20.dp).padding(top = 24.dp).enter(2, play))
            }
            item {
                AnimatedVisibility(
                    visible = trip is TripState.Active,
                    enter = expandVertically(NodMotion.snappy()) + fadeIn(),
                    exit = shrinkVertically(NodMotion.snappy()) + fadeOut(),
                ) {
                    (trip as? TripState.Active)?.let { ActiveTripCard(it, units, onTrip, Modifier.padding(horizontal = 20.dp).padding(top = 20.dp)) }
                }
            }
            item {
                AnimatedVisibility(
                    visible = showStar,
                    enter = expandVertically(NodMotion.gentle()) + fadeIn(NodMotion.gentle()),
                    exit = shrinkVertically(NodMotion.snappy()) + fadeOut(),
                ) {
                    StarCard(
                        onStar = { openNodRepo(context); vm.answerStarPrompt() },
                        onNotNow = vm::answerStarPrompt,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp),
                    )
                }
            }

            if (loaded && saved!!.isEmpty() && recent!!.isEmpty()) {
                item { HowItWorks(Modifier.padding(horizontal = 20.dp).padding(top = 36.dp).enter(3, play)) }
            } else if (loaded) {
                item {
                    SectionLabel(stringResource(R.string.saved_places), Modifier.padding(start = 24.dp, top = 36.dp, bottom = 12.dp).enter(3, play))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.enter(4, play),
                    ) {
                        items(saved!!, key = { it.id }) { p ->
                            SavedCard(p, units, onClick = { onOpen(p.toDestination(), p.id) }, onLongClick = { confirmDelete = p }, modifier = Modifier.animateItem())
                        }
                        item(key = "add") { AddCard(onSearch) }
                    }
                }
                if (recent!!.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 32.dp, bottom = 4.dp).enter(5, play),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionLabel(stringResource(R.string.recent))
                            Spacer(Modifier.weight(1f))
                            TextButton(onHistory) { Text(stringResource(R.string.see_all), style = MaterialTheme.typography.labelMedium, color = NodTheme.colors.muted) }
                        }
                    }
                    items(recent!!, key = { "r${it.id}" }) { t ->
                        RecentRow(t, onClick = { onOpen(t.toDestination(), -1) }, modifier = Modifier.animateItem().enter(6, play))
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 40.dp).enter(7, play),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bedtime, null, tint = NodTheme.colors.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.footer_free), style = MaterialTheme.typography.bodySmall, color = NodTheme.colors.muted)
                }
            }
        }
        StatusBarScrim()
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.remove_place_title)) },
            text = { Text(stringResource(R.string.remove_place_text, p.name)) },
            confirmButton = { TextButton({ vm.delete(p); confirmDelete = null }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = NodTheme.colors.card,
        )
    }
}

/** Nod's public source repository, linked from the Home star card and Settings → About. */
const val NOD_REPO_URL = "https://github.com/ToTt0G/nod-stop-alarm"

/** Opens the repo in the browser; silently does nothing if no app can handle the link. */
fun openNodRepo(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NOD_REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** One-time, pressure-free ask for a GitHub star. Inline in the list; either button answers it for good. */
@Composable
private fun StarCard(onStar: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val c = NodTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Star, null, tint = if (c.isDark) c.accent else c.cta, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            // Title and body are read together by TalkBack.
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.star_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.star_body), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        // Wraps to two lines on narrow screens, large font scales or longer translations.
        FlowRow(Modifier.padding(start = 50.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NodButton(stringResource(R.string.star_action), onStar, Modifier.align(Alignment.CenterVertically).height(44.dp))
            TextButton(onNotNow, Modifier.align(Alignment.CenterVertically)) {
                Text(stringResource(R.string.star_not_now), style = MaterialTheme.typography.labelLarge, color = c.muted, maxLines = 1)
            }
        }
    }
}

private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    in 18..22 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

@Composable
private fun SearchPill(onSearch: () -> Unit, onMap: () -> Unit, modifier: Modifier = Modifier) {
    val c = NodTheme.colors
    Row(
        modifier
            .sharedBounds("search")
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(c.card)
            .border(1.dp, c.hairline, CircleShape)
            .bounceClick(pressedScale = 0.98f, onClick = onSearch)
            .padding(start = 22.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = c.muted, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier
                .bounceClick(pressedScale = 0.9f, onClick = onMap)
                .size(48.dp)
                .clip(CircleShape)
                .background(c.cta),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Map, stringResource(R.string.pick_on_map), tint = c.onCta, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun ActiveTripCard(trip: TripState.Active, units: Units, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = NodTheme.colors
    val ink = c.onAccent
    val live = trip.live
    val progress by animateFloatAsState(live?.progress ?: 0f, NodMotion.gentle(), label = "p")
    Column(
        modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(28.dp))
            .background(c.accent)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(ink, size = 8.dp)
            Text(
                stringResource(if (trip.phase == TripPhase.ALARM) R.string.status_arrived else R.string.on_the_way).uppercase(),
                style = MaterialTheme.typography.labelSmall, color = ink,
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(trip.plan.destination.name, style = MaterialTheme.typography.titleLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (live != null) {
                val d = Format.distance(live.distanceM, units)
                RollingText(d.value, MaterialTheme.typography.displayMedium, ink, contentDescription = d.toString())
                Spacer(Modifier.width(6.dp))
                Text(d.unit, style = MaterialTheme.typography.headlineSmall, color = ink.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 6.dp).clearAndSetSemantics {})
            } else {
                Text(stringResource(R.string.locating), style = MaterialTheme.typography.headlineSmall, color = ink)
            }
            Spacer(Modifier.weight(1f))
            live?.alarmInSec?.let {
                Text(stringResource(R.string.alarm_in_short, Format.duration(it)), style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.14f))) {
            Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).height(6.dp).clip(CircleShape).background(ink))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedCard(p: PlaceEntity, units: Units, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(156.dp)
            .height(150.dp)
            .pressable(interaction)
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(24.dp))
            .combinedClickable(interaction, null, onClick = { haptics.click(); onClick() }, onLongClick = { haptics.heavy(); onLongClick() })
            .padding(16.dp),
    ) {
        KindBadge(p.kind, size = 40.dp)
        Spacer(Modifier.weight(1f))
        Text(p.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.zone_size, Format.distance(p.radiusM.toDouble(), units).toString()),
            style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1,
        )
    }
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    val c = NodTheme.colors
    Column(
        Modifier
            .width(120.dp)
            .height(150.dp)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, c.hairline, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.add_place), style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}

@Composable
private fun RecentRow(t: TripEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = NodTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(t.kind)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    relativeDay(t.startedAt),
                    stringResource(if (t.outcome == TripOutcome.ARRIVED) R.string.outcome_arrived else R.string.outcome_ended),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun relativeDay(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val days = ((now.timeInMillis - then.timeInMillis) / 86_400_000L).toInt()
    val sameDay = now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    return when {
        sameDay -> stringResource(R.string.today)
        days <= 1 -> stringResource(R.string.yesterday)
        days < 7 -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(then.time)
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(then.time)
    }
}

@Composable
private fun HowItWorks(modifier: Modifier = Modifier) {
    val c = NodTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(28.dp))
            .padding(24.dp),
    ) {
        SectionLabel(stringResource(R.string.how_it_works))
        Spacer(Modifier.height(18.dp))
        listOf(
            R.string.step1_title to R.string.step1_text,
            R.string.step2_title to R.string.step2_text,
            R.string.step3_title to R.string.step3_text,
        ).forEachIndexed { i, (t, d) ->
            Row(Modifier.padding(bottom = if (i < 2) 18.dp else 0.dp)) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(if (i == 2) c.accent else c.cardHigh), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = if (i == 2) c.onAccent else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(t), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(d), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            }
        }
    }
}
