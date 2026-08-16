package io.aule.android.traces

import android.content.Context
import io.aule.android.BuildConfig
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.GpsTraceFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Les CSV de diagnostic GPS, dans `files/traces/`.
 *
 * Actif en debug, comme Flutter (`kDebugMode || ENABLE_GPS_TRACES`). Le
 * guidage n'écrit pas encore ici : l'écran profil liste, exporte et purge.
 */
class FileGpsTraceCatalog(
    private val directory: File,
    override val enabled: Boolean,
) : GpsTraceCatalog {

    constructor(
        context: Context,
        enabled: Boolean = BuildConfig.DEBUG,
    ) : this(File(context.applicationContext.filesDir, "traces"), enabled)

    override suspend fun list(): List<GpsTraceFile> = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext emptyList()
        val files = directory.listFiles { file -> file.isFile && file.extension == "csv" }
            ?: return@withContext emptyList()
        files.sortedByDescending { it.name }.map { file ->
            GpsTraceFile(
                name = file.name,
                path = file.absolutePath,
                bytes = file.length(),
            )
        }
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
        Unit
    }
}
