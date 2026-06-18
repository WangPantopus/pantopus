@file:Suppress("PackageNaming", "TooManyFunctions", "MagicNumber")

package app.pantopus.android.ui.screens.scheduling.resources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.CreateResourceRequest
import app.pantopus.android.data.api.models.scheduling.ResourceDto
import app.pantopus.android.data.api.models.scheduling.UpdateResourceRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/** F10 editor load lifecycle. */
sealed interface ResourceEditorLoadState {
    data object Loading : ResourceEditorLoadState

    data object Ready : ResourceEditorLoadState

    data class Error(
        val message: String,
    ) : ResourceEditorLoadState
}

/** The editable resource form. Duration is edited in whole hours, stored as minutes. */
data class ResourceEditorForm(
    val name: String = "",
    val kind: ResourceKind = ResourceKind.Other,
    val whoCanBook: WhoCanBook = WhoCanBook.Members,
    val maxDurationHours: Int = 2,
    val bufferMin: Int = 0,
    val requiresApproval: Boolean = false,
    val hoursDays: Set<Int> = AvailableHours.weekdayDefault.days,
    val hoursStart: LocalTime = LocalTime.of(9, 0),
    val hoursEnd: LocalTime = LocalTime.of(17, 0),
)

/**
 * Stream A12 — F10 Resource Editor (create / edit / delete). Picking a type
 * seeds smart rule defaults; edit mode loads the existing resource. Home-only.
 */
@HiltViewModel
class ResourceEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repo: SchedulingRepository,
        private val homes: HomesRepository,
    ) : ViewModel() {
        private val argId: String =
            savedStateHandle
                .get<String>(
                    SchedulingRoutes.ARG_RESOURCE_ID,
                ).orEmpty()

        /** `"new"`/blank = create; anything else edits that resource. */
        val isCreate: Boolean = argId.isBlank() || argId == NEW_SENTINEL
        private val resourceId: String? = argId.takeUnless { isCreate }

        val screenTitle: String = if (isCreate) "New resource" else "Edit resource"

        private val _loadState =
            MutableStateFlow<ResourceEditorLoadState>(ResourceEditorLoadState.Loading)
        val loadState: StateFlow<ResourceEditorLoadState> = _loadState.asStateFlow()

        private val _form = MutableStateFlow(ResourceEditorForm())
        val form: StateFlow<ResourceEditorForm> = _form.asStateFlow()

        private val _isSaving = MutableStateFlow(false)
        val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

        private val _saveError = MutableStateFlow<String?>(null)
        val saveError: StateFlow<String?> = _saveError.asStateFlow()

        private val _showDeleteConfirm = MutableStateFlow(false)
        val showDeleteConfirm: StateFlow<Boolean> = _showDeleteConfirm.asStateFlow()

        private val _isDeleting = MutableStateFlow(false)
        val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

        private var homeId: String? = null
        private var started = false

        // ── Derived validation ───────────────────────────────────────────────
        val nameError: String?
            get() = if (_form.value.name.isBlank()) "Give this resource a name" else null

        val durationError: String?
            get() = if (_form.value.maxDurationHours > 0) null else "Set a max duration above zero"

        val isValid: Boolean
            get() = _form.value.name.isNotBlank() && _form.value.maxDurationHours > 0

        val isDirty: Boolean
            get() = if (isCreate) _form.value.name.isNotBlank() else true

        /** Collapsed-rule helper — "4 hr max · No approval". */
        val ruleHelper: String
            get() {
                val f = _form.value
                val approval = if (f.requiresApproval) "Needs approval" else "No approval"
                return "${f.maxDurationHours} hr max · $approval"
            }

        fun start() {
            if (started) return
            started = true
            load()
        }

        fun load() {
            viewModelScope.launch {
                val hid = homeId ?: resolvePrimaryHomeId(homes)
                if (hid == null) {
                    _loadState.value =
                        ResourceEditorLoadState.Error("Join or create a home to manage resources.")
                    return@launch
                }
                homeId = hid
                if (isCreate) {
                    applyDefaults(_form.value.kind)
                    _loadState.value = ResourceEditorLoadState.Ready
                    return@launch
                }
                _loadState.value = ResourceEditorLoadState.Loading
                when (val result = repo.getResources(SchedulingOwner.Home(hid))) {
                    is NetworkResult.Success -> {
                        val resource = result.data.resources.firstOrNull { it.id == resourceId }
                        if (resource == null) {
                            _loadState.value =
                                ResourceEditorLoadState.Error(
                                    "This resource is no longer available.",
                                )
                        } else {
                            apply(resource)
                            _loadState.value = ResourceEditorLoadState.Ready
                        }
                    }
                    is NetworkResult.Failure ->
                        _loadState.value =
                            ResourceEditorLoadState.Error(
                                result.error.message ?: "Couldn't load this resource.",
                            )
                }
            }
        }

        private fun apply(resource: ResourceDto) {
            val hours = AvailableHours.fromJson(resource.availableHours)
            _form.value =
                ResourceEditorForm(
                    name = resource.name,
                    kind = ResourceKind.fromWire(resource.resourceType),
                    whoCanBook = WhoCanBook.fromWire(resource.whoCanBook),
                    maxDurationHours =
                        (
                            (resource.maxDurationMin ?: DEFAULT_DURATION_MIN) /
                                MINUTES_PER_HOUR
                        ).coerceAtLeast(1),
                    bufferMin = resource.bufferMin ?: 0,
                    requiresApproval = resource.requiresApproval ?: false,
                    hoursDays = hours?.days ?: AvailableHours.weekdayDefault.days,
                    hoursStart = hours?.let { seedTime(it.start) } ?: LocalTime.of(9, 0),
                    hoursEnd = hours?.let { seedTime(it.end) } ?: LocalTime.of(17, 0),
                )
        }

        // ── Editing ───────────────────────────────────────────────────────────
        fun setName(value: String) = _form.update { it.copy(name = value) }

        fun setWhoCanBook(value: WhoCanBook) = _form.update { it.copy(whoCanBook = value) }

        fun setMaxDurationHours(value: Int) = _form.update { it.copy(maxDurationHours = value) }

        fun setBufferMin(value: Int) = _form.update { it.copy(bufferMin = value) }

        fun setRequiresApproval(value: Boolean) = _form.update { it.copy(requiresApproval = value) }

        fun setHoursStart(value: LocalTime) = _form.update { it.copy(hoursStart = value) }

        fun setHoursEnd(value: LocalTime) = _form.update { it.copy(hoursEnd = value) }

        fun selectKind(kind: ResourceKind) {
            _form.update { it.copy(kind = kind) }
            applyDefaults(kind)
        }

        private fun applyDefaults(kind: ResourceKind) {
            val defaults = kind.defaultRules
            _form.update {
                it.copy(
                    maxDurationHours =
                        (defaults.maxDurationMin / MINUTES_PER_HOUR).coerceAtLeast(
                            1,
                        ),
                    bufferMin = defaults.bufferMin,
                    requiresApproval = defaults.requiresApproval,
                )
            }
        }

        fun toggleDay(weekday: Int) {
            _form.update {
                val days = it.hoursDays.toMutableSet()
                if (!days.add(weekday)) days.remove(weekday)
                it.copy(hoursDays = days)
            }
        }

        fun requestDelete() {
            _showDeleteConfirm.value = true
        }

        fun dismissDelete() {
            _showDeleteConfirm.value = false
        }

        fun clearSaveError() {
            _saveError.value = null
        }

        // ── Save / delete ───────────────────────────────────────────────────
        private fun availableHours(): AvailableHours {
            val f = _form.value
            return AvailableHours(
                days = f.hoursDays,
                start = hhmm(f.hoursStart),
                end = hhmm(f.hoursEnd),
            )
        }

        /** Returns true on success so the screen can dismiss. */
        suspend fun save(): Boolean {
            if (!isValid || _isSaving.value) return false
            val hid = homeId ?: return false
            _isSaving.value = true
            try {
                val f = _form.value
                val owner = SchedulingOwner.Home(hid)
                val result =
                    if (resourceId != null) {
                        repo.updateResource(
                            owner,
                            resourceId,
                            UpdateResourceRequest(
                                name = f.name.trim(),
                                resourceType = f.kind.wire,
                                whoCanBook = f.whoCanBook.wire,
                                maxDurationMin = f.maxDurationHours * MINUTES_PER_HOUR,
                                bufferMin = f.bufferMin,
                                requiresApproval = f.requiresApproval,
                                availableHours = availableHours().toJson(),
                            ),
                        )
                    } else {
                        repo.createResource(
                            owner,
                            CreateResourceRequest(
                                name = f.name.trim(),
                                resourceType = f.kind.wire,
                                whoCanBook = f.whoCanBook.wire,
                                maxDurationMin = f.maxDurationHours * MINUTES_PER_HOUR,
                                bufferMin = f.bufferMin,
                                requiresApproval = f.requiresApproval,
                                availableHours = availableHours().toJson(),
                                ownerType = owner.ownerType,
                                ownerId = owner.ownerId,
                            ),
                        )
                    }
                return when (result) {
                    is NetworkResult.Success -> true
                    is NetworkResult.Failure -> {
                        _saveError.value = result.error.message ?: "Couldn't save this resource."
                        false
                    }
                }
            } finally {
                _isSaving.value = false
            }
        }

        /** Returns true on success so the screen can dismiss. */
        suspend fun confirmDelete(): Boolean {
            val id = resourceId ?: return false
            val hid = homeId ?: return false
            if (_isDeleting.value) return false
            _isDeleting.value = true
            _showDeleteConfirm.value = false
            try {
                return when (val result = repo.deleteResource(SchedulingOwner.Home(hid), id)) {
                    is NetworkResult.Success -> true
                    is NetworkResult.Failure -> {
                        _saveError.value = result.error.message ?: "Couldn't delete this resource."
                        false
                    }
                }
            } finally {
                _isDeleting.value = false
            }
        }

        private fun seedTime(hhmm: String): LocalTime {
            val parts = hhmm.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
            val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            return LocalTime.of(hour, minute)
        }

        private fun hhmm(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

        private companion object {
            const val NEW_SENTINEL = "new"
            const val MINUTES_PER_HOUR = 60
            const val DEFAULT_DURATION_MIN = 120
        }
    }
