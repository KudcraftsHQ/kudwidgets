package com.kudcrafts.widgets.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Display
import kotlinx.coroutines.flow.first

private val Context.accounts by preferencesDataStore(name = "accounts")

/** A data source the app signs in to. */
enum class Source(val key: String, val label: String) {
    Trading("trading", "kudtrading"),
    Board("board", "board"),
}

/** Who is signed in to a source, until when. The key itself is only ever held encrypted. */
data class Account(
    val email: String?,
    val keyName: String?,
    val expiresAt: Long?,
    val refused: Boolean,
    val hasKey: Boolean,
) {
    fun status(now: Long): AuthStatus = Display.authFor(hasKey, refused, expiresAt, now)
}

class Accounts(private val context: Context) {
    private fun k(source: Source, name: String) = stringPreferencesKey("${source.key}.$name")
    private fun l(source: Source, name: String) = longPreferencesKey("${source.key}.$name")
    private fun b(source: Source, name: String) = booleanPreferencesKey("${source.key}.$name")

    private suspend fun prefs(): Preferences = context.accounts.data.first()

    suspend fun account(source: Source): Account {
        val p = prefs()
        return Account(
            email = p[k(source, "email")],
            keyName = p[k(source, "keyName")],
            expiresAt = p[l(source, "expiresAt")],
            refused = p[b(source, "refused")] ?: false,
            hasKey = p[k(source, "token")] != null,
        )
    }

    /** The bearer key, or null when signed out or it can't be decrypted. */
    suspend fun token(source: Source): String? = prefs()[k(source, "token")]?.let(Crypto::decrypt)

    suspend fun signIn(source: Source, token: String, email: String?, keyName: String?, expiresAt: Long?) {
        val enc = Crypto.encrypt(token)
        context.accounts.edit { p ->
            p[k(source, "token")] = enc
            if (email != null) p[k(source, "email")] = email else p.remove(k(source, "email"))
            if (keyName != null) p[k(source, "keyName")] = keyName else p.remove(k(source, "keyName"))
            if (expiresAt != null) p[l(source, "expiresAt")] = expiresAt else p.remove(l(source, "expiresAt"))
            p[b(source, "refused")] = false
            p.remove(k(source, "pendingState"))
            p.remove(k(source, "pendingVerifier"))
        }
    }

    suspend fun markRefused(source: Source) {
        context.accounts.edit { it[b(source, "refused")] = true }
    }

    suspend fun signOut(source: Source) {
        context.accounts.edit { p ->
            listOf("token", "email", "keyName").forEach { p.remove(k(source, it)) }
            p.remove(l(source, "expiresAt"))
            p.remove(b(source, "refused"))
        }
    }

    /** PKCE state kept across the Custom Tab round trip (the process may die meanwhile). */
    suspend fun savePending(source: Source, state: String, verifier: String) {
        val enc = Crypto.encrypt(verifier)
        context.accounts.edit {
            it[k(source, "pendingState")] = state
            it[k(source, "pendingVerifier")] = enc
        }
    }

    suspend fun pending(source: Source): Pair<String, String>? {
        val p = prefs()
        val state = p[k(source, "pendingState")] ?: return null
        val verifier = p[k(source, "pendingVerifier")]?.let(Crypto::decrypt) ?: return null
        return state to verifier
    }

    suspend fun clearPending(source: Source) {
        context.accounts.edit {
            it.remove(k(source, "pendingState"))
            it.remove(k(source, "pendingVerifier"))
        }
    }
}
