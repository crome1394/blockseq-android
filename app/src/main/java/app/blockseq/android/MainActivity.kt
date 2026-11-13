package app.blockseq.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.blockseq.android.ui.BlockseqTheme
import app.blockseq.android.ui.HomeScreen
import app.blockseq.android.ui.PageScreen
import app.blockseq.android.ui.PairScreen
import app.blockseq.android.ui.SettingsScreen
import android.net.Uri
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlockseqTheme {
              androidx.compose.runtime.CompositionLocalProvider(app.blockseq.android.ui.LocalControlsAtBottom provides model.controlsAtBottom) {
                // Test tags double as view ids, so adb/uiautomator can find controls.
                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                    if (model.api == null) {
                        PairScreen(model)
                    } else {
                        val nav = rememberNavController()
                        val open: (String) -> Unit = { name -> nav.navigate("page/" + Uri.encode(name)) }
                        NavHost(nav, startDestination = "home") {
                            composable("home") { HomeScreen(model, open) { nav.navigate("settings") } }
                            composable("page/{name}") { entry ->
                                val name = Uri.decode(entry.arguments?.getString("name") ?: "")
                                PageScreen(model, name, open) { nav.popBackStack() }
                            }
                            composable("settings") { SettingsScreen(model) { nav.popBackStack() } }
                        }
                    }
                }
              }
            }
        }
    }
}
