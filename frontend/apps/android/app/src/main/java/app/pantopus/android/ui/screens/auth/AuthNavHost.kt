package app.pantopus.android.ui.screens.auth

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pantopus.android.ui.screens.auth.auth_error.AuthErrorScreen
import app.pantopus.android.ui.screens.auth.forgot_password.ForgotPasswordScreen
import app.pantopus.android.ui.screens.auth.reset_password.ResetPasswordScreen
import app.pantopus.android.ui.screens.auth.reset_password.ResetPasswordViewModel
import app.pantopus.android.ui.screens.auth.sign_up.SignUpScreen
import app.pantopus.android.ui.screens.auth.verify_email.VerifyEmailScreen

/**
 * Nav graph rooted at [AuthRoutes.LOGIN] for the signed-out experience.
 * Mirrors iOS `LoginView`'s `NavigationStack` + `navigationDestination`.
 *
 * P4 wires Login → SignUp / Forgot, and SignUp success → VerifyEmail
 * (since the backend currently hard-gates `/login` until verified —
 * see `docs/mobile/auth-backend-contracts.md`). P5 wires the Verify /
 * Reset deep links and the inline AuthError destination.
 */
@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AuthRoutes.LOGIN,
    ) {
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                onNavigateToSignUp = { navController.navigate(AuthRoutes.SIGN_UP) },
                onNavigateToForgotPassword = { navController.navigate(AuthRoutes.FORGOT_PASSWORD) },
                onNavigateToVerifyEmail = { navController.navigate(AuthRoutes.VERIFY_EMAIL) },
                onNavigateToResetPassword = { token ->
                    navController.navigate(AuthRoutes.resetPassword(token))
                },
                onNavigateToAuthError = { navController.navigate(AuthRoutes.AUTH_ERROR) },
            )
        }
        composable(AuthRoutes.SIGN_UP) {
            SignUpScreen(
                onClose = { navController.popBackStack() },
                onSuccess = {
                    // Backend hard-gates login on email_confirmed_at today
                    // (see docs/mobile/auth-backend-contracts.md
                    // §"Backend gap"). Route the user to verify-email so
                    // they can finish onboarding.
                    navController.navigate(AuthRoutes.VERIFY_EMAIL) {
                        popUpTo(AuthRoutes.LOGIN)
                    }
                },
            )
        }
        composable(AuthRoutes.FORGOT_PASSWORD) { ForgotPasswordScreen() }
        composable(
            route = AuthRoutes.RESET_PASSWORD_PATTERN,
            arguments = listOf(navArgument(ResetPasswordViewModel.TOKEN_KEY) { type = NavType.StringType }),
        ) { entry ->
            val token = entry.arguments?.getString(ResetPasswordViewModel.TOKEN_KEY).orEmpty()
            ResetPasswordScreen(token = token)
        }
        composable(AuthRoutes.VERIFY_EMAIL) { VerifyEmailScreen() }
        composable(AuthRoutes.AUTH_ERROR) {
            AuthErrorScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
