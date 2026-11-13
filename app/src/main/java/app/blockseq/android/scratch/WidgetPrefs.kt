package app.blockseq.android.scratch

import android.content.Context

/** How the Scratchpad widget looks; one setting for every widget, changed in the app's Settings or on Reconfigure. */
data class WidgetLookPrefs(
    /** ARGB, alpha included (0 = fully transparent). */
    val background: Int = DEFAULT_BACKGROUND,
    val text: Int = DEFAULT_TEXT,
    val newestFirst: Boolean = true,
    /** Sync and + along the bottom edge instead of the top. */
    val buttonsBottom: Boolean = false,
) {
    companion object {
        const val DEFAULT_BACKGROUND = 0xE6202124.toInt()
        const val DEFAULT_TEXT = 0xFFFFFFFF.toInt()
    }
}

object WidgetPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun load(context: Context): WidgetLookPrefs = prefs(context).let { p ->
        WidgetLookPrefs(
            background = p.getInt("background", WidgetLookPrefs.DEFAULT_BACKGROUND),
            text = p.getInt("text", WidgetLookPrefs.DEFAULT_TEXT),
            newestFirst = p.getBoolean("newest_first", true),
            buttonsBottom = p.getBoolean("buttons_bottom", false),
        )
    }

    fun save(context: Context, look: WidgetLookPrefs) {
        prefs(context).edit()
            .putInt("background", look.background).putInt("text", look.text)
            .putBoolean("newest_first", look.newestFirst).putBoolean("buttons_bottom", look.buttonsBottom)
            .remove("locked").apply()
    }
}
