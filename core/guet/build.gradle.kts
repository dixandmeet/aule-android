plugins {
    alias(libs.plugins.aule.jvm.library)
}

// Le socle du Guet : pur, comme `:core:geo`.
//
// Il dépend de `:core:model` et de `:core:geo` — il raisonne sur des passages,
// des arrêts et des distances — et de **rien d'autre**. Pas d'Android, pas de
// MapLibre, pas de Compose : une décision du Guet doit pouvoir s'exercer en une
// ligne de test, sur la JVM de l'hôte, en quelques millisecondes.
//
// C'est ce qui rend le port fiable. Les tests iOS correspondants
// (`GuetEngineTests`, `GuetScoringTests`, `GuetLevelTests`, …) se portent avec le
// code, et ils sont le seul filet : ce moteur décide de réveiller quelqu'un à
// six heures du matin, et un défaut n'y ressemble pas à un plantage.
dependencies {
    api(projects.core.model)
    api(projects.core.geo)
}
