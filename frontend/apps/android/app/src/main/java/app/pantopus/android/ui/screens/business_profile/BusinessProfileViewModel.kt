@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.business_profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.businesses.BusinessCatalogItemDto
import app.pantopus.android.data.api.models.businesses.BusinessDetailResponse
import app.pantopus.android.data.api.models.businesses.BusinessHoursDto
import app.pantopus.android.data.api.models.businesses.BusinessLocationDto
import app.pantopus.android.data.api.models.businesses.BusinessProfileDetailDto
import app.pantopus.android.data.api.models.businesses.BusinessPublicResponse
import app.pantopus.android.data.api.models.businesses.BusinessUserDetailDto
import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.models.profile.PublicProfileReview
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.businesses.BusinessesRepository
import app.pantopus.android.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

/** Nav-arg key for the business UUID. */
const val BUSINESS_PROFILE_BUSINESS_ID_KEY = "businessId"

/** View-model for the Business Profile screen. */
@HiltViewModel
class BusinessProfileViewModel
    @Inject
    constructor(
        private val businesses: BusinessesRepository,
        private val profiles: ProfileRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val businessId: String =
            requireNotNull(savedStateHandle[BUSINESS_PROFILE_BUSINESS_ID_KEY]) {
                "BusinessProfileViewModel requires a '$BUSINESS_PROFILE_BUSINESS_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<BusinessProfileUiState>(BusinessProfileUiState.Loading)
        val state: StateFlow<BusinessProfileUiState> = _state.asStateFlow()

        private val _selectedTab = MutableStateFlow(BusinessProfileTab.Overview)
        val selectedTab: StateFlow<BusinessProfileTab> = _selectedTab.asStateFlow()

        private val _saveState = MutableStateFlow<BusinessProfileSaveState>(BusinessProfileSaveState.Idle)
        val saveState: StateFlow<BusinessProfileSaveState> = _saveState.asStateFlow()

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

        private val _showOverflow = MutableStateFlow(false)
        val showOverflow: StateFlow<Boolean> = _showOverflow.asStateFlow()

        fun load() {
            if (_state.value is BusinessProfileUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = BusinessProfileUiState.Loading
            viewModelScope.launch { fetch() }
        }

        fun selectTab(tab: BusinessProfileTab) {
            _selectedTab.value = tab
        }

        fun dismissToast() {
            _toastMessage.value = null
        }

        fun setShowOverflow(show: Boolean) {
            _showOverflow.value = show
        }

        fun save() {
            if (_saveState.value is BusinessProfileSaveState.InFlight) return
            if (_saveState.value is BusinessProfileSaveState.Saved) return
            _saveState.value = BusinessProfileSaveState.InFlight
            viewModelScope.launch {
                // Optimistic UX — the follow-a-business endpoint ships
                // later. When it lands, this is the single integration
                // site.
                _saveState.value = BusinessProfileSaveState.Saved
                _toastMessage.value = "Saved"
            }
        }

        private suspend fun fetch() {
            when (val detail = businesses.business(businessId)) {
                is NetworkResult.Success -> {
                    val payload = detail.data
                    coroutineScope {
                        val publicDeferred =
                            async {
                                payload.business.username
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { username ->
                                        val res = businesses.publicBusiness(username)
                                        (res as? NetworkResult.Success)?.data
                                    }
                            }
                        val reviewsDeferred =
                            async {
                                val res = profiles.publicProfile(businessId)
                                (res as? NetworkResult.Success)?.data
                            }
                        val publicResponse = publicDeferred.await()
                        val reviewsResponse = reviewsDeferred.await()
                        _state.value =
                            BusinessProfileUiState.Loaded(
                                build(payload, publicResponse, reviewsResponse),
                            )
                    }
                }
                is NetworkResult.Failure -> {
                    when (detail.error) {
                        NetworkError.NotFound -> _state.value = BusinessProfileUiState.NotFound
                        else -> _state.value = BusinessProfileUiState.Error(friendlyMessage(detail.error))
                    }
                }
            }
        }

        private fun build(
            detail: BusinessDetailResponse,
            publicResponse: BusinessPublicResponse?,
            reviewsResponse: PublicProfileDto?,
        ): BusinessProfileContent {
            val business = detail.business
            val profile = detail.profile
            val primaryLocation =
                profile?.primaryLocation
                    ?: detail.locations.firstOrNull { it.isPrimary == true }
                    ?: detail.locations.firstOrNull()
            val header =
                BusinessProfileHeader(
                    displayName =
                        business.name?.takeIf { it.isNotEmpty() }
                            ?: business.username?.let { "@$it" }
                            ?: "Business",
                    handle = business.username,
                    locality = locality(business, primaryLocation),
                    logoUrl = business.profilePictureUrl,
                    isVerified = isVerified(business, profile),
                    categoryChips = (profile?.categories ?: emptyList()).take(4),
                )

            val about =
                profile?.description?.takeIf { it.isNotEmpty() }
                    ?: business.bio?.takeIf { it.isNotEmpty() }
                    ?: business.tagline?.takeIf { it.isNotEmpty() }

            val hours = buildHours(publicResponse?.hours.orEmpty(), primaryLocation?.id)
            val address = primaryLocation?.let { buildAddress(it) }
            val contact = buildContact(profile, primaryLocation)
            val services = (publicResponse?.catalog ?: emptyList()).map { buildService(it) }
            val reviewCards = (reviewsResponse?.reviews ?: emptyList()).map { buildReview(it) }
            val stats = buildStats(business, reviewsResponse)

            return BusinessProfileContent(
                businessId = business.id,
                header = header,
                stats = stats,
                about = about,
                hours = hours,
                address = address,
                contact = contact,
                services = services,
                reviews = reviewCards,
                websiteUrl = normalizedWebsite(profile?.website),
                viewerIsOwner = detail.access?.isOwner == true,
            )
        }

        private fun isVerified(
            business: BusinessUserDetailDto,
            profile: BusinessProfileDetailDto?,
        ): Boolean {
            business.verified?.let { return it }
            return profile?.verificationStatus?.let { it != "unverified" } == true
        }

        private fun locality(
            business: BusinessUserDetailDto,
            location: BusinessLocationDto?,
        ): String? {
            val locCity = location?.city
            val locState = location?.state
            if (!locCity.isNullOrEmpty() && !locState.isNullOrEmpty()) {
                return "$locCity, $locState"
            }
            val bizCity = business.city
            val bizState = business.state
            if (!bizCity.isNullOrEmpty() && !bizState.isNullOrEmpty()) {
                return "$bizCity, $bizState"
            }
            return bizCity ?: bizState
        }

        private fun buildStats(
            business: BusinessUserDetailDto,
            reviewsResponse: PublicProfileDto?,
        ): List<BusinessStatCell> {
            val followers = business.followersCount ?: reviewsResponse?.followersCount ?: 0
            val reviews = business.reviewCount ?: reviewsResponse?.reviewCount ?: 0
            val years = yearsOnPantopus(business.createdAt)
            return listOf(
                BusinessStatCell(id = "followers", value = formatStat(followers), label = "Followers"),
                BusinessStatCell(id = "reviews", value = formatStat(reviews), label = "Reviews"),
                BusinessStatCell(
                    id = "years",
                    value = years,
                    label = if (years == "1") "Year" else "Years",
                ),
            )
        }

        private fun formatStat(value: Int): String {
            if (value < 1_000) return value.toString()
            val truncated = value / 100.0 / 10.0
            return if (truncated == truncated.toInt().toDouble()) {
                "${truncated.toInt()}K"
            } else {
                String.format(Locale.US, "%.1fK", truncated)
            }
        }

        private fun yearsOnPantopus(iso: String?): String {
            if (iso.isNullOrEmpty()) return "—"
            val instant =
                try {
                    Instant.parse(iso)
                } catch (_: Throwable) {
                    return "—"
                }
            val years = Duration.between(instant, Instant.now()).toDays() / 365
            return if (years < 1) "<1" else "$years"
        }

        private fun buildHours(
            rows: List<BusinessHoursDto>,
            primaryLocationId: String?,
        ): List<BusinessHoursRow> {
            val scoped =
                if (primaryLocationId != null) {
                    rows.filter { it.locationId == primaryLocationId }
                } else {
                    rows
                }
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val sorted = scoped.sortedBy { it.dayOfWeek }
            return sorted.map { row ->
                val dayIndex = row.dayOfWeek.coerceIn(0, 6)
                val dayLabel = dayNames[dayIndex]
                val isClosed = row.isClosed == true
                val timeLabel =
                    when {
                        isClosed -> "Closed"
                        row.openTime != null && row.closeTime != null ->
                            "${formatTime(row.openTime)} – ${formatTime(row.closeTime)}"
                        else -> "—"
                    }
                BusinessHoursRow(
                    id = row.id ?: "${row.locationId.orEmpty()}-${row.dayOfWeek}",
                    dayLabel = dayLabel,
                    timeLabel = timeLabel,
                    isClosed = isClosed,
                )
            }
        }

        private fun formatTime(raw: String): String {
            val parts = raw.split(":")
            if (parts.size < 2) return raw
            val hour = parts[0].toIntOrNull() ?: return raw
            val minute = parts[1].toIntOrNull() ?: return raw
            val suffix = if (hour >= 12) "PM" else "AM"
            val normalised = if (hour % 12 == 0) 12 else hour % 12
            return if (minute == 0) {
                "$normalised $suffix"
            } else {
                String.format(Locale.US, "%d:%02d %s", normalised, minute, suffix)
            }
        }

        private fun buildAddress(location: BusinessLocationDto): BusinessAddress {
            val lines = mutableListOf<String>()
            location.address?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
            location.address2?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
            val cityLine =
                listOfNotNull(
                    location.city?.takeIf { it.isNotEmpty() },
                    location.state?.takeIf { it.isNotEmpty() },
                    location.zipcode?.takeIf { it.isNotEmpty() },
                ).joinToString(", ")
            if (cityLine.isNotEmpty()) lines.add(cityLine)
            return BusinessAddress(
                lines = lines,
                latitude = location.location?.lat,
                longitude = location.location?.lng,
            )
        }

        private fun buildContact(
            profile: BusinessProfileDetailDto?,
            location: BusinessLocationDto?,
        ): List<BusinessContactRow> {
            val rows = mutableListOf<BusinessContactRow>()
            val phone = profile?.publicPhone ?: location?.phone
            if (!phone.isNullOrEmpty()) {
                rows +=
                    BusinessContactRow(
                        id = "phone",
                        kind = BusinessContactRow.Kind.Phone,
                        value = phone,
                        actionUri = "tel:${phone.filter { it.isDigit() || it == '+' }}",
                    )
            }
            val email = profile?.publicEmail ?: location?.email
            if (!email.isNullOrEmpty()) {
                rows +=
                    BusinessContactRow(
                        id = "email",
                        kind = BusinessContactRow.Kind.Email,
                        value = email,
                        actionUri = "mailto:$email",
                    )
            }
            val website = profile?.website
            if (!website.isNullOrEmpty()) {
                rows +=
                    BusinessContactRow(
                        id = "website",
                        kind = BusinessContactRow.Kind.Website,
                        value = prettyHost(website) ?: website,
                        actionUri = normalizedWebsite(website),
                    )
            }
            return rows
        }

        private fun buildService(item: BusinessCatalogItemDto): BusinessServiceRow =
            BusinessServiceRow(
                id = item.id,
                name = item.name,
                detail = item.description,
                priceLabel = priceLabel(item),
            )

        private fun priceLabel(item: BusinessCatalogItemDto): String {
            val currency = item.currency?.uppercase(Locale.US) ?: "USD"
            val symbol = if (currency == "USD") "$" else ""
            val formatDollars: (Int) -> String = { cents ->
                val value = cents / 100.0
                if (value == value.roundToInt().toDouble()) {
                    String.format(Locale.US, "%s%d", symbol, value.toInt())
                } else {
                    String.format(Locale.US, "%s%.2f", symbol, value)
                }
            }
            val min = item.priceCents
            val max = item.priceMaxCents
            return when {
                min != null && max != null && max > min -> "${formatDollars(min)} – ${formatDollars(max)}"
                min != null -> {
                    val unit = item.priceUnit?.let { "/$it" } ?: ""
                    "${formatDollars(min)}$unit"
                }
                item.kind == "donation" -> "Suggested"
                else -> "Contact"
            }
        }

        private fun buildReview(review: PublicProfileReview): BusinessReviewCard =
            BusinessReviewCard(
                id = review.id ?: UUID.randomUUID().toString(),
                reviewerName = review.reviewerName ?: "Anonymous",
                reviewerAvatarUrl = review.reviewerAvatar,
                rating = review.rating.coerceIn(0, 5),
                body = review.content.orEmpty(),
                timestamp = relativeTimestamp(review.createdAt),
            )

        private fun relativeTimestamp(iso: String?): String {
            if (iso.isNullOrEmpty()) return ""
            val instant =
                try {
                    Instant.parse(iso)
                } catch (_: Throwable) {
                    return ""
                }
            val seconds = Duration.between(instant, Instant.now()).seconds
            return when {
                seconds < 60 -> "Just now"
                seconds < 3_600 -> "${seconds / 60}m ago"
                seconds < 86_400 -> "${seconds / 3_600}h ago"
                seconds < 604_800 -> "${seconds / 86_400}d ago"
                else ->
                    instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
            }
        }

        private fun normalizedWebsite(raw: String?): String? {
            if (raw.isNullOrEmpty()) return null
            return if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                raw
            } else {
                "https://$raw"
            }
        }

        private fun prettyHost(raw: String): String? {
            val normalised = normalizedWebsite(raw) ?: return raw
            val withoutProtocol =
                normalised
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
            return withoutProtocol.takeWhile { it != '/' }
        }

        private fun friendlyMessage(error: NetworkError): String =
            when (error) {
                NetworkError.NotFound -> "We couldn't find this business."
                NetworkError.Forbidden -> "This business profile is private."
                is NetworkError.Transport -> "Check your connection and try again."
                else -> "Something went wrong. Try again."
            }
    }
