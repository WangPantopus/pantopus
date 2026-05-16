@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.my_bids

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.offers.BidDto
import app.pantopus.android.data.api.models.offers.WithdrawBidReason
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag on the My bids root container. */
const val MY_BIDS_TAG = "my-bids"

/**
 * T5.3.1 — My bids. Thin wrapper around [ListOfRowsScreen]. Four tabs
 * (Active / Accepted / Rejected / Done), 48dp extended-pill FAB
 * labelled "Browse tasks", filter icon in the top-bar trailing slot,
 * and a primary-tinted banner above the Active tab. The
 * `WithdrawBidSheet` is a screen-bespoke addition for the destructive
 * confirmation flow — every other state lives in the shared shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun MyBidsScreen(
    onBack: () -> Unit,
    onOpenBid: (BidDto) -> Unit,
    onOpenFilters: () -> Unit = {},
    onBrowseTasks: () -> Unit = {},
    onMessageClient: (BidDto) -> Unit = {},
    onEditBid: (BidDto) -> Unit = {},
    onLeaveReview: (BidDto) -> Unit = {},
    viewModel: MyBidsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topBarAction by viewModel.topBarAction.collectAsStateWithLifecycle()
    val fab by viewModel.fab.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val withdrawTarget by viewModel.withdrawTarget.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.bindCallbacks(
            onOpenBid = onOpenBid,
            onOpenFilters = onOpenFilters,
            onBrowseTasks = onBrowseTasks,
            onMessageClient = onMessageClient,
            onEditBid = onEditBid,
            onLeaveReview = onLeaveReview,
        )
        viewModel.load()
    }

    Box(modifier = Modifier.fillMaxSize().testTag(MY_BIDS_TAG)) {
        ListOfRowsScreen(
            title = "My bids",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = { viewModel.loadMoreIfNeeded() },
            tabs = tabs,
            selectedTab = selectedTab,
            onSelectTab = { viewModel.selectTab(it) },
            topBarAction = topBarAction,
            fab = fab,
            banner = banner,
            onBack = onBack,
        )
    }

    val target = withdrawTarget
    if (target != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.cancelWithdraw() },
            sheetState = sheetState,
        ) {
            WithdrawBidSheetContent(
                target = target,
                onCancel = { viewModel.cancelWithdraw() },
                onConfirm = { reason -> viewModel.confirmWithdraw(reason) },
            )
        }
    }
}

@Composable
private fun WithdrawBidSheetContent(
    target: WithdrawSheetTarget,
    onCancel: () -> Unit,
    onConfirm: (WithdrawBidReason?) -> Unit,
) {
    var selected by remember { mutableStateOf<WithdrawBidReason?>(null) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(
                text = "Withdraw bid",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Text(
                text = "Why are you withdrawing your bid on ${target.gigTitle}?",
                fontSize = 14.sp,
                color = PantopusColors.appTextSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            for (reason in WithdrawBidReason.entries) {
                ReasonRow(
                    reason = reason,
                    isSelected = selected == reason,
                    onClick = { selected = reason },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SecondaryButton(
                text = "Cancel",
                modifier = Modifier.weight(1f).testTag("withdraw-cancel"),
                onClick = onCancel,
            )
            DestructiveButton(
                text = "Withdraw bid",
                modifier = Modifier.weight(1f).testTag("withdraw-confirm"),
                onClick = { onConfirm(selected) },
            )
        }
    }
}

@Composable
private fun ReasonRow(
    reason: WithdrawBidReason,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("withdraw-reason-${reason.wireValue}")
                .clip(RoundedCornerShape(Radii.md))
                .background(if (isSelected) PantopusColors.primary50 else PantopusColors.appSurfaceSunken)
                .border(
                    width = 1.dp,
                    color = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.md),
                )
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.weight(1f),
            color = Color.Transparent,
            onClick = onClick,
        ) {
            Text(
                text = reason.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appText,
            )
        }
        if (isSelected) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = "Selected",
                size = 18.dp,
                tint = PantopusColors.primary600,
            )
        }
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(44.dp)
                .clip(RoundedCornerShape(Radii.md))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
        }
    }
}

@Composable
private fun DestructiveButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(44.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.error),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }

    // Padding helper — kept here so the layout matches the iOS withdraw sheet.
    Spacer(modifier = Modifier.width(0.dp))
}
