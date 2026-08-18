package io.aule.android.traces

import android.content.Context
import io.aule.android.BuildConfig
import io.aule.android.core.model.GPS_TRACE_CSV_HEADER
import io.aule.android.core.model.GpsTracePoint
import io.aule.android.core.model.toCsvRow
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.GpsTraceFile
import io.aule.android.core.model.repository.GpsTraceRecorder
import java.io.BufferedWriter
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Les CSV de diagnostic GPS, dans `files/traces/`.
 *
 * Actif en debug, comme Flutter (`kDebugMode || ENABLE_GPS_TRACES`). L'écran
 * profil les liste, les exporte et les purge ; le guidage les écrit.
 */
class FileGpsTraceCatalog(
    private val directory: File,
    override val enabled: Boolean,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
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

    /**
     * Le nom porte l'heure locale, à la seconde.
     *
     * Locale, parce que c'est celle sous laquelle le conducteur reconnaîtra
     * son essai en ouvrant le partage ; à la seconde, parce que c'est ce qui
     * garantit que deux guidages ne se disputent pas le même fichier — même le
     * dimanche d'octobre où l'heure recule.
     *
     * L'ordre du nom est celui du temps : [list] trie dessus, et la trace la
     * plus récente se lit en tête sans qu'on ait à ouvrir les fichiers.
     */
    override fun startRecording(): GpsTraceRecorder? {
        if (!enabled) return null
        val stamp = FILE_STAMP.withZone(zone).format(now())
        return FileGpsTraceRecorder(directory, "trace-$stamp.csv")
    }

    private companion object {
        val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
    }
}

/**
 * Une trace ouverte : les points entrent sans attendre, le disque suit.
 *
 * La boucle de guidage tourne une fois par seconde sur le fil principal ; elle
 * n'a pas à attendre un `write`. Les points passent donc par une file, qu'une
 * seule coroutine vide sur le fil d'entrées-sorties. La file est sans borne à
 * dessein : un guidage produit un point par seconde, et laisser tomber une
 * mesure pour économiser quelques octets ferait mentir la trace au moment
 * précis où l'on cherche ce qui s'est passé.
 *
 * **Rien de ce qui rate ici ne remonte.** Un disque plein, un dossier
 * supprimé sous les pieds : une trace de diagnostic qui interromprait le
 * guidage serait une bien plus grosse panne que celle qu'elle documente.
 */
private class FileGpsTraceRecorder(
    private val directory: File,
    private val fileName: String,
) : GpsTraceRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val points = Channel<GpsTracePoint>(Channel.UNLIMITED)
    private val writing = scope.launch { drain() }

    override fun record(point: GpsTracePoint) {
        points.trySend(point)
    }

    override suspend fun close() {
        points.close()
        writing.join()
        withContext(Dispatchers.IO) { prune() }
        scope.cancel()
    }

    /**
     * Le fichier naît au premier point, pas à l'ouverture : un guidage
     * démarré puis arrêté aussitôt — le geste de quelqu'un qui s'est trompé de
     * destination — ne laisse pas un fichier vide dans la liste.
     */
    private suspend fun drain() {
        var writer: BufferedWriter? = null
        try {
            for (point in points) {
                if (writer == null) {
                    directory.mkdirs()
                    writer = File(directory, fileName).bufferedWriter()
                    writer.appendLine(GPS_TRACE_CSV_HEADER)
                }
                writer.appendLine(point.toCsvRow())
            }
        } catch (_: Exception) {
            // Voir le KDoc de la classe : la trace se tait, le guidage continue.
        } finally {
            writer?.runCatching {
                flush()
                close()
            }
        }
    }

    /**
     * Ne garde que les dernières traces.
     *
     * Sans borne, un dossier de diagnostic grossit jusqu'à devenir le
     * problème qu'il devait aider à résoudre. Vingt essais, c'est de quoi
     * comparer une semaine de mises au point ; au-delà, on ne relit plus, on
     * archive.
     */
    private fun prune() {
        val files = directory.listFiles { file -> file.isFile && file.extension == "csv" }
            ?: return
        files.sortedByDescending { it.name }
            .drop(KEPT_TRACES)
            .forEach { file -> runCatching { file.delete() } }
    }

    private companion object {
        const val KEPT_TRACES = 20
    }
}
