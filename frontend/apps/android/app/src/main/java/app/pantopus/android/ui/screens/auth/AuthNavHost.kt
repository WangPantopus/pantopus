@file:Suppress("LongMethod")

package app.pantopus.android.ui.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pantopus.android.core.routing.DeepLinkRouter
import app.pantopus.android.ui.screens.auth.auth_error.AuthErrorScreen
import app.pantopus.android.ui.screens.auth.forgot_password.ForgotPasswordScreen
import app.pantopus.android.ui.screens.auth.set_password.SetNewPasswordScreen
import app.pantopus.android.ui.screens.auth.set_password.SetNewPasswordViewModel
import app.pantopus.android.ui.screens.auth.sign_up.SignUpScreen
import app.pantopus.android.ui.screens.auth.verify_email.VerifyEmailScreen
import app.pantopus.android.ui.screens.auth.verify_email.VerifyEmailViewModel

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
    val pendingDeepLink by DeepLinkRouter.pending.collectAsStateWithLifecycle()

    // Pull auth deep links (reset / verify) off DeepLinkRouter and push
    // them onto the nav stack. Anything else stays pending for the
    // signed-in tab tree to consume after sign-in.
    LaunchedEffect(pendingDeepLink) {
        when (val pending = pendingDeepLink) {
            is DeepLinkRouter.Destination.ResetPassword -> {
                DeepLinkRouter.consume()
                navController.navigate(AuthRoutes.resetPassword(pending.token)) {
                    popUpTo(AuthRoutes.LOGIN)
                }
            }
            is DeepLinkRouter.Destination.VerifyEmail -> {
                DeepLinkRouter.consume()
                navController.navigate(
                    AuthRoutes.verifyEmail(email = pending.email, token = pending.token),
                ) {
                    popUpTo(AuthRoutes.LOGIN)
                }
            }
            else -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = AuthRoutes.LOGIN,
    ) {
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                onNavigateToSignUp = { navController.navigate(AuthRoutes.SIGN_UP) },
                onNavigateToForgotPassword = { navController.navigate(AuthRoutes.FORGOT_PASSWORD) },
                onNavigateToVerifyEmail = { navController.navigate(AuthRoutes.verifyEmail()) },
                onNavigateToResetPassword = { token ->
                    navController.navigate(AuthRoutes.resetPassword(token))
                },
                onNavigateToAuthError = { navController.navigate(AuthRoutes.AUTH_ERROR) },
            )
        }
        composable(AuthRoutes.SIGN_UP) {
            SignUpScreen(
                onClose = { navController.popBackStack() },
                onSuccess = { email ->
                    // Backend hard-gates login on email_confirmed_at today
                    // (see docs/mobile/auth-backend-contracts.md
                    // §"Backend gap"). Route the user to verify-email so
                    // they can finish onboarding. We hand it the email so
                    // the body copy + resend CTA render correctly.
                    navController.navigate(AuthRoutes.verifyEmail(email = email)) {
                        popUpTo(AuthRoutes.LOGIN)
                    }
                },
            )
        }
        composable(AuthRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = AuthRoutes.RESET_PASSWORD_PATTERN,
            arguments = listOf(navArgument(SetNewPasswordViewModel.TOKEN_KEY) { type = NavType.StringType }),
        ) { entry ->
            val token = entry.arguments?.getString(SetNewPasswordViewModel.TOKEN_KEY).orEmpty()
            SetNewPasswordScreen(
                token = token,
                onBack = { navController.popBackStack() },
                onContinue = {
                    navController.popBackStack(AuthRoutes.LOGIN, inclusive = false)
                },
            )
        }
        composable(
            route = AuthRoutes.VERIFY_EMAIL_PATTERN,
            arguments =
                listOf(
                    navArgument(VerifyEmailViewModel.EMAIL_KEY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(VerifyEmailViewModel.TOKEN_KEY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) {
            VerifyEmailScreen(
                onDone = {
                    navController.popBackStack(AuthRoutes.LOGIN, inclusive = false)
                },
                onChangeEmail = { _ ->
                    navController.navigate(AuthRoutes.SIGN_UP) {
                        popUpTo(AuthRoutes.LOGIN)
                    }
                },
            )
        }
        composable(AuthRoutes.AUTH_ERROR) {
            AuthErrorScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
