package io.aule.android.core.model

import java.time.Instant
import java.util.Locale

/**
 * Un point relevé pendant un guidage, tel qu'on l'écrit dans une trace.
 *
 * Le diagnostic d'un guidage se fait après coup, sur un fichier : « pourquoi
 * la caméra a-t-elle pivoté là », « pourquoi l'app a-t-elle cru qu'on
 * quittait l'itinéraire ». Y répondre demande la mesure telle qu'elle est
 * arrivée — sa précision et sa vitesse comprises, car ce sont elles qui
 * décident si la position est retenue ou écartée.
 *
 * [isMocked] a l'air anecdotique et ne l'est pas : une trace enregistrée sous
 * position simulée n'apprend rien sur le comportement en vrai, et rien ne le
 * dit une fois le fichier sorti du téléphone.
 */
data class GpsTracePoint(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val speedMetersPerSecond: Double,
    val courseDegrees: Double?,
    val isMocked: Boolean,
)

/** L'en-tête du fichier. Une trace sans en-tête est une colonne de chiffres. */
const val GPS_TRACE_CSV_HEADER =
    "timestamp,latitude,longitude,accuracyMeters,speedMps,courseDegrees,mocked"

/**
 * Une ligne de trace, en CSV.
 *
 * **[Locale.ROOT], et pas la locale du téléphone.** Un appareil réglé en
 * français écrit « 47,256012 » : dans un fichier dont le séparateur est la
 * virgule, une seule coordonnée décale toutes les colonnes de la ligne, et le
 * fichier ne se relit plus. C'est le genre de panne qu'on ne voit pas en la
 * relisant sur la machine qui l'a écrite.
 *
 * L'horodatage est en ISO-8601 UTC : il se trie comme du texte, il se reparse
 * sans convention, et il ne recule pas d'une heure en octobre.
 *
 * Un cap absent laisse sa colonne **vide** plutôt qu'un zéro — à l'arrêt, le
 * GPS n'a pas de cap, et « 0 » se lirait comme un cap au nord.
 */
fun GpsTracePoint.toCsvRow(): String = listOf(
    Instant.ofEpochMilli(timestampMillis).toString(),
    latitude.format(COORDINATE_DECIMALS),
    longitude.format(COORDINATE_DECIMALS),
    accuracyMeters.format(MEASURE_DECIMALS),
    speedMetersPerSecond.format(MEASURE_DECIMALS),
    courseDegrees?.format(MEASURE_DECIMALS).orEmpty(),
    if (isMocked) "1" else "0",
).joinToString(",")

private fun Double.format(decimals: Int): String =
    String.format(Locale.ROOT, "%.${decimals}f", this)

/** Six décimales valent onze centimètres : au-delà, on écrit du bruit de mesure. */
private const val COORDINATE_DECIMALS = 6

/** Une décimale suffit à une précision, une vitesse ou un cap. */
private const val MEASURE_DECIMALS = 1
