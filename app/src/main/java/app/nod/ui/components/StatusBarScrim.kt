package app.nod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Background-coloured band behind the transparent edge-to-edge status bar, with a short soft fade
 * below it, so list content scrolling underneath never collides with the clock and status icons.
 * Draw it after (above) the scrolling content. It has no input handling, so touches pass through.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.background) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(color))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0f)))))
    }
}
