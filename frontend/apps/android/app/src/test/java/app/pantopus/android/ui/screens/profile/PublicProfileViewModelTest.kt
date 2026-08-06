@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.profile

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.posts.MyPostDto
import app.pantopus.android.data.api.models.posts.MyPostsResponse
import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.models.profile.PublicProfileReview
import app.pantopus.android.data.api.models.relationships.ConnectionRequestResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.blocks.BlocksRepository
import app.pantopus.android.data.posts.PostsRepository
import app.pantopus.android.data.profile.ProfileRepository
import app.pantopus.android.data.relationships.RelationshipsRepository
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileTab
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PublicProfileViewModelTest {
    private val repo: ProfileRepository = mockk()
    private val relationships: RelationshipsRepository = mockk()
    private val blocks: BlocksRepository = mockk()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val posts: PostsRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Local-kind profiles pull `GET /api/posts/user/:id`; default the
        // stub to an empty feed and let individual tests override it.
        coEvery { posts.userPosts(any(), any()) } returns
            NetworkResult.Success(MyPostsResponse(emptyList()))
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): PublicProfileViewModel =
        PublicProfileViewModel(
            repo = repo,
            relationships = relationships,
            blocks = blocks,
            authRepository = authRepository,
            posts = posts,
            savedStateHandle = SavedStateHandle(mapOf(PUBLIC_PROFILE_USER_ID_KEY to "u1")),
        )

    private fun profile(
        verified: Boolean = true,
        reviews: List<PublicProfileReview> = emptyList(),
        rating: Double? = 4.8,
        gigs: Int? = 5,
        residency: Map<String, Any?>? = null,
    ): PublicProfileDto =
        PublicProfileDto(
            id = "u1",
            username = "alex",
            firstName = "Alex",
            lastName = "Rivera",
            name = "Alex Rivera",
            bio = "Cambridge transplant.",
            tagline = "Builder",
            city = "Cambridge",
            state = "MA",
            accountType = "personal",
            verified = verified,
            residency = residency,
            createdAt = "2025-01-01T00:00:00.000Z",
            gigsPosted = 2,
            gigsCompleted = gigs,
            averageRating = rating,
            reviewCount = reviews.size,
            followersCount = 12,
            reviews = reviews,
            skills = listOf("Carpentry", "Spanish"),
        )

    @Test fun load_happy_path() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as PublicProfileUiState.Loaded
            assertEquals("Alex Rivera", loaded.content.header.displayName)
            assertEquals("alex", loaded.content.header.handle)
            assertEquals("Cambridge, MA", loaded.content.header.locality)
            assertTrue(loaded.content.header.isVerified)
            assertEquals(listOf("Carpentry", "Spanish"), loaded.content.stats.skills)
        }

    @Test fun tab_switching_does_not_refetch() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            val vm = makeVm()
            vm.load()
            vm.selectTab(ProfileTab.Reviews)
            vm.selectTab(ProfileTab.Gigs)
            coVerify(exactly = 1) { repo.publicProfile("u1") }
            assertEquals(ProfileTab.Gigs, vm.selectedTab.value)
        }

    @Test fun empty_reviews_state() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns
                NetworkResult.Success(profile(verified = false, reviews = emptyList(), rating = 0.0, gigs = 0))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as PublicProfileUiState.Loaded
            assertTrue(loaded.content.stats.reviews.isEmpty())
            assertFalse(loaded.content.header.isVerified)
        }

    @Test fun not_found_emits_friendly_message() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Failure(NetworkError.NotFound)
            val vm = makeVm()
            vm.load()
            val errorState = vm.state.value as PublicProfileUiState.Error
            assertTrue(errorState.message.contains("profile"))
        }

    @Test fun connect_sends_request_and_marks_succeeded() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            coEvery { relationships.sendRequest("u1", null) } returns
                NetworkResult.Success(ConnectionRequestResponse(message = "ok"))
            val vm = makeVm()
            vm.load()
            vm.connect()
            assertEquals(PublicProfileActionState.Succeeded, vm.connectState.value)
            assertEquals("Connection request sent", vm.toastMessage.value)
        }

    @Test fun connect_failure_surfaces_toast() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            coEvery { relationships.sendRequest("u1", null) } returns
                NetworkResult.Failure(NetworkError.Forbidden)
            val vm = makeVm()
            vm.load()
            vm.connect()
            assertTrue(vm.connectState.value is PublicProfileActionState.Failed)
            assertTrue(!vm.toastMessage.value.isNullOrEmpty())
        }

    @Test fun block_succeeds_and_emits_toast() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            coEvery { blocks.block("u1") } returns NetworkResult.Success(Unit)
            val vm = makeVm()
            vm.load()
            vm.block()
            assertEquals(PublicProfileActionState.Succeeded, vm.blockState.value)
            assertEquals("User blocked", vm.toastMessage.value)
        }

    @Test fun overflow_flag_toggles() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            val vm = makeVm()
            vm.load()
            assertFalse(vm.showOverflow.value)
            vm.setShowOverflow(true)
            assertTrue(vm.showOverflow.value)
        }

    // P6.5 — Persona vs Local kind discrimination

    @Test fun profile_without_residency_is_persona_kind() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile(residency = null))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as PublicProfileUiState.Loaded
            assertEquals(PublicProfileKind.Persona, loaded.content.kind)
            assertEquals("Persona · Verified", loaded.content.header.tierLabel)
            assertFalse(loaded.content.header.isVerifiedNeighbor)
        }

    @Test fun profile_with_verified_residency_is_local_kind() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns
                NetworkResult.Success(profile(residency = mapOf("verified" to true, "address" to "412 Elm St")))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as PublicProfileUiState.Loaded
            assertEquals(PublicProfileKind.Local, loaded.content.kind)
            assertTrue(loaded.content.header.isVerifiedNeighbor)
            assertEquals(null, loaded.content.header.tierLabel)
            assertNotNull(loaded.content.neighbor?.mutuals)
        }

    /**
     * `GET /api/users/id/:id` carries no Beacon handle, and `User.username` is
     * a different namespace from `PublicPersona.handle` — so the handshake
     * must not open against a handle we can't attribute.
     */
    @Test fun follow_does_not_use_username_as_beacon_handle() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            val vm = makeVm()
            vm.load()
            vm.follow()
            assertFalse(vm.showFollowHandshake.value)
            assertEquals("", vm.loadedPersonaHandle())
            assertEquals("Following isn't available from this profile yet.", vm.toastMessage.value)
        }

    // A21.2 — the Local archetype's post feed

    @Test fun local_profile_projects_user_posts_onto_the_feed() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns
                NetworkResult.Success(profile(residency = mapOf("verified" to true)))
            coEvery { posts.userPosts("u1", any()) } returns
                NetworkResult.Success(
                    MyPostsResponse(
                        listOf(
                            MyPostDto(
                                id = "p1",
                                userId = "u1",
                                content = "Free pile on the curb.",
                                postType = "service_offer",
                                createdAt = "2025-01-01T00:00:00.000Z",
                                likeCount = 28,
                                commentCount = 12,
                                locationName = "88 Beech St",
                            ),
                            MyPostDto(
                                id = "p2",
                                userId = "u1",
                                content = "Water main flagged on Beech.",
                                postType = "recommendation",
                                createdAt = "2025-01-01T00:00:00.000Z",
                            ),
                        ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as PublicProfileUiState.Loaded
            assertEquals(2, loaded.content.posts.size)
            val first = loaded.content.posts.first()
            assertEquals("Free pile on the curb.", first.body)
            assertEquals("88 Beech St", first.locality)
            assertEquals(28, first.reactions)
            assertEquals(12, first.replies)
            assertEquals(PublicProfilePost.Intent.Offer, first.intent)
            // Never invent a tier chip for a plain neighbourhood post.
            assertEquals(null, first.visibility)
            assertFalse(first.isLocked)
            // A post type with no honest chip renders without one.
            assertEquals(null, loaded.content.posts[1].intent)
            // The neighbour projection sees the same feed.
            assertEquals(2, loaded.content.neighbor?.posts?.size)
        }

    @Test fun local_profile_post_failure_degrades_to_empty_feed() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns
                NetworkResult.Success(profile(residency = mapOf("verified" to true)))
            coEvery { posts.userPosts("u1", any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as PublicProfileUiState.Loaded
            assertTrue(loaded.content.posts.isEmpty())
            assertEquals(PublicProfileKind.Local, loaded.content.kind)
        }

    @Test fun persona_profile_does_not_fetch_user_posts() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile(residency = null))
            val vm = makeVm()
            vm.load()
            coVerify(exactly = 0) { posts.userPosts(any(), any()) }
            assertTrue((vm.state.value as PublicProfileUiState.Loaded).content.posts.isEmpty())
        }

    @Test fun local_tab_defaults_to_posts_and_switches_without_refetch() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns
                NetworkResult.Success(profile(residency = mapOf("verified" to true)))
            val vm = makeVm()
            vm.load()
            assertEquals(LocalProfileTab.Posts, vm.selectedLocalTab.value)
            vm.selectLocalTab(LocalProfileTab.About)
            assertEquals(LocalProfileTab.About, vm.selectedLocalTab.value)
            coVerify(exactly = 1) { repo.publicProfile("u1") }
        }

    @Test fun unlock_broadcast_without_beacon_handle_stays_closed() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            val vm = makeVm()
            vm.load()
            vm.unlockBroadcast(2)
            assertFalse(vm.showFollowHandshake.value)
            assertEquals(null, vm.handshakePreselectedTierRank.value)
            assertEquals("Following isn't available from this profile yet.", vm.toastMessage.value)
        }
}
