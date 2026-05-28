@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "CyclomaticComplexMethod", "FunctionNaming")

package app.pantopus.android.ui.screens.settings.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.BalanceHero
import app.pantopus.android.ui.components.BalanceHeroPayoutFooter
import app.pantopus.android.ui.screens.settings.payments.components.PaymentMethodRow
import app.pantopus.android.ui.screens.settings.payments.components.PaymentMethodRowModel
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * P5.2 / A14.6 — Settings → Payments. Payments-OUT surface (cards on
 * file · Stripe Connect setup · payout routing) — distinct from
 * A10.10 Wallet which surfaces earnings-IN. Three grouped cards
 * under an optional balance hero, with an "Add payment method" blue
 * row as the final item in the Payment methods card (iOS convention)
 * and a destructive Close-account card on the populated frame only.
 */
@Composable
fun PaymentsScreen(
    onBack: () -> Unit = {},
    viewModel: PaymentsViewModel = hiltViewModel(),
    seed: PaymentsSeed = PaymentsSeed.Populated,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(seed) {
        viewModel.seed(seed)
        viewModel.load()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("payments"),
    ) {
        TopBar(onBack = onBack)
        when (val current = state) {
            is PaymentsUiState.Loading -> LoadingFrame()
            is PaymentsUiState.Loaded ->
                LoadedFrame(
                    loaded = current.content,
                    onTapRow = viewModel::tapRow,
                    onAddMethod = viewModel::tapAddMethod,
                    onCloseAccount = viewModel::tapCloseAccount,
                )
            is PaymentsUiState.Error ->
                ErrorFrame(
                    message = current.message,
                    onRetry = viewModel::refresh,
                )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(PantopusColors.appBg)
                .padding(horizontal = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .testTag("paymentsTopBarBack"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = "Back",
                size = 22.dp,
                tint = PantopusColors.appText,
            )
        }
        Text(
            text = "Payments",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.size(36.dp))
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PantopusColors.appBorder),
    )
}

@Composable
private fun LoadedFrame(
    loaded: PaymentsLoaded,
    onTapRow: (String) -> Unit,
    onAddMethod: () -> Unit,
    onCloseAccount: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("paymentsContent"),
        contentPadding = PaddingValues(bottom = Spacing.s5),
    ) {
        if (loaded.balance != null) {
            item(key = "balance") {
                BalanceHeroSection(loaded.balance)
            }
        }
        item(key = "overline_methods") { SectionOverline("Payment methods", id = "methods") }
        item(key = "card_methods") {
            MethodsCard(methods = loaded.methods, onTapRow = onTapRow, onAddMethod = onAddMethod)
        }
        item(key = "overline_payouts") { SectionOverline("Payouts", id = "payouts") }
        item(key = "card_payouts") {
            PayoutsCard(payouts = loaded.payouts, onTapRow = onTapRow)
        }
        if (loaded.payouts.helper != null) {
            item(key = "helper_payouts") {
                Text(
                    text = loaded.payouts.helper,
                    color = PantopusColors.appTextSecondary,
                    fontSize = 11.5.sp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                            .testTag("paymentsHelper_payouts"),
                )
            }
        }
        item(key = "overline_activity") { SectionOverline("Activity", id = "activity") }
        item(key = "card_activity") {
            ActivityCard(activity = loaded.activity, onTapRow = onTapRow)
        }
        if (loaded.canCloseAccount) {
            item(key = "destructive") {
                DestructiveCard(onCloseAccount = onCloseAccount)
            }
        }
        item(key = "footer") {
            Text(
                text = loaded.footerCaption,
                color = PantopusColors.appTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s4, vertical = 18.dp)
                        .testTag("paymentsFooter"),
            )
        }
    }
}

@Composable
private fun BalanceHeroSection(balance: PaymentsBalance) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s3, vertical = 14.dp),
    ) {
        BalanceHero(
            overline = balance.overline,
            amount = balance.amount,
            currencyCode = "USD",
            payoutFooter =
                BalanceHeroPayoutFooter(
                    nextPayoutLabel = balance.nextPayoutLabel,
                    frequencyPill = balance.frequencyPill,
                ),
            modifier = Modifier.testTag("paymentsBalanceHero"),
        )
    }
}

@Composable
private fun MethodsCard(
    methods: List<PaymentMethod>,
    onTapRow: (String) -> Unit,
    onAddMethod: () -> Unit,
) {
    Card(id = "methods") {
        if (methods.isEmpty()) {
            InlineEmpty(
                icon = PantopusIcon.CreditCard,
                title = "No payment methods yet",
                body = "Add a card or bank account to hire neighbors and pay for marketplace listings.",
            )
            Divider()
        } else {
            methods.forEachIndexed { index, method ->
                PaymentMethodRow(
                    model =
                        PaymentMethodRowModel(
                            rowIdentifier = method.id,
                            brand = method.brand,
                            label = method.label,
                            subtext = method.subtext,
                            chip = method.chip,
                            trailing = PaymentsRowTrailing.Chevron,
                        ),
                    modifier = Modifier.clickable { onTapRow(method.id) },
                )
                if (index < methods.size - 1) {
                    Divider()
                }
            }
            Divider()
        }
        AddMethodRow(onAddMethod = onAddMethod)
    }
}

@Composable
private fun PayoutsCard(
    payouts: PaymentsPayouts,
    onTapRow: (String) -> Unit,
) {
    val rows =
        buildList {
            add(payouts.stripe)
            add(payouts.payoutMethod)
            payouts.payoutSchedule?.let { add(it) }
            add(payouts.taxInfo)
        }
    Card(id = "payouts") {
        rows.forEachIndexed { index, row ->
            val isGated = row.trailing is PaymentsRowTrailing.GatedDash
            PaymentMethodRow(
                model =
                    PaymentMethodRowModel(
                        rowIdentifier = row.id,
                        brand = row.leadingBrand,
                        label = row.label,
                        subtext = row.subtext,
                        trailing = row.trailing,
                    ),
                modifier =
                    if (isGated) {
                        Modifier
                    } else {
                        Modifier.clickable { onTapRow(row.id) }
                    },
            )
            if (index < rows.size - 1) {
                Divider()
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: PaymentsActivity,
    onTapRow: (String) -> Unit,
) {
    Card(id = "activity") {
        when (activity) {
            is PaymentsActivity.Stats ->
                activity.rows.forEachIndexed { index, stat ->
                    ActivityStatRow(
                        stat = stat,
                        modifier = Modifier.clickable { onTapRow(stat.id) },
                    )
                    if (index < activity.rows.size - 1) {
                        Divider()
                    }
                }
            is PaymentsActivity.Empty -> ActivityEmptyRow(title = activity.title, body = activity.body)
        }
    }
}

@Composable
private fun DestructiveCard(onCloseAccount: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s3)
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .testTag("paymentsCard_close"),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onCloseAccount)
                    .padding(horizontal = Spacing.s4, vertical = 14.dp)
                    .testTag("paymentsRow_closeAccount"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Close payment account",
                color = PantopusColors.error,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ActivityStatRow(
    stat: PaymentsActivityStat,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = Spacing.s4, vertical = 14.dp)
                .testTag("paymentsActivityStat_${stat.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.label,
                color = PantopusColors.appText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (stat.subtext != null) {
                Text(
                    text = stat.subtext,
                    color = PantopusColors.appTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        PantopusIconImage(
            icon = PantopusIcon.ChevronRight,
            contentDescription = null,
            size = 16.dp,
            strokeWidth = 2.2f,
            tint = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun ActivityEmptyRow(
    title: String,
    body: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4, vertical = 18.dp)
                .testTag("paymentsActivityEmpty"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Receipt,
                contentDescription = null,
                size = 16.dp,
                strokeWidth = 1.75f,
                tint = PantopusColors.appTextMuted,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PantopusColors.appTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = body,
                color = PantopusColors.appTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AddMethodRow(onAddMethod: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onAddMethod)
                .padding(horizontal = Spacing.s4, vertical = 13.dp)
                .testTag("paymentsAddMethodRow"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 38.dp, height = 26.dp)
                    .clip(RoundedCornerShape(Radii.xs))
                    .background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Plus,
                contentDescription = null,
                size = 16.dp,
                strokeWidth = 2.5f,
                tint = PantopusColors.primary600,
            )
        }
        Text(
            text = "Add payment method",
            color = PantopusColors.primary600,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InlineEmpty(
    icon: PantopusIcon,
    title: String,
    body: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s5)
                .padding(top = 28.dp, bottom = 22.dp)
                .testTag("paymentsMethodsInlineEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 22.dp,
                strokeWidth = 1.75f,
                tint = PantopusColors.appTextMuted,
            )
        }
        Text(
            text = title,
            color = PantopusColors.appText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            color = PantopusColors.appTextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.s5),
        )
    }
}

@Composable
private fun Card(
    id: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s3)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .testTag("paymentsCard_$id"),
    ) {
        content()
    }
}

@Composable
private fun Divider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(start = Spacing.s4)
                .background(PantopusColors.appBorder.copy(alpha = 0.6f)),
    )
}

@Composable
private fun SectionOverline(
    text: String,
    id: String,
) {
    Text(
        text = text.uppercase(),
        color = PantopusColors.appTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.s4, end = Spacing.s4, top = 18.dp, bottom = Spacing.s2)
                .testTag("paymentsOverline_$id"),
    )
}

@Composable
private fun LoadingFrame() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("paymentsLoading"),
        contentPadding = PaddingValues(vertical = Spacing.s3),
    ) {
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                        .height(96.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PantopusColors.appSurfaceSunken),
            )
        }
        items(3, key = { i -> "card_skel_$i" }) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                        .height(110.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.appSurfaceSunken),
            )
        }
    }
}

@Composable
private fun ErrorFrame(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.s6)
                .testTag("paymentsError"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 40.dp,
            tint = PantopusColors.error,
        )
        Box(modifier = Modifier.height(12.dp))
        Text(
            text = "Couldn't load Payments",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Box(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.height(16.dp))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp)
                    .heightIn(min = 44.dp)
                    .testTag("paymentsRetry"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}
