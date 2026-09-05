package io.aule.android.feature.auth

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Ouvre l'adresse d'un fournisseur d'identité dans un onglet de navigateur.
 *
 * ## Ni WebView, ni fenêtre maison
 *
 * Google refuse d'authentifier dans une WebView (`disallowed_useragent`), et il
 * a raison de le faire : une WebView appartient à l'application qui l'héberge,
 * qui pourrait donc lire le mot de passe du compte Google frappé dedans. La
 * RFC 8252 dit la même chose pour tous les fournisseurs. Il reste l'onglet du
 * navigateur, qui a la barre d'adresse, le cadenas, et les cookies de session
 * de quelqu'un déjà connecté à son compte.
 *
 * ## Ce que l'onglet apporte face à un `ACTION_VIEW` nu
 *
 * Il se **referme** quand le deep link ramène dans l'application. Un navigateur
 * complet, lui, garde derrière nous un onglet posé sur l'URL de redirection :
 * revenir dessus plus tard rejoue l'échange avec un code déjà consommé, et
 * l'écran de connexion annonce alors un refus que personne ne comprend.
 *
 * Rend `false` si aucun navigateur ne peut ouvrir l'adresse — appareil sans
 * navigateur, ou navigateur désactivé par une politique d'entreprise. Ce n'est
 * pas un refus d'authentification, et l'écran ne doit pas le dire comme tel.
 */
internal fun openOAuthTab(context: Context, url: String): Boolean = try {
    CustomTabsIntent.Builder()
        // La page de Google n'a rien à partager et rien à ajouter aux favoris :
        // les deux entrées du menu ne feraient qu'offrir des chemins de sortie
        // au milieu d'une authentification.
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .setUrlBarHidingEnabled(false)
        .setShowTitle(true)
        .build()
        .launchUrl(context, url.toUri())
    true
} catch (_: ActivityNotFoundException) {
    false
}
