@file:Suppress("MagicNumber", "PackageNaming")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.contentdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

@Composable
fun InvoiceDetailScreen(
    onBack: () -> Unit = {},
    viewModel: InvoiceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) { viewModel.load() }

    ContentDetailShell(
        state = state,
        onBack = onBack,
        onPrimaryAction = { sheetVisible = true },
        onSecondaryAction = null,
        onRetry = { viewModel.load() },
        onMessageCounterparty = null,
    )

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState,
        ) {
            PaySheetStub(onDismiss = { sheetVisible = false })
        }
    }
}

@Composable
private fun PaySheetStub(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.ShieldCheck,
            contentDescription = null,
            size = 36.dp,
            tint = PantopusColors.primary600,
        )
        Text(
            text = "Stripe payment sheet",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Text(
            text =
                "The real payment flow hooks the existing two-intent + sensitive-action-guard plumbing. " +
                    "Stub for now.",
            fontSize = 13.sp,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.s1))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onDismiss)
                    .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Got it", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appTextInverse)
        }
        Spacer(modifier = Modifier.height(Spacing.s5))
    }
}
