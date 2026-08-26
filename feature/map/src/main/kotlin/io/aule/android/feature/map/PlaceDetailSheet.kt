package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.Place
import io.aule.android.core.model.shortLabel

/**
 * Le panneau d'une adresse : ce qu'on a nommé, et rien d'autre.
 *
 * Une adresse n'a ni desserte ni horaires — la seule chose qu'on puisse en
 * faire, c'est y aller. Le bouton garde donc toute la largeur : contrairement
 * au volet d'un arrêt, il ne prend la place de rien.
 *
 * Le badge de mode n'apparaît que sur un lieu que le géocodeur a reconnu comme
 * un arrêt. C'est ce qui distingue « Commerce », la station, de « rue du
 * Commerce », la voie — deux résultats de recherche que le nom seul confond.
 *
 * ## Le même « Y aller » que sur le volet d'un arrêt
 *
 * Les deux volets se succèdent dans le même cadre, et c'est le même geste qu'on
 * y fait. Le bouton portait pourtant deux visages : à côté du nom d'un arrêt il
 * avait son icône d'itinéraire, ici il n'avait qu'un mot. Une action qui change
 * d'aspect selon l'écran se redemande à chaque fois, alors qu'on l'avait
 * apprise. Icône, écarts et crans sont donc repris à l'identique — ceux de
 * Material, que ni l'un ni l'autre volet ne décide.
 *
 * Il gagne au passage la hauteur des actions principales de la maison,
 * [AuleControl.height], au lieu de garder celle que Material lui donne par
 * défaut — laquelle passe sous le plancher tactile qu'Aule tient partout
 * ailleurs. C'était la cible la plus large de l'écran, et c'était la plus
 * basse : un pouce qui vise un bouton pleine largeur ne rate pas sa gauche ou
 * sa droite, il rate son bord haut.
 *
 * ## Pas de cartouche autour de l'adresse
 *
 * `SheetCard` existe pour distinguer *un tableau* du *texte d'un volet*. Une
 * seule ligne d'adresse n'est pas un tableau : l'encadrer donnerait une boîte
 * autour de rien, et ferait passer pour une liste ce qui est une précision. Ce
 * volet reste ce que son titre annonce — ce qu'on a nommé, et rien d'autre.
 */
@Composable
internal fun PlaceDetailSheet(
    place: Place,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetBody(modifier = modifier) {
        // Deux blocs, deux rangs : l'identité se pose, l'action la suit d'un
        // souffle. Le volet cesse d'apparaître d'un coup — il se déplie, comme
        // les rangées de passages du volet d'à côté. C'est peu de chose sur deux
        // éléments, et c'est ce peu qui fait qu'on les lit dans l'ordre plutôt
        // que d'avoir à choisir par où commencer.
        Column(
            modifier = Modifier.auleEnter(index = 0),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            SheetTitle(place.shortLabel())
            place.stopMode?.let { mode ->
                TransportBadge(
                    mode = mode,
                    label = mode.label(),
                    tint = mode.markerColor(AuleTheme.night).color,
                )
            }
            // L'adresse complète sous le nom court : deux « Rue de Strasbourg »
            // ne se distinguent que par leur commune.
            Text(
                text = place.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onRoute,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height)
                .auleEnter(index = 1),
            colors = auleAccentButtonColors(),
            // Les crans d'un bouton à icône sont ceux de Material : ni la taille
            // de l'icône ni son écart au texte ne se décident ici.
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(
                imageVector = AuleGlyph.ROUTE.asImageVector(),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.route_go))
        }
    }
}
