package app.blockseq.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/** Settings → Controls at the bottom: action buttons sit along the bottom edge. */
val LocalControlsAtBottom = staticCompositionLocalOf { true }

/** Settings → Code blocks: how long code blocks fold. */
val LocalCodeFold = staticCompositionLocalOf { app.blockseq.android.data.CodeFold() }

/**
 * The padlock for #Encrypted blocks, coloured so its state reads at a glance:
 * amber and closed while locked, green and open while unlocked.
 */
@Composable
fun LockButton(unlocked: Boolean, onLock: () -> Unit, onUnlock: () -> Unit) {
    if (unlocked) {
        IconButton(onClick = onLock, modifier = Modifier.testTag("lock")) { Icon(Icons.Filled.LockOpen, "Lock encrypted blocks", tint = Color(0xFF43A047)) }
    } else {
        IconButton(onClick = onUnlock, modifier = Modifier.testTag("unlock-button")) { Icon(Icons.Filled.Lock, "Unlock encrypted blocks", tint = Color(0xFFFFA000)) }
    }
}
