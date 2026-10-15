package com.kudcrafts.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kudcrafts.widgets.auth.TradingSignIn
import com.kudcrafts.widgets.board.BoardSignIn
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.gallery.WidgetKind
import com.kudcrafts.widgets.gallery.WidgetPreview
import com.kudcrafts.widgets.live.Live
import com.kudcrafts.widgets.store.Account
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.RefreshLog
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.store.WidgetOptions
import com.kudcrafts.widgets.ui.AppUi
import com.kudcrafts.widgets.ui.Palette
import com.kudcrafts.widgets.work.Refresher
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * The app is the gallery: every widget drawn live from the same content the launcher shows, with "Add to home
 * screen"; then the sources and their sign-in; then the refresh log.
 * singleTask: the kudtrading OAuth redirect lands back here and closes the Custom Tab.
 */
class MainActivity : ComponentActivity() {
    private var message by mutableStateOf<String?>(null)
    /** The board device code while the person approves it in the browser. */
    private var boardCode by mutableStateOf<String?>(null)
    private var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        Refresher.now(this)
        Live.ensure(this)
        // The live notification is the service's face; without the permission it runs hidden.
        if (Live.enabled(this) && !Live.canNotify(this) && android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // Redraw the gallery whenever a refresh finishes.
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("refresh-now").observe(this) { infos ->
            if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) version++
        }
        setContent { Screen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        version++
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data
        if (TradingSignIn.isCallback(data)) {
            message = "Signing in…"
            lifecycleScope.launch {
                message = TradingSignIn.finish(this@MainActivity, data!!) ?: "Signed in to kudtrading."
                Refresher.now(this@MainActivity)
                Live.ensure(this@MainActivity)
                Refresher.redraw(this@MainActivity)
                version++
            }
            return
        }
        when (intent?.getStringExtra(EXTRA_SIGN_IN)) {
            Source.Trading.key -> {
                intent.removeExtra(EXTRA_SIGN_IN)
                lifecycleScope.launch { TradingSignIn.start(this@MainActivity) }
            }
            Source.Board.key -> {
                intent.removeExtra(EXTRA_SIGN_IN)
                signInBoard()
            }
        }
    }

    private fun signInBoard() {
        if (boardCode != null) return
        lifecycleScope.launch {
            message = "Starting board sign-in…"
            val error = BoardSignIn.run(this@MainActivity) { code ->
                boardCode = code.userCode
                message = "Approve code ${code.userCode} in board, with read on your products."
            }
            boardCode = null
            message = error ?: "Signed in to board."
            Refresher.now(this@MainActivity)
            Live.ensure(this@MainActivity)
            Refresher.redraw(this@MainActivity)
            version++
        }
    }

    @Composable
    private fun Screen() {
        val v = version
        val trading by produceState<Account?>(null, v) { value = Accounts(this@MainActivity).account(Source.Trading) }
        val board by produceState<Account?>(null, v) { value = Accounts(this@MainActivity).account(Source.Board) }
        val log by produceState(emptyList<Triple<Long, String, String>>(), v) { value = RefreshLog(this@MainActivity).entries() }
        val appVersion = BuildConfigInfo.versionName(this)

        Column(
            Modifier.fillMaxSize().background(Palette.appBackground).safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("Kud Widgets", style = TextStyle(color = Palette.cream, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.padding(start = 8.dp))
                AppUi.Dim("v$appVersion")
            }
            message?.let { AppUi.Body(it) }

            AppUi.Section("Widgets")
            WidgetKind.entries.forEach { kind ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetPreview(kind, WidgetOptions(), v)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppUi.Button("Add to home screen") { pin(kind) }
                        AppUi.Dim(kind.title)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            AppUi.Dim("Long-press a placed widget › reconfigure (or the pencil) to choose its period, business or product, and tap link.")

            AppUi.Section("Sources")
            SourceCard(Source.Trading, trading)
            SourceCard(Source.Board, board)
            LiveCard(v)

            AppUi.Section("Last refreshes")
            if (log.isEmpty()) AppUi.Dim("Nothing yet.")
            log.takeLast(12).reversed().forEach { (at, source, outcome) ->
                AppUi.Dim("${Jakarta.clock(at)}  $source  ${outcome.lowercase()}")
            }
        }
    }

    @Composable
    private fun SourceCard(source: Source, account: Account?) {
        val status = account?.status(System.currentTimeMillis())
        val revokeHint = if (source == Source.Trading) "read-only · revoke in Admin → API Keys" else "revoke in board → Settings → CLI devices"
        Column(
            Modifier.fillMaxWidth().background(AppUi.card, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppUi.Title(source.label)
            val line = when (status) {
                null -> "…"
                AuthStatus.SignedIn -> "Signed in" + (account.email?.let { " as $it" } ?: "") +
                    (account.expiresAt?.let { " · until ${day(it)}" } ?: "")
                AuthStatus.SignedOut -> "Signed out"
                AuthStatus.Refused -> "${source.label} refused the key. Sign in again."
                AuthStatus.Expired -> "The key expired. Sign in again."
            }
            AppUi.Body(line, if (status == AuthStatus.SignedIn || status == null || status == AuthStatus.SignedOut) Palette.creamDim else Palette.downText)
            account?.keyName?.let { AppUi.Dim("\"$it\" · $revokeHint") }
            if (source == Source.Board && boardCode != null) {
                BasicText(boardCode!!, style = TextStyle(color = Palette.cream, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
                AppUi.Dim("Waiting for approval in board…")
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (status == AuthStatus.SignedIn) {
                    AppUi.Button("Refresh now") { Refresher.now(this@MainActivity); message = "Refreshing…" }
                    AppUi.Button("Sign out") {
                        lifecycleScope.launch {
                            val revoked = if (source == Source.Trading) TradingSignIn.signOut(this@MainActivity)
                            else BoardSignIn.signOut(this@MainActivity)
                            Refresher.redraw(this@MainActivity)
                            Live.ensure(this@MainActivity)
                            message = if (revoked) "Signed out of ${source.label} and revoked the key."
                            else "Signed out here, but ${source.label} didn't confirm the revoke. Check it there."
                            version++
                        }
                    }
                } else if (!(source == Source.Board && boardCode != null)) {
                    AppUi.Button("Sign in") {
                        if (source == Source.Trading) lifecycleScope.launch { TradingSignIn.start(this@MainActivity) }
                        else signInBoard()
                    }
                }
            }
        }
    }

    /** Live updates: on or off, how the streams are doing, and the two system settings it needs to stay up. */
    @Composable
    private fun LiveCard(v: Int) {
        val live by Live.state.collectAsState()
        val enabled = v.let { Live.enabled(this) }
        val canNotify = v.let { Live.canNotify(this) }
        val unrestricted = v.let { Live.unrestricted(this) }
        Column(
            Modifier.fillMaxWidth().background(AppUi.card, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppUi.Title("Live updates")
            val active = live.links.values.count { it != Live.Link.Refused }
            val line = when {
                !enabled -> "Off · widgets refresh every 15 minutes"
                !live.running -> "Starting…"
                !live.online -> "Offline · waiting for network"
                live.links.isEmpty() -> "Sign in to a source first"
                live.liveCount == active -> "Connected to ${live.liveCount} stream${if (live.liveCount == 1) "" else "s"} · widgets update as numbers move"
                else -> "Connected to ${live.liveCount} of $active streams · reconnecting"
            }
            AppUi.Body(line, Palette.creamDim)
            live.lastEventAt?.let { AppUi.Dim("Last change pushed ${Jakarta.clock(it)}") }
            if (enabled && !unrestricted) AppUi.Dim("Battery optimisation can cut the connection while the phone sleeps.")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppUi.Button(if (enabled) "Turn off" else "Turn on") {
                    Live.setEnabled(this@MainActivity, !enabled)
                    version++
                }
                if (enabled && !unrestricted) AppUi.Button("Keep alive") {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(android.net.Uri.parse("package:$packageName")),
                    )
                }
                if (enabled && !canNotify && android.os.Build.VERSION.SDK_INT >= 33) AppUi.Button("Show notification") {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        version++
    }

    private fun pin(kind: WidgetKind) {
        val mgr = getSystemService(AppWidgetManager::class.java)
        message = if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, kind.receiver), null, null)
            null
        } else "Your launcher can't add widgets from here: long-press the home screen › Widgets › Kud Widgets."
    }

    private fun day(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("d MMM yyyy").withZone(Jakarta.zone).format(Instant.ofEpochMilli(epochMillis))

    companion object {
        const val EXTRA_SIGN_IN = "signIn"
    }
}
