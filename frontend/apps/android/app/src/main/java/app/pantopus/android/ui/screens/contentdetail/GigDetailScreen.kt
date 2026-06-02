@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.contentdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.my_bids.EditBidSheetContent
import app.pantopus.android.ui.screens.my_bids.EditBidSheetTarget
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun GigDetailScreen(
    onBack: () -> Unit = {},
    onOpenMessages: (app.pantopus.android.data.api.models.gigs.GigDto) -> Unit = {},
    viewModel: GigDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetTarget by remember { mutableStateOf<EditBidSheetTarget?>(null) }
    var deliveryTarget by remember { mutableStateOf<DeliveryProofTarget?>(null) }
    var toastText by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deliverySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { viewModel.load() }

    val openMessages: () -> Unit = {
        viewModel.gigSnapshot()?.let { onOpenMessages(it) }
    }

    LaunchedEffect(toastText) {
        if (toastText != null) {
            kotlinx.coroutines.delay(2_500)
            toastText = null
        }
    }

    ContentDetailShell(
        state = state,
        onBack = onBack,
        onPrimaryAction = {
            val gig = (state as? ContentDetailUiState.Loaded)?.content?.hero
            // Pull the gig identifiers off the loaded state. The VM keeps
            // the gig id internally; we look it up here for the sheet
            // header copy. When the state isn't loaded yet, the dock CTA
            // is hidden so we never reach this branch in practice.
            if (viewModel.canMarkDelivered()) {
                // Assigned worker on an in-progress task → Delivery Proof sheet.
                deliveryTarget =
                    DeliveryProofTarget(
                        id = "deliver",
                        gigId = viewModel.currentGigId(),
                        gigTitle = gig?.title ?: "this task",
                    )
            } else {
                sheetTarget =
                    EditBidSheetTarget(
                        id = "new-bid",
                        gigId = viewModel.currentGigId(),
                        gigTitle = gig?.title ?: "this task",
                        bidId = null,
                    )
            }
        },
        onSecondaryAction = openMessages,
        onRetry = { viewModel.load() },
        onMessageCounterparty = openMessages,
    )

    val target = sheetTarget
    if (target != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetTarget = null },
            sheetState = sheetState,
        ) {
            EditBidSheetContent(
                target = target,
                onSubmit = { draft ->
                    val ok =
                        suspendCancellableCoroutine<Boolean> { cont ->
                            viewModel.placeBid(
                                amount = draft.amount,
                                message = draft.message,
                                proposedTime = draft.proposedTime,
                            ) { result -> cont.resume(result) }
                        }
                    if (ok) {
                        sheetTarget = null
                        toastText = "Bid submitted."
                    }
                    ok
                },
                onCancel = { sheetTarget = null },
            )
        }
    }

    val delivery = deliveryTarget
    if (delivery != null) {
        ModalBottomSheet(
            onDismissRequest = { deliveryTarget = null },
            sheetState = deliverySheetState,
        ) {
            DeliveryProofSheet(
                target = delivery,
                onSubmit = { photos, note ->
                    suspendCancellableCoroutine<Boolean> { cont ->
                        viewModel.submitDeliveryProof(photos, note) { result -> cont.resume(result) }
                    }
                },
                onDismiss = { deliveryTarget = null },
            )
        }
    }

    toastText?.let { text ->
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(Spacing.s4)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.success)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                        .testTag("gig-detail-toast"),
            ) {
                Text(
                    text = text,
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
    }
}
