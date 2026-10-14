package com.kudcrafts.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.kudcrafts.widgets.auth.TradingSignIn
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.RefreshLog
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.ui.Palette
import com.kudcrafts.widgets.widget.TradingBusinessReceiver
import com.kudcrafts.widgets.widget.TradingTotalReceiver
import com.kudcrafts.widgets.work.Refresher
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Sources and sign-in, the refresh log, and pin-to-home-screen. The live widget gallery replaces the
 * pin buttons in phase 4. singleTask: the OAuth redirect lands back here and closes the Custom Tab.
 */
class MainActivity : ComponentActivity() {
    private var message by mutableStateOf<String?>(null)
    private var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        Refresher.now(this)
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
                Refresher.redraw(this@MainActivity)
                version++
            }
        } else if (intent?.getStringExtra(EXTRA_SIGN_IN) == Source.Trading.key) {
            intent.removeExtra(EXTRA_SIGN_IN)
            lifecycleScope.launch { TradingSignIn.start(this@MainActivity) }
        }
    }

    @Composable
    private fun Screen() {
        val v = version
        val account by produceState<com.kudcrafts.widgets.store.Account?>(null, v) {
            value = Accounts(this@MainActivity).account(Source.Trading)
        }
        val log by produceState(emptyList<Triple<Long, String, String>>(), v) { value = RefreshLog(this@MainActivity).entries() }
        val appVersion = BuildConfigInfo.versionName(this)

        Column(
            Modifier.fillMaxSize().background(Palette.surface).safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("Kud Widgets", style = TextStyle(color = Palette.cream, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.padding(start = 8.dp))
                BasicText("v$appVersion", style = TextStyle(color = Palette.creamDim, fontSize = 13.sp))
            }
            message?.let { BasicText(it, style = TextStyle(color = Palette.cream, fontSize = 14.sp)) }

            Section("Sources")
            SourceCard(account)

            Section("Add to home screen")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("Trading · total") { pin(TradingTotalReceiver::class.java) }
                Button("Trading · by business") { pin(TradingBusinessReceiver::class.java) }
            }

            Section("Last refreshes")
            if (log.isEmpty()) Dim("Nothing yet.")
            log.takeLast(12).reversed().forEach { (at, source, outcome) ->
                Dim("${Jakarta.clock(at)}  $source  $outcome")
            }
        }
    }

    @Composable
    private fun SourceCard(account: com.kudcrafts.widgets.store.Account?) {
        val now = System.currentTimeMillis()
        val status = account?.status(now)
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF1A171B), RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText("kudtrading", style = TextStyle(color = Palette.cream, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            val line = when (status) {
                null -> "…"
                AuthStatus.SignedIn -> "Signed in as ${account.email ?: "?"}" +
                    (account.expiresAt?.let { " · until ${day(it)}" } ?: "")
                AuthStatus.SignedOut -> "Signed out"
                AuthStatus.Refused -> "kudtrading refused the key. Sign in again."
                AuthStatus.Expired -> "The key expired. Sign in again."
            }
            BasicText(line, style = TextStyle(color = if (status == AuthStatus.SignedIn || status == null) Palette.creamDim else Palette.downText, fontSize = 14.sp))
            account?.keyName?.let { Dim("Key \"$it\" · read-only · revoke in Admin → API Keys") }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (status == AuthStatus.SignedIn) {
                    Button("Refresh now") { Refresher.now(this@MainActivity); message = "Refreshing…" }
                    Button("Sign out") {
                        lifecycleScope.launch {
                            val revoked = TradingSignIn.signOut(this@MainActivity)
                            Refresher.redraw(this@MainActivity)
                            message = if (revoked) "Signed out of kudtrading and revoked the key."
                            else "Signed out here, but kudtrading didn't confirm the revoke. Check Admin → API Keys."
                            version++
                        }
                    }
                } else {
                    Button("Sign in") { lifecycleScope.launch { TradingSignIn.start(this@MainActivity) } }
                }
            }
        }
    }

    private fun pin(receiver: Class<*>) {
        val mgr = getSystemService(AppWidgetManager::class.java)
        message = if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, receiver), null, null)
            null
        } else "Your launcher can't pin from here: long-press the home screen › Widgets › Kud Widgets."
    }

    @Composable
    private fun Section(title: String) {
        BasicText(title.uppercase(), style = TextStyle(color = Palette.creamDim, fontSize = 12.sp, letterSpacing = 1.sp), modifier = Modifier.padding(top = 8.dp))
    }

    @Composable
    private fun Dim(text: String) = BasicText(text, style = TextStyle(color = Palette.creamDim, fontSize = 13.sp))

    @Composable
    private fun Button(label: String, onClick: () -> Unit) {
        BasicText(
            label,
            style = TextStyle(color = Palette.cream, fontSize = 14.sp),
            modifier = Modifier
                .border(1.dp, Palette.cream.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }

    private fun day(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("d MMM yyyy").withZone(Jakarta.zone).format(Instant.ofEpochMilli(epochMillis))

    companion object {
        const val EXTRA_SIGN_IN = "signIn"
    }
}
