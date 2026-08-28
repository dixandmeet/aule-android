package io.aule.android.core.common.config

/**
 * D'où vient la donnée.
 *
 * Le nom est lu depuis la configuration de build ; une valeur inconnue ne prend
 * pas un défaut silencieux, elle lève. Se tromper de source de données est le
 * genre de défaut qui ne se voit qu'en production, quand quelqu'un regarde des
 * véhicules qui n'existent pas.
 */
enum class DataSource(val id: String) {
    /** Fixtures en mémoire. Indisponible hors du flavor `development` (ADR-005). */
    MOCK("mock"),

    /** Supabase en direct pour les arrêts et la flotte, BFF pour les passages. */
    DEVELOPMENT("development"),

    /** Le BFF `www.aule.fr` pour tout. */
    PRODUCTION("production");

    companion object {
        fun of(id: String): DataSource = entries.firstOrNull { it.id == id }
            ?: error("Source de données inconnue : « $id ». Attendu : ${entries.joinToString { it.id }}.")
    }
}

/**
 * La configuration résolue de l'application.
 *
 * Construite une fois au démarrage à partir de la configuration de build, puis
 * passée par la racine de composition. Rien ne la lit depuis une variable
 * globale : c'est ce qui permet à un test de la fabriquer autrement.
 */
data class AppConfig(
    val dataSource: DataSource,
    val apiBase: String,
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    /**
     * Le routeur de voirie qui décrit les manœuvres.
     *
     * ## Pourquoi cette valeur remonte jusqu'ici
     *
     * `/api/route` ne rend aucune manœuvre : « tourner à droite », « prendre le
     * rond-point » viennent d'un **second** serveur, un OSRM, interrogé pour la
     * même jambe. Tant que l'adresse vivait en dur dans `OsrmRoadRouter`, les
     * trois flavors partaient sur `router.project-osrm.org` — le serveur de
     * **démonstration** public d'OSRM, sans garantie de service et dont les
     * conditions d'usage excluent la production. Le commentaire du routeur
     * promettait un hôte injectable ; rien ne l'injectait.
     *
     * Elle se pose maintenant dans `local.properties` (`aule.osrmOrigin`), donc
     * par machine et par flavor, sans toucher au code. [usesPublicDemoRouter]
     * dit à voix haute quand on est resté sur le repli.
     */
    val roadRouterOrigin: String,
    val environmentLabel: String,
    val versionName: String,
    val versionCode: Int,
) {
    init {
        require(apiBase.startsWith("https://")) {
            "L'API doit être jointe en HTTPS — reçu « $apiBase »."
        }
        require(roadRouterOrigin.startsWith("https://")) {
            "Le routeur de voirie doit être joint en HTTPS — reçu « $roadRouterOrigin »."
        }
    }

    /**
     * Vrai quand les manœuvres sortent encore du serveur de démonstration public.
     *
     * Ce n'est pas une panne, et l'application marche : c'est une dépendance
     * qu'on ne maîtrise pas sur le chemin du guidage. Le jour où elle limite le
     * débit, le bandeau retombe en silence sur le libellé de la jambe — d'où le
     * fait de le dire au démarrage plutôt que de le découvrir en roulant.
     */
    val usesPublicDemoRouter: Boolean
        get() = roadRouterOrigin.trimEnd('/') == PUBLIC_DEMO_ROAD_ROUTER

    /** Vrai quand la configuration Supabase manque : à dire, pas à deviner. */
    val supabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()

    /** Ce qu'affiche le pied de page d'un build de développement. */
    val buildLabel: String
        get() = "$environmentLabel · $versionName ($versionCode) · ${dataSource.id}"
}

/**
 * Le serveur de démonstration public d'OSRM.
 *
 * Il rend le bon service et c'est le repli tant qu'Aule n'héberge pas le sien —
 * mais il n'a ni garantie de disponibilité, ni droit d'usage en production.
 * Nommé ici pour que [AppConfig.usesPublicDemoRouter] puisse le reconnaître, et
 * pour qu'il n'y ait qu'**un** endroit où cette adresse est écrite.
 */
const val PUBLIC_DEMO_ROAD_ROUTER = "https://router.project-osrm.org"
