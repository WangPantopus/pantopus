package app.pantopus.android.ui.screens.auth.verify_email

import androidx.lifecycle.ViewModel
import app.pantopus.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** P3 stub. P5 wires real state + verify/resend submission. */
@HiltViewModel
class VerifyEmailViewModel
    @Inject
    constructor(
        @Suppress("UnusedPrivateProperty") private val authRepository: AuthRepository,
    ) : ViewModel()
