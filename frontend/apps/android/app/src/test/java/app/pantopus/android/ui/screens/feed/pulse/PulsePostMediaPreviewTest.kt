@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.feed.pulse

import org.junit.Assert.assertEquals
import org.junit.Test

class PulsePostMediaPreviewTest {
    @Test
    fun resolvePulsePostMediaUrls_usesFullResolutionUrls() {
        val resolved =
            resolvePulsePostMediaUrls(
                urls = listOf("https://cdn.example.com/full.jpg"),
                thumbnails = listOf("https://cdn.example.com/thumb.jpg"),
            )
        assertEquals(listOf("https://cdn.example.com/full.jpg"), resolved)
    }

    @Test
    fun resolvePulsePostMediaUrls_fallsBackToFullUrl() {
        val resolved =
            resolvePulsePostMediaUrls(
                urls = listOf("https://cdn.example.com/full.jpg"),
                thumbnails = emptyList(),
            )
        assertEquals(listOf("https://cdn.example.com/full.jpg"), resolved)
    }
}
