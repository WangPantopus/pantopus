@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.contentdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import coil.compose.AsyncImage

/**
 * T2.6 Transactional Detail shell — single canvas shared by gig,
 * listing, and invoice variants. Per-entity view-models project their
 * payload into a [ContentDetailContent]; the shell paints the slots.
 */
@Composable
fun ContentDetailShell(
    state: ContentDetailUiState,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit = {},
    onSecondaryAction: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
    onMessageCounterparty: (() -> Unit)? = null,
    overflowItems: List<ContentDetailOverflowItem> = emptyList(),
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurface)
                .testTag("contentDetailShell"),
    ) {
        when (state) {
            is ContentDetailUiState.Loading -> LoadingFrame(onBack = onBack, overflowItems = overflowItems)
            is ContentDetailUiState.Error ->
                ErrorFrame(
                    message = state.message,
                    onBack = onBack,
                    onRetry = onRetry,
                    overflowItems = overflowItems,
                )
            is ContentDetailUiState.Loaded ->
                LoadedFrame(
                    content = state.content,
                    onBack = onBack,
                    onPrimaryAction = onPrimaryAction,
                    onSecondaryAction = onSecondaryAction,
                    onMessageCounterparty = onMessageCounterparty,
                    overflowItems = overflowItems,
                )
        }
    }
}

@Composable
private fun LoadingFrame(
    onBack: () -> Unit,
    overflowItems: List<ContentDetailOverflowItem>,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(onBack = onBack, transparent = false, overflowItems = overflowItems)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PantopusColors.primary600)
        }
    }
}

@Composable
private fun ErrorFrame(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    overflowItems: List<ContentDetailOverflowItem>,
) {
    Column(modifier = Modifier.fillMaxSize().testTag("contentDetailError")) {
        TopNav(onBack = onBack, transparent = false, overflowItems = overflowItems)
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.s6),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PantopusIconImage(icon = PantopusIcon.AlertCircle, contentDescription = null, size = 40.dp, tint = PantopusColors.error)
            Spacer(modifier = Modifier.height(Spacing.s3))
            Text(
                text = "Couldn't load detail",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message, fontSize = 13.5.sp, color = PantopusColors.appTextSecondary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(Spacing.s4))
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary600)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 22.dp)
                        .heightIn(min = 44.dp)
                        .testTag("contentDetailRetry"),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Try again", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appTextInverse)
            }
        }
    }
}

@Composable
private fun LoadedFrame(
    content: ContentDetailContent,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: (() -> Unit)?,
    onMessageCounterparty: (() -> Unit)?,
    overflowItems: List<ContentDetailOverflowItem>,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            content.cover?.let { CoverImage(it) }
            if (content.cover == null) {
                TopNav(onBack = onBack, transparent = false, overflowItems = overflowItems)
            }
            HeroBlock(content = content)
            if (content.statStrip.isNotEmpty()) {
                StatStrip(content.statStrip)
                    .also { /* spacer below */ }
            }
            content.counterparty?.let { party ->
                Spacer(modifier = Modifier.height(18.dp))
                CounterpartyCard(party = party, onMessage = onMessageCounterparty)
            }
            content.modules.forEach { module ->
                Spacer(modifier = Modifier.height(22.dp))
                ModuleView(module = module)
            }
            if (content.trustCapsules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.s5))
                TrustCapsuleWrap(content.trustCapsules)
            }
            Spacer(modifier = Modifier.height(120.dp))
        }
        if (content.cover != null) {
            TopNav(onBack = onBack, transparent = true, overflowItems = overflowItems)
        }
        StickyDock(
            dock = content.dock,
            onPrimary = onPrimaryAction,
            onSecondary = onSecondaryAction,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// MARK: - Top nav

@Composable
private fun TopNav(
    onBack: () -> Unit,
    transparent: Boolean,
    overflowItems: List<ContentDetailOverflowItem> = emptyList(),
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (transparent) Color.Transparent else PantopusColors.appSurface)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (transparent) Color.White.copy(alpha = 0.85f) else Color.Transparent)
                    .clickable(onClick = onBack)
                    .testTag("contentDetailBackButton"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = "Back",
                size = 20.dp,
                strokeWidth = 2.2f,
                tint = PantopusColors.appText,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (overflowItems.isNotEmpty()) {
            OverflowMenu(items = overflowItems, transparent = transparent)
        }
    }
}

@Composable
private fun OverflowMenu(
    items: List<ContentDetailOverflowItem>,
    transparent: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (transparent) Color.White.copy(alpha = 0.85f) else Color.Transparent)
                    .clickable { expanded = true }
                    .testTag("contentDetailOverflowMenu")
                    .semantics { contentDescription = "More actions" },
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.MoreVertical,
                contentDescription = null,
                size = 20.dp,
                strokeWidth = 2.2f,
                tint = PantopusColors.appText,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                    modifier = Modifier.testTag(item.testTag),
                )
            }
        }
    }
}

// MARK: - Cover

@Composable
private fun CoverImage(cover: ContentDetailCover) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.linearGradient(colors = listOf(cover.gradient.start, cover.gradient.end)),
                )
                .testTag("contentDetailCover"),
        contentAlignment = Alignment.Center,
    ) {
        if (!cover.imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = cover.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            PantopusIconImage(
                icon = cover.placeholderIcon,
                contentDescription = null,
                size = 56.dp,
                strokeWidth = 1.6f,
                tint = Color.White.copy(alpha = 0.85f),
            )
        }
        if (cover.pageCount > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(cover.pageCount) { i ->
                    Box(
                        modifier =
                            Modifier
                                .width(if (i == cover.activePage) 18.dp else 5.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (i == cover.activePage) Color.White else Color.White.copy(alpha = 0.6f),
                                ),
                    )
                }
            }
        }
    }
}

// MARK: - Hero

@Composable
private fun HeroBlock(content: ContentDetailContent) {
    Column(modifier = Modifier.fillMaxWidth()) {
        content.statusPill?.let {
            Row(
                modifier = Modifier.padding(start = Spacing.s5, top = Spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) { PillView(it) }
        }
        content.hero.monoId?.let {
            Text(
                text = it,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier.padding(start = Spacing.s5, top = 10.dp),
            )
        }
        Text(
            text = content.hero.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(horizontal = Spacing.s5)
                    .padding(top = if (content.hero.monoId == null) 4.dp else 6.dp)
                    .semantics { heading() },
        )
        if (content.hero.categoryChip != null || content.hero.meta != null) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.s5, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                content.hero.categoryChip?.let { chip ->
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(Radii.pill))
                                .background(chip.category.color.copy(alpha = 0.12f))
                                .padding(horizontal = Spacing.s2, vertical = 2.dp),
                    ) {
                        Text(
                            text = chip.label.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = chip.category.color,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }
                content.hero.meta?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }
        }
        content.hero.priceLine?.let { price ->
            Row(
                modifier = Modifier.padding(horizontal = Spacing.s5, vertical = Spacing.s2),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Text(
                    text = price,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color =
                        if (content.kind == ContentDetailKind.Listing) {
                            PantopusColors.primary600
                        } else {
                            PantopusColors.appText
                        },
                )
                content.hero.priceCaption?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PillView(pill: ContentDetailPill) {
    val fg =
        when (pill.tone) {
            ContentDetailPill.Tone.Info -> PantopusColors.primary700
            ContentDetailPill.Tone.Success -> PantopusColors.success
            ContentDetailPill.Tone.Warning -> PantopusColors.warning
            ContentDetailPill.Tone.Business -> PantopusColors.business
            ContentDetailPill.Tone.Neutral -> PantopusColors.appTextSecondary
        }
    val bg =
        when (pill.tone) {
            ContentDetailPill.Tone.Info -> PantopusColors.primary50
            ContentDetailPill.Tone.Success -> PantopusColors.successBg
            ContentDetailPill.Tone.Warning -> PantopusColors.warningBg
            ContentDetailPill.Tone.Business -> PantopusColors.businessBg
            ContentDetailPill.Tone.Neutral -> PantopusColors.appSurfaceSunken
        }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        pill.icon?.let {
            PantopusIconImage(icon = it, contentDescription = null, size = 11.dp, strokeWidth = 2.4f, tint = fg)
        }
        Text(
            text = pill.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

// MARK: - Stat strip

@Composable
private fun StatStrip(stats: List<ContentDetailStat>) {
    Row(
        modifier =
            Modifier
                .padding(horizontal = Spacing.s5)
                .padding(top = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurfaceSunken)
                .padding(10.dp)
                .testTag("contentDetailStatStrip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, stat ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stat.top, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = stat.bottom, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = PantopusColors.appTextSecondary)
            }
            if (index < stats.size - 1) {
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color.Black.copy(alpha = 0.10f)),
                )
            }
        }
    }
}

// MARK: - Counterparty

@Composable
private fun CounterpartyCard(
    party: ContentDetailCounterparty,
    onMessage: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .padding(horizontal = Spacing.s5)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PantopusColors.appSurfaceSunken)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(14.dp))
                .padding(Spacing.s3)
                .testTag("contentDetailCounterparty"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        AvatarView(initials = party.initials, verified = party.verified, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = party.displayName,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                party.identityKind?.let { IdentityChip(it) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                party.rating?.let {
                    PantopusIconImage(icon = PantopusIcon.Star, contentDescription = null, size = 10.dp, tint = PantopusColors.warning)
                    Text(
                        text = String.format("%.1f", it),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = PantopusColors.appTextSecondary,
                    )
                }
                party.trailing?.let {
                    val prefix = if (party.rating != null) " · " else ""
                    Text(text = "$prefix$it", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = PantopusColors.appTextSecondary)
                }
            }
        }
        if (party.showsMessageButton && onMessage != null) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, CircleShape)
                        .clickable(onClick = onMessage)
                        .semantics { contentDescription = "Message" },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Send,
                    contentDescription = null,
                    size = 14.dp,
                    strokeWidth = 2.2f,
                    tint = PantopusColors.appTextStrong,
                )
            }
        }
    }
}

@Composable
private fun IdentityChip(kind: String) {
    val isBusiness = kind == "business"
    val label = if (isBusiness) "BUSINESS" else "PERSONAL"
    val fg = if (isBusiness) PantopusColors.business else PantopusColors.primary700
    val bg = if (isBusiness) PantopusColors.businessBg else PantopusColors.primary50
    val icon = if (isBusiness) PantopusIcon.ShieldCheck else PantopusIcon.User
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 8.dp, strokeWidth = 2.6f, tint = fg)
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = fg, letterSpacing = 0.6.sp)
    }
}

// MARK: - Modules

@Composable
private fun ModuleView(module: ContentDetailModule) {
    when (module) {
        is ContentDetailModule.Description ->
            SectionCard(title = module.title, icon = module.icon) {
                Text(
                    text = module.body,
                    fontSize = 13.5.sp,
                    color = PantopusColors.appTextStrong,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        is ContentDetailModule.DetailRow ->
            SectionCard(title = module.title, icon = module.sectionIcon) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(PantopusColors.appSurfaceSunken)
                            .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = Spacing.s3, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    PantopusIconImage(icon = module.rowIcon, contentDescription = null, size = 14.dp, tint = PantopusColors.primary600)
                    Text(text = module.label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
                    Spacer(modifier = Modifier.weight(1f))
                    module.trailing?.let {
                        Text(text = it, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PantopusColors.appTextSecondary)
                    }
                }
            }
        is ContentDetailModule.CaptionedText ->
            SectionCard(title = module.title, icon = module.icon) {
                Text(
                    text = module.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = PantopusColors.appTextStrong,
                )
            }
        is ContentDetailModule.PhotoStrip ->
            SectionCard(title = module.title, icon = module.icon, sub = module.countLabel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    module.tiles.forEach { tile ->
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(colors = listOf(tile.gradient.start, tile.gradient.end)),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            PantopusIconImage(
                                icon = tile.icon,
                                contentDescription = null,
                                size = 24.dp,
                                strokeWidth = 1.8f,
                                tint = Color.White.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }
        is ContentDetailModule.SimilarStrip ->
            SectionCard(title = module.title, icon = null, sub = module.sub) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items = module.items, key = { it.id }) { item ->
                        Column(modifier = Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(colors = listOf(item.gradient.start, item.gradient.end)),
                                        ),
                            )
                            Text(
                                text = item.title,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PantopusColors.appText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.price,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PantopusColors.primary600,
                            )
                        }
                    }
                }
            }
        is ContentDetailModule.Bids ->
            SectionCard(title = module.title, icon = null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PantopusColors.appSurface)
                            .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp)),
                ) {
                    module.bids.forEachIndexed { index, bid ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AvatarView(initials = bid.initials, verified = bid.verified, size = 36.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bid.displayName,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PantopusColors.appText,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                                    PantopusIconImage(
                                        icon = PantopusIcon.Star,
                                        contentDescription = null,
                                        size = 9.dp,
                                        tint = PantopusColors.warning,
                                    )
                                    Text(
                                        text = bid.ratingLine,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PantopusColors.appTextSecondary,
                                    )
                                }
                            }
                            Text(
                                text = bid.amount,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PantopusColors.primary600,
                            )
                        }
                        if (index < module.bids.size - 1) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder.copy(alpha = 0.5f)))
                        }
                    }
                }
            }
        is ContentDetailModule.FromTo ->
            Row(
                modifier = Modifier.padding(horizontal = Spacing.s5).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                PartyCard(party = module.from, modifier = Modifier.weight(1f))
                PartyCard(party = module.to, modifier = Modifier.weight(1f))
            }
        is ContentDetailModule.LineItems ->
            SectionCard(title = module.title, icon = module.icon) {
                LineItemsTable(rows = module.rows)
            }
        is ContentDetailModule.Summary ->
            SummaryCard(summary = module, modifier = Modifier.padding(horizontal = Spacing.s5))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: PantopusIcon?,
    sub: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s5).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon?.let { PantopusIconImage(icon = it, contentDescription = null, size = 13.dp, tint = PantopusColors.appTextSecondary) }
            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextSecondary,
                letterSpacing = 1.2.sp,
            )
            sub?.let { Text(text = "· $it", fontSize = 10.sp, color = PantopusColors.appTextMuted) }
        }
        Spacer(modifier = Modifier.height(Spacing.s2))
        content()
    }
}

@Composable
private fun PartyCard(
    party: ContentDetailParty,
    modifier: Modifier = Modifier,
) {
    val accent =
        when (party.accent) {
            ContentDetailParty.Accent.Business -> PantopusColors.business
            ContentDetailParty.Accent.Personal -> PantopusColors.primary600
            ContentDetailParty.Accent.Neutral -> PantopusColors.appTextSecondary
        }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .padding(Spacing.s3),
    ) {
        Text(
            text = party.label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextMuted,
            letterSpacing = 1.2.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = party.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
        Spacer(modifier = Modifier.height(Spacing.s1))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(text = party.sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = accent)
        }
    }
}

@Composable
private fun LineItemsTable(rows: List<ContentDetailLineItem>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        ) {
            Text(
                text = "ITEM",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "QTY",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp),
            )
            Text(
                text = "UNIT",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp),
            )
            Text(
                text = "TOTAL",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp),
            )
        }
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.item,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = PantopusColors.appText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.qty,
                    fontSize = 12.sp,
                    color = PantopusColors.appTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp),
                )
                Text(
                    text = row.unit,
                    fontSize = 12.sp,
                    color = PantopusColors.appTextSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(60.dp),
                )
                Text(
                    text = row.total,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(60.dp),
                )
            }
            if (index < rows.size - 1) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: ContentDetailModule.Summary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurfaceSunken)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        summary.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = row.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PantopusColors.appTextStrong)
                Text(text = row.value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PantopusColors.appTextStrong)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = summary.totalLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
            Text(text = summary.totalValue, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PantopusColors.primary600)
        }
    }
}

// MARK: - Trust + dock + avatar

@Composable
private fun TrustCapsuleWrap(capsules: List<ContentDetailPill>) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            Modifier
                .padding(horizontal = Spacing.s5)
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        capsules.forEach { PillView(it) }
    }
}

@Composable
private fun StickyDock(
    dock: ContentDetailDock,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface.copy(alpha = 0.97f))
                .testTag("contentDetailDock"),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s3, bottom = Spacing.s6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            dock.secondary?.let { secondary ->
                Row(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PantopusColors.appSurface)
                            .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                            .clickable { onSecondary?.invoke() }
                            .padding(horizontal = 18.dp)
                            .heightIn(min = 48.dp)
                            .testTag("contentDetailDockSecondary"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    secondary.icon?.let {
                        PantopusIconImage(
                            icon = it,
                            contentDescription = null,
                            size = 15.dp,
                            strokeWidth = 2.2f,
                            tint = PantopusColors.appText,
                        )
                    }
                    Text(text = secondary.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
                }
            }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PantopusColors.primary600)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp))
                        .clickable(onClick = onPrimary)
                        .heightIn(min = 48.dp)
                        .testTag("contentDetailDockPrimary"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                dock.primary.icon?.let {
                    PantopusIconImage(
                        icon = it,
                        contentDescription = null,
                        size = 16.dp,
                        strokeWidth = 2.2f,
                        tint = PantopusColors.appTextInverse,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = dock.primary.label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appTextInverse)
            }
        }
    }
}

@Composable
private fun AvatarView(
    initials: String,
    verified: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(modifier = Modifier.size(size + 4.dp), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(PantopusColors.primary500),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
        if (verified) {
            Box(
                modifier =
                    Modifier
                        .size(size * 0.36f)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600)
                        .border(2.dp, PantopusColors.appSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Check,
                    contentDescription = null,
                    size = size * 0.18f,
                    strokeWidth = 3f,
                    tint = PantopusColors.appTextInverse,
                )
            }
        }
    }
}
