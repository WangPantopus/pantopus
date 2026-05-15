@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.inbox.conversation

import app.pantopus.android.data.api.models.chats.ChatMessageDto
import app.pantopus.android.data.api.models.chats.ChatMessageSender
import app.pantopus.android.data.api.models.chats.ChatMessagesResponse
import app.pantopus.android.data.api.models.chats.SendChatMessageBody
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors the iOS [ChatConversationViewModelTests] coverage: backend
 * load → day-divider + bubble projection, AI mode → empty welcome
 * frame, and send-failure → failed delivery state on the optimistic
 * bubble.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatConversationViewModelTest {
    private val repo: ChatRepository = mockk()
    private val socket: SocketManager = mockk()

    private val counterpartyPerson =
        ChatCounterparty.Person(
            displayName = "Maria K.",
            initials = "MK",
            locality = "Elm Park",
            verified = true,
            online = true,
        )

    private val counterpartyAi = ChatCounterparty.Ai(displayName = "Ask Pantopus")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { socket.eventsOf(any()) } returns emptyFlow()
        coEvery { repo.markRoomRead(any()) } returns NetworkResult.Success(Unit)
        coEvery { repo.markConversationRead(any()) } returns NetworkResult.Success(Unit)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun message(
        id: String,
        userId: String,
        text: String,
        createdAt: String = "2026-04-20T10:00:00.000Z",
        clientMessageId: String? = null,
    ): ChatMessageDto =
        ChatMessageDto(
            id = id,
            roomId = "r1",
            userId = userId,
            messageText = text,
            messageType = "text",
            clientMessageId = clientMessageId,
            createdAt = createdAt,
            sender = ChatMessageSender(id = userId, username = "u"),
        )

    @Test fun load_produces_loaded_with_day_divider_and_bubbles() =
        runTest {
            coEvery { repo.conversationMessages(any(), any(), any(), any()) } returns
                NetworkResult.Success(
                    ChatMessagesResponse(
                        messages =
                            listOf(
                                message(id = "m2", userId = "u_me", text = "hello", createdAt = "2026-04-20T10:00:30.000Z"),
                                message(id = "m1", userId = "u_other", text = "hi"),
                            ),
                        hasMore = false,
                    ),
                )
            val vm = ChatConversationViewModel(repo, socket)
            vm.configure(
                mode = ChatThreadMode.Person(otherUserId = "u_other"),
                counterparty = counterpartyPerson,
                currentUserId = "u_me",
            )
            vm.load()
            val loaded = vm.state.value as ChatConversationUiState.Loaded
            assertEquals(3, loaded.rows.size)
            assertTrue(loaded.rows.first() is ChatTimelineRow.DayDivider)
        }

    @Test fun ai_thread_starts_in_empty_for_welcome_frame() =
        runTest {
            val vm = ChatConversationViewModel(repo, socket)
            vm.configure(
                mode = ChatThreadMode.Ai,
                counterparty = counterpartyAi,
                currentUserId = "u_me",
            )
            vm.load()
            assertTrue(vm.state.value is ChatConversationUiState.Empty)
        }

    @Test fun send_failure_marks_optimistic_bubble_as_failed() =
        runTest {
            coEvery { repo.conversationMessages(any(), any(), any(), any()) } returns
                NetworkResult.Success(ChatMessagesResponse(messages = emptyList(), hasMore = false))
            coEvery { repo.sendMessage(any<SendChatMessageBody>()) } returns
                NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = ChatConversationViewModel(repo, socket)
            vm.configure(
                mode = ChatThreadMode.Person(otherUserId = "u_other"),
                counterparty = counterpartyPerson,
                currentUserId = "u_me",
            )
            vm.load()
            vm.setComposerText("Hello")
            vm.send()
            val loaded = vm.state.value as ChatConversationUiState.Loaded
            val outgoing =
                loaded.rows
                    .filterIsInstance<ChatTimelineRow.Bubble>()
                    .firstOrNull { it.content.side == ChatMessageSide.Outgoing }
            assertNotNull(outgoing)
            assertEquals(ChatDeliveryState.Failed, outgoing!!.content.deliveryState)
        }

    @Test fun load_failure_transitions_error() =
        runTest {
            coEvery { repo.conversationMessages(any(), any(), any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val vm = ChatConversationViewModel(repo, socket)
            vm.configure(
                mode = ChatThreadMode.Person(otherUserId = "u_other"),
                counterparty = counterpartyPerson,
                currentUserId = "u_me",
            )
            vm.load()
            assertTrue(vm.state.value is ChatConversationUiState.Error)
        }

    @Test fun load_older_paginates_backward() =
        runTest {
            val initial =
                ChatMessagesResponse(
                    messages = listOf(message(id = "m2", userId = "u_other", text = "hi")),
                    hasMore = true,
                )
            val older =
                ChatMessagesResponse(
                    messages =
                        listOf(
                            message(id = "m1", userId = "u_other", text = "earlier", createdAt = "2026-04-20T09:59:00.000Z"),
                        ),
                    hasMore = false,
                )
            coEvery {
                repo.conversationMessages(any(), any(), any(), any())
            } returnsMany listOf(NetworkResult.Success(initial), NetworkResult.Success(older))
            val vm = ChatConversationViewModel(repo, socket)
            vm.configure(
                mode = ChatThreadMode.Person(otherUserId = "u_other"),
                counterparty = counterpartyPerson,
                currentUserId = "u_me",
            )
            vm.load()
            vm.loadOlder()
            val loaded = vm.state.value as ChatConversationUiState.Loaded
            val bubbleIds =
                loaded.rows
                    .filterIsInstance<ChatTimelineRow.Bubble>()
                    .map { it.content.id }
            assertTrue("expected both pages merged, got $bubbleIds", bubbleIds.containsAll(listOf("m1", "m2")))
        }

    @Test fun refresh_merges_new_message_into_loaded_thread() =
        runTest {
            val first =
                ChatMessagesResponse(
                    messages = listOf(message(id = "m1", userId = "u_other", text = "hi")),
                    hasMore = false,
                )
            val second =
                ChatMessagesResponse(
                    messages =
                        listOf(
                            message(id = "m1", userId = "u_other", text = "hi"),
                            message(id = "m2", userId = "u_other", text = "follow-up", createdAt = "2026-04-20T10:01:00.000Z"),
                        ),
                    hasMore = false,
                )
            coEvery {
                repo.conversationMessages(any(), any(), any(), any())
            } returnsMany listOf(NetworkResult.Success(first), NetworkResult.Success(second))
            val vm = ChatConversationViewModel(repo, socket)
            vm.configure(
                mode = ChatThreadMode.Person(otherUserId = "u_other"),
                counterparty = counterpartyPerson,
                currentUserId = "u_me",
            )
            vm.load()
            // Simulate realtime: server-side echo → refresh() reads
            // the merged list. handleIncoming + handleReaction both
            // call fetch(initial = true) under the hood.
            vm.refresh()
            val loaded = vm.state.value as ChatConversationUiState.Loaded
            val bubbleIds =
                loaded.rows
                    .filterIsInstance<ChatTimelineRow.Bubble>()
                    .map { it.content.id }
            assertTrue("expected new message merged in, got $bubbleIds", bubbleIds.contains("m2"))
        }
}
