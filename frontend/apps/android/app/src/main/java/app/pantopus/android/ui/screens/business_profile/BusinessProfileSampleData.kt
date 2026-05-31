@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.business_profile

import app.pantopus.android.ui.theme.PantopusIcon

/**
 * A10.6 — the two hand-authored design frames used by previews, the
 * Paparazzi snapshots, and iOS parity: [populated] (Marlow & Co.
 * Cleaning — open, verified) and [newlyClaimed] (Tide Pool Pet Care —
 * newly-claimed + closed, with `EmptyBlock`s for the unfilled sections).
 *
 * Design reference: `docs/designs/A10/business-frames.jsx`.
 */
object BusinessProfileSampleData {
    val populated: BusinessProfileContent =
        BusinessProfileContent(
            businessId = "marlow",
            header =
                BusinessProfileHeader(
                    displayName = "Marlow & Co. Cleaning",
                    handle = "marlowco",
                    locality = "Elm Park",
                    isVerified = true,
                    logoIcon = PantopusIcon.Sparkles,
                ),
            stats =
                listOf(
                    BusinessStatCell("rating", "4.9", "128 reviews", leadingStar = true, tint = BusinessStatTint.Star),
                    BusinessStatCell("jobs", "340", "Jobs done"),
                    BusinessStatCell("response", "~20m", "Response"),
                ),
            categories =
                listOf(
                    BusinessCategoryChip("cleaning", "Cleaning", PantopusIcon.Sparkles, BusinessCategoryAccent.Cleaning),
                    BusinessCategoryChip("home", "Home & apartment", PantopusIcon.Home, BusinessCategoryAccent.Neutral),
                    BusinessCategoryChip("moveout", "Move-out", PantopusIcon.Package, BusinessCategoryAccent.Neutral),
                    BusinessCategoryChip("eco", "Eco products", PantopusIcon.Leaf, BusinessCategoryAccent.Neutral),
                ),
            about =
                "Family-run cleaning crew that's worked Elm Park homes since 2019. " +
                    "Two-person teams, your own checklist, same crew each visit. We bring " +
                    "eco-safe supplies — you don't stock a thing. Bonded and insured.",
            aboutChips =
                listOf(
                    BusinessAboutChip("bonded", "Bonded & insured", PantopusIcon.Shield),
                    BusinessAboutChip("team", "3 team members", PantopusIcon.Users),
                    BusinessAboutChip("since", "Since 2019", PantopusIcon.CalendarCheck),
                ),
            status =
                BusinessOpenState(
                    isOpen = true,
                    statusLabel = "Open now",
                    statusDetail = "Closes 6:00 PM",
                    chipLabel = "Open now",
                ),
            hours =
                listOf(
                    BusinessHoursRow("mon", "Monday", "8:00 AM – 6:00 PM", isClosed = false, isToday = true),
                    BusinessHoursRow("tue", "Tuesday", "8:00 AM – 6:00 PM", isClosed = false),
                    BusinessHoursRow("wed", "Wednesday", "8:00 AM – 6:00 PM", isClosed = false),
                    BusinessHoursRow("thu", "Thursday", "8:00 AM – 6:00 PM", isClosed = false),
                    BusinessHoursRow("fri", "Friday", "8:00 AM – 5:00 PM", isClosed = false),
                    BusinessHoursRow("sat", "Saturday", "9:00 AM – 2:00 PM", isClosed = false),
                    BusinessHoursRow("sun", "Sunday", "Closed", isClosed = true),
                ),
            serviceArea =
                BusinessServiceArea(
                    title = "Based near 5th & Birch",
                    detail = "Exact address shared after booking",
                    serviceArea = "Serves Elm Park & Cedar Heights — within 4 mi",
                    latitude = 42.37,
                    longitude = -71.11,
                ),
            services =
                listOf(
                    BusinessServiceRow(
                        "standard", "Standard clean", "2 hr · 2-person team",
                        "from $90", "per visit", PantopusIcon.Droplets,
                    ),
                    BusinessServiceRow(
                        "deep", "Deep clean", "4 hr · baseboards, inside oven",
                        "from $180", "per visit", PantopusIcon.Sparkles,
                    ),
                    BusinessServiceRow(
                        "moveout", "Move-out clean", "Empty home · deposit-ready",
                        "from $240", "flat", PantopusIcon.Package,
                    ),
                ),
            gallery =
                listOf(
                    BusinessGalleryItem("kitchen", "Kitchen", BusinessGalleryTint.Primary),
                    BusinessGalleryItem("bath", "Bathroom", BusinessGalleryTint.Success),
                    BusinessGalleryItem("living", "Living room", BusinessGalleryTint.Slate),
                    BusinessGalleryItem("more", null, BusinessGalleryTint.Deep, moreCount = 9),
                ),
            reviewSummary =
                BusinessReviewSummary(
                    average = 4.9,
                    count = 128,
                    distribution = listOf(0.92, 0.06, 0.02, 0.0, 0.0),
                ),
            reviews =
                listOf(
                    BusinessReviewCard(
                        id = "jt",
                        reviewerName = "Jamal T.",
                        reviewerAvatarUrl = null,
                        rating = 5,
                        body =
                            "Same two folks every time, which I love. They remember the dog and " +
                                "shut the gate. Place smells like nothing, which is exactly right.",
                        timestamp = "1w · Standard clean",
                        verified = true,
                    ),
                ),
            dock = BusinessActionDock(BusinessActionDock.Secondary.Book, note = null),
            isNewlyClaimed = false,
            phoneNumber = "+15555550100",
            websiteUrl = null,
            viewerIsOwner = false,
        )

    val newlyClaimed: BusinessProfileContent =
        BusinessProfileContent(
            businessId = "tidepool",
            header =
                BusinessProfileHeader(
                    displayName = "Tide Pool Pet Care",
                    handle = "tidepoolpets",
                    locality = "Cedar Heights",
                    isVerified = true,
                    logoIcon = PantopusIcon.PawPrint,
                ),
            stats =
                listOf(
                    BusinessStatCell("rating", "—", "No reviews yet", leadingStar = true, tint = BusinessStatTint.Muted),
                    BusinessStatCell("jobs", "0", "Jobs done"),
                    BusinessStatCell("new", "New", "On Pantopus", tint = BusinessStatTint.Business),
                ),
            categories =
                listOf(
                    BusinessCategoryChip("pet", "Pet care", PantopusIcon.PawPrint, BusinessCategoryAccent.Pet),
                    BusinessCategoryChip("dog", "Dog walking", PantopusIcon.Dog, BusinessCategoryAccent.Neutral),
                ),
            about = null,
            aboutChips = emptyList(),
            status =
                BusinessOpenState(
                    isOpen = false,
                    statusLabel = "Closed now",
                    statusDetail = "Opens tomorrow at 8:00 AM",
                    chipLabel = "Closed · opens 8 AM",
                ),
            hours = emptyList(),
            serviceArea = null,
            services =
                listOf(
                    BusinessServiceRow(
                        "walk", "30-min dog walk", "Solo walk · your route",
                        "$22", "per walk", PantopusIcon.PawPrint,
                    ),
                    BusinessServiceRow(
                        "dropin", "Drop-in visit", "Feed, water, playtime",
                        "$20", "per visit", PantopusIcon.Home,
                    ),
                ),
            gallery = emptyList(),
            reviewSummary = null,
            reviews = emptyList(),
            dock =
                BusinessActionDock(
                    BusinessActionDock.Secondary.Call,
                    note = "Closed now — messages answered at 8 AM",
                ),
            isNewlyClaimed = true,
            phoneNumber = "+15555550111",
            websiteUrl = null,
            viewerIsOwner = false,
        )
}
