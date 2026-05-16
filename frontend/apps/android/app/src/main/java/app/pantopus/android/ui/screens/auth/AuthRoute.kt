package app.pantopus.android.ui.screens.auth

import app.pantopus.android.data.auth.AuthError

/**
 * Typed destinations for the signed-out experience. Mirrors iOS `AuthRoute`.
 * The NavController graph in `AuthNavHost` declares string routes; this
 * sealed hierarchy is the type-safe handle the call site uses.
 */
sealed class AuthRoute {
    data object Login : AuthRoute()

    data object SignUp : AuthRoute()

    data object ForgotPassword : AuthRoute()

    data class ResetPassword(
        val token: String,
    ) : AuthRoute()

    data object VerifyEmail : AuthRoute()

    data class ErrorRoute(
        val error: AuthError,
    ) : AuthRoute()
}

/** Flat string-route table that the NavHost composables register against. */
object AuthRoutes {
    const val LOGIN = "auth/login"
    const val SIGN_UP = "auth/sign_up"
    const val FORGOT_PASSWORD = "auth/forgot_password"
    const val VERIFY_EMAIL = "auth/verify_email"
    const val AUTH_ERROR = "auth/error"

    /** Reset password takes a `{token}` path argument. */
    const val RESET_PASSWORD_PATTERN = "auth/reset_password/{token}"

    fun resetPassword(token: String): String = "auth/reset_password/$token"
}
