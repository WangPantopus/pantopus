@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.inbox.chat

import app.pantopus.android.data.api.models.chats.ChatOtherIdentity
import app.pantopus.android.data.api.models.chats.ChatStats
import app.pantopus.android.data.api.models.chats.ChatStatsResponse
import app.pantopus.android.data.api.models.chats.ChatTopic
import app.pantopus.android.data.api.models.chats.UnifiedConversationDto
import app.pantopus.android.data.api.models.chats.UnifiedConversationsResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.chats.ChatRepository
import app.pantopus.android.data.realtime.SocketManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {
    private val repo: ChatRepository = mockk()
    private val socket: SocketManager = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { socket.eventsOf(any()) } returns emptyFlow()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun direct(): UnifiedConversationDto =
        UnifiedConversationDto(
            type = "conversation",
            otherParticipantId = "u1",
            otherParticipantName = "Marcus R.",
            otherParticipantAvatar = null,
            otherParticipantIdentity =
                ChatOtherIdentity(
                    id = "u1",
                    displayName = "Marcus R.",
                    avatarUrl = null,
                    identityKind = "personal",
                    verified = true,
                ),
            roomIds = listOf("r1"),
            topics = listOf(ChatTopic(id = "t1", topicType = "gig", title = "Shelves")),
            totalUnread = 2,
            lastMessageAt = "2026-04-20T10:00:00Z",
            lastMessagePreview = "Can you start at 9?",
        )

    private fun business(): UnifiedConversationDto =
        UnifiedConversationDto(
            type = "conversation",
            otherParticipantId = "b1",
            otherParticipantName = "Dahlia's Petals",
            otherParticipantIdentity =
                ChatOtherIdentity(
                    id = "b1",
                    displayName = "Dahlia's Petals",
                    identityKind = "business",
                    verified = true,
                ),
            totalUnread = 1,
            lastMessageAt = "2026-04-20T09:00:00Z",
            lastMessagePreview = "On the porch.",
        )

    private fun group(): UnifiedConversationDto =
        UnifiedConversationDto(
            type = "room",
            id = "g1",
            roomType = "group",
            roomName = "Rose Court Block",
            totalUnread = 0,
            lastMessageAt = "2026-04-19T12:00:00Z",
            lastMessagePreview = "I'll grab the chairs",
        )

    private fun stats(): ChatStatsResponse =
        ChatStatsResponse(
            ChatStats(totalChats = 3, totalMessages = 17, totalUnread = 3, directChats = 2, gigChats = 1),
        )

    @Test fun load_produces_loaded_with_ai_row_pinned() =
        runTest {
            coEvery { repo.unifiedConversations(any()) } returns
                NetworkResult.Success(
                    UnifiedConversationsResponse(
                        conversations = listOf(direct(), business(), group()),
                        total = 3,
                        totalUnread = 3,
                    ),
                )
            coEvery { repo.stats() } returns NetworkResult.Success(stats())
            val vm = ChatListViewModel(repo, socket)
            vm.load()
            val loaded = vm.state.value as ChatListUiState.Loaded
            assertEquals("ai_assistant", loaded.rows.first().id)
            assertEquals(4, loaded.rows.size)
            val marcus = loaded.rows.first { it.id == "u1" }
            assertEquals(2, marcus.unread)
            assertTrue(marcus.verified)
            assertNull(marcus.identityChip)
            assertTrue(marcus.topicKinds.contains("gig"))
            val biz = loaded.rows.first { it.id == "b1" }
            assertEquals(ConversationIdentityChip.Business, biz.identityChip)
        }

    @Test fun load_empty_transitions_empty() =
        runTest {
            coEvery { repo.unifiedConversations(any()) } returns
                NetworkResult.Success(UnifiedConversationsResponse(emptyList(), 0, 0))
            coEvery { repo.stats() } returns NetworkResult.Success(stats())
            val vm = ChatListViewModel(repo, socket)
            vm.load()
            assertTrue(vm.state.value is ChatListUiState.Empty)
        }

    @Test fun load_failure_transitions_error() =
        runTest {
            coEvery { repo.unifiedConversations(any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, null))
            coEvery { repo.stats() } returns NetworkResult.Success(stats())
            val vm = ChatListViewModel(repo, socket)
            vm.load()
            assertTrue(vm.state.value is ChatListUiState.Error)
        }

    @Test fun select_filter_filters_without_refetch() =
        runTest {
            coEvery { repo.unifiedConversations(any()) } returns
                NetworkResult.Success(
                    UnifiedConversationsResponse(listOf(direct(), business(), group()), 3, 3),
                )
            coEvery { repo.stats() } returns NetworkResult.Success(stats())
            val vm = ChatListViewModel(repo, socket)
            vm.load()
            vm.selectFilter(ChatFilter.Gigs)
            val loaded = vm.state.value as ChatListUiState.Loaded
            assertEquals(2, loaded.rows.size)
            assertTrue(loaded.rows.any { it.id == "u1" })
            assertTrue(loaded.rows.none { it.id == "b1" })
        }
}
