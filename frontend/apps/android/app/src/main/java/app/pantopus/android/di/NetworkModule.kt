package app.pantopus.android.di

import android.content.Context
import app.pantopus.android.BuildConfig
import app.pantopus.android.data.api.ApiService
import app.pantopus.android.data.api.models.homes.UploadEvidenceRequestJsonAdapter
import app.pantopus.android.data.api.net.RetryInterceptor
import app.pantopus.android.data.api.services.AdminApi
import app.pantopus.android.data.api.services.AudienceProfileApi
import app.pantopus.android.data.api.services.AuthApi
import app.pantopus.android.data.api.services.BlocksApi
import app.pantopus.android.data.api.services.BusinessDiscoveryApi
import app.pantopus.android.data.api.services.BusinessesApi
import app.pantopus.android.data.api.services.ChatApi
import app.pantopus.android.data.api.services.FilesApi
import app.pantopus.android.data.api.services.GigsApi
import app.pantopus.android.data.api.services.HomeMembersApi
import app.pantopus.android.data.api.services.HomePetsApi
import app.pantopus.android.data.api.services.HomeTasksApi
import app.pantopus.android.data.api.services.HomesApi
import app.pantopus.android.data.api.services.HubApi
import app.pantopus.android.data.api.services.IdentityCenterApi
import app.pantopus.android.data.api.services.ListingOffersApi
import app.pantopus.android.data.api.services.ListingsMutationApi
import app.pantopus.android.data.api.services.ListingsReadApi
import app.pantopus.android.data.api.services.MailComposeApi
import app.pantopus.android.data.api.services.MailboxApi
import app.pantopus.android.data.api.services.MailboxV2Api
import app.pantopus.android.data.api.services.MailboxVaultApi
import app.pantopus.android.data.api.services.NotificationsApi
import app.pantopus.android.data.api.services.OffersApi
import app.pantopus.android.data.api.services.PostsApi
import app.pantopus.android.data.api.services.PrivacyApi
import app.pantopus.android.data.api.services.PrivacyHandshakeApi
import app.pantopus.android.data.api.services.RelationshipsApi
import app.pantopus.android.data.api.services.SupportTrainsApi
import app.pantopus.android.data.api.services.TokenAcceptApi
import app.pantopus.android.data.api.services.UsersApi
import app.pantopus.android.data.auth.AuthInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.sentry.android.okhttp.SentryOkHttpInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val HTTP_CACHE_DIR = "pantopus-http"
private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024 * 1024
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_WRITE_TIMEOUT_SECONDS = 30L

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object NetworkModule {
    @Provides
    @Singleton
    fun provideMoshi(): Moshi =
        Moshi
            .Builder()
            // Custom serializers must be registered ahead of the
            // generic Kotlin factory so they win the lookup. The
            // UploadEvidenceRequest one omits optional fields when
            // null instead of writing JSON `null`.
            .add(UploadEvidenceRequestJsonAdapter())
            .add(app.pantopus.android.data.api.models.homes.BillDecimalAdapter())
            .add(app.pantopus.android.data.api.models.homes.PollOptionAdapter())
            .add(Instant::class.java, Rfc3339DateJsonAdapter().nullSafe())
            .addLast(KotlinJsonAdapterFactory())
            .build()

    /**
     * 10 MB on-disk HTTP cache. OkHttp honours `Cache-Control`, `ETag`, and
     * `If-None-Match` automatically — backend endpoints that emit those
     * headers get conditional revalidation for free.
     */
    @Provides
    @Singleton
    fun provideOkHttpCache(
        @ApplicationContext context: Context,
    ): Cache = Cache(File(context.cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_SIZE_BYTES)

    @Provides
    @Singleton
    fun provideRetryInterceptor(): RetryInterceptor = RetryInterceptor()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        retryInterceptor: RetryInterceptor,
        cache: Cache,
    ): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                level =
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }
        return OkHttpClient
            .Builder()
            .cache(cache)
            .addInterceptor(authInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(logging)
            .addInterceptor(SentryOkHttpInterceptor())
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        moshi: Moshi,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(BuildConfig.PANTOPUS_API_BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    // Per-feature interfaces — new code should depend on these directly.

    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun provideUsersApi(retrofit: Retrofit): UsersApi = retrofit.create(UsersApi::class.java)

    @Provides @Singleton
    fun provideHubApi(retrofit: Retrofit): HubApi = retrofit.create(HubApi::class.java)

    @Provides @Singleton
    fun provideBusinessDiscoveryApi(retrofit: Retrofit): BusinessDiscoveryApi = retrofit.create(BusinessDiscoveryApi::class.java)

    @Provides @Singleton
    fun provideBusinessesApi(retrofit: Retrofit): BusinessesApi = retrofit.create(BusinessesApi::class.java)

    @Provides @Singleton
    fun provideHomesApi(retrofit: Retrofit): HomesApi = retrofit.create(HomesApi::class.java)

    @Provides @Singleton
    fun provideHomePetsApi(retrofit: Retrofit): HomePetsApi = retrofit.create(HomePetsApi::class.java)

    @Provides @Singleton
    fun provideHomeTasksApi(retrofit: Retrofit): HomeTasksApi = retrofit.create(HomeTasksApi::class.java)

    @Provides @Singleton
    fun provideHomeMembersApi(retrofit: Retrofit): HomeMembersApi = retrofit.create(HomeMembersApi::class.java)

    @Provides @Singleton
    fun provideFilesApi(retrofit: Retrofit): FilesApi = retrofit.create(FilesApi::class.java)

    @Provides @Singleton
    fun provideMailboxApi(retrofit: Retrofit): MailboxApi = retrofit.create(MailboxApi::class.java)

    @Provides @Singleton
    fun provideMailboxV2Api(retrofit: Retrofit): MailboxV2Api = retrofit.create(MailboxV2Api::class.java)

    @Provides @Singleton
    fun provideMailboxVaultApi(retrofit: Retrofit): MailboxVaultApi = retrofit.create(MailboxVaultApi::class.java)

    @Provides @Singleton
    fun providePostsApi(retrofit: Retrofit): PostsApi = retrofit.create(PostsApi::class.java)

    @Provides @Singleton
    fun provideRelationshipsApi(retrofit: Retrofit): RelationshipsApi = retrofit.create(RelationshipsApi::class.java)

    @Provides @Singleton
    fun provideBlocksApi(retrofit: Retrofit): BlocksApi = retrofit.create(BlocksApi::class.java)

    @Provides @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideGigsApi(retrofit: Retrofit): GigsApi = retrofit.create(GigsApi::class.java)

    @Provides
    @Singleton
    fun provideListingsReadApi(retrofit: Retrofit): ListingsReadApi = retrofit.create(ListingsReadApi::class.java)

    @Provides
    @Singleton
    fun provideListingsMutationApi(retrofit: Retrofit): ListingsMutationApi = retrofit.create(ListingsMutationApi::class.java)

    @Provides
    @Singleton
    fun provideListingOffersApi(retrofit: Retrofit): ListingOffersApi = retrofit.create(ListingOffersApi::class.java)

    @Provides
    @Singleton
    fun providePrivacyApi(retrofit: Retrofit): PrivacyApi = retrofit.create(PrivacyApi::class.java)

    @Provides
    @Singleton
    fun provideIdentityCenterApi(retrofit: Retrofit): IdentityCenterApi = retrofit.create(IdentityCenterApi::class.java)

    @Provides
    @Singleton
    fun provideAudienceProfileApi(retrofit: Retrofit): AudienceProfileApi = retrofit.create(AudienceProfileApi::class.java)

    @Provides
    @Singleton
    fun providePrivacyHandshakeApi(retrofit: Retrofit): PrivacyHandshakeApi = retrofit.create(PrivacyHandshakeApi::class.java)

    @Provides
    @Singleton
    fun provideTokenAcceptApi(retrofit: Retrofit): TokenAcceptApi = retrofit.create(TokenAcceptApi::class.java)

    @Provides
    @Singleton
    fun provideMailComposeApi(retrofit: Retrofit): MailComposeApi = retrofit.create(MailComposeApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create(NotificationsApi::class.java)

    @Provides
    @Singleton
    fun provideOffersApi(retrofit: Retrofit): OffersApi = retrofit.create(OffersApi::class.java)

    @Provides
    @Singleton
    fun provideSupportTrainsApi(retrofit: Retrofit): SupportTrainsApi = retrofit.create(SupportTrainsApi::class.java)

    @Provides
    @Singleton
    fun provideAdminApi(retrofit: Retrofit): AdminApi = retrofit.create(AdminApi::class.java)

    // Legacy aggregate — retained for existing AuthRepository / FeedScreen.
    @Provides @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
