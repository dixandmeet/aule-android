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
    val environmentLabel: String,
    val versionName: String,
    val versionCode: Int,
    /**
     * La pastille de diagnostic — quel binaire, quelle source. Absente de la
     * production : un usager n'a rien à faire de « development · production ».
     */
    val showsDiagnostics: Boolean = false,
) {
    init {
        require(apiBase.startsWith("https://")) {
            "L'API doit être jointe en HTTPS — reçu « $apiBase »."
        }
    }

    /** Vrai quand la configuration Supabase manque : à dire, pas à deviner. */
    val supabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()

    /** Ce qu'affiche le pied de page d'un build de développement. */
    val buildLabel: String
        get() = "$environmentLabel · $versionName ($versionCode) · ${dataSource.id}"
}
