@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.businesses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.businesses.BusinessMembership
import app.pantopus.android.data.api.models.businesses.BusinessProfileDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.businesses.BusinessesRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.AvatarBackground
import app.pantopus.android.ui.screens.shared.list_of_rows.AvatarBadgeSize
import app.pantopus.android.ui.screens.shared.list_of_rows.BannerConfig
import app.pantopus.android.ui.screens.shared.list_of_rows.BannerCtaTint
import app.pantopus.android.ui.screens.shared.list_of_rows.GradientPair
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTemplate
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for My businesses — wraps `GET /api/businesses/my-businesses`.
 *
 * T6.3f / P14 row anatomy (avatar-first, no tabs):
 *   leading  → 44dp business-violet avatar w/ verified badge when
 *              `profile.is_published == true`
 *   title    → business.name (or username fallback)
 *   subtitle → `<Category> · <Role>` (Owner / Manager / Staff)
 *   body     → "<city>, <state>" — or "Online only" when blank
 *   trailing → chevron (tap → business dashboard)
 *
 * Plus a business-tinted intro banner and a `.SecondaryCreate` FAB
 * tinted `FabTint.Business`.
 */
@HiltViewModel
class MyBusinessesViewModel
    @Inject
    constructor(
        private val repo: BusinessesRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _banner = MutableStateFlow<BannerConfig?>(null)
        val banner: StateFlow<BannerConfig?> = _banner.asStateFlow()

        private var onOpenBusiness: (String) -> Unit = {}
        private var onRegister: () -> Unit = {}

        fun configureNavigation(
            onOpenBusiness: (String) -> Unit,
            onRegister: () -> Unit,
        ) {
            this.onOpenBusiness = onOpenBusiness
            this.onRegister = onRegister
        }

        fun load() {
            if (_state.value is ListOfRowsUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = ListOfRowsUiState.Loading
            _banner.value = null
            viewModelScope.launch {
                when (val result = repo.myBusinesses()) {
                    is NetworkResult.Success -> applySuccess(result.data.businesses)
                    is NetworkResult.Failure -> _state.value = ListOfRowsUiState.Error(result.error.message)
                }
            }
        }

        private fun applySuccess(memberships: List<BusinessMembership>) {
            if (memberships.isEmpty()) {
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Building2,
                        headline = "No businesses yet",
                        subcopy =
                            "Create a business profile to take quotes inside Pantopus and earn the violet verified mark.",
                        ctaTitle = "Register a business",
                        onCta = onRegister,
                    )
                return
            }
            val rows = memberships.map(::rowFor)
            _state.value =
                ListOfRowsUiState.Loaded(
                    sections = listOf(RowSection(id = "my-businesses", rows = rows)),
                    hasMore = false,
                )
            _banner.value =
                BannerConfig(
                    icon = PantopusIcon.Building2,
                    title = if (rows.size == 1) "1 verified business" else "${rows.size} verified businesses",
                    subtitle = "Tap any business to manage its inbox, gigs, and reviews",
                    tint = BannerCtaTint.Business,
                )
        }

        private fun rowFor(membership: BusinessMembership): RowModel {
            val title =
                membership.business.name?.takeIf { it.isNotEmpty() }
                    ?: membership.business.username?.takeIf { it.isNotEmpty() }
                    ?: "Untitled business"
            val category = categoryLabel(membership.profile)
            val role = roleLabel(membership.roleBase)
            val subtitle =
                listOfNotNull(category, role)
                    .joinToString(" · ")
                    .takeIf { it.isNotEmpty() }
            val locality =
                listOfNotNull(membership.business.city, membership.business.state)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
                    .takeIf { it.isNotEmpty() }
            val body = locality ?: "Online only"
            val bodyIcon = if (locality != null) PantopusIcon.MapPin else PantopusIcon.Info

            return RowModel(
                id = membership.businessUserId,
                title = title,
                subtitle = subtitle,
                template = RowTemplate.AvatarKebab,
                leading =
                    RowLeading.AvatarWithBadge(
                        name = title,
                        imageUrl = membership.business.profilePictureUrl,
                        background =
                            AvatarBackground.Gradient(
                                GradientPair(
                                    start = PantopusColors.business,
                                    end = PantopusColors.business,
                                ),
                            ),
                        size = AvatarBadgeSize.Large,
                        verified = membership.profile?.isPublished == true,
                    ),
                trailing = RowTrailing.Chevron,
                onTap = { onOpenBusiness(membership.businessUserId) },
                body = body,
                bodyIcon = bodyIcon,
            )
        }

        private fun categoryLabel(profile: BusinessProfileDto?): String? {
            val first = profile?.categories?.firstOrNull()?.takeIf { it.isNotEmpty() }
            if (first != null) return first.replace('_', ' ').replaceFirstChar { it.uppercase() }
            val bizType = profile?.businessType?.takeIf { it.isNotEmpty() }
            return bizType?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
        }

        private fun roleLabel(roleBase: String?): String? =
            when (roleBase) {
                "owner" -> "Owner"
                "admin" -> "Admin"
                "manager" -> "Manager"
                "staff" -> "Staff"
                "viewer" -> "Viewer"
                "editor" -> "Editor"
                null, "" -> null
                else -> roleBase.replaceFirstChar { it.uppercase() }
            }
    }
