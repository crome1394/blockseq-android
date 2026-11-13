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
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import app.blockseq.android.ui.BlockseqTheme
import app.blockseq.android.ui.HomeScreen
import app.blockseq.android.ui.PageScreen
import app.blockseq.android.ui.PairScreen
import app.blockseq.android.ui.SettingsScreen
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    /** A page to open on start (the widget opens the Scratchpad). */
    private var openPage by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openPage = intent.getStringExtra(OPEN_PAGE)
    }

    override fun onResume() {
        super.onResume()
        // Android can hold a widget retry back for minutes; opening the app sends waiting notes now.
        if (app.blockseq.android.scratch.ScratchpadStore.load(this).pending.isNotEmpty()) app.blockseq.android.scratch.ScratchpadSync.request(this)
    }

    companion object {
        const val OPEN_PAGE = "open_page"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPage = intent?.getStringExtra(OPEN_PAGE)
        setContent {
            val viewer = androidx.compose.runtime.remember { app.blockseq.android.render.ImageViewerHost() }
            BlockseqTheme {
              androidx.compose.runtime.CompositionLocalProvider(
                  app.blockseq.android.ui.LocalControlsAtBottom provides model.controlsAtBottom,
                  app.blockseq.android.ui.LocalCodeFold provides model.codeFold,
                  app.blockseq.android.render.LocalImageViewer provides viewer,
              ) {
                // Test tags double as view ids, so adb/uiautomator can find controls.
                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                    if (model.api == null) {
                        PairScreen(model)
                    } else {
                        val nav = rememberNavController()
                        val open: (String) -> Unit = { name -> nav.navigate("page/" + Uri.encode(name)) }
                        androidx.compose.runtime.LaunchedEffect(openPage) {
                            openPage?.let { open(it); openPage = null }
                        }
                        NavHost(nav, startDestination = "home") {
                            composable("home") {
                                HomeScreen(model, open, onSettings = { nav.navigate("settings") }, onQuickNote = { name -> nav.navigate("page/" + Uri.encode(name) + "?note=true") })
                            }
                            composable("page/{name}?note={note}") { entry ->
                                val name = Uri.decode(entry.arguments?.getString("name") ?: "")
                                val note = entry.arguments?.getString("note") == "true"
                                PageScreen(model, name, open, quickNote = note) { nav.popBackStack() }
                            }
                            // A card over the screen it was opened from.
                            dialog("settings", dialogProperties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                                SettingsScreen(model) { nav.popBackStack() }
                            }
                        }
                    }
                }
                app.blockseq.android.render.ImageViewerLayer(viewer)
              }
            }
        }
    }
}
