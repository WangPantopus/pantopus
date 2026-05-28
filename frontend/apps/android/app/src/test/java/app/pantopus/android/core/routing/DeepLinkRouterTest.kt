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

    // MARK: - T4.1 routing table

    @Test
    fun notifications_routes_to_notifications() {
        assertEquals(DeepLinkRouter.Destination.Notifications, DeepLinkRouter.resolveString("pantopus://notifications"))
        assertEquals(
            DeepLinkRouter.Destination.Notifications,
            DeepLinkRouter.resolveString("https://pantopus.app/notifications"),
        )
    }

    @Test
    fun support_train_route() {
        assertEquals(
            DeepLinkRouter.Destination.SupportTrain("st_1"),
            DeepLinkRouter.resolveString("pantopus://support-trains/st_1"),
        )
        assertEquals(
            DeepLinkRouter.Destination.SupportTrain("st_2"),
            DeepLinkRouter.resolveString("pantopus://support_train/st_2"),
        )
    }

    @Test
    fun gig_route() {
        assertEquals(
            DeepLinkRouter.Destination.Gig("g_1"),
            DeepLinkRouter.resolveString("pantopus://gig/g_1"),
        )
        assertEquals(
            DeepLinkRouter.Destination.Gig("g_2"),
            DeepLinkRouter.resolveString("https://pantopus.app/gigs/g_2"),
        )
    }

    @Test
    fun listing_route() {
        assertEquals(
            DeepLinkRouter.Destination.Listing("l_1"),
            DeepLinkRouter.resolveString("pantopus://listing/l_1"),
        )
        assertEquals(
            DeepLinkRouter.Destination.Listing("l_2"),
            DeepLinkRouter.resolveString("https://pantopus.app/listings/l_2"),
        )
    }

    @Test
    fun home_detail_route() {
        assertEquals(
            DeepLinkRouter.Destination.HomeDetail("h_1"),
            DeepLinkRouter.resolveString("pantopus://homes/h_1"),
        )
    }

    @Test
    fun home_dashboard_route() {
        assertEquals(
            DeepLinkRouter.Destination.HomeDashboard("h_1"),
            DeepLinkRouter.resolveString("pantopus://homes/h_1/dashboard"),
        )
    }

    @Test
    fun home_member_requests_requires_tab_query() {
        assertEquals(
            DeepLinkRouter.Destination.HomeMemberRequests("h_1"),
            DeepLinkRouter.resolveString("pantopus://homes/h_1/members?tab=requests"),
        )
    }

    @Test
    fun home_members_without_tab_falls_back_to_detail() {
        assertEquals(
            DeepLinkRouter.Destination.HomeDetail("h_1"),
            DeepLinkRouter.resolveString("pantopus://homes/h_1/members"),
        )
    }

    @Test
    fun chat_route_uses_conversation_case() {
        assertEquals(
            DeepLinkRouter.Destination.Conversation("c_1"),
            DeepLinkRouter.resolveString("pantopus://chat/c_1"),
        )
    }

    @Test
    fun user_route() {
        assertEquals(
            DeepLinkRouter.Destination.User("u_1"),
            DeepLinkRouter.resolveString("pantopus://user/u_1"),
        )
        assertEquals(
            DeepLinkRouter.Destination.User("u_2"),
            DeepLinkRouter.resolveString("https://pantopus.app/users/u_2"),
        )
    }

    @Test
    fun connections_route() {
        assertEquals(DeepLinkRouter.Destination.Connections, DeepLinkRouter.resolveString("pantopus://connections"))
    }

    // MARK: - T6.1c P5 — Auth deep links

    @Test
    fun reset_password_custom_scheme() {
        assertEquals(
            DeepLinkRouter.Destination.ResetPassword("hashed-recovery"),
            DeepLinkRouter.resolveString("pantopus://auth/reset-password?token=hashed-recovery"),
        )
    }

    @Test
    fun reset_password_https_host() {
        assertEquals(
            DeepLinkRouter.Destination.ResetPassword("abc-123"),
            DeepLinkRouter.resolveString("https://pantopus.app/auth/reset-password?token=abc-123"),
        )
    }

    @Test
    fun reset_password_without_token_falls_back() {
        assertTrue(
            DeepLinkRouter.resolveString("pantopus://auth/reset-password") is DeepLinkRouter.Destination.Unknown,
        )
    }

    @Test
    fun reset_password_accepts_token_hash_param() {
        assertEquals(
            DeepLinkRouter.Destination.ResetPassword("hash-shape"),
            DeepLinkRouter.resolveString("pantopus://auth/reset-password?token_hash=hash-shape"),
        )
    }

    @Test
    fun reset_password_accepts_bare_shape_without_auth_prefix() {
        // Backend's older recovery template emits `/reset-password?token=…`.
        assertEquals(
            DeepLinkRouter.Destination.ResetPassword("bare-shape-tok"),
            DeepLinkRouter.resolveString("pantopus://reset-password?token=bare-shape-tok"),
        )
    }

    @Test
    fun verify_email_custom_scheme() {
        assertEquals(
            DeepLinkRouter.Destination.VerifyEmail(token = "hashed-otp", email = "alice@example.com"),
            DeepLinkRouter.resolveString(
                "pantopus://auth/verify-email?token=hashed-otp&email=alice@example.com",
            ),
        )
    }

    @Test
    fun verify_email_https_host_without_email() {
        assertEquals(
            DeepLinkRouter.Destination.VerifyEmail(token = "tok", email = null),
            DeepLinkRouter.resolveString("https://pantopus.app/auth/verify-email?token=tok"),
        )
    }

    @Test
    fun verify_email_without_token_falls_back() {
        assertTrue(
            DeepLinkRouter.resolveString("pantopus://auth/verify-email") is DeepLinkRouter.Destination.Unknown,
        )
    }

    // MARK: - Path-form (notification payload) entry point

    @Test
    fun handle_path_boxes_absolute_path_into_router() {
        DeepLinkRouter.handle("/post/abc-123")
        val pending = DeepLinkRouter.consume()
        assertEquals(DeepLinkRouter.Destination.Post("abc-123"), pending)
    }

    @Test
    fun handle_path_boxes_relative_into_router() {
        DeepLinkRouter.handle("homes/h_1/dashboard")
        val pending = DeepLinkRouter.consume()
        assertEquals(DeepLinkRouter.Destination.HomeDashboard("h_1"), pending)
    }

    @Test
    fun handle_path_passes_through_full_urls() {
        DeepLinkRouter.handle("pantopus://gigs/g_99")
        val pending = DeepLinkRouter.consume()
        assertEquals(DeepLinkRouter.Destination.Gig("g_99"), pending)
    }

    // ---- A13.16 My Mail Day ----

    @Test
    fun mail_day_custom_scheme() {
        assertEquals(DeepLinkRouter.Destination.MailDay, DeepLinkRouter.resolveString("pantopus://mailbox/mailday"))
    }

    @Test
    fun mail_day_https_host() {
        assertEquals(
            DeepLinkRouter.Destination.MailDay,
            DeepLinkRouter.resolveString("https://pantopus.app/mailbox/mailday"),
        )
    }

    @Test
    fun mailbox_without_subroute_falls_back() {
        assertTrue(DeepLinkRouter.resolveString("pantopus://mailbox") is DeepLinkRouter.Destination.Unknown)
    }

    // MARK: - Verify-landlord routes (P2.1 / A12.5–A12.7)

    @Test
    fun verify_landlord_custom_scheme() {
        assertEquals(
            DeepLinkRouter.Destination.VerifyLandlord("h_42"),
            DeepLinkRouter.resolveString("pantopus://homes/h_42/verify-landlord"),
        )
    }

    @Test
    fun verify_landlord_underscore_shape() {
        assertEquals(
            DeepLinkRouter.Destination.VerifyLandlord("h_42"),
            DeepLinkRouter.resolveString("pantopus://homes/h_42/verify_landlord"),
        )
    }

    @Test
    fun postcard_verification_deep_link() {
        assertEquals(
            DeepLinkRouter.Destination.PostcardVerification("h_42"),
            DeepLinkRouter.resolveString("pantopus://homes/h_42/verify-postcard"),
        )
    }

    @Test
    fun verify_landlord_https_host() {
        assertEquals(
            DeepLinkRouter.Destination.VerifyLandlord("h_42"),
            DeepLinkRouter.resolveString("https://pantopus.app/homes/h_42/verify-landlord"),
        )
    }
}
