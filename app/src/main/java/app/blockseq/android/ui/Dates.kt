package app.blockseq.android.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Journal page names as the server writes them: "Nov 12th, 2026". */
fun journalName(date: LocalDate): String {
    val d = date.dayOfMonth
    val suffix = if (d in 11..13) "th" else when (d % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "${date.format(DateTimeFormatter.ofPattern("MMM", Locale.US))} $d$suffix, ${date.year}"
}

fun journalDay(date: LocalDate): Long = date.year * 10000L + date.monthValue * 100 + date.dayOfMonth

fun formatTime(ms: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(ms))
