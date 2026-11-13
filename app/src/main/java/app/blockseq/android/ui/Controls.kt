package app.blockseq.android.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Settings → Controls at the bottom: action buttons sit along the bottom edge. */
val LocalControlsAtBottom = staticCompositionLocalOf { true }

/** Settings → Code blocks: how long code blocks fold. */
val LocalCodeFold = staticCompositionLocalOf { app.blockseq.android.data.CodeFold() }
