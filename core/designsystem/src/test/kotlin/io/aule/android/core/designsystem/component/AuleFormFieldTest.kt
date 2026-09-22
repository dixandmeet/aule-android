package io.aule.android.core.designsystem.component

import io.aule.android.core.designsystem.ScreenSources
import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * La garde du champ de formulaire : un refus se voit.
 *
 * Le message d'erreur naît **sous** la boîte de saisie, c'est-à-dire du côté où le clavier mange
 * l'écran. Sur l'inscription du voyageur, il tombait quatre-vingt-dix points sous le bord du
 * clavier : le champ se cerclait bien de rouge et l'arbre d'accessibilité portait bien son
 * `error()`, mais rien ne défilait, et l'appui se lisait comme un appui perdu
 * (BUG-AND-213, S21, 22/09/2026).
 *
 * Ce qu'aucune relecture ne tient : rendre le focus à un champ **ne fait rien défiler** quand il
 * l'a déjà, et c'est précisément le cas d'une soumission partie du clavier de ce champ-là. Le
 * moyen doit donc rester en place — l'ancre, et l'appel qui la tire.
 *
 * ⚠️ **Les commentaires sont retirés avant de chercher** : la prose qui explique le correctif en
 * nomme tous les rouages, et sans cette coupe elle satisferait la garde à elle seule.
 */
@DisplayName("Le champ de formulaire")
class AuleFormFieldTest {

    @Test
    @DisplayName("ramène sous les yeux le champ qu'une soumission vient de refuser")
    fun champRefuseRamene() {
        val fichier = source()
        assumeTrue(fichier != null, "AuleFormField.kt hors de portée : rien à confronter")
        requireNotNull(fichier)

        val code = sansCommentaires(fichier)
        for ((rouage, ceQuiSePerd) in ROUAGES) {
            assertTrue(
                rouage in code,
                "AuleFormField ne porte plus « $rouage » : $ceQuiSePerd",
            )
        }
    }

    /**
     * Le texte du fichier sans sa prose.
     *
     * Les chaînes littérales ne sont pas protégées, faute d'analyseur ; aucune de ce fichier ne
     * contient `//`, et la garde échouerait bruyamment si cela changeait plutôt que de se taire.
     */
    private fun sansCommentaires(fichier: File): String =
        fichier.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun source(): File? {
        val racine = ScreenSources.repositoryRoot() ?: return null
        return File(racine, CHEMIN).takeIf { it.isFile }
    }

    private companion object {
        const val CHEMIN =
            "core/designsystem/src/main/kotlin/io/aule/android/core/designsystem/component/AuleFormField.kt"

        /** Chaque rouage, et ce que l'écran reperd s'il disparaît. */
        val ROUAGES = listOf(
            "bringIntoViewRequester(" to
                "l'ancre n'est plus posée sur la colonne, et rien ne peut plus ramener le " +
                "message sous les yeux — ni le champ, ni l'écran qui l'héberge",
            ".bringIntoView(" to
                "plus personne ne tire l'ancre : le champ se cercle de rouge sous le clavier, " +
                "et le refus redevient un appui sans effet",
            "onFocusChanged" to
                "le champ ne sait plus s'il a le focus, donc plus lequel de deux champs " +
                "refusés doit défiler — et la file de bringIntoView donne le dernier, " +
                "c'est-à-dire le plus bas, jamais le premier fautif",
        )
    }
}
