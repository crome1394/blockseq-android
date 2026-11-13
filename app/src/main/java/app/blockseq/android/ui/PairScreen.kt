package app.blockseq.android.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch

/**
 * First run (or after the server revoked this phone): the server address and
 * the password, once. The server gives back a token for this phone; the
 * password is not kept.
 */
@Composable
fun PairScreen(model: AppModel) {
    var server by remember { mutableStateOf(model.store.server) }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(model.store.deviceName.ifEmpty { "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}" }) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(model.unpairedMessage) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Blockseq", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Connect to your Blockseq server. The password is used once to pair this phone; " +
                "the server then gives it a token of its own, which you can revoke in Settings → Security.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            server, { server = it }, label = { Text("Server address") }, placeholder = { Text("notes.example.com") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("server"),
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("password"),
        )
        OutlinedTextField(name, { name = it }, label = { Text("Name for this phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && server.isNotBlank(),
            onClick = {
                busy = true
                message = null
                scope.launch {
                    runCatching { model.pair(server, password, name) }.onFailure { message = it.message ?: "Could not connect." }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("pair"),
        ) { if (busy) CircularProgressIndicator(Modifier.padding(2.dp)) else Text("Pair") }
    }
}
