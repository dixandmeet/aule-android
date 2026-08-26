package io.aule.android.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * La phrase de pied d'écran, et les deux textes qu'elle rend atteignables.
 *
 * Le web l'écrit d'une traite, liens compris (`login-form.tsx`), et c'est la
 * seule forme qui se traduise : une phrase découpée en cinq morceaux — début,
 * lien, liaison, lien, fin — impose au traducteur l'ordre des mots du français.
 * Le gabarit garde donc ses deux emplacements, et les liens sont **retrouvés**
 * dans le texte assemblé plutôt que collés bout à bout.
 *
 * Les cibles sont des liens de texte et non des boutons : ce sont deux mots
 * dans une phrase de deux lignes, et un bouton par mot ferait trois contrôles
 * là où la charte n'en veut aucun. Ils sont soulignés — sur un pied d'écran en
 * douze points, la couleur seule ne dit pas qu'on peut appuyer, et elle ne dit
 * rien du tout à qui ne distingue pas le teal du gris.
 */
@Composable
fun legalNotice(
    template: String,
    terms: String,
    privacy: String,
    linkColor: Color,
): AnnotatedString {
    val text = template.format(terms, privacy)
    return buildAnnotatedString {
        append(text)
        link(text, terms, TERMS_URL, linkColor)
        link(text, privacy, PRIVACY_URL, linkColor)
    }
}

/**
 * Pose un lien sur la première occurrence d'un fragment.
 *
 * Une occurrence absente ne lève rien : un gabarit traduit qui aurait perdu un
 * de ses emplacements doit donner une phrase sans lien, jamais un écran qui
 * tombe.
 */
private fun AnnotatedString.Builder.link(
    text: String,
    fragment: String,
    url: String,
    color: Color,
) {
    val start = text.indexOf(fragment)
    if (start < 0) return
    addLink(
        LinkAnnotation.Url(
            url = url,
            styles = TextLinkStyles(
                style = SpanStyle(
                    color = color,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
        ),
        start = start,
        end = start + fragment.length,
    )
}

/** Les deux textes vivent sur `aule.fr` : ils changent sans l'application. */
const val TERMS_URL = "https://www.aule.fr/conditions"

private const val PRIVACY_URL = "https://www.aule.fr/confidentialite"
