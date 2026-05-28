@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.settings.payments.components

import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.screens.settings.payments.PaymentMethodBrand
import app.pantopus.android.ui.screens.settings.payments.PaymentMethodChip
import app.pantopus.android.ui.screens.settings.payments.PaymentsRowTrailing
import app.pantopus.android.ui.theme.PantopusColors

data class PaymentMethodRowModel(
    val rowIdentifier: String,
    val label: String,
    val trailing: PaymentsRowTrailing,
    val brand: PaymentMethodBrand? = null,
    val subtext: String? = null,
    val chip: PaymentMethodChip? = null,
    val labelColor: Color = PantopusColors.appText,
)
