package com.kudcrafts.widgets.store

import android.content.Context
import com.kudcrafts.widgets.core.Cached
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class CachedFile<T>(
    val data: T? = null,
    val fetchedAt: Long? = null,
    val lastError: String? = null,
    val lastErrorAt: Long? = null,
)

/** The last numbers per source, as JSON in the app's files dir. Widgets draw from here, never the network. */
class CacheStore<T>(context: Context, name: String, private val serializer: KSerializer<T>) {
    private val file = File(context.filesDir, "cache-$name.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val fileSerializer = CachedFile.serializer(serializer)

    @Synchronized
    fun read(): Cached<T>? = runCatching {
        if (!file.exists()) return null
        val f = json.decodeFromString(fileSerializer, file.readText())
        Cached(f.data, f.fetchedAt, f.lastError, f.lastErrorAt)
    }.getOrNull()

    @Synchronized
    fun write(value: Cached<T>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(fileSerializer, CachedFile(value.data, value.fetchedAt, value.lastError, value.lastErrorAt)))
        tmp.renameTo(file)
    }

    fun clear() {
        file.delete()
    }
}
