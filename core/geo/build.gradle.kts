plugins {
    alias(libs.plugins.aule.jvm.library)
}

// Aucune dépendance, pas même Android. C'est délibéré : ce module est le socle
// géométrique de la carte et de la navigation, et il doit rester vérifiable en
// quelques millisecondes sur la JVM de l'hôte.
