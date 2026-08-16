package io.aule.android.core.model

/**
 * Une ligne du catalogue GTFS, telle que la prise de service la choisit.
 *
 * [id] est `route_id`. Les libellés de direction se fabriquent à l'écran
 * (ADR-011) : le modèle ne porte que les terminus.
 */
data class ServiceLine(
    val id: String,
    val label: String,
    val description: String,
    val mode: TransportMode,
    val colorHex: String? = null,
    val networkId: String? = null,
    val directions: List<ServiceDirection>,
)

data class ServiceDirection(
    val key: String,
    val terminus: String,
) {
    val id: Int get() = key.toIntOrNull() ?: 0
}

/**
 * Les deux terminus d'un `route_long_name` Naolib (« Hermeland - Chantrerie »).
 *
 * Port de `_endpoints` dans `SAE/lib/services/transport_repository.dart`.
 */
fun serviceLineEndpoints(longName: String): Pair<String, String> {
    val parts = longName.split(Regex("""\s+-\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
    return if (parts.size >= 2) {
        parts.first() to parts.last()
    } else {
        val left = longName.trim().ifEmpty { "" }
        left to ""
    }
}

fun compareServiceLines(a: ServiceLine, b: ServiceLine): Int {
    val byMode = a.mode.ordinal.compareTo(b.mode.ordinal)
    if (byMode != 0) return byMode
    val an = a.label.toIntOrNull()
    val bn = b.label.toIntOrNull()
    if (an != null && bn != null) return an.compareTo(bn)
    if (an != null) return -1
    if (bn != null) return 1
    return a.label.compareTo(b.label)
}
