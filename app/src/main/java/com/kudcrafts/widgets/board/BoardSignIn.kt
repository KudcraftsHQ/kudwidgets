package com.kudcrafts.widgets.board

import android.content.Context
import com.kudcrafts.widgets.auth.TradingSignIn
import com.kudcrafts.widgets.net.Http
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * board sign-in from the app: start a device code, open board's approval page in a Custom Tab (approve with
 * **read** on the products the widgets should show), poll until approved. The device appears in board →
 * Settings → CLI devices as "Kud Widgets · <phone>".
 */
object BoardSignIn {
    fun deviceName(): String = "Kud Widgets · ${TradingSignIn.deviceName()}"

    /** Returns null on success, or words for the person. `onCode` shows the code while the person approves. */
    suspend fun run(context: Context, onCode: (DeviceFlow.Code) -> Unit): String? {
        val api = BoardApi(Http.client)
        val code = when (val s = withContext(Dispatchers.IO) { api.startDevice(deviceName()) }) {
            is BoardApi.Start.Error -> return s.message
            is BoardApi.Start.Ok -> s.code
        }
        onCode(code)
        TradingSignIn.openTab(context, code.verificationUriComplete)
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        var interval = code.interval.coerceAtLeast(1)
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val poll = withContext(Dispatchers.IO) { api.poll(code.deviceCode) }
            if (poll is DeviceFlow.Poll.Done) {
                val t = poll.token
                BoardRepo.clear(context)
                Accounts(context).signIn(
                    Source.Board,
                    token = t.accessToken,
                    email = t.user?.email,
                    keyName = t.token?.name,
                    expiresAt = t.expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                )
                return null
            }
            if (poll is DeviceFlow.Poll.Failed) return poll.message
            interval = DeviceFlow.nextInterval(interval, poll) ?: return "Sign-in failed."
        }
        return "The code expired. Try again."
    }

    /** `board logout`, then forget the token here. True when board revoked it. */
    suspend fun signOut(context: Context): Boolean = withContext(Dispatchers.IO) {
        val accounts = Accounts(context)
        val revoked = accounts.token(Source.Board)?.let { BoardApi(Http.client).logout(it) } ?: false
        accounts.signOut(Source.Board)
        BoardRepo.clear(context)
        revoked
    }
}
