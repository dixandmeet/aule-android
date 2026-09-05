package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath

/**
 * Un arrêt du parcours affiché — **le même objet pour la carte et pour la
 * liste**.
 *
 * ## Pourquoi ce type existe
 *
 * La fiche d'une ligne montre deux fois la même chose : une colonne d'arrêts
 * dans le volet, des pastilles sur le tracé. Tant que chacune se construit de
 * son côté, rien ne garantit qu'elles disent la même chose, et l'écart ne se
 * verrait nulle part : un rang de plus dans le volet que sur la carte se lit
 * comme une carte incomplète, pas comme un défaut.
 *
 * Le marqueur est donc **le** parcours, calculé une fois, lu par les deux. Port
 * de `Native/Aule/Models/LineStopMarker.swift`.
 *
 * ## Un marqueur par lieu, pas par rang
 *
 * Une desserte qui repasse au même endroit y poserait deux pastilles
 * exactement superposées, la seconde masquant la première sans que rien ne le
 * dise. Les rangs qui désignent le même lieu sont donc réunis, et [sequences]
 * garde lesquels.
 *
 * @param id l'identité du marqueur **dans ce parcours**, et rien d'autre : le
 *   rang du premier passage, et non l'identifiant de l'arrêt. Le même lieu peut
 *   être desservi par deux parcours de la même ligne, et deux marqueurs de même
 *   identifiant dans deux listes distinctes ne gênent personne — alors qu'un
 *   identifiant d'arrêt répété **dans un même parcours** ferait sauter la
 *   sélection d'un rang à l'autre.
 * @param coordinate le centre des positions réunies ici. `null` quand le
 *   référentiel ne donne la position d'aucune : le rang reste dans la liste,
 *   la carte ne peut simplement pas le poser.
 * @param sequences les rangs du parcours que ce marqueur réunit, dans l'ordre.
 * @param stopIds les identifiants GTFS réunis, pour retrouver l'arrêt du
 *   catalogue quand on ouvre sa fiche.
 */
data class LineStopMarker(
    val id: String,
    val name: String,
    val coordinate: Coordinate?,
    val role: Role,
    val sequences: List<Int>,
    val stopIds: List<String>,
) {
    /**
     * Ce que ce marqueur est **dans le parcours**, et non dans le réseau.
     *
     * Le départ et le terminus sont les deux rangs qu'on cherche des yeux pour
     * savoir dans quel sens la liste se lit ; sur la carte, ce sont les deux
     * bouts du trait. Ils méritent donc une marque que les autres n'ont pas —
     * c'est la seule différence de dessin que la donnée justifie sans rien
     * inventer.
     */
    enum class Role {
        ORIGIN,
        TERMINUS,

        /** Les deux à la fois : un parcours en boucle revient d'où il part. */
        BOTH,
        INTERMEDIATE,
        ;

        val isEnd: Boolean get() = this != INTERMEDIATE
    }
}

/**
 * Les marqueurs d'un parcours, dans l'ordre où il dessert.
 *
 * Le regroupement se fait sur **le nom de lieu et la distance**, jamais sur le
 * seul nom : « Écoles » nomme des arrêts dans trois communes, et les fondre
 * poserait une pastille au milieu de nulle part. C'est la règle de
 * [SAME_PLACE_METERS], et c'est celle qu'applique déjà la recherche.
 *
 * @param profileId l'identité du parcours, qui préfixe celle des marqueurs.
 */
fun buildLineStopMarkers(
    profileId: String,
    stops: List<LineJourneyStop>,
): List<LineStopMarker> {
    if (stops.isEmpty()) return emptyList()

    val order = mutableListOf<String>()
    val groups = LinkedHashMap<String, MutableList<IndexedValue<LineJourneyStop>>>()
    for (entry in stops.withIndex()) {
        val key = groupKey(entry.value, groups, order)
        if (key !in groups) order += key
        groups.getOrPut(key) { mutableListOf() } += entry
    }

    val last = stops.lastIndex
    return order.mapNotNull { key ->
        val members = groups[key] ?: return@mapNotNull null
        val first = members.firstOrNull() ?: return@mapNotNull null
        val sequences = members.map { it.index }
        LineStopMarker(
            id = "$profileId#${sequences.first()}",
            name = first.value.name,
            coordinate = centre(members.mapNotNull { it.value.coordinate }),
            role = roleOf(holdsStart = 0 in sequences, holdsEnd = last in sequences),
            sequences = sequences,
            stopIds = members.map { it.value.id },
        )
    }
}

private fun roleOf(holdsStart: Boolean, holdsEnd: Boolean): LineStopMarker.Role = when {
    holdsStart && holdsEnd -> LineStopMarker.Role.BOTH
    holdsStart -> LineStopMarker.Role.ORIGIN
    holdsEnd -> LineStopMarker.Role.TERMINUS
    else -> LineStopMarker.Role.INTERMEDIATE
}

/**
 * La clé du groupe auquel ce rang appartient : celle d'un groupe déjà ouvert au
 * même nom **et à portée**, ou une clé neuve.
 *
 * Le second critère n'est pas une précaution : sans lui, une ligne
 * interurbaine qui dessert deux « Mairie » à vingt kilomètres n'en montrerait
 * qu'une, à mi-chemin des deux.
 *
 * Un rang sans position ne se rapproche de rien — on ne sait pas s'il est à
 * portée. Il rejoint donc le groupe de même nom quand il y en a un, ce qui est
 * le comportement le moins surprenant : deux « Commerce » sans coordonnée dans
 * la même desserte sont le même lieu.
 */
private fun groupKey(
    stop: LineJourneyStop,
    groups: Map<String, List<IndexedValue<LineJourneyStop>>>,
    order: List<String>,
): String {
    val name = normalizeStopName(stop.name).ifEmpty { stop.id }
    for (key in order) {
        if (!key.startsWith("$name#")) continue
        val members = groups[key] ?: continue
        val reference = members.firstNotNullOfOrNull { it.value.coordinate }
        val here = stop.coordinate
        if (reference == null || here == null) return key
        if (GeoMath.distance(reference, here) <= SAME_PLACE_METERS) return key
    }
    return "$name#${order.size}"
}

/**
 * Le point où poser le marqueur : le milieu des positions réunies.
 *
 * Un lieu traversé deux fois n'a pas deux points, et le milieu des deux est le
 * seul qui ne privilégie aucun passage.
 */
private fun centre(points: List<Coordinate>): Coordinate? = when {
    points.isEmpty() -> null
    points.size == 1 -> points.first()
    else -> Coordinate(
        latitude = points.sumOf { it.latitude } / points.size,
        longitude = points.sumOf { it.longitude } / points.size,
    )
}
