package app.blockseq.android.scratch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.blockseq.android.ui.BlockseqTheme
import kotlinx.coroutines.launch

/**
 * The widget's +: a card over the home screen with a text box. The note is
 * date-stamped and kept on the phone at once (so it works offline), shown on
 * the widget, and sent to the Scratchpad page when there is a connection.
 */
class QuickAddActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlockseqTheme {
                var text by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                val focus = remember { FocusRequester() }
                val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    // The window over the home screen takes a moment to get focus; then the
                    // text box gets the cursor and the keyboard opens.
                    kotlinx.coroutines.delay(350)
                    focus.requestFocus()
                    keyboard?.show()
                }
                ModalBottomSheet(onDismissRequest = { finish() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Add to Scratchpad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            androidx.compose.material3.IconButton(onClick = { finish() }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, "Close") }
                        }
                        OutlinedTextField(
                            text, { text = it }, placeholder = { Text("Write a note…") }, minLines = 3, maxLines = 10,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).focusRequester(focus),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { finish() }) { Text("Cancel") }
                            Button(enabled = text.isNotBlank(), onClick = {
                                ScratchpadStore.add(applicationContext, text)
                                scope.launch {
                                    ScratchpadWidget.refresh(applicationContext)
                                    ScratchpadSync.request(applicationContext)
                                    finish()
                                }
                            }) { Text("Add") }
                        }
                    }
                }
            }
        }
    }
}
