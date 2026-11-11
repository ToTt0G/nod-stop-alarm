package app.nod.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nod.R
import app.nod.core.Format
import app.nod.data.model.Destination
import app.nod.data.model.PlaceKind
import app.nod.data.model.Units
import app.nod.data.places.toDestination
import app.nod.data.search.PlaceResult
import app.nod.data.search.SearchFilter
import app.nod.ui.components.CircleIconButton
import app.nod.ui.components.KindBadge
import app.nod.ui.components.NodButton
import app.nod.ui.components.NodButtonStyle
import app.nod.ui.components.SectionLabel
import app.nod.ui.components.bounceClick
import app.nod.ui.components.icon
import app.nod.ui.components.rememberHaptics
import app.nod.ui.nav.sharedBounds
import app.nod.ui.theme.NodTheme
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPick: (Destination, Long) -> Unit,
    onPickOnMap: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val c = NodTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) {
        delay(320) // let the shared-element transition settle before the keyboard slides in
        runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, elevated = false, container = androidx.compose.ui.graphics.Color.Transparent)
            Spacer(Modifier.width(4.dp))
            Row(
                Modifier
                    .sharedBounds("search")
                    .weight(1f)
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(c.card)
                    .border(1.dp, c.hairline, CircleShape)
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = c.muted, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(if (c.isDark) c.accent else MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    CircleIconButton(Icons.Rounded.Close, stringResource(R.string.clear), { vm.query.value = "" }, size = 40.dp, elevated = false, container = c.cardHigh)
                }
            }
        }

        FilterChips(filter, onSelect = { vm.filter.value = it })

        AnimatedContent(
            targetState = ui,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "search",
            modifier = Modifier.weight(1f),
        ) { state ->
            when (state) {
                SearchUi.Idle -> IdleContent(saved.map { it.toDestination() to it.id }, units, onPick, onPickOnMap, bottom)
                SearchUi.Loading -> Skeleton()
                is SearchUi.Results -> LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
                    items(state.items, key = { "${it.destination.name}${it.destination.lat}${it.destination.lon}" }) { r ->
                        ResultRow(r, query, units, Modifier.animateItem()) { keyboard?.hide(); onPick(r.destination, -1) }
                    }
                }
                SearchUi.Empty -> Message(Icons.Rounded.SearchOff, stringResource(R.string.no_results_title), stringResource(R.string.no_results_text, query.trim())) {
                    NodButton(stringResource(R.string.pick_on_map), onPickOnMap, style = NodButtonStyle.Secondary, leading = Icons.Rounded.Map)
                }
                SearchUi.Error -> Message(Icons.Rounded.CloudOff, stringResource(R.string.search_error_title), stringResource(R.string.search_error_text)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NodButton(stringResource(R.string.retry), vm::retry, style = NodButtonStyle.Secondary)
                        NodButton(stringResource(R.string.pick_on_map), onPickOnMap, leading = Icons.Rounded.Map)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SearchFilter.entries) { f ->
            val sel = f == selected
            val bg by androidx.compose.animation.animateColorAsState(if (sel) c.cta else c.cardHigh, label = "bg")
            val fg by androidx.compose.animation.animateColorAsState(if (sel) c.onCta else MaterialTheme.colorScheme.onSurface, label = "fg")
            Row(
                Modifier
                    .bounceClick(pressedScale = 0.92f) { haptics.tick(); onSelect(f) }
                    .clip(CircleShape)
                    .background(bg)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                f.kind()?.let {
                    Icon(it.icon(), null, tint = fg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(f.label()), style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
    }
}

private fun SearchFilter.kind(): PlaceKind? = when (this) {
    SearchFilter.ALL -> null
    SearchFilter.TRAIN -> PlaceKind.TRAIN
    SearchFilter.METRO -> PlaceKind.METRO
    SearchFilter.TRAM -> PlaceKind.TRAM
    SearchFilter.BUS -> PlaceKind.BUS
    SearchFilter.FERRY -> PlaceKind.FERRY
    SearchFilter.CITY -> PlaceKind.CITY
}

private fun SearchFilter.label(): Int = when (this) {
    SearchFilter.ALL -> R.string.filter_all
    SearchFilter.TRAIN -> R.string.kind_train
    SearchFilter.METRO -> R.string.kind_metro
    SearchFilter.TRAM -> R.string.kind_tram
    SearchFilter.BUS -> R.string.kind_bus
    SearchFilter.FERRY -> R.string.kind_ferry
    SearchFilter.CITY -> R.string.kind_city
}

@Composable
private fun IdleContent(
    saved: List<Pair<Destination, Long>>,
    units: Units,
    onPick: (Destination, Long) -> Unit,
    onPickOnMap: () -> Unit,
    bottom: androidx.compose.ui.unit.Dp,
) {
    val c = NodTheme.colors
    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .bounceClick(pressedScale = 0.98f, onClick = onPickOnMap)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.cta), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Map, null, tint = c.onCta, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.pick_on_map), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.pick_on_map_hint), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        if (saved.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.saved_places), Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp)) }
            items(saved, key = { it.second }) { (d, id) ->
                Row(
                    Modifier.fillMaxWidth().bounceClick(pressedScale = 0.98f) { onPick(d, id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KindBadge(d.kind)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.subtitle.ifBlank { stringResource(R.string.zone_size, Format.distance(d.radiusM.toDouble(), units).toString()) }, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
                    }
                    Icon(Icons.Rounded.Star, null, tint = c.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
        item {
            Text(
                stringResource(R.string.search_tip),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun ResultRow(r: PlaceResult, query: String, units: Units, modifier: Modifier, onClick: () -> Unit) {
    val c = NodTheme.colors
    val d = r.destination
    Row(
        modifier.fillMaxWidth().bounceClick(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(d.kind)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(highlight(d.name, query), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.subtitle.isNotBlank()) {
                Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        r.distanceFromMeM?.let {
            Spacer(Modifier.width(8.dp))
            Text(Format.distance(it, units).toString(), style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
    }
}

private fun highlight(text: String, query: String): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    val i = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (i < 0) {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text) }
    } else {
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(text.substring(0, i)) }
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(text.substring(i, i + q.length)) }
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(text.substring(i + q.length)) }
    }
}

@Composable
private fun Skeleton() {
    val c = NodTheme.colors
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        repeat(6) { i ->
            Row(Modifier.padding(vertical = 10.dp).graphicsLayer { alpha = a - i * 0.04f }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.cardHigh))
                Spacer(Modifier.width(14.dp))
                Column {
                    Box(Modifier.width((180 - i * 14).dp).height(14.dp).clip(CircleShape).background(c.cardHigh))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width((110 + i * 9).dp).height(10.dp).clip(CircleShape).background(c.cardHigh))
                }
            }
        }
    }
}

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, action: @Composable () -> Unit) {
    val c = NodTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        action()
    }
}
