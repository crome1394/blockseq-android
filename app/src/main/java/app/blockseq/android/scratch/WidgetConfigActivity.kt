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
import app.blockseq.android.ui.BlockseqTheme
import kotlinx.coroutines.launch

private val BACKGROUNDS = listOf(0xFF202124, 0xFF000000, 0xFFFFFFFF, 0xFF1B3A4B, 0xFF2E6F6F, 0xFF3D2B56, 0xFF4A3F14, 0xFF7A1F2B)
private val TEXTS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFE0E0E0, 0xFFFFD600, 0xFF6CC9C9, 0xFFD1A2EA, 0xFF9CCC65, 0xFFFF8A65)

/**
 * Shown when the widget is placed (and again from "Reconfigure" on a long
 * press). The look is shared by every widget and also lives in the app's
 * Settings → Widget.
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
                var look by remember { mutableStateOf(WidgetPrefs.load(applicationContext)) }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Scratchpad widget", style = MaterialTheme.typography.headlineSmall)
                    WidgetLookEditor(look) { look = it }
                    Spacer(Modifier.height(8.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch {
                            WidgetPrefs.save(applicationContext, look)
                            ScratchpadWidget.refresh(applicationContext)
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

/** Background (solid, see-through or transparent), text colour, order, button position, with a preview. */
@Composable
fun WidgetLookEditor(look: WidgetLookPrefs, onChange: (WidgetLookPrefs) -> Unit) {
    val base = look.background or 0xFF000000.toInt()
    val alpha = (look.background ushr 24) / 255f
    fun bg(rgb: Int = base, a: Float = alpha) = Color(rgb).copy(alpha = a).toArgb()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Preview, over a grey backdrop so transparency shows.
        Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF6A7B8C), RoundedCornerShape(16.dp)).padding(10.dp)) {
            Column(Modifier.fillMaxSize().background(Color(look.background), RoundedCornerShape(16.dp)).padding(12.dp)) {
                val fg = Color(look.text)
                val head = @Composable { Row { Text("Scratchpad", color = fg, fontSize = 16.sp, modifier = Modifier.weight(1f)); Text("🔓 ⟳ +", color = fg) } }
                if (!look.buttonsBottom) head()
                Text("Nov 12th, 2026", color = fg, fontSize = 12.sp)
                Text("21:48", color = fg.copy(alpha = 0.65f), fontSize = 11.sp)
                Text("Pick up stakes and twine", color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (look.buttonsBottom) head()
            }
        }
        Text("Background", style = MaterialTheme.typography.titleMedium)
        Swatches(BACKGROUNDS, base) { onChange(look.copy(background = bg(rgb = it, a = if (alpha == 0f) 1f else alpha))) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Solid" to 1f, "See-through" to 0.6f, "Transparent" to 0f).forEach { (label, a) ->
                androidx.compose.material3.FilterChip(selected = alpha == a, onClick = { onChange(look.copy(background = bg(a = a))) }, label = { Text(label) })
            }
        }
        Text("See-through: ${((1 - alpha) * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        Slider(value = 1 - alpha, onValueChange = { onChange(look.copy(background = bg(a = 1 - it))) })
        Text("Text", style = MaterialTheme.typography.titleMedium)
        Swatches(TEXTS, look.text) { onChange(look.copy(text = it)) }
        Text("Order", style = MaterialTheme.typography.titleMedium)
        Choice(listOf(true to "Newest first", false to "Oldest first"), look.newestFirst) { onChange(look.copy(newestFirst = it)) }
        Text("Buttons (padlock, Sync, +)", style = MaterialTheme.typography.titleMedium)
        Choice(listOf(false to "Top", true to "Bottom"), look.buttonsBottom) { onChange(look.copy(buttonsBottom = it)) }
    }
}

@Composable
private fun Choice(options: List<Pair<Boolean, String>>, value: Boolean, onPick: (Boolean) -> Unit) {
    options.forEach { (v, label) ->
        Row(Modifier.fillMaxWidth().clickable { onPick(v) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = value == v, onClick = { onPick(v) })
            Text(label)
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
