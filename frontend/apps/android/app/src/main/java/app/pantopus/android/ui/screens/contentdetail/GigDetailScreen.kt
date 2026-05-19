@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.theme.PantopusColors

@Composable
fun GigDetailScreen(
    onBack: () -> Unit = {},
    onOpenMessages: (app.pantopus.android.data.api.models.gigs.GigDto) -> Unit = {},
    viewModel: GigDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) { viewModel.load() }

    val openMessages: () -> Unit = {
        viewModel.gigSnapshot()?.let { onOpenMessages(it) }
    }

    ContentDetailShell(
        state = state,
        onBack = onBack,
        onPrimaryAction = { sheetVisible = true },
        onSecondaryAction = openMessages,
        onRetry = { viewModel.load() },
        onMessageCounterparty = openMessages,
    )

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState,
        ) {
            BidSheetContent(
                onSubmit = { amount, message ->
                    viewModel.placeBid(amount, message) { ok ->
                        if (ok) sheetVisible = false
                    }
                },
            )
        }
    }
}

@Composable
private fun BidSheetContent(onSubmit: (Double, String?) -> Unit) {
    var amountField by remember { mutableStateOf(TextFieldValue("")) }
    var messageField by remember { mutableStateOf(TextFieldValue("")) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Place a bid",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Text(
            text = "Tell the poster what you'd charge and add a short message about your approach.",
            fontSize = 13.sp,
            color = PantopusColors.appTextSecondary,
        )
        OutlinedTextField(
            value = amountField,
            onValueChange = { amountField = it },
            label = { Text("Amount") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = messageField,
            onValueChange = { messageField = it },
            label = { Text("Message (optional)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        val amount = amountField.text.toDoubleOrNull()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (amount != null && amount > 0) PantopusColors.primary600 else PantopusColors.appBorder)
                    .clickable(enabled = amount != null && amount > 0) {
                        amount?.let { onSubmit(it, messageField.text.takeIf { msg -> msg.isNotEmpty() }) }
                    }
                    .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Submit bid",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
