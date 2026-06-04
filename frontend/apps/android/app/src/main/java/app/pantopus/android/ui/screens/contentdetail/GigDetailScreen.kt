@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.contentdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.my_bids.EditBidSheetContent
import app.pantopus.android.ui.screens.my_bids.EditBidSheetTarget
import app.pantopus.android.ui.screens.settings.payments.StripePaymentSheets
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import com.stripe.android.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun GigDetailScreen(
    onBack: () -> Unit = {},
    onOpenMessages: (app.pantopus.android.data.api.models.gigs.GigDto) -> Unit = {},
    viewModel: GigDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tipStatus by viewModel.tipStatus.collectAsStateWithLifecycle()
    var sheetTarget by remember { mutableStateOf<EditBidSheetTarget?>(null) }
    var deliveryTarget by remember { mutableStateOf<DeliveryProofTarget?>(null) }
    var showTipSheet by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deliverySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tipSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Block 3D — Stripe PaymentSheet for tipping (created in composition).
    val paymentSheet =
        rememberPaymentSheet { result ->
            viewModel.onTipOutcome(StripePaymentSheets.checkoutOutcome(result))
        }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GigTipEvent.PresentTipSheet -> {
                    showTipSheet = false
                    paymentSheet.presentWithPaymentIntent(
                        paymentIntentClientSecret = event.params.clientSecret.orEmpty(),
                        configuration =
                            StripePaymentSheets.paymentConfiguration(
                                customerId = event.params.customer,
                                ephemeralKey = event.params.ephemeralKey,
                            ),
                    )
                }
            }
        }
    }
    // Tip success → toast (PaymentSheet itself surfaces decline / SCA errors).
    LaunchedEffect(tipStatus) {
        if (tipStatus is TipStatus.Succeeded) toastText = "Tip sent — thank you!"
    }

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
            when {
                // Poster on a completed gig → Send-a-tip sheet (Block 3D).
                viewModel.canTip() -> showTipSheet = true
                // Assigned worker on an in-progress task → Delivery Proof sheet.
                viewModel.canMarkDelivered() ->
                    deliveryTarget =
                        DeliveryProofTarget(
                            id = "deliver",
                            gigId = viewModel.currentGigId(),
                            gigTitle = gig?.title ?: "this task",
                        )
                else ->
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

    if (showTipSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTipSheet = false },
            sheetState = tipSheetState,
        ) {
            TipAmountSheet(
                sending = tipStatus is TipStatus.Sending,
                onSelect = { cents ->
                    showTipSheet = false
                    viewModel.sendTip(cents)
                },
                onCancel = { showTipSheet = false },
            )
        }
    }

    TipMarkers(canTip = viewModel.canTip(), tipStatus = tipStatus)

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

/** Send-a-tip amount picker (Block 3D). Preset amounts in cents. */
@Composable
private fun TipAmountSheet(
    sending: Boolean,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s5)
                .testTag("tip.amount"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.HandCoins,
            contentDescription = null,
            size = 32.dp,
            tint = PantopusColors.primary600,
        )
        Text(text = "Send a tip", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
        Text(
            text = "100% goes to your helper. Charged to your card via Stripe.",
            fontSize = 13.sp,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            listOf(500, 1000, 2000).forEach { cents ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(PantopusColors.primary50)
                            .clickable(enabled = !sending) { onSelect(cents) }
                            .testTag("tip.amount.$cents"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\$${cents / 100}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.primary600,
                    )
                }
            }
        }
        Text(
            text = "Not now",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.clickable(onClick = onCancel),
        )
    }
}

/** Invisible test anchors for the tip stages. */
@Composable
private fun TipMarkers(
    canTip: Boolean,
    tipStatus: TipStatus,
) {
    if (canTip) Box(modifier = Modifier.size(0.dp).testTag("tip.affordance"))
    if (tipStatus is TipStatus.Sending) Box(modifier = Modifier.size(0.dp).testTag("tip.paymentSheet"))
    if (tipStatus is TipStatus.Succeeded) Box(modifier = Modifier.size(0.dp).testTag("tip.success"))
}
