package app.pantopus.android.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore by preferencesDataStore(name = "auth_tokens")

/**
 * Persists access + refresh tokens in DataStore.
 *
 * NOTE: DataStore is not encrypted at rest. For a production app you should
 * wrap the values with Jetpack Security's `MasterKey` + EncryptedFile or use
 * the Android Keystore-backed `EncryptedSharedPreferences`. This stub is
 * intentionally simple — swap it in a follow-up PR.
 */
@Singleton
class TokenStorage
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val ACCESS = stringPreferencesKey("access_token")
            val REFRESH = stringPreferencesKey("refresh_token")
            val USER_ID = stringPreferencesKey("user_id")
        }

        val accessTokenFlow: Flow<String?> =
            context.tokenDataStore.data.map { it[Keys.ACCESS] }

        suspend fun accessToken(): String? = context.tokenDataStore.data.map { it[Keys.ACCESS] }.first()

        suspend fun save(
            accessToken: String,
            refreshToken: String?,
            userId: String,
        ) {
            context.tokenDataStore.edit { prefs ->
                prefs[Keys.ACCESS] = accessToken
                if (refreshToken != null) prefs[Keys.REFRESH] = refreshToken
                prefs[Keys.USER_ID] = userId
            }
        }

        suspend fun clear() {
            context.tokenDataStore.edit { it.clear() }
        }
    }
