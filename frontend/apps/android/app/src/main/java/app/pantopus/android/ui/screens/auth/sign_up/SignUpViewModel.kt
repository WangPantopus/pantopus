package app.pantopus.android.ui.screens.auth.sign_up

import androidx.lifecycle.ViewModel
import app.pantopus.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** P3 stub. P4 wires real state + signup submission. */
@HiltViewModel
class SignUpViewModel
    @Inject
    constructor(
        @Suppress("UnusedPrivateProperty") private val authRepository: AuthRepository,
    ) : ViewModel()
