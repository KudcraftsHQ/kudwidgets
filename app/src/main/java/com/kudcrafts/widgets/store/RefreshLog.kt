package com.kudcrafts.widgets.store

import android.content.Context
import java.io.File

/** The last refreshes, newest last, for the app's log screen. Small and append-only, trimmed to 100 lines. */
class RefreshLog(context: Context) {
    private val file = File(context.filesDir, "refresh.log")

    @Synchronized
    fun add(source: String, outcome: String, now: Long = System.currentTimeMillis()) {
        val lines = (if (file.exists()) file.readLines() else emptyList()) + "$now\t$source\t$outcome"
        file.writeText(lines.takeLast(100).joinToString("\n") + "\n")
    }

    fun entries(): List<Triple<Long, String, String>> =
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size == 3) p[0].toLongOrNull()?.let { Triple(it, p[1], p[2]) } else null
        }
}
