package io.aule.android.feature.auth

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.model.initialsOf

/**
 * Le portrait : la photo si on l'a, les initiales sinon.
 *
 * Pas de Chargeur d'images tiers — les octets viennent déjà du ViewModel,
 * décodés ici. Une photo illisible retombe sur les initiales, ce n'est
 * pas un bandeau d'erreur.
 *
 * ## Pourquoi la tuile est paramétrable
 *
 * Le repli sur les initiales n'a de sens que si la tuile se détache de ce qui
 * la porte, et l'aplat par défaut — `primaryContainer` — ne s'en détache que
 * sur une surface neutre. Posé sur la surface de marque de l'en-tête du profil,
 * il disparaît : de nuit, `primaryContainer` **est** la couleur d'arrivée du
 * dégradé, au point près. Un appelant qui pose le portrait sur un fond de
 * marque doit donc pouvoir lui donner l'aplat inverse ; les deux paramètres
 * existent pour ce cas-là et n'ont aucune raison de servir ailleurs. Sur une
 * carte neutre — celle du menu de compte, par exemple —, les défauts sont les
 * bons : l'aplat de teinte primaire y est le seul endroit où l'accent se voit.
 *
 * ## Et pourquoi la forme est paramétrable
 *
 * Le carré arrondi est la forme d'une **fiche** : le portrait y voisine un nom
 * et des puces, et il se lit comme leur vignette. Posé seul sur la carte — dans
 * le socle de recherche —, il devient une **cible**, au milieu de pastilles
 * rondes ; un carré y serait la seule forme anguleuse de l'écran. Voir
 * [AccountAvatarButton].
 */
@Composable
internal fun AvatarPortrait(
    name: String,
    bytes: ByteArray?,
    modifier: Modifier = Modifier,
    /**
     * Le côté du portrait.
     *
     * Il est **posé ici et non par l'appelant** : la photo se recadre sur cette
     * mesure, et un portrait dont le cadre et la photo ne s'accordent pas
     * rogne deux fois. Le socle de la carte le demande au cran du chrome.
     */
    size: Dp = AuleControl.avatar,
    shape: Shape = MaterialTheme.shapes.small,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    onContainer: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val bitmap = remember(bytes) {
        bytes?.takeIf { it.isNotEmpty() }?.let { payload ->
            BitmapFactory.decodeByteArray(payload, 0, payload.size)
        }
    }
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = container,
        contentColor = onContainer,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initialsOf(name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
