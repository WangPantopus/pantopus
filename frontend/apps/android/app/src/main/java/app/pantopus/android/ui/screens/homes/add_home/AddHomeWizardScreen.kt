@file:Suppress("PackageNaming", "LongMethod", "MagicNumber")

package app.pantopus.android.ui.screens.homes.add_home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.wizard.WizardShell
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.ReviewSummaryBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.ReviewSummaryRow
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.screens.status.StatusWaitingBody
import app.pantopus.android.ui.screens.status.StatusWaitingContent
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag applied to the AddHome screen container. */
const val ADD_HOME_SCREEN_TAG = "addHomeWizard"

/**
 * Concrete Add-Home wizard composable. The view model survives config
 * changes via Hilt's `SavedStateHandle`, so the wizard restores after
 * process death (acceptance criterion #5).
 */
@Composable
fun AddHomeWizardScreen(
    onDismiss: () -> Unit,
    onOpenHomeDashboard: (String) -> Unit,
    viewModel: AddHomeWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()

    LaunchedEffect(pendingEvent) {
        when (val event = pendingEvent) {
            AddHomeOutboundEvent.Dismiss -> {
                viewModel.acknowledgeEvent()
                onDismiss()
            }
            is AddHomeOutboundEvent.OpenHomeDashboard -> {
                viewModel.acknowledgeEvent()
                onOpenHomeDashboard(event.homeId)
            }
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        // Fire the initial step view; subsequent transitions emit
        // their own ScreenAddHomeWizardStepViewed events from the VM.
        val current = state.form.currentStep
        current.stepNumber?.let { number ->
            Analytics.track(
                AnalyticsEvent.ScreenAddHomeWizardStepViewed(
                    stepNumber = number,
                    stepName = current.name,
                ),
            )
        }
    }

    WizardShell(
        model = viewModel,
        modifier = Modifier.testTag(ADD_HOME_SCREEN_TAG),
    ) {
        when (state.form.currentStep) {
            AddHomeStep.Address -> AddressStep(state, viewModel)
            AddHomeStep.Confirm -> ConfirmStep(state, viewModel)
            AddHomeStep.Role -> RoleStep(state, viewModel)
            AddHomeStep.Review -> ReviewStep(state)
            AddHomeStep.Success -> SuccessStep()
        }
        state.errorMessage?.let { ErrorBanner(it) }
    }
}

// MARK: - Step 1

@Composable
private fun AddressStep(
    state: AddHomeUiState,
    vm: AddHomeWizardViewModel,
) {
    HeadlineBlock("Where do you live?")
    SubcopyBlock("Pick your address to start. You'll verify it next.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        AddHomeSearchField(
            query = state.homeSearchQuery,
            onQueryChange = vm::updateSearchQuery,
            onClear = vm::clearSearchQuery,
        )
        if (vm.showsAutocomplete) {
            AddHomeAutocompleteDropdown(
                query = state.homeSearchQuery,
                results = vm.autocompleteResults,
                onSelect = vm::selectAddressCandidate,
                onAddManually = vm::addManuallyTapped,
            )
        } else {
            UseCurrentLocationPill(onClick = vm::useCurrentLocation)
            NearbyHomesSection(
                homes = vm.nearbyHomes,
                selectedHomeId = state.selectedHomeId,
                onSelect = vm::selectAddressCandidate,
            )
            ManualAddressButton(onClick = vm::addManuallyTapped)
        }
    }
}

// MARK: - Step 2

@Composable
private fun ConfirmStep(
    state: AddHomeUiState,
    vm: AddHomeWizardViewModel,
) {
    HeadlineBlock("Confirm the property")
    SubcopyBlock(
        "We checked this address against our property records. Review the details before continuing.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        ReviewSummaryBlock(
            rows =
                listOf(
                    ReviewSummaryRow("Street", state.form.address.street),
                    ReviewSummaryRow("City", state.form.address.city),
                    ReviewSummaryRow("State", state.form.address.state),
                    ReviewSummaryRow("ZIP", state.form.address.zipCode),
                ),
        )
        state.addressCheck?.let { check ->
            AddressVerdictRow(check)
        }
        PrimaryHomeToggle(
            isPrimary = state.form.isPrimary,
            onChange = vm::setPrimaryHome,
        )
    }
}

// MARK: - Step 3

@Composable
private fun RoleStep(
    state: AddHomeUiState,
    vm: AddHomeWizardViewModel,
) {
    HeadlineBlock("What's your role?")
    SubcopyBlock("This determines what verification we'll ask for next.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        AddHomeRole.entries.forEach { role ->
            RoleRow(
                role = role,
                isSelected = state.form.role == role,
                onTap = { vm.selectRole(role) },
            )
        }
    }
}

// MARK: - Step 4

@Composable
private fun ReviewStep(state: AddHomeUiState) {
    HeadlineBlock("Review and submit")
    SubcopyBlock("Make sure everything below looks right before submitting.")
    val composedAddress =
        buildString {
            append(state.form.address.street)
            if (state.form.address.unit
                    .isNotEmpty()
            ) {
                append(", ${state.form.address.unit}")
            }
            append(", ${state.form.address.city}")
            append(", ${state.form.address.state} ${state.form.address.zipCode}")
        }
    ReviewSummaryBlock(
        rows =
            listOf(
                ReviewSummaryRow("Address", composedAddress),
                ReviewSummaryRow("Role", state.form.role?.label ?: "—"),
                ReviewSummaryRow("Primary", if (state.form.isPrimary) "Yes" else "No"),
            ),
    )
}

// MARK: - Step 5

@Composable
private fun SuccessStep() {
    // Reuse the T3.6 Status / Waiting body. The headline + subcopy
    // are the Add-Home variants of the success frame; everything
    // else (illustration, action cards, explainer bullets) is shared.
    StatusWaitingBody(
        content =
            StatusWaitingContent.claimSubmitted().copy(
                headline = "Home added",
                subcopy = "We'll email you when verification completes.",
            ),
    )
}

// MARK: - Helpers

@Composable
private fun AddHomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(
                    width = if (query.isEmpty()) 1.dp else 2.dp,
                    color = if (query.isEmpty()) PantopusColors.appBorder else PantopusColors.primary600,
                    shape = RoundedCornerShape(Radii.lg),
                ).padding(horizontal = Spacing.s3)
                .testTag("addHomeSearchField")
                .semantics {
                    contentDescription = "Search by address or nearby"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Search,
            contentDescription = null,
            size = 18.dp,
            tint = if (query.isEmpty()) PantopusColors.primary600 else PantopusColors.appTextSecondary,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
            cursorBrush = SolidColor(PantopusColors.primary600),
            modifier = Modifier.weight(1f).testTag("addHomeSearchInput"),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search by address or nearby…",
                        style = PantopusTextStyle.body,
                        color = PantopusColors.primary600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.appSurfaceSunken)
                        .clickable(role = Role.Button, onClick = onClear)
                        .testTag("addHome_clearSearch")
                        .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.X,
                    contentDescription = null,
                    size = 14.dp,
                    tint = PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun UseCurrentLocationPill(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.primary50)
                .border(1.dp, PantopusColors.primary100, RoundedCornerShape(Radii.pill))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3)
                .testTag("addHome_useCurrentLocation")
                .semantics { contentDescription = "Use current location" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.MapPin,
            contentDescription = null,
            size = 16.dp,
            tint = PantopusColors.primary700,
        )
        Text(
            text = "Use current location",
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.primary700,
            modifier = Modifier.padding(start = Spacing.s2),
        )
    }
}

@Composable
private fun NearbyHomesSection(
    homes: List<AddHomeAddressCandidate>,
    selectedHomeId: String?,
    onSelect: (AddHomeAddressCandidate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.MapPin,
                contentDescription = null,
                size = 12.dp,
                tint = PantopusColors.appTextSecondary,
            )
            Text(
                text = "Nearby · Brooklyn, NY",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextSecondary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            homes.forEach { home ->
                NearbyHomeRow(
                    home = home,
                    isSelected = selectedHomeId == home.id,
                    onSelect = { onSelect(home) },
                )
            }
        }
    }
}

@Composable
private fun NearbyHomeRow(
    home: AddHomeAddressCandidate,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(Radii.xl))
                .background(if (isSelected) PantopusColors.primary50 else PantopusColors.appSurface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.xl),
                ).clickable(enabled = !home.isClaimed, role = Role.Button, onClick = onSelect)
                .padding(Spacing.s3)
                .testTag("addHome_nearby_${home.id}")
                .semantics {
                    contentDescription = "${home.line1}, ${home.secondaryLine}, ${home.status.label}"
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(if (isSelected) PantopusColors.primary600 else PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Home,
                contentDescription = null,
                size = 18.dp,
                tint = if (isSelected) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = home.line1,
                style = PantopusTextStyle.body,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(
                    text = home.line2,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                home.distance?.let { distance ->
                    Text(
                        text = "·",
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextSecondary,
                    )
                    Text(
                        text = distance,
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }
        }
        StatusPill(status = home.status)
        if (isSelected) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = null,
                size = 16.dp,
                tint = PantopusColors.primary600,
            )
        }
    }
}

@Composable
private fun StatusPill(status: AddHomeAddressStatus) {
    Text(
        text = status.label,
        style = PantopusTextStyle.caption,
        fontWeight = FontWeight.SemiBold,
        color = if (status == AddHomeAddressStatus.Available) PantopusColors.success else PantopusColors.appTextSecondary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(
                    if (status == AddHomeAddressStatus.Available) {
                        PantopusColors.successBg
                    } else {
                        PantopusColors.appSurfaceSunken
                    },
                ).padding(horizontal = Spacing.s2, vertical = Spacing.s1),
    )
}

@Composable
private fun AddHomeAutocompleteDropdown(
    query: String,
    results: List<AddHomeAddressCandidate>,
    onSelect: (AddHomeAddressCandidate) -> Unit,
    onAddManually: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurfaceMuted)
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Search,
                contentDescription = null,
                size = 10.dp,
                tint = PantopusColors.appTextMuted,
            )
            Text(
                text = "${results.size} matches",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextMuted,
            )
        }
        results.forEachIndexed { index, candidate ->
            AutocompleteRow(
                candidate = candidate,
                query = query,
                onSelect = { onSelect(candidate) },
                modifier = Modifier.testTag("addHome_autocomplete_$index"),
            )
            HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorderSubtle)
        }
        ManualFallbackRow(onClick = onAddManually)
    }
}

@Composable
private fun AutocompleteRow(
    candidate: AddHomeAddressCandidate,
    query: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onSelect)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                .semantics {
                    contentDescription = "${candidate.line1}, ${candidate.secondaryLine}"
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.MapPin,
                contentDescription = null,
                size = 15.dp,
                tint = PantopusColors.appTextSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            HighlightedAddressText(value = candidate.line1, query = query)
            Text(
                text = candidate.secondaryLine,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PantopusIconImage(
            icon = PantopusIcon.ChevronRight,
            contentDescription = null,
            size = 14.dp,
            tint = PantopusColors.appTextMuted,
        )
    }
}

@Composable
private fun HighlightedAddressText(
    value: String,
    query: String,
) {
    val ranges = highlightRanges(value = value, query = query)
    val text =
        buildAnnotatedString {
            var cursor = 0
            ranges.forEach { range ->
                if (cursor < range.first) {
                    append(value.substring(cursor, range.first))
                }
                withStyle(SpanStyle(color = PantopusColors.appText, fontWeight = FontWeight.Bold)) {
                    append(value.substring(range.first, range.second))
                }
                cursor = range.second
            }
            if (cursor < value.length) {
                append(value.substring(cursor))
            }
        }
    Text(
        text = text,
        style = PantopusTextStyle.body,
        color = PantopusColors.appTextStrong,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun highlightRanges(
    value: String,
    query: String,
): List<Pair<Int, Int>> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()

    val normalizedValue = value.lowercase()
    val normalizedNeedle = needle.lowercase()
    val phraseIndex = normalizedValue.indexOf(normalizedNeedle)
    if (phraseIndex >= 0) {
        return listOf(phraseIndex to phraseIndex + needle.length)
    }

    val ranges = mutableListOf<Pair<Int, Int>>()
    normalizedNeedle
        .split(" ")
        .filter { it.isNotBlank() }
        .forEach { token ->
            var searchStart = 0
            while (searchStart < normalizedValue.length) {
                val index = normalizedValue.indexOf(token, searchStart)
                if (index < 0) break
                val range = index to index + token.length
                if (ranges.none { it.overlaps(range) }) {
                    ranges += range
                }
                searchStart = range.second
            }
        }
    return ranges.sortedBy { it.first }
}

private fun Pair<Int, Int>.overlaps(other: Pair<Int, Int>): Boolean = first < other.second && other.first < second

@Composable
private fun ManualFallbackRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(PantopusColors.primary50)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                .testTag("addHome_manualFallback")
                .semantics { contentDescription = "Add address manually" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Plus,
                contentDescription = null,
                size = 16.dp,
                tint = PantopusColors.primary600,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Add manually",
                style = PantopusTextStyle.body,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.primary700,
            )
            Text(
                text = "We'll geocode it and mail a verification code.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        PantopusIconImage(
            icon = PantopusIcon.ChevronRight,
            contentDescription = null,
            size = 16.dp,
            tint = PantopusColors.primary600,
        )
    }
}

@Composable
private fun ManualAddressButton(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.md))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Spacing.s1)
                .testTag("addHome_addAddressManually"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Plus,
            contentDescription = null,
            size = 14.dp,
            tint = PantopusColors.primary600,
        )
        Text(
            text = "Add address manually",
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.primary600,
        )
    }
}

@Composable
private fun AddressVerdictRow(check: app.pantopus.android.data.api.models.homes.CheckAddressResponse) {
    val verdict =
        if (check.exists) {
            Verdict(
                icon = PantopusIcon.AlertCircle,
                color = PantopusColors.warning,
                headline = "Already on Pantopus",
                subcopy = "Another household already has this address. We'll route you to a join flow next.",
            )
        } else {
            Verdict(
                icon = PantopusIcon.CheckCircle,
                color = PantopusColors.success,
                headline = "Looks good",
                subcopy = "We'll create a new household for this address.",
            )
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceMuted)
                .padding(Spacing.s3)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${verdict.headline}. ${verdict.subcopy}"
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(icon = verdict.icon, contentDescription = null, size = 20.dp, tint = verdict.color)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = verdict.headline, style = PantopusTextStyle.body, color = PantopusColors.appText)
            Text(
                text = verdict.subcopy,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

private data class Verdict(
    val icon: PantopusIcon,
    val color: androidx.compose.ui.graphics.Color,
    val headline: String,
    val subcopy: String,
)

@Composable
private fun PrimaryHomeToggle(
    isPrimary: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "This is my primary home",
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
            )
            Text(
                text = "Use this home for default mail and notifications.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        Switch(
            checked = isPrimary,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = PantopusColors.appTextInverse,
                    checkedTrackColor = PantopusColors.primary600,
                ),
            modifier = Modifier.testTag("addHome_primaryToggle"),
        )
    }
}

@Composable
private fun RoleRow(
    role: AddHomeRole,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .clickable(onClick = onTap, role = Role.RadioButton)
                .padding(Spacing.s3)
                .testTag("addHome_role_${role.name.lowercase()}")
                .semantics { contentDescription = role.label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        RadioCircle(isSelected = isSelected)
        Text(text = role.label, style = PantopusTextStyle.body, color = PantopusColors.appText)
    }
}

@Composable
private fun RadioCircle(isSelected: Boolean) {
    val borderColor = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(PantopusColors.appSurface)
                .border(width = 2.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600),
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.errorBg)
                .padding(Spacing.s3)
                .testTag("addHomeErrorBanner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 18.dp,
            tint = PantopusColors.error,
        )
        Text(
            text = message,
            style = PantopusTextStyle.caption,
            color = PantopusColors.error,
        )
    }
}
