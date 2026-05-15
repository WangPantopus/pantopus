package app.pantopus.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.pantopus.android.core.routing.DeepLinkRouter
import app.pantopus.android.ui.navigation.PantopusNavHost
import app.pantopus.android.ui.theme.PantopusTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity that hosts the whole Compose UI tree.
 * Navigation is entirely in-Compose via [PantopusNavHost]; incoming
 * deep-link intents get forwarded into [DeepLinkRouter] for the
 * RootTabScreen to consume.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cold-start deep links arrive via the launch intent.
        forwardDeepLink(intent)
        setContent {
            PantopusTheme {
                PantopusNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm-start deep links (app already in memory).
        forwardDeepLink(intent)
        setIntent(intent)
    }

    private fun forwardDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        DeepLinkRouter.handle(uri)
    }
}
