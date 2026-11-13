package app.blockseq.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.blockseq.android.AppModel
import app.blockseq.android.BuildConfig
import app.blockseq.android.data.Workbook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: AppModel, onBack: () -> Unit) {
    var workbooks by remember { mutableStateOf<List<Workbook>>(emptyList()) }
    var unlocking by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    LaunchedEffect(model.api) { runCatching { model.api?.workbooks() }.getOrNull()?.let { workbooks = it } }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("Server")
            Text(model.store.server, style = MaterialTheme.typography.bodyLarge)
            Text("Paired as “${model.store.deviceName}”. Revoke this phone any time in the web app: Settings → Security.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Section("Workbook")
            workbooks.forEach { w ->
                Row(Modifier.fillMaxWidth().clickable { model.switchWorkbook(w.id) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = model.store.workbook == w.id, onClick = { model.switchWorkbook(w.id) })
                    Text(w.name)
                }
            }
            HorizontalDivider()
            Section("Encrypted blocks")
            when {
                model.setup == null -> Text("No encryption password is set on the server.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                model.key == null -> OutlinedButton(onClick = { unlocking = true }, modifier = Modifier.testTag("settings-unlock")) { Text("Unlock…") }
                else -> OutlinedButton(onClick = model::lock) { Text("Lock now") }
            }
            HorizontalDivider()
            OutlinedButton(onClick = { confirmUnpair = true }) { Text("Unpair this phone") }
            Text("Blockseq for Android ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (unlocking) UnlockDialog(model) { unlocking = false }
    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair this phone?") },
            text = { Text("The token is removed from this phone. Pair again with the password to use it. (To stop the old token on the server too, revoke it in the web app.)") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; model.unpair() }) { Text("Unpair") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
