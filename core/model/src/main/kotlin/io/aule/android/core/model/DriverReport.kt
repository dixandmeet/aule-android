package io.aule.android.core.model

/**
 * Un signalement de terrain — ce qu'un conducteur voit et que le régulateur
 * doit savoir.
 *
 * Les [wire] recopient le `CHECK` de `public.driver_reports` (migration
 * `021_driver_mvp.sql`). Un type mal orthographié ne casse ni la compilation
 * ni un test d'écran : il casse **l'insertion, chez le conducteur**. Les
 * libellés, eux, vivent dans `strings.xml` (ADR-011).
 *
 * L'ordre des constantes est celui de l'écran, du plus fréquent au plus
 * rare — pas celui de la migration.
 */
enum class DriverReportType(val wire: String) {
    TRAFFIC("traffic"),
    DELAY("delay"),
    DETOUR("detour"),
    CROWDED("crowded"),
    STOP_SKIPPED("stop_skipped"),
    BREAKDOWN("breakdown"),
    ACCIDENT("accident"),
    PASSENGER_ILLNESS("passenger_illness"),
    INCIVILITY("incivility"),
    OTHER("other"),
}

enum class DriverReportUrgency(val wire: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

data class DriverReport(
    val type: DriverReportType,
    val urgency: DriverReportUrgency = DriverReportUrgency.MEDIUM,
    val message: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /**
     * Le corps que `driver_reports` attend.
     *
     * `driver_id` n'est pas laissé à l'écran : la RLS n'accepte que
     * `current_driver_id()`, et le laisser à l'appelant serait offrir de
     * signaler au nom d'un autre. Les clés absentes plutôt que nulles :
     * envoyer `null` écrirait « on ne sait pas » là où « on n'a pas
     * demandé » est plus juste.
     */
    fun toInsert(
        driverId: String,
        driverServiceId: String? = null,
        vehicleId: String? = null,
    ): Map<String, Any> {
        val body = linkedMapOf<String, Any>(
            "driver_id" to driverId,
            "type" to type.wire,
            "urgency" to urgency.wire,
        )
        driverServiceId?.let { body["driver_service_id"] = it }
        vehicleId?.let { body["vehicle_id"] = it }
        message?.trim()?.takeIf { it.isNotEmpty() }?.let { body["message"] = it }
        latitude?.let { body["latitude"] = it }
        longitude?.let { body["longitude"] = it }
        return body
    }
}

enum class DriverReportFailureKind {
    NOT_SIGNED_IN,
    NO_DRIVER,
    NOT_CONFIGURED,
    NETWORK,
    REJECTED,
    UNKNOWN,
}

class DriverReportException(
    val kind: DriverReportFailureKind,
) : Exception(kind.name)
