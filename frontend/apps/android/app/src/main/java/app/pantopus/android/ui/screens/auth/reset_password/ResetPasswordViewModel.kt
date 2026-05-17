@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.auth.reset_password

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.pantopus.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** P3 stub. P4 wires real state + reset-password submission. */
@HiltViewModel
class ResetPasswordViewModel
    @Inject
    constructor(
        @Suppress("UnusedPrivateProperty") private val authRepository: AuthRepository,
        @Suppress("UnusedPrivateProperty") private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        companion object {
            const val TOKEN_KEY = "token"
        }
    }
