@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * P1.4 Paparazzi baselines for the Edit Profile form. Locks the
 * shimmer skeleton, the empty-clean loaded form (Save disabled), the
 * dirty + submitting state (Save spinner), and the error empty-state.
 *
 * Note: the iOS counterpart relies on
 * `PantopusTests/__Snapshots__/<screen>-ios.png` baseline tripwires.
 * On Android we record real composable baselines via Paparazzi.
 */
class EditProfileSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2800,
                    softButtons = false,
                ),
        )

    @Test
    fun edit_profile_loading_skeleton() {
        paparazzi.snapshot {
            Frame { EditProfileSkeleton() }
        }
    }

    @Test
    fun edit_profile_loaded_clean_save_disabled() {
        paparazzi.snapshot {
            Frame {
                EditProfileLoaded(
                    state = loadedState(),
                    onClose = {},
                    onCommit = {},
                    onUpdate = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun edit_profile_loaded_dirty_submitting_save_spinner() {
        paparazzi.snapshot {
            Frame {
                EditProfileLoaded(
                    state =
                        loadedState(
                            fields = seededFields(firstName = "Alex"),
                            isDirty = true,
                            isSaving = true,
                        ),
                    onClose = {},
                    onCommit = {},
                    onUpdate = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun edit_profile_error_state() {
        paparazzi.snapshot {
            Frame {
                EmptyState(
                    icon = PantopusIcon.AlertCircle,
                    headline = "Couldn't load profile",
                    subcopy = "Can't reach Pantopus. Check your connection.",
                    ctaTitle = "Try again",
                    onCta = {},
                )
            }
        }
    }

    @Test
    fun edit_profile_validation_error_with_toast() {
        paparazzi.snapshot {
            Frame {
                Box(modifier = Modifier.fillMaxSize()) {
                    EditProfileLoaded(
                        state =
                            loadedState(
                                fields = seededFields(firstName = "", firstNameError = "First name is required."),
                                isValid = false,
                                isDirty = true,
                            ),
                        onClose = {},
                        onCommit = {},
                        onUpdate = { _, _ -> },
                    )
                    EditProfileToastView(
                        payload = EditProfileToast("Fix the highlighted field.", isError = true),
                        modifier = Modifier,
                    )
                }
            }
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }

    private fun loadedState(
        fields: Map<EditProfileField, FormFieldState> = seededFields(),
        isValid: Boolean = true,
        isDirty: Boolean = false,
        isSaving: Boolean = false,
    ): EditProfileLoadedState =
        EditProfileLoadedState(
            fields = fields,
            email = "alice@example.com",
            emailVerified = true,
            isValid = isValid,
            isDirty = isDirty,
            isSaving = isSaving,
        )

    /** Build a populated field map mirroring the iOS hydration. Lets
     *  callers override individual fields to simulate dirty / error
     *  poses without re-listing every key. */
    private fun seededFields(
        firstName: String = "Alice",
        firstNameError: String? = null,
    ): Map<EditProfileField, FormFieldState> {
        fun seeded(
            field: EditProfileField,
            value: String,
            error: String? = null,
            touched: Boolean = false,
        ) = FormFieldState(
            id = field.key,
            value = value,
            originalValue = if (touched) "" else value,
            touched = touched,
            error = error,
        )
        return mapOf(
            EditProfileField.FirstName to
                seeded(
                    EditProfileField.FirstName,
                    value = firstName,
                    error = firstNameError,
                    touched = firstNameError != null || firstName != "Alice",
                ),
            EditProfileField.MiddleName to seeded(EditProfileField.MiddleName, "Q"),
            EditProfileField.LastName to seeded(EditProfileField.LastName, "Doe"),
            EditProfileField.Bio to seeded(EditProfileField.Bio, "Builder of homes."),
            EditProfileField.Tagline to seeded(EditProfileField.Tagline, "Hello, neighbor."),
            EditProfileField.PhoneNumber to seeded(EditProfileField.PhoneNumber, "+15555550123"),
            EditProfileField.DateOfBirth to seeded(EditProfileField.DateOfBirth, "1990-04-12"),
            EditProfileField.Address to seeded(EditProfileField.Address, "123 Main St"),
            EditProfileField.City to seeded(EditProfileField.City, "Portland"),
            EditProfileField.State to seeded(EditProfileField.State, "OR"),
            EditProfileField.Zipcode to seeded(EditProfileField.Zipcode, "97201"),
            EditProfileField.Website to seeded(EditProfileField.Website, "https://alice.dev"),
            EditProfileField.Linkedin to seeded(EditProfileField.Linkedin, ""),
            EditProfileField.Twitter to seeded(EditProfileField.Twitter, ""),
            EditProfileField.Instagram to seeded(EditProfileField.Instagram, ""),
            EditProfileField.Facebook to seeded(EditProfileField.Facebook, ""),
            EditProfileField.ProfileVisibility to seeded(EditProfileField.ProfileVisibility, "public"),
        )
    }
}
