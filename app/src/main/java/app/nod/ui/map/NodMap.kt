package app.nod.ui.map

import android.graphics.Color as AColor
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.nod.core.Geo
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object MapStyles {
    // OpenFreeMap: free vector tiles with no API key, built from OpenStreetMap.
    const val LIGHT = "https://tiles.openfreemap.org/styles/positron"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
}

/**
 * MapLibre in Compose. Lifecycle is forwarded from the host; the style is swapped when the theme
 * changes and [onStyle] re-adds custom layers each time.
 */
@Composable
fun NodMap(
    dark: Boolean,
    background: Color,
    modifier: Modifier = Modifier,
    attributionBottomPx: Int = 0,
    onMap: (MapLibreMap) -> Unit = {},
    onStyle: (MapLibreMap, Style) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val currentOnStyle by rememberUpdatedState(onStyle)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        // Loaded on first map, not at app start: most launches never show one.
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .foregroundLoadColor(background.toArgb())
            .compassEnabled(false)
            // One-finger pan, pinch, double-tap in, two-finger tap out and double-tap-and-drag
            // (quick zoom) all on; rotate and tilt off so north stays up and the map stays flat.
            .scrollGesturesEnabled(true)
            .horizontalScrollGesturesEnabled(true)
            .zoomGesturesEnabled(true)
            .doubleTapGesturesEnabled(true)
            .quickZoomGesturesEnabled(true)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .logoEnabled(false)
            .attributionEnabled(true)
        MapView(context, options).apply {
            setBackgroundColor(background.toArgb())
            onCreate(null)
            getMapAsync { m ->
                // Set again at runtime so the gesture set doesn't depend on how the options were built.
                m.uiSettings.apply {
                    isScrollGesturesEnabled = true
                    isHorizontalScrollGesturesEnabled = true
                    isZoomGesturesEnabled = true
                    isDoubleTapGesturesEnabled = true
                    isQuickZoomGesturesEnabled = true
                    isFlingVelocityAnimationEnabled = true
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                m.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
                m.uiSettings.setAttributionTintColor(AColor.GRAY)
                map = m
                onMap(m)
            }
        }
    }

    LaunchedEffect(map, attributionBottomPx) {
        val m = map ?: return@LaunchedEffect
        val px = with(density) { 12 * this.density }.toInt()
        m.uiSettings.setAttributionMargins(px, 0, 0, attributionBottomPx + px)
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        m.setStyle(Style.Builder().fromUri(if (dark) MapStyles.DARK else MapStyles.LIGHT)) { style ->
            currentOnStyle(m, style)
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Custom layers used on the live trip map. */
object TripLayers {
    private const val ZONE = "nod-zone"
    private const val ROUTE = "nod-route"
    private const val ME = "nod-me"
    private const val DEST = "nod-dest"

    fun install(style: Style, zone: Color, ink: Color, isDark: Boolean) {
        val z = zone.toArgb()
        style.addSource(GeoJsonSource(ZONE))
        style.addSource(GeoJsonSource(ROUTE))
        style.addSource(GeoJsonSource(ME))
        style.addSource(GeoJsonSource(DEST))
        style.addLayer(FillLayer("$ZONE-fill", ZONE).withProperties(PropertyFactory.fillColor(z), PropertyFactory.fillOpacity(if (isDark) 0.14f else 0.18f)))
        style.addLayer(LineLayer("$ZONE-line", ZONE).withProperties(PropertyFactory.lineColor(z), PropertyFactory.lineWidth(2.5f)))
        style.addLayer(
            LineLayer("$ROUTE-line", ROUTE).withProperties(
                PropertyFactory.lineColor(if (isDark) AColor.WHITE else ink.toArgb()),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineDasharray(arrayOf(1.5f, 2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            ),
        )
        style.addLayer(CircleLayer("$DEST-dot", DEST).withProperties(PropertyFactory.circleRadius(5f), PropertyFactory.circleColor(z), PropertyFactory.circleStrokeColor(ink.toArgb()), PropertyFactory.circleStrokeWidth(2f)))
        style.addLayer(CircleLayer("$ME-halo", ME).withProperties(PropertyFactory.circleRadius(18f), PropertyFactory.circleColor(Expression.color(AColor.parseColor("#7CC4FF"))), PropertyFactory.circleOpacity(0.22f)))
        style.addLayer(CircleLayer("$ME-dot", ME).withProperties(PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(AColor.parseColor("#3A9BFF")), PropertyFactory.circleStrokeColor(AColor.WHITE), PropertyFactory.circleStrokeWidth(3f)))
    }

    fun setZone(style: Style, lat: Double, lon: Double, radiusM: Double) {
        val ring = Geo.circle(lat, lon, radiusM).map { Point.fromLngLat(it.lon, it.lat) }
        style.getSourceAs<GeoJsonSource>(ZONE)?.setGeoJson(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))))
        style.getSourceAs<GeoJsonSource>(DEST)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
    }

    fun setMe(style: Style, lat: Double, lon: Double, destLat: Double, destLon: Double) {
        style.getSourceAs<GeoJsonSource>(ME)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
        style.getSourceAs<GeoJsonSource>(ROUTE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(lon, lat), Point.fromLngLat(destLon, destLat))))),
            ),
        )
    }
}
