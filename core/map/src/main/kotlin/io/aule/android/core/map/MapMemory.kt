package io.aule.android.core.map

import android.content.ComponentCallbacks2

/**
 * Quand rendre au système ce que la carte garde en mémoire.
 *
 * ## Ce que la campagne du 28/08/2026 a mesuré
 *
 * Trente minutes de guidage continu, et le `TOTAL PSS` passe de **597 à 952 Mo**,
 * pic à 1 037 Mo. Ce n'est pas une fuite : `Activities: 1`, `Views: 29`, et un
 * tas Java qui ne bouge pas — 9,8 Mo. Tout est dans le **tas natif** (410 Mo) et
 * la **mémoire graphique** (377 Mo, dont 349 de textures GL) : le cache de
 * tuiles de MapLibre, qui grossit à mesure que la carte couvre du terrain.
 *
 * C'est un cache, donc c'est sain — **tant qu'on sait le rendre**. Or personne
 * ne le demandait : le projet ne relayait ni `onLowMemory` ni `onTrimMemory` au
 * moteur, et la mesure le montrait sans ambiguïté — un
 * `am send-trim-memory RUNNING_CRITICAL` ne faisait rien retomber (935 → 957 Mo).
 *
 * Sur le S21 et ses 8 Go, cela ne se voit pas : Android n'a jamais besoin de
 * réclamer. Sur un appareil à 4 Go, c'est l'application qui est tuée — et une
 * application de guidage tuée en route, c'est le trajet perdu.
 *
 * ## Pourquoi un seuil, et pas « à chaque demande »
 *
 * Rendre les textures a un coût visible : les tuiles se rechargent, et la carte
 * clignote. Aux premiers paliers, le système *suggère* plus qu'il ne réclame —
 * [ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE] arrive sur un appareil qui
 * respire encore. On attend donc que la demande soit sérieuse.
 *
 * À partir de [ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW], elle l'est : le
 * système est en train de chercher de la place, et le prochain palier est celui
 * où il commence à tuer. Les paliers d'arrière-plan (l'interface n'est plus
 * visible, et au-delà) sont tous au-dessus de ce seuil : on y rend d'autant plus
 * volontiers que personne ne regarde.
 */
fun shouldReleaseGraphics(level: Int): Boolean =
    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
