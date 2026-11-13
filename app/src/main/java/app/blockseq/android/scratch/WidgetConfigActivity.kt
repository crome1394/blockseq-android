package app.blockseq.android.scratch

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.blockseq.android.ui.BlockseqTheme
import kotlinx.coroutines.launch

private val BACKGROUNDS = listOf(0xFF202124, 0xFF000000, 0xFFFFFFFF, 0xFF1B3A4B, 0xFF2E6F6F, 0xFF3D2B56, 0xFF4A3F14, 0xFF7A1F2B)
private val TEXTS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFE0E0E0, 0xFFFFD600, 0xFF6CC9C9, 0xFFD1A2EA, 0xFF9CCC65, 0xFFFF8A65)

/**
 * Shown when the widget is placed (and again from "Reconfigure" on a long
 * press): background colour and how see-through it is, text colour, order.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent {
            BlockseqTheme {
                val scope = rememberCoroutineScope()
                var bg by remember { mutableIntStateOf(BACKGROUNDS[0].toInt()) }
                var alpha by remember { mutableFloatStateOf(0.9f) }
                var fg by remember { mutableIntStateOf(TEXTS[0].toInt()) }
                var newestFirst by remember { mutableStateOf(true) }
                val background = Color(bg).copy(alpha = alpha)
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Scratchpad widget", style = MaterialTheme.typography.headlineSmall)
                    // Preview, over a checkerboard so transparency shows.
                    Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF6A7B8C), RoundedCornerShape(16.dp)).padding(10.dp)) {
                        Column(Modifier.fillMaxSize().background(background, RoundedCornerShape(16.dp)).padding(12.dp)) {
                            Text("Scratchpad", color = Color(fg), fontSize = 16.sp)
                            Text("Nov 12th, 2026 21:48", color = Color(fg).copy(alpha = 0.65f), fontSize = 11.sp)
                            Text("Pick up stakes and twine", color = Color(fg), fontSize = 14.sp)
                        }
                    }
                    Text("Background", style = MaterialTheme.typography.titleMedium)
                    Swatches(BACKGROUNDS, bg) { bg = it }
                    Text("See-through: ${((1 - alpha) * 100).toInt()}%  (0% solid, 100% fully transparent)")
                    Slider(value = 1 - alpha, onValueChange = { alpha = 1 - it })
                    Text("Text", style = MaterialTheme.typography.titleMedium)
                    Swatches(TEXTS, fg) { fg = it }
                    Text("Order", style = MaterialTheme.typography.titleMedium)
                    listOf(true to "Newest first", false to "Oldest first").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickable { newestFirst = value }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = newestFirst == value, onClick = { newestFirst = value })
                            Text(label)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch {
                            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(widgetId)
                            updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                                prefs.toMutablePreferences().apply {
                                    this[WidgetLook.BACKGROUND] = background.toArgb()
                                    this[WidgetLook.TEXT] = fg
                                    this[WidgetLook.NEWEST_FIRST] = newestFirst
                                }
                            }
                            ScratchpadWidget().update(applicationContext, glanceId)
                            ScratchpadSync.schedule(applicationContext)
                            ScratchpadSync.request(applicationContext)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        }
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun Swatches(colors: List<Long>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        colors.forEach { c ->
            Box(
                Modifier.size(34.dp).background(Color(c), CircleShape)
                    .border(if (c.toInt() == selected) 3.dp else 1.dp, if (c.toInt() == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onPick(c.toInt()) },
            )
        }
    }
}
