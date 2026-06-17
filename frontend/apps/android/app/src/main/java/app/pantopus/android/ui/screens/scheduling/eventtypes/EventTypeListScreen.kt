@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.scheduling.eventtypes

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.shared.identity.IdentityOption
import app.pantopus.android.ui.screens.shared.identity.IdentitySwitcherPillRow
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val TOAST_MS = 1800L

object EventTypeListTags {
    const val PILLAR_PREFIX = "eventTypesPillar"
    const val CREATE = "eventTypesCreate"
    const val ROW_PREFIX = "eventTypeRow_"
    const val TOGGLE_PREFIX = "eventTypeToggle_"
}

@Composable
fun EventTypeListScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: EventTypeListViewModel = hiltViewModel(),
) {
    val pillar by viewModel.pillar.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shareRequest by viewModel.shareRequest.collectAsStateWithLifecycle()
    val copyRequest by viewModel.copyRequest.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val navRequest by viewModel.navRequest.collectAsStateWithLifecycle()
    val deactivatePrompt by viewModel.deactivatePrompt.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var toastText by remember { mutableStateOf<String?>(null) }

    // start() on first resume (load), refresh on subsequent resumes — so an event
    // type created/edited on the pushed editor reflects on return.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.start() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(navRequest) {
        navRequest?.let { route ->
            onNavigate(route)
            viewModel.navRequestConsumed()
        }
    }
    LaunchedEffect(shareRequest) {
        shareRequest?.let { url ->
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
            viewModel.shareRequestConsumed()
        }
    }
    LaunchedEffect(copyRequest) {
        copyRequest?.let { url ->
            clipboard.setText(AnnotatedString(url))
            toastText = "Link copied"
            viewModel.copyRequestConsumed()
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            toastText = it
            viewModel.toastConsumed()
        }
    }
    LaunchedEffect(toastText) {
        if (toastText != null) {
            delay(TOAST_MS)
            toastText = null
        }
    }

    val canEdit = (state as? EventTypeListUiState.Content)?.canEdit ?: true

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            EtTopBar(
                title = if (pillar == SchedulingPillar.Business) "Services" else "Event types",
                onBack = onBack,
                trailingIcon = PantopusIcon.Plus,
                trailingEnabled = canEdit,
                trailingContentDescription = "New event type",
                onTrailing = { onNavigate(viewModel.createRoute()) },
            )
            FilterHeader(pillar = pillar, tab = tab, onSelectPillar = viewModel::selectPillar, onSelectTab = viewModel::selectTab)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    EventTypeListUiState.Loading -> EventTypeListSkeleton()
                    is EventTypeListUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                    is EventTypeListUiState.Content ->
                        ContentBody(
                            state = s,
                            onOpen = { onNavigate(viewModel.editorRoute(it)) },
                            onCreate = { onNavigate(viewModel.createRoute()) },
                            onTemplate = viewModel::createFromTemplate,
                            onToggle = viewModel::toggleActive,
                            onCopy = viewModel::copyLink,
                            onShare = viewModel::share,
                            onDuplicate = viewModel::duplicate,
                            onHide = viewModel::hide,
                            onDelete = viewModel::delete,
                            onViewHidden = { viewModel.selectTab(EventTypeTab.Hidden) },
                        )
                }
            }
        }
        toastText?.let { Toast(text = it, modifier = Modifier.align(Alignment.TopCenter)) }
    }

    deactivatePrompt?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeactivate,
            title = { Text("This event type has upcoming bookings") },
            text = {
                Text(
                    "Delete isn't available while people are booked. Deactivate “${row.name}” instead — " +
                        "it stops new bookings and keeps the ones you have.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmDeactivate) { Text("Deactivate") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDeactivate) { Text("Keep active") } },
        )
    }
}

@Composable
private fun FilterHeader(
    pillar: SchedulingPillar,
    tab: EventTypeTab,
    onSelectPillar: (SchedulingPillar) -> Unit,
    onSelectTab: (EventTypeTab) -> Unit,
) {
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(pillar.accentBg, PantopusColors.appSurface)))
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                IdentitySwitcherPillRow(
                    options =
                        listOf(
                            IdentityOption("personal", "Personal", PantopusIcon.User, SchedulingPillar.Personal.accent),
                            IdentityOption("home", "Home", PantopusIcon.Home, SchedulingPillar.Home.accent),
                            IdentityOption("business", "Business", PantopusIcon.Briefcase, SchedulingPillar.Business.accent),
                        ),
                    activeId =
                        when (pillar) {
                            SchedulingPillar.Personal -> "personal"
                            SchedulingPillar.Home -> "home"
                            SchedulingPillar.Business -> "business"
                        },
                    onSelect = { id ->
                        onSelectPillar(
                            when (id) {
                                "home" -> SchedulingPillar.Home
                                "business" -> SchedulingPillar.Business
                                else -> SchedulingPillar.Personal
                            },
                        )
                    },
                    identifierPrefix = EventTypeListTags.PILLAR_PREFIX,
                )
                EtSegmented(
                    options = EventTypeTab.entries.map { it.label },
                    selected = tab.label,
                    onSelect = { label -> EventTypeTab.entries.firstOrNull { it.label == label }?.let(onSelectTab) },
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorder)
    }
}

@Composable
internal fun ContentBody(
    state: EventTypeListUiState.Content,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onTemplate: (Int) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onHide: (String) -> Unit,
    onDelete: (String) -> Unit,
    onViewHidden: () -> Unit,
) {
    if (state.rows.isEmpty()) {
        when {
            state.activeCount == 0 && state.hiddenCount == 0 ->
                EventTypesEmptyTemplates(onCreate = onCreate, onTemplate = onTemplate)
            state.tab == EventTypeTab.Active ->
                EventTypesAllHidden(onViewHidden = onViewHidden)
            else ->
                EventTypesNothingHidden()
        }
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        EtSectionOverline(
            text = if (state.pillar == SchedulingPillar.Business) "Bookable services" else "Your event types",
            accent = state.pillar.accent,
            modifier = Modifier.padding(start = Spacing.s1, bottom = Spacing.s1),
        )
        state.rows.forEach { row ->
            EventTypeRowCard(
                row = row,
                canEdit = state.canEdit,
                onOpen = { onOpen(row.id) },
                onToggle = { onToggle(row.id, it) },
                onCopy = { onCopy(row.id) },
                onShare = { onShare(row.id) },
                onDuplicate = { onDuplicate(row.id) },
                onHide = { onHide(row.id) },
                onDelete = { onDelete(row.id) },
            )
        }
        Spacer(Modifier.height(Spacing.s6))
    }
}

@Composable
private fun EventTypeRowCard(
    row: EventTypeRowUi,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDuplicate: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ROW_RADIUS))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(ROW_RADIUS))
                .clickable(onClick = onOpen)
                .testTag("${EventTypeListTags.ROW_PREFIX}${row.id}")
                .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        EtColorDot(color = eventDotColor(row.colorHex, row.id))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(
                    text = row.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.isActive) PantopusColors.appText else PantopusColors.appTextStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.isSecret) {
                    PantopusIconImage(
                        icon = PantopusIcon.EyeOff,
                        contentDescription = "Unlisted",
                        size = 11.dp,
                        tint = PantopusColors.appTextMuted,
                    )
                }
            }
            val sub = listOfNotNull(row.meta.takeIf { it.isNotEmpty() }, row.priceLabel).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        EtToggle(
            checked = row.isActive,
            onToggle = onToggle,
            enabled = canEdit,
            modifier = Modifier.testTag("${EventTypeListTags.TOGGLE_PREFIX}${row.id}"),
        )
        Box {
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(Radii.md)).clickable(enabled = canEdit) { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.MoreVertical,
                    contentDescription = "More",
                    size = 17.dp,
                    tint = if (canEdit) PantopusColors.appTextSecondary else PantopusColors.appTextMuted,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                OverflowItem(PantopusIcon.Link, "Copy booking link") {
                    menuOpen = false
                    onCopy()
                }
                OverflowItem(PantopusIcon.Copy, "Duplicate") {
                    menuOpen = false
                    onDuplicate()
                }
                OverflowItem(PantopusIcon.Share, "Share") {
                    menuOpen = false
                    onShare()
                }
                OverflowItem(PantopusIcon.EyeOff, "Hide") {
                    menuOpen = false
                    onHide()
                }
                HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorder)
                OverflowItem(PantopusIcon.Trash2, "Delete", danger = true) {
                    menuOpen = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun OverflowItem(
    icon: PantopusIcon,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 15.dp,
                tint = if (danger) PantopusColors.error else PantopusColors.appTextSecondary,
            )
        },
        text = {
            Text(text = label, fontSize = 12.5.sp, color = if (danger) PantopusColors.error else PantopusColors.appText)
        },
        onClick = onClick,
    )
}

// ─── Empty states ───────────────────────────────────────────────────────────

@Composable
private fun EventTypesEmptyTemplates(
    onCreate: () -> Unit,
    onTemplate: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(Brush.linearGradient(listOf(PantopusColors.primary50, PantopusColors.primary100))),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.CalendarPlus, contentDescription = null, size = 36.dp, tint = PantopusColors.primary600)
        }
        Spacer(Modifier.height(Spacing.s4))
        Text(
            "You don't have any event types yet",
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        Spacer(Modifier.height(Spacing.s2))
        Text(
            "An event type is something people can book — a call, a meeting, a visit. Start from a template or build your own.",
            fontSize = 12.sp,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(horizontal = Spacing.s2),
        )
        Spacer(Modifier.height(Spacing.s4))
        EtPrimaryButton(
            label = "Create your first event type",
            onClick = onCreate,
            leadingIcon = PantopusIcon.Plus,
            modifier = Modifier.testTag(EventTypeListTags.CREATE),
        )
        Spacer(Modifier.height(Spacing.s4))
        Text(
            "START FROM A TEMPLATE",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextMuted,
        )
        Spacer(Modifier.height(Spacing.s2))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            listOf(15, 30, 60).forEach { mins ->
                EtQuickChip(label = "$mins min", onClick = { onTemplate(mins) })
            }
        }
    }
}

@Composable
private fun EventTypesAllHidden(onViewHidden: () -> Unit) {
    CalmEmpty(
        icon = PantopusIcon.EyeOff,
        headline = "Everything's hidden",
        subcopy = "Switch to Hidden to bring one back, or create a new event type.",
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .clickable(onClick = onViewHidden)
                    .padding(horizontal = Spacing.s4, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text("View hidden", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.primary700)
            PantopusIconImage(icon = PantopusIcon.ArrowRight, contentDescription = null, size = 13.dp, tint = PantopusColors.primary700)
        }
    }
}

@Composable
private fun EventTypesNothingHidden() {
    CalmEmpty(
        icon = PantopusIcon.EyeOff,
        headline = "Nothing hidden",
        subcopy = "Event types you hide will show up here.",
    )
}

@Composable
private fun CalmEmpty(
    icon: PantopusIcon,
    headline: String,
    subcopy: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(Radii.pill)).background(PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = icon, contentDescription = null, size = 26.dp, tint = PantopusColors.appTextMuted)
        }
        Spacer(Modifier.height(Spacing.s4))
        Text(headline, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
        Spacer(Modifier.height(Spacing.s2))
        Text(subcopy, fontSize = 12.sp, color = PantopusColors.appTextSecondary, modifier = Modifier.padding(horizontal = Spacing.s2))
        if (action != null) {
            Spacer(Modifier.height(Spacing.s4))
            action()
        }
    }
}

// ─── Loading skeleton ───────────────────────────────────────────────────────

@Composable
private fun EventTypeListSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Shimmer(width = 88.dp, height = 9.dp, cornerRadius = Radii.sm)
        repeat(3) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ROW_RADIUS))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(ROW_RADIUS))
                        .padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Shimmer(width = 7.dp, height = 7.dp, cornerRadius = Radii.pill)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Shimmer(width = 140.dp, height = 11.dp, cornerRadius = Radii.xs)
                    Shimmer(width = 96.dp, height = 9.dp, cornerRadius = Radii.xs)
                }
                Shimmer(width = 36.dp, height = 20.dp, cornerRadius = Radii.pill)
            }
        }
    }
}

// ─── Transient toast pill ───────────────────────────────────────────────────

@Composable
private fun Toast(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(top = Spacing.s12)
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.appText)
                .padding(horizontal = Spacing.s4, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(icon = PantopusIcon.Check, contentDescription = null, size = 15.dp, tint = PantopusColors.success)
        Text(text = text, color = PantopusColors.appTextInverse, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
