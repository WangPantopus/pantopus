package app.pantopus.android.ui.screens.auth.forgot_password

import androidx.lifecycle.ViewModel
import app.pantopus.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** P3 stub. P4 wires real state + forgot-password submission. */
@HiltViewModel
class ForgotPasswordViewModel
    @Inject
    constructor(
        @Suppress("UnusedPrivateProperty") private val authRepository: AuthRepository,
    ) : ViewModel()
