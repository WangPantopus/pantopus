package app.pantopus.android.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches `Authorization: Bearer <token>` on every
 * request. Also fires `AuthRepository.signOut()` when the server returns 401.
 *
 * Using runBlocking here is pragmatic: OkHttp interceptors are synchronous.
 * Reads from DataStore are fast (in-memory cached) and bounded to the
 * OkHttp dispatcher thread.
 */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val tokenStorage: TokenStorage,
        private val authRepositoryProvider: dagger.Lazy<AuthRepository>,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request =
                chain.request().newBuilder().apply {
                    val token = runBlocking { tokenStorage.accessToken() }
                    if (!token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                    header("X-Client-Platform", "android")
                }.build()

            val response = chain.proceed(request)
            if (response.code == 401) {
                runBlocking { authRepositoryProvider.get().signOut() }
            }
            return response
        }
    }
