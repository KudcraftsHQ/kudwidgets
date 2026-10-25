package com.kudcrafts.widgets.live

import android.content.Context
import com.kudcrafts.widgets.core.Jakarta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * What each live stream did, newest last: connects, how long the server took to answer, what it said, how it ended.
 * Kept in a file so it survives the service restarting; shown in the app with a Copy button for debugging.
 */
object StreamLog {
    private const val MAX = 200
    private const val FILE = "stream-log.txt"
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(Jakarta.zone)

    private var file: File? = null
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun init(context: Context) = synchronized(this) {
        if (file != null) return
        file = File(context.filesDir, FILE).also { f ->
            _lines.value = runCatching { f.readLines().takeLast(MAX) }.getOrDefault(emptyList())
        }
    }

    fun add(stream: Stream?, message: String) = synchronized(this) {
        val who = stream?.let { "${it.source.name.lowercase()}·${it.name}" } ?: "service"
        val line = "${clock.format(Instant.now())} $who  $message"
        android.util.Log.i("StreamLog", line)
        val next = (_lines.value + line).takeLast(MAX)
        _lines.value = next
        file?.let { f -> runCatching { f.writeText(next.joinToString("\n", postfix = "\n")) } }
    }

    fun clear() = synchronized(this) {
        _lines.value = emptyList()
        file?.delete()
    }

    fun text(): String = _lines.value.joinToString("\n")
}
