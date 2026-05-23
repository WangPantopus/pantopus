@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.support_trains.start_train

import app.pantopus.android.data.api.models.mail_compose.MailRecipientDto

/** Deterministic A12.11 fixtures for previews and snapshots. */
object StartSupportTrainSampleData {
    val verifiedNeighbor =
        MailRecipientDto(
            userId = "u_maya_patel",
            name = "Maya Patel",
            username = "maya",
            homeId = "home_elm_418",
            homeAddress = "418 Elm St, Apt 2",
            isVerified = true,
            homeMediaUrl = null,
            isOnPantopus = true,
        )

    const val VERIFIED_CONTEXT_NOTE: String =
        "Maya is home after knee surgery on the 12th. Meals, dog walks for Pixel, and rides to PT would help."

    const val INVITE_QUERY: String = "David Chen"
}
