@file:Suppress("PackageNaming", "LongMethod", "MagicNumber", "TooManyFunctions")

package app.pantopus.android.ui.screens.ceremonial_mail_open

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage

@Composable
fun CeremonialMailOpenScreen(
    onBack: () -> Unit = {},
    onWriteBack: (String) -> Unit = {},
    onOutcome: (CeremonialOutcomeCta) -> Unit = {},
    viewModel: CeremonialMailOpenViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isVoicePlaying by viewModel.isVoicePlaying.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("ceremonialMailOpen"),
    ) {
        TopBar(onBack = onBack)
        when (val current = state) {
            is CeremonialMailOpenUiState.Loading -> LoadingFrame()
            is CeremonialMailOpenUiState.Error ->
                ErrorFrame(message = current.message, onRetry = viewModel::load)
            is CeremonialMailOpenUiState.Loaded ->
                LoadedBody(
                    letter = current.letter,
                    phase = current.phase,
                    isVoicePlaying = isVoicePlaying,
                    onTapSeal = viewModel::startBreakingSeal,
                    onToggleVoice = viewModel::toggleVoicePlayback,
                    onWriteBack = {
                        viewModel.enterReplying()
                        onWriteBack(current.letter.sender.displayName)
                    },
                    onOutcome = onOutcome,
                )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("ceremonialMailOpenBackButton"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ChevronLeft,
                    contentDescription = "Back",
                    size = 22.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appText,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Letter",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(36.dp))
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
    }
}

@Composable
private fun LoadingFrame() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("ceremonialMailOpenLoading"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Shimmer(width = 360.dp, height = 140.dp, cornerRadius = 16.dp)
        Shimmer(width = 360.dp, height = 200.dp, cornerRadius = 16.dp)
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
                .padding(20.dp)
                .testTag("ceremonialMailOpenError"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 36.dp,
            strokeWidth = 2f,
            tint = PantopusColors.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Couldn't open this letter",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = message, fontSize = 13.sp, color = PantopusColors.appTextSecondary)
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp)
                    .height(36.dp)
                    .testTag("ceremonialMailOpenRetry"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
internal fun LoadedBody(
    letter: CeremonialMailLetter,
    phase: CeremonialMailPhase,
    isVoicePlaying: Boolean,
    onTapSeal: () -> Unit,
    onToggleVoice: () -> Unit,
    onWriteBack: () -> Unit,
    onOutcome: (CeremonialOutcomeCta) -> Unit,
) {
    // Seal-break animation — rotation + scale animate when the phase
    // moves from .sealed → .breaking. Paper opacity + slide-up
    // animate when phase reaches .open / .replying.
    val sealRotation by animateFloatAsState(
        targetValue = if (phase == CeremonialMailPhase.Breaking) 35f else 0f,
        animationSpec = tween(durationMillis = 550),
        label = "sealRotation",
    )
    val sealScale by animateFloatAsState(
        targetValue = if (phase == CeremonialMailPhase.Breaking) 0.05f else 1f,
        animationSpec = tween(durationMillis = 550),
        label = "sealScale",
    )
    val paperOpacity by animateFloatAsState(
        targetValue = if (phase == CeremonialMailPhase.Open || phase == CeremonialMailPhase.Replying) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "paperOpacity",
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxSize()) {
        if (phase == CeremonialMailPhase.Sealed || phase == CeremonialMailPhase.Breaking) {
            EnvelopeLayer(
                letter = letter,
                phase = phase,
                sealRotation = sealRotation,
                sealScale = sealScale,
                onTapSeal = onTapSeal,
            )
        } else {
            PaperLayer(
                letter = letter,
                phase = phase,
                isVoicePlaying = isVoicePlaying,
                opacity = paperOpacity,
                onToggleVoice = onToggleVoice,
                onWriteBack = onWriteBack,
                onOutcome = onOutcome,
            )
        }
    }
}

@Composable
private fun EnvelopeLayer(
    letter: CeremonialMailLetter,
    phase: CeremonialMailPhase,
    sealRotation: Float,
    sealScale: Float,
    onTapSeal: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.testTag("ceremonialMailEnvelope_${phase.key}"),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(letter.stationery.paperColor)
                    .clickable(onClick = onTapSeal)
                    .testTag("ceremonialMailSealCard"),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "FROM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = letter.ink.color.copy(alpha = 0.7f),
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = letter.sender.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = letter.ink.color,
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(letter.seal.color)
                        .rotate(sealRotation)
                        .scale(sealScale)
                        .testTag("ceremonialMailSealMedallion"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✷",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        if (phase == CeremonialMailPhase.Sealed) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(PantopusColors.primary50)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Send,
                    contentDescription = null,
                    size = 14.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.primary600,
                )
                Text(
                    text = "Tap the seal to open",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.primary700,
                )
            }
        }
    }
}

@Composable
private fun PaperLayer(
    letter: CeremonialMailLetter,
    phase: CeremonialMailPhase,
    isVoicePlaying: Boolean,
    opacity: Float,
    onToggleVoice: () -> Unit,
    onWriteBack: () -> Unit,
    onOutcome: (CeremonialOutcomeCta) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("ceremonialMailOpenContent"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SenderCard(letter.sender, modifier = Modifier.scale(if (opacity > 0f) opacity else 1f))
        PaperBody(letter)
        if (letter.voicePostscriptUri != null) {
            VoicePostscriptCard(isPlaying = isVoicePlaying, onToggle = onToggleVoice)
        }
        if (phase == CeremonialMailPhase.Replying) {
            WriteBackBanner(letter)
        }
        OutcomeRow(letter, onWriteBack = onWriteBack, onOutcome = onOutcome)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SenderCard(
    sender: CeremonialSenderCard,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("ceremonialMailSenderCard"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = sender.displayName.take(1).uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.primary700,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = sender.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            sender.handle?.let {
                Text(text = "@$it", fontSize = 12.sp, color = PantopusColors.appTextSecondary)
            }
        }
        sender.trustLabel?.let {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(PantopusColors.successBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = it.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.success,
                )
            }
        }
    }
}

@Composable
private fun PaperBody(letter: CeremonialMailLetter) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(letter.stationery.paperColor)
                .border(1.dp, letter.stationery.paperShadow, RoundedCornerShape(14.dp))
                .padding(20.dp)
                .testTag("ceremonialMailPaperBody"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = letter.subject,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = letter.ink.color,
        )
        letter.bodyParagraphs.forEach { paragraph ->
            Text(text = paragraph, fontSize = 14.sp, color = letter.ink.color)
        }
    }
}

@Composable
private fun VoicePostscriptCard(
    isPlaying: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(12.dp)
                .testTag("ceremonialMailVoicePostscript"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(PantopusColors.primary600),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = if (isPlaying) PantopusIcon.Check else PantopusIcon.Send,
                contentDescription = if (isPlaying) "Stop" else "Play",
                size = 16.dp,
                strokeWidth = 2.4f,
                tint = PantopusColors.appTextInverse,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Voice postscript",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Text(
                text = if (isPlaying) "Playing…" else "Tap to listen",
                fontSize = 11.sp,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun WriteBackBanner(letter: CeremonialMailLetter) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.primary50)
                .padding(12.dp)
                .testTag("ceremonialMailWriteBackBanner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Send,
            contentDescription = null,
            size = 16.dp,
            strokeWidth = 2f,
            tint = PantopusColors.primary600,
        )
        Text(
            text = "Opening compose for ${letter.sender.displayName}…",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.primary700,
        )
    }
}

@Composable
private fun OutcomeRow(
    letter: CeremonialMailLetter,
    onWriteBack: () -> Unit,
    onOutcome: (CeremonialOutcomeCta) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        letter.outcomeCtas.forEach { cta ->
            val isPrimary = cta.style == CeremonialOutcomeCta.Style.Primary
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isPrimary) PantopusColors.primary600 else PantopusColors.appSurface)
                        .border(
                            1.dp,
                            if (isPrimary) PantopusColors.primary600 else PantopusColors.appBorder,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable {
                            if (cta.id == "write_back") onWriteBack()
                            onOutcome(cta)
                        }
                        .testTag("ceremonialMailOutcome_${cta.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PantopusIconImage(
                        icon = cta.icon,
                        contentDescription = null,
                        size = 14.dp,
                        strokeWidth = 2f,
                        tint = if (isPrimary) PantopusColors.appTextInverse else PantopusColors.primary600,
                    )
                    Text(
                        text = cta.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrimary) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
                    )
                }
            }
        }
    }
}
