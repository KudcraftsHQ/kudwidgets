package com.kudcrafts.widgets

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.kudcrafts.widgets.work.Refresher

/** A widget tap: refresh in the background, open the dashboard, leave no screen behind. */
class OpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Refresher.now(this)
        intent.getStringExtra(EXTRA_URL)?.let { url ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
        }
        finish()
    }

    companion object {
        private const val EXTRA_URL = "url"

        fun intent(context: Context, url: String): Intent =
            Intent(context, OpenActivity::class.java)
                .putExtra(EXTRA_URL, url)
                // Distinct data per URL, so PendingIntents for different bands don't collapse into one.
                .setData(Uri.parse("kudwidgets-open:" + url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
