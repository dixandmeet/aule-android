package io.aule.android.core.guet

import io.aule.android.core.model.TransportMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * À quelle vitesse l'utilisateur marche.
 */
enum class WalkingPace {
    SLOW,
    NORMAL,
    FAST,

    /**
     * Déduite des marches déjà mesurées — voir [GuetHabits.paceFactor]. Tant
     * qu'aucune ne l'a été, se comporte exactement comme [NORMAL] : une estimation
     * par défaut vaut mieux qu'un réglage qu'on demande avant d'avoir de quoi le
     * remplir.
     */
    AUTOMATIC,
    ;

    /**
     * Le multiplicateur à appliquer à une durée de marche estimée.
     *
     * @param measured le rapport mesuré, ou `null` s'il n'y en a pas encore.
     */
    fun factor(measured: Double?): Double = when (this) {
        SLOW -> 1.25
        NORMAL -> 1.0
        FAST -> 0.85
        AUTOMATIC -> measured ?: 1.0
    }
}

/**
 * Ce que l'alerte a le droit de faire.
 */
data class AlertPreferences(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val notifications: Boolean = true,
    /**
     * L'équivalent Android de la Live Activity : une notification permanente
     * enrichie. Portée réduite, assumée — voir le § 3.5 du plan de rattrapage.
     */
    val ongoingNotification: Boolean = true,
    val intensity: Intensity = Intensity.STANDARD,
) {
    /**
     * Trois crans, et **chacun fait quelque chose de différent** : tout ce qu'un
     * niveau change se lit dans [chimeVolume] et [repriseDelaySeconds], et nulle
     * part ailleurs.
     *
     * ⚠️ **Un niveau jumeau d'un autre est un réglage qui ment.** L'utilisateur
     * choisit, l'écran confirme, et rien ne bouge — c'est ce qu'`INSISTENT` a été
     * pendant tout un lot côté iOS, pour la seule raison qu'un unique appelant
     * lisait l'intensité et n'y distinguait que `DISCREET`. Le test « deux niveaux
     * ne font jamais la même chose » l'interdit désormais.
     */
    enum class Intensity {
        DISCREET,
        STANDARD,
        INSISTENT,
    }

    /**
     * Le volume du carillon, de 0 à 1.
     *
     * `DISCREET` descend à 0,4 : le son se mêle à ce que l'utilisateur écoute
     * déjà, et ce niveau-là le rend franchement discret sans le supprimer — pour
     * le supprimer, il y a l'interrupteur [sound].
     */
    val chimeVolume: Float get() = if (intensity == Intensity.DISCREET) 0.4f else 1f

    /**
     * Au bout de combien de temps l'alerte se rejoue, ou `null` quand elle ne se
     * rejoue pas.
     *
     * ⚠️ **Trois secondes, une seule fois, et jamais hors de l'application.** C'est
     * la seule forme d'insistance que la doctrine du Guet autorise : la reprise
     * vit et meurt avec la fenêtre d'alerte et s'annule dès que quelqu'un a
     * répondu. Une seconde notification de rappel ferait de l'alerte une alarme,
     * quand le Guet annonce un bus, pas un incident.
     *
     * Ce n'est pas qu'une affaire de son : la reprise rejoue **le signal entier**,
     * vibration comprise, parce que c'est la vibration qui porte.
     */
    val repriseDelaySeconds: Int? get() = if (intensity == Intensity.INSISTENT) 3 else null
}

/**
 * Les réglages du Guet.
 *
 * Type valeur, dans le module pur : le moteur en a besoin pour classer, et il n'a
 * aucune raison de connaître l'objet qui le persiste.
 *
 * Port de `Native/Aule/Core/Guet/GuetPreferences.swift`.
 */
data class GuetPreferences(
    /**
     * **Éteint par défaut.** Une veille qui s'allume seule et se met à sonner le
     * lendemain matin serait une surprise, pas un service.
     */
    val isEnabled: Boolean = false,

    /**
     * Combien de temps il faut à l'utilisateur avant de pouvoir sortir.
     *
     * **Zéro par défaut** : le cas de base est « l'alerte tombe quand il faut
     * partir ». La préparation est une personnalisation, et une valeur par défaut
     * non nulle ferait sonner tout le monde trop tôt au nom d'un besoin que peu
     * ont.
     */
    val preparationMinutes: Int = 0,

    /**
     * La marge qu'on veut avoir sur le quai. Deux minutes : de quoi voir le
     * véhicule arriver sans avoir couru.
     */
    val platformMarginMinutes: Int = 2,

    val pace: WalkingPace = WalkingPace.AUTOMATIC,

    val modes: Set<TransportMode> = setOf(TransportMode.BUS, TransportMode.TRAM, TransportMode.BOAT),

    /**
     * Les lignes que l'utilisateur a désignées.
     *
     * **Ne restreint pas la veille** : elles pèsent dans le classement, elles n'en
     * excluent pas les autres. Une veille qui ne regarderait que les lignes
     * déclarées manquerait le bus qu'on ne prend qu'une fois.
     */
    val followedLines: Set<String> = emptySet(),

    val alerts: AlertPreferences = AlertPreferences(),
) {
    val preparationSeconds: Int get() = preparationMinutes.coerceAtLeast(0) * 60
    val platformSeconds: Int get() = platformMarginMinutes.coerceAtLeast(0) * 60

    /**
     * ⚠️ Un tableau **trié** pour les modes et les lignes, jamais un ensemble tel
     * quel : le fichier se relit à l'œil, et deux enregistrements identiques
     * doivent produire deux fichiers identiques.
     */
    fun encode(): String = buildJsonObject {
        put("isEnabled", isEnabled)
        put("preparationMinutes", preparationMinutes)
        put("platformMarginMinutes", platformMarginMinutes)
        put("pace", pace.name)
        putJsonArray("modes") { modes.map { it.name }.sorted().forEach { add(it) } }
        putJsonArray("followedLines") { followedLines.sorted().forEach { add(it) } }
        putJsonObject("alerts") {
            put("sound", alerts.sound)
            put("haptics", alerts.haptics)
            put("notifications", alerts.notifications)
            put("ongoingNotification", alerts.ongoingNotification)
            put("intensity", alerts.intensity.name)
        }
    }.toString()

    companion object {
        /**
         * Les valeurs que l'écran de réglages propose. Ici, et non dans la vue :
         * ce sont des paliers de produit, pas une mise en forme.
         */
        val PREPARATION_CHOICES = listOf(0, 1, 2, 3, 5, 10)
        val PLATFORM_MARGIN_CHOICES = listOf(0, 1, 2, 3, 5)

        val DEFAULTS = GuetPreferences()

        /**
         * ⚠️ **Un décodage strict effacerait tous les réglages pour un champ
         * ajouté.**
         *
         * Un dépôt de préférences rend `null` quand la lecture échoue, et
         * l'appelant retombe alors sur les valeurs par défaut — c'est-à-dire que
         * le Guet **s'éteindrait tout seul** chez quelqu'un qui vient de mettre à
         * jour, sans un mot.
         *
         * Même règle pour les valeurs inconnues — un réglage écrit par une version
         * plus récente : on retombe sur le défaut du champ concerné, et **de lui
         * seul**. Perdre une préférence est déjà fâcheux ; les perdre toutes parce
         * que l'une n'a pas été comprise ne l'est plus.
         */
        fun decode(raw: String?): GuetPreferences {
            if (raw.isNullOrBlank()) return DEFAULTS
            val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: return DEFAULTS
            return GuetPreferences(
                isEnabled = obj.bool("isEnabled") ?: DEFAULTS.isEnabled,
                preparationMinutes = obj.int("preparationMinutes") ?: DEFAULTS.preparationMinutes,
                platformMarginMinutes = obj.int("platformMarginMinutes")
                    ?: DEFAULTS.platformMarginMinutes,
                pace = obj.text("pace")
                    ?.let { name -> WalkingPace.entries.firstOrNull { it.name == name } }
                    ?: DEFAULTS.pace,
                // Un mode inconnu est ignoré, pas fatal : le jour où le réseau en
                // gagne un, une version ancienne doit continuer de surveiller ceux
                // qu'elle connaît.
                modes = obj.strings("modes")
                    ?.mapNotNull { name -> TransportMode.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                    ?: DEFAULTS.modes,
                followedLines = obj.strings("followedLines")?.toSet() ?: DEFAULTS.followedLines,
                alerts = decodeAlerts(obj["alerts"]),
            )
        }

        private fun decodeAlerts(element: JsonElement?): AlertPreferences {
            val obj = runCatching { element?.jsonObject }.getOrNull() ?: return AlertPreferences()
            val fallback = AlertPreferences()
            return AlertPreferences(
                sound = obj.bool("sound") ?: fallback.sound,
                haptics = obj.bool("haptics") ?: fallback.haptics,
                notifications = obj.bool("notifications") ?: fallback.notifications,
                ongoingNotification = obj.bool("ongoingNotification")
                    ?: fallback.ongoingNotification,
                intensity = obj.text("intensity")
                    ?.let { name ->
                        AlertPreferences.Intensity.entries.firstOrNull { it.name == name }
                    }
                    ?: fallback.intensity,
            )
        }

        private fun JsonObject.bool(key: String): Boolean? =
            runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()

        private fun JsonObject.int(key: String): Int? =
            runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

        private fun JsonObject.text(key: String): String? =
            runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

        private fun JsonObject.strings(key: String): List<String>? = runCatching {
            this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull()
    }
}
