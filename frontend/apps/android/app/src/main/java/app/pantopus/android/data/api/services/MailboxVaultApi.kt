package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.mailbox.vault.CreateVaultFolderRequest
import app.pantopus.android.data.api.models.mailbox.vault.FileToVaultRequest
import app.pantopus.android.data.api.models.mailbox.vault.FileToVaultResponse
import app.pantopus.android.data.api.models.mailbox.vault.VaultFolderItemsResponse
import app.pantopus.android.data.api.models.mailbox.vault.VaultFolderResponse
import app.pantopus.android.data.api.models.mailbox.vault.VaultFoldersResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * T6.5e (P19.5) — Mailbox Vault routes from
 * `backend/routes/mailboxV2Phase2.js`. The phase-2 routes are mounted
 * at `/api/mailbox/v2/p2` (see `backend/app.js:314`).
 */
interface MailboxVaultApi {
    /** `GET /api/mailbox/v2/p2/vault/folders` — route `backend/routes/mailboxV2Phase2.js:952`. */
    @GET("api/mailbox/v2/p2/vault/folders")
    suspend fun folders(
        @Query("drawer") drawer: String? = "personal",
    ): VaultFoldersResponse

    /** `POST /api/mailbox/v2/p2/vault/folder` — route `backend/routes/mailboxV2Phase2.js:983`. */
    @POST("api/mailbox/v2/p2/vault/folder")
    suspend fun createFolder(
        @Body body: CreateVaultFolderRequest,
    ): VaultFolderResponse

    /** `GET /api/mailbox/v2/p2/vault/folder/:folderId/items` — route `backend/routes/mailboxV2Phase2.js:1033`. */
    @GET("api/mailbox/v2/p2/vault/folder/{folderId}/items")
    suspend fun folderItems(
        @Path("folderId") folderId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): VaultFolderItemsResponse

    /** `POST /api/mailbox/v2/p2/vault/file` — route `backend/routes/mailboxV2Phase2.js:1054`. */
    @POST("api/mailbox/v2/p2/vault/file")
    suspend fun file(
        @Body body: FileToVaultRequest,
    ): FileToVaultResponse
}
