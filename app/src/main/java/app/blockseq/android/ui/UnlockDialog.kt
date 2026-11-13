package app.blockseq.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.blockseq.android.AppModel
import app.blockseq.android.crypto.WrongPassword
import kotlinx.coroutines.launch

/** The encryption password (the one for #Encrypted blocks, not the server's). */
@Composable
fun UnlockDialog(model: AppModel, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Unlock encrypted blocks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your encryption password. Blocks are decrypted on this phone; the key stays in memory until you lock or close the app.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    password, { password = it }, singleLine = true, label = { Text("Encryption password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.testTag("unlock-password"),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && password.isNotEmpty(), modifier = Modifier.testTag("unlock"), onClick = {
                busy = true
                error = null
                scope.launch {
                    runCatching { model.unlock(password) }
                        .onSuccess { onClose() }
                        .onFailure { error = if (it is WrongPassword) "Wrong password" else it.message ?: "Could not unlock" }
                    busy = false
                }
            }) { Text(if (busy) "Unlocking…" else "Unlock") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Cancel") } },
    )
}
