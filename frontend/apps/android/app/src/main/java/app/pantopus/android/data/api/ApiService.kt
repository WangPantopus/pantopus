package app.pantopus.android.data.api

import app.pantopus.android.data.api.models.AuthResponse
import app.pantopus.android.data.api.models.FeedResponse
import app.pantopus.android.data.api.models.LoginRequest
import app.pantopus.android.data.api.models.RegisterPushTokenRequest
import app.pantopus.android.data.api.models.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface for the Pantopus backend. Add one function per endpoint;
 * body / query / path params are all statically typed.
 */
interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("api/users/me")
    suspend fun me(): UserDto

    @GET("api/posts")
    suspend fun feed(): FeedResponse

    @POST("api/notifications/register")
    suspend fun registerPushToken(@Body body: RegisterPushTokenRequest): Unit
}
