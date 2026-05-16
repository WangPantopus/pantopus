@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.listing_offers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.listing_offers.ListingOfferDto
import app.pantopus.android.data.api.models.listing_offers.ListingOfferUserDto
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag on the listing-offers root container. */
const val LISTING_OFFERS_TAG = "listing-offers"

/**
 * T5.3.4 — Listing offers. Thin wrapper around [ListOfRowsScreen]. No
 * tabs, no FAB, listing-context header card pinned above the flat offer
 * list. Counter actions surface a half-sheet that the seller fills in
 * before the POST hits the backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingOffersScreen(
    onBack: () -> Unit,
    onShareListing: () -> Unit = {},
    onOpenBuyer: (ListingOfferUserDto) -> Unit = {},
    onOpenTransaction: (ListingOfferDto) -> Unit = {},
    onSort: () -> Unit = {},
    viewModel: ListingOffersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topBarAction by viewModel.topBarAction.collectAsStateWithLifecycle()
    val listingContext by viewModel.listingContext.collectAsStateWithLifecycle()
    val subtitle by viewModel.subtitle.collectAsStateWithLifecycle()
    val counterTarget by viewModel.counterTarget.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.bindCallbacks(
            onShareListing = onShareListing,
            onOpenBuyer = onOpenBuyer,
            onOpenTransaction = onOpenTransaction,
            onSort = onSort,
        )
        viewModel.load()
    }

    Box(modifier = Modifier.fillMaxSize().testTag(LISTING_OFFERS_TAG)) {
        ListOfRowsScreen(
            title = "Listing offers",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = { viewModel.loadMoreIfNeeded() },
            topBarAction = topBarAction,
            onBack = onBack,
            listingContext = listingContext,
            subtitle = subtitle,
        )

        val target = counterTarget
        if (target != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.cancelCounter() },
                sheetState = sheetState,
                containerColor = PantopusColors.appSurface,
            ) {
                CounterOfferSheet(
                    target = target,
                    onCancel = { viewModel.cancelCounter() },
                    onConfirm = { amount, message -> viewModel.confirmCounter(amount, message) },
                )
            }
        }
    }
}

@Composable
private fun CounterOfferSheet(
    target: CounterSheetTarget,
    onCancel: () -> Unit,
    onConfirm: (Double, String?) -> Unit,
) {
    var amountText by remember {
        mutableStateOf(target.suggestedAmount?.let { kotlin.math.round(it).toInt().toString() } ?: "")
    }
    var messageText by remember { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(
                text = "Counter ${target.buyerName}'s offer",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            target.originalAmount?.let {
                Text(
                    text = "Original offer: ${ListingOffersViewModel.formatPrice(it)}",
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(
                text = "Your counter amount",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                        .padding(Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    text = "$",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                BasicTextField(
                    value = amountText,
                    onValueChange = { value -> amountText = value.filter { it.isDigit() || it == '.' } },
                    textStyle =
                        TextStyle(
                            color = PantopusColors.appText,
                            fontSize = 16.sp,
                        ),
                    cursorBrush = SolidColor(PantopusColors.primary600),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("counter-amount"),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(
                text = "Optional message",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                        .padding(Spacing.s3),
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    textStyle =
                        TextStyle(
                            color = PantopusColors.appText,
                            fontSize = 14.sp,
                        ),
                    cursorBrush = SolidColor(PantopusColors.primary600),
                    modifier = Modifier.fillMaxWidth().testTag("counter-message"),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                        .clickable { onCancel() }
                        .padding(Spacing.s3)
                        .testTag("counter-cancel"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Cancel",
                    style = PantopusTextStyle.small,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
            }
            val parsedAmount = amountText.toDoubleOrNull()
            val canSend = parsedAmount != null && parsedAmount > 0
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(if (canSend) PantopusColors.primary600 else PantopusColors.appTextMuted)
                        .clickable(enabled = canSend) {
                            parsedAmount?.let { onConfirm(it, messageText.ifEmpty { null }) }
                        }
                        .padding(Spacing.s3)
                        .testTag("counter-confirm"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Send counter",
                    style = PantopusTextStyle.small,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s2))
    }
}
