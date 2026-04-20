@file:Suppress("MagicNumber")

package app.pantopus.android.data.api.net

/**
 * Result of a network call — either a successful decoded body or a typed
 * [NetworkError]. Call sites switch on this rather than on raw HTTP codes.
 */
sealed interface NetworkResult<out T> {
    /** Successful response with a decoded body. */
    data class Success<T>(val data: T) : NetworkResult<T>

    /** Failure — carries the typed error for the UI to route on. */
    data class Failure(val error: NetworkError) : NetworkResult<Nothing>
}

/** Every failure mode of the Pantopus HTTP client. */
sealed class NetworkError(
    val code: Int?,
    override val message: String,
    override val cause: Throwable? = null,
) : Throwable(message, cause) {
    /** 401 — token missing or expired. Callers should redirect to sign-in. */
    data object Unauthorized : NetworkError(401, "Your session has expired.")

    /** 403 — authenticated but not permitted. */
    data object Forbidden : NetworkError(403, "You don't have permission to do that.")

    /** 404 — resource not found. */
    data object NotFound : NetworkError(404, "We couldn't find what you were looking for.")

    /** 4xx with server-supplied message. */
    class ClientError(code: Int, val body: String?) :
        NetworkError(code, body ?: "Request failed ($code).")

    /** 5xx after retries exhausted. */
    class Server(code: Int, val body: String?) :
        NetworkError(code, "Server error $code. Please try again.")

    /** Network-layer failure (offline, timeout, DNS). */
    class Transport(cause: Throwable) :
        NetworkError(null, "Can't reach Pantopus. Check your connection.", cause)

    /** Response decoded into an unexpected shape. */
    class Decoding(cause: Throwable) :
        NetworkError(null, "Received an unexpected response.", cause)

    /** Retry loop exhausted without a 2xx. */
    data object RetriesExhausted :
        NetworkError(null, "The server is having trouble. Please try again.")
}
