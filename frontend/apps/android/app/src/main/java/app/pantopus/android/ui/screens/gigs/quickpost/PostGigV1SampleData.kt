@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.gigs.quickpost

import app.pantopus.android.ui.screens.gigs.GigsCategory
import java.time.LocalDateTime

object PostGigV1SampleData {
    const val MAX_PHOTOS = 6
    const val DESCRIPTION_MIN_LENGTH = 40
    const val DESCRIPTION_MAX_LENGTH = 600

    val referenceNow: LocalDateTime = LocalDateTime.of(2026, 5, 24, 12, 0)

    val filledForm =
        PostGigV1Form(
            category = GigsCategory.Moving,
            title = "Help moving a sofa up 3 flights",
            description =
                "Sleeper sofa from the curb up to apt 3B. Building has no elevator, the stairwell is wide " +
                    "but there's a tight corner on the 2nd-floor landing. Should take 30-45 min with two people. " +
                    "I'll buy pizza after.",
            price = "80",
            priceType = PostGigV1PriceType.Flat,
            scheduledAt = LocalDateTime.of(2026, 5, 30, 14, 0),
            location = "Pearl District · NW 11th & Johnson",
            photos =
                listOf(
                    PostGigV1Photo(id = "sofa", tone = PostGigV1PhotoTone.Sofa),
                    PostGigV1Photo(id = "stairs", tone = PostGigV1PhotoTone.Stairs),
                    PostGigV1Photo(id = "street", tone = PostGigV1PhotoTone.Street),
                ),
        )

    val validationErrorForm =
        PostGigV1Form(
            category = GigsCategory.Moving,
            title = "Sofa help",
            description = "Need help with sofa.",
            price = "",
            priceType = PostGigV1PriceType.Flat,
            scheduledAt = LocalDateTime.of(2026, 5, 12, 9, 0),
            location = "Pearl District · NW 11th & Johnson",
            photos = emptyList(),
        )

    val validationErrors =
        listOf(
            PostGigV1ValidationError(
                field = PostGigV1Field.Description,
                message = "Description must be at least $DESCRIPTION_MIN_LENGTH characters.",
            ),
            PostGigV1ValidationError(
                field = PostGigV1Field.Price,
                message = "Enter a price, or pick Free.",
            ),
            PostGigV1ValidationError(
                field = PostGigV1Field.DateTime,
                message = "Date is in the past. Pick a future time.",
            ),
        )

    val filledState = PostGigV1UiState.Content(form = filledForm)

    val validationErrorState =
        PostGigV1UiState.Content(
            form = validationErrorForm,
            validationErrors = validationErrors,
        )
}
