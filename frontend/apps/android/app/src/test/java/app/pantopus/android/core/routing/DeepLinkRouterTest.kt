@file:Suppress("PackageNaming")

package app.pantopus.android.core.routing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors iOS [DeepLinkRouterTests]. Uses the [DeepLinkRouter.resolveString]
 * helper so the suite stays on the pure JVM (no Robolectric — the
 * production code does `Uri.parse` then `toString()` to hand off).
 */
class DeepLinkRouterTest {
    @After
    fun tearDown() {
        DeepLinkRouter.consume()
    }

    @Test
    fun feed_custom_scheme() {
        assertEquals(DeepLinkRouter.Destination.Feed, DeepLinkRouter.resolveString("pantopus://feed"))
    }

    @Test
    fun home_https_host() {
        assertEquals(DeepLinkRouter.Destination.Home, DeepLinkRouter.resolveString("https://pantopus.app/home"))
    }

    @Test
    fun post_id_extracted() {
        assertEquals(
            DeepLinkRouter.Destination.Post("abc-123"),
            DeepLinkRouter.resolveString("https://pantopus.app/posts/abc-123"),
        )
    }

    @Test
    fun conversation_id_extracted() {
        assertEquals(
            DeepLinkRouter.Destination.Conversation("conv_42"),
            DeepLinkRouter.resolveString("pantopus://messages/conv_42"),
        )
    }

    @Test
    fun unknown_path_falls_back() {
        assertTrue(DeepLinkRouter.resolveString("pantopus://wat") is DeepLinkRouter.Destination.Unknown)
    }

    @Test
    fun invite_token_custom_scheme() {
        assertEquals(
            DeepLinkRouter.Destination.Invite("abc-123"),
            DeepLinkRouter.resolveString("pantopus://invite/abc-123"),
        )
    }

    @Test
    fun invite_token_https_host() {
        assertEquals(
            DeepLinkRouter.Destination.Invite("xyz789"),
            DeepLinkRouter.resolveString("https://pantopus.app/invite/xyz789"),
        )
    }

    @Test
    fun invite_without_token_falls_back() {
        assertTrue(DeepLinkRouter.resolveString("pantopus://invite") is DeepLinkRouter.Destination.Unknown)
    }

    @Test
    fun query_and_fragment_are_ignored() {
        assertEquals(
            DeepLinkRouter.Destination.Invite("abc-123"),
            DeepLinkRouter.resolveString("https://pantopus.app/invite/abc-123?utm_source=email#anchor"),
        )
    }
}
