@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.contentdetail

import app.pantopus.android.data.api.models.payments.PaymentIntentSheetParamsDto

/**
 * Block 3D — tip status + one-shot event for the gig-detail tip flow. The
 * poster tips the worker; the VM creates the tip payment and asks the screen to
 * present PaymentSheet, then reconciles via refresh-status. Mirrors iOS
 * `GigDetailViewModel.TipStatus`.
 */
sealed interface TipStatus {
    data object Idle : TipStatus

    data object Sending : TipStatus

    data object Succeeded : TipStatus

    data object Canceled : TipStatus

    data class Failed(val message: String) : TipStatus
}

/** One-shot effect: present Stripe PaymentSheet for the created tip payment. */
sealed interface GigTipEvent {
    data class PresentTipSheet(val params: PaymentIntentSheetParamsDto) : GigTipEvent
}
