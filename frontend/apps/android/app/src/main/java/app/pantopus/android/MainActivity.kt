package app.pantopus.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.pantopus.android.ui.navigation.PantopusNavHost
import app.pantopus.android.ui.theme.PantopusTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity that hosts the whole Compose UI tree.
 * Navigation is entirely in-Compose via [PantopusNavHost].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PantopusTheme {
                PantopusNavHost()
            }
        }
    }
}
