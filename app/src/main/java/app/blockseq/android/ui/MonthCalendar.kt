package app.blockseq.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month of days, as in the web app's left pane: a dot under days whose
 * journal has notes, today ringed; tap a day to open its journal. Weeks
 * start on [firstDay] (Settings → Display; Sunday by default, as in the web app).
 * The weekday letters are bright red.
 */
@Composable
fun MonthCalendar(days: Set<Long>, firstDay: DayOfWeek = DayOfWeek.SUNDAY, onDay: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp).testTag("calendar")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).clickable { month = YearMonth.from(today) },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
        }
        Row(Modifier.fillMaxWidth()) {
            (0L until 7L).map { firstDay.plus(it) }.forEach {
                Text(it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = WEEKDAY_RED, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val lead = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDay.value, 7)
        val cells = (List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }).chunked(7)
        cells.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val isToday = date == today
                            Column(
                                Modifier.size(32.dp).then(if (isToday) Modifier.border(1.5.dp, primary, CircleShape) else Modifier)
                                    .clickable { onDay(date) }.testTag("day-${date.dayOfMonth}"),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("${date.dayOfMonth}", style = MaterialTheme.typography.bodySmall, fontWeight = if (isToday) FontWeight.Bold else null)
                                Box(Modifier.size(4.dp).background(if (journalDay(date) in days) primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The weekday letters: bright red, readable on dark and light. */
private val WEEKDAY_RED = androidx.compose.ui.graphics.Color(0xFFFF3B30)
