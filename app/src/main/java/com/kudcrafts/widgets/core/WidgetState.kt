package com.kudcrafts.widgets.core

/** What a source's sign-in looks like right now. */
enum class AuthStatus {
    SignedIn,
    SignedOut,
    /** The server answered 401/403 to the stored key (revoked, or a server-side change). */
    Refused,
    /** The key's own expiresAt has passed; no request is made with it. */
    Expired,
}

/** The last numbers a source produced, and how the latest attempt went. */
data class Cached<T>(
    val data: T?,
    val fetchedAt: Long?,
    val lastError: String? = null,
    val lastErrorAt: Long? = null,
)

/** Everything a widget can draw (docs/PLAN.md › States every widget must draw). */
sealed interface Display<out T> {
    /** First run: signed in, nothing fetched yet. */
    data object Loading : Display<Nothing>

    /** Signed out, refused or expired: "Sign in", tap opens the app's sign-in. */
    data class SignIn(val reason: AuthStatus) : Display<Nothing>

    /** Signed in, never fetched successfully, and the last try failed. */
    data class Failed(val error: String) : Display<Nothing>

    /**
     * Numbers to show. `stale` dims them and shows `age`; `error` means the latest refresh failed and
     * these are the last good numbers.
     */
    data class Data<T>(
        val data: T,
        val fetchedAt: Long,
        val ageMinutes: Long,
        val stale: Boolean,
        val error: String?,
    ) : Display<T>

    companion object {
        /** Older than two missed 15-minute refreshes. */
        const val STALE_AFTER_MINUTES = 30L

        fun <T> resolve(auth: AuthStatus, cached: Cached<T>?, now: Long): Display<T> {
            if (auth != AuthStatus.SignedIn) return SignIn(auth)
            val data = cached?.data
            val at = cached?.fetchedAt
            if (data == null || at == null) {
                val err = cached?.lastError
                return if (err != null) Failed(err) else Loading
            }
            val age = ((now - at) / 60_000L).coerceAtLeast(0)
            val failedSince = cached.lastError != null && (cached.lastErrorAt ?: 0L) >= at
            return Data(
                data = data,
                fetchedAt = at,
                ageMinutes = age,
                stale = age > STALE_AFTER_MINUTES,
                error = if (failedSince) cached.lastError else null,
            )
        }

        /** A stored key past its expiry counts as expired, before any request is made. */
        fun authFor(hasKey: Boolean, refused: Boolean, expiresAt: Long?, now: Long): AuthStatus = when {
            !hasKey -> AuthStatus.SignedOut
            refused -> AuthStatus.Refused
            expiresAt != null && now >= expiresAt -> AuthStatus.Expired
            else -> AuthStatus.SignedIn
        }
    }
}

/** How a refresh ended, and what it does to the stored state. */
sealed interface FetchResult<out T> {
    data class Ok<T>(val data: T) : FetchResult<T>
    /** 401/403: flips the widget to "Sign in". */
    data object Refused : FetchResult<Nothing>
    data class Error(val message: String) : FetchResult<Nothing>

    companion object {
        fun forStatus(code: Int): FetchResult<Nothing>? = when (code) {
            401, 403 -> Refused
            in 200..299 -> null
            else -> Error("HTTP $code")
        }
    }
}

/** Fold a refresh result into the cache. Refused leaves the numbers alone; auth state flips elsewhere. */
fun <T> Cached<T>?.after(result: FetchResult<T>, now: Long): Cached<T> {
    val prev: Cached<T> = this ?: Cached(null, null)
    if (result is FetchResult.Ok<T>) {
        val data: T = result.data
        return Cached(data, now, null, null)
    }
    if (result is FetchResult.Error) return prev.copy(lastError = result.message, lastErrorAt = now)
    return prev
}
