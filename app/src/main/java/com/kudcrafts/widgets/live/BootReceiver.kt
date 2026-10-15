package com.kudcrafts.widgets.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Back to live after a reboot or an update (Obtainium's included): two of the few moments a background start is allowed. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Live.ensure(context)
    }
}
