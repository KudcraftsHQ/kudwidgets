package com.kudcrafts.widgets.live

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The live connection's on/off switch and its current state, shared by the service, the app and the notification. */
object Live {
    private const val PREFS = "live"
    private const val ENABLED = "enabled"

    /** On by default: signing in to a source is the opt-in. */
    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, on).apply()
        if (on) ensure(context) else stop(context)
    }

    /**
     * Start the service if it should run. Android only lets a foreground service start from the foreground (an
     * activity, boot, an app update) or from an app exempt from battery optimisation; anywhere else this quietly
     * does nothing and the next app open starts it.
     */
    fun ensure(context: Context) {
        if (!enabled(context)) return
        try {
            context.startForegroundService(Intent(context, LiveService::class.java))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException.
            Log.i("Live", "not allowed to start from here: ${e.message}")
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, LiveService::class.java))
        _state.value = State()
    }

    fun refreshNow(context: Context) {
        context.startService(Intent(context, LiveService::class.java).setAction(LiveService.ACTION_REFRESH))
    }

    /** Without the exemption Doze cuts the connection while the phone sleeps. */
    fun unrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    enum class Link { Connecting, Live, Waiting, Refused }

    /** Each stream's link, by stream name; the time the numbers last moved because of an event. */
    data class State(
        val running: Boolean = false,
        val online: Boolean = true,
        val links: Map<Stream, Link> = emptyMap(),
        val lastEventAt: Long? = null,
    ) {
        val liveCount get() = links.values.count { it == Link.Live }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    internal fun update(f: (State) -> State) {
        synchronized(this) { _state.value = f(_state.value) }
    }
}
