package app.pantopus.android.data.mailbox

import app.pantopus.android.data.api.models.mailbox.MailboxListResponse
import app.pantopus.android.data.api.models.mailbox.v2.DrawerListResponse
import app.pantopus.android.data.api.models.mailbox.v2.MailboxItemActionRequest
import app.pantopus.android.data.api.models.mailbox.v2.MailboxItemActionResponse
import app.pantopus.android.data.api.models.mailbox.v2.MailboxV2ItemResponse
import app.pantopus.android.data.api.models.mailbox.v2.PackageDetailResponse
import app.pantopus.android.data.api.models.mailbox.v2.PackageStatusUpdateRequest
import app.pantopus.android.data.api.models.mailbox.v2.PackageStatusUpdateResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.MailboxApi
import app.pantopus.android.data.api.services.MailboxV2Api
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [MailboxApi] + [MailboxV2Api] that returns the typed
 * [NetworkResult] taxonomy.
 */
@Singleton
class MailboxRepository
    @Inject
    constructor(
        private val mailboxApi: MailboxApi,
        private val v2Api: MailboxV2Api,
    ) {
        /** `GET /api/mailbox`. */
        @Suppress("LongParameterList")
        suspend fun list(
            viewed: Boolean?,
            archived: Boolean,
            starred: Boolean?,
            limit: Int,
            offset: Int,
        ): NetworkResult<MailboxListResponse> =
            safeApiCall {
                mailboxApi.list(
                    viewed = viewed,
                    archived = archived,
                    starred = starred,
                    limit = limit,
                    offset = offset,
                )
            }

        /** `GET /api/mailbox/v2/drawers`. */
        suspend fun drawers(): NetworkResult<DrawerListResponse> = safeApiCall { v2Api.drawers() }

        /** `GET /api/mailbox/v2/item/:id`. */
        suspend fun item(mailId: String): NetworkResult<MailboxV2ItemResponse> = safeApiCall { v2Api.item(mailId) }

        /** `GET /api/mailbox/v2/package/:mailId`. */
        suspend fun packageDetail(mailId: String): NetworkResult<PackageDetailResponse> = safeApiCall { v2Api.packageDetail(mailId) }

        /** `POST /api/mailbox/v2/item/:id/action`. */
        suspend fun itemAction(
            mailId: String,
            action: String,
        ): NetworkResult<MailboxItemActionResponse> = safeApiCall { v2Api.itemAction(mailId, MailboxItemActionRequest(action)) }

        /** `PATCH /api/mailbox/v2/package/:mailId/status`. */
        suspend fun packageStatusUpdate(
            mailId: String,
            status: String,
        ): NetworkResult<PackageStatusUpdateResponse> =
            safeApiCall { v2Api.packageStatusUpdate(mailId, PackageStatusUpdateRequest(status = status)) }
    }
