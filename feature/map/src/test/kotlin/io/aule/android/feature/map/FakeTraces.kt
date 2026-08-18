package io.aule.android.feature.map

import io.aule.android.core.model.GpsTracePoint
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.GpsTraceFile
import io.aule.android.core.model.repository.GpsTraceRecorder

/** Le catalogue de production : coupé, donc aucun enregistreur à porter. */
internal object NoTraces : GpsTraceCatalog {
    override val enabled: Boolean = false
    override suspend fun list(): List<GpsTraceFile> = emptyList()
    override suspend fun deleteAll() = Unit
    override fun startRecording(): GpsTraceRecorder? = null
}

/** Un catalogue qui retient ce qu'on lui écrit, pour qu'un test puisse le lire. */
internal class RecordingTraces : GpsTraceCatalog {
    val recorders = mutableListOf<FakeRecorder>()
    override val enabled: Boolean = true
    override suspend fun list(): List<GpsTraceFile> = emptyList()
    override suspend fun deleteAll() = Unit
    override fun startRecording(): GpsTraceRecorder =
        FakeRecorder().also { recorders += it }
}

internal class FakeRecorder : GpsTraceRecorder {
    val points = mutableListOf<GpsTracePoint>()
    var closed = false
        private set

    override fun record(point: GpsTracePoint) {
        points += point
    }

    override fun close() {
        closed = true
    }
}
