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

    /**
     * Verify-email surface. [email] is the address the link was sent to
     * (rendered in the body copy + used by the resend CTA). [token] is
     * the hashed Supabase OTP from the verification link, set when the
     * route was reached via the verification email's deep link.
     */
    data class VerifyEmail(
        val email: String? = null,
        val token: String? = null,
    ) : AuthRoute()

    data class ErrorRoute(
        val error: AuthError,
    ) : AuthRoute()
}

/** Flat string-route table that the NavHost composables register against. */
object AuthRoutes {
    const val LOGIN = "auth/login"
    const val SIGN_UP = "auth/sign_up"
    const val FORGOT_PASSWORD = "auth/forgot_password"
    const val AUTH_ERROR = "auth/error"

    /** Reset password takes a `{token}` path argument. */
    const val RESET_PASSWORD_PATTERN = "auth/reset_password/{token}"

    /**
     * Verify-email accepts optional `{email}` + `{token}` query args. The
     * blank-string default mirrors the iOS optional bindings.
     */
    const val VERIFY_EMAIL_PATTERN = "auth/verify_email?email={email}&token={token}"

    fun resetPassword(token: String): String = "auth/reset_password/$token"

    fun verifyEmail(
        email: String? = null,
        token: String? = null,
    ): String {
        val emailArg = email?.takeIf { it.isNotEmpty() }.orEmpty()
        val tokenArg = token?.takeIf { it.isNotEmpty() }.orEmpty()
        return "auth/verify_email?email=$emailArg&token=$tokenArg"
    }
}
