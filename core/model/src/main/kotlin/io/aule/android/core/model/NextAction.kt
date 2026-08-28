package io.aule.android.core.model

/**
 * « Que dois-je faire maintenant ? » — et rien d'autre.
 *
 * Port de `SAE/lib/navigation/next_action.dart`.
 *
 * ⚠️ Aucune horloge en entrée : sans elle, aucune arrivée ne peut être
 * calculée. La distance totale restante n'entre pas non plus dans le
 * bandeau d'un trajet d'une seule jambe — ce serait le résumé que la
 * barre du bas porte déjà.
 */

enum class NextActionKind {
    ARRIVE,
    TRANSFER,
    ALIGHT,
    BOARD,
    MANEUVER,
    FOLLOW,
}

data class NextAction(
    val kind: NextActionKind,
    val title: String,
    val mode: LegMode,
    val leadMeters: Double? = null,
    val leadStops: Int? = null,
    val leadIsTransfer: Boolean = false,
    val detail: String? = null,
    val maneuver: ManeuverKind? = null,
    /** La sortie du rond-point, quand la manœuvre en est un et qu'OSRM l'a dite. */
    val maneuverExit: Int? = null,
    val line: String? = null,
    val lineColor: String? = null,
    val destinationLabel: String? = null,
)

const val BOARDING_NOTICE_M = 150.0

fun nextAction(
    plan: JourneyPlan,
    progress: JourneyProgress,
    maneuvers: List<PinnedManeuver> = emptyList(),
    stopsToAlight: Int? = null,
    alightStopName: String? = null,
    transferStopName: String? = null,
    transferPlatform: String? = null,
): NextAction? {
    if (plan.isEmpty) return null
    val legs = plan.legs
    val leg = legs[progress.legIndex.coerceIn(0, legs.lastIndex)]
    val next = legs.getOrNull(progress.legIndex + 1)

    if (progress.arrived) {
        return NextAction(
            kind = NextActionKind.ARRIVE,
            title = plan.destinationLabel ?: "",
            mode = leg.mode,
            destinationLabel = plan.destinationLabel,
        )
    }

    val isTransfer = leg.mode == LegMode.WALK &&
        next?.mode == LegMode.TRANSIT &&
        progress.legIndex > 0 &&
        legs[progress.legIndex - 1].mode == LegMode.TRANSIT
    if (isTransfer) {
        // `isTransfer` a déjà exigé `next?.mode == LegMode.TRANSIT` : le
        // compilateur sait ici que `next` n'est pas nul, et un appel sûr de
        // plus laisserait croire le contraire au lecteur.
        val target = transferStopName ?: next.line
        return NextAction(
            kind = NextActionKind.TRANSFER,
            title = target.orEmpty(),
            leadIsTransfer = true,
            detail = transferPlatform,
            mode = leg.mode,
            line = next.line,
            lineColor = next.lineColor,
        )
    }

    if (leg.mode == LegMode.TRANSIT) {
        if (stopsToAlight != null && stopsToAlight > 0) {
            return NextAction(
                kind = NextActionKind.ALIGHT,
                title = alightStopName.orEmpty(),
                leadStops = stopsToAlight,
                mode = leg.mode,
                line = leg.line,
                lineColor = leg.lineColor,
            )
        }
        return NextAction(
            kind = NextActionKind.FOLLOW,
            title = leg.title,
            mode = leg.mode,
            line = leg.line,
            lineColor = leg.lineColor,
        )
    }

    val ahead = nextManeuver(
        maneuvers.filter { it.t <= leg.endT },
        progress.routeT,
        plan.distanceMeters,
    )
    if (ahead != null) {
        val street = ahead.maneuver.streetName
        return NextAction(
            kind = NextActionKind.MANEUVER,
            title = street ?: "",
            leadMeters = ahead.meters,
            maneuver = ahead.maneuver.kind,
            maneuverExit = ahead.maneuver.exit,
            mode = leg.mode,
        )
    }

    if (next?.mode == LegMode.TRANSIT && progress.legRemainingMeters <= BOARDING_NOTICE_M) {
        return NextAction(
            kind = NextActionKind.BOARD,
            title = next.line.orEmpty(),
            leadMeters = progress.legRemainingMeters,
            detail = next.title,
            mode = leg.mode,
            line = next.line,
            lineColor = next.lineColor,
        )
    }

    return NextAction(
        kind = NextActionKind.FOLLOW,
        title = leg.title,
        leadMeters = if (legs.size > 1) progress.legRemainingMeters else null,
        mode = leg.mode,
        line = leg.line,
        lineColor = leg.lineColor,
    )
}
