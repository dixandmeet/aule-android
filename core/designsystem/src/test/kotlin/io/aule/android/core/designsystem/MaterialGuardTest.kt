package io.aule.android.core.designsystem

import io.aule.android.core.designsystem.ScreenSources.isComment
import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * La garde Material 3.
 *
 * Material 3 est le design system de l'application (ADR-010). Une décision de
 * cette portée ne tient pas par la relecture : il suffit d'un écran pressé pour
 * réintroduire un `BasicText`, une carte faite à la main ou un import
 * Material 2, et l'application repart avec deux design systems en parallèle.
 *
 * ## Comment cette garde fonctionne
 *
 * Chaque règle porte une **dette** : la liste nominative des fichiers qui la
 * violent encore, parce que la migration se fait écran par écran. Deux tests
 * l'encadrent, et c'est leur combinaison qui fait tout le travail :
 *
 * - un fichier **hors dette** qui viole la règle fait échouer la garde : on ne
 *   régresse pas, et un écran neuf naît conforme ;
 * - un fichier **en dette** qui ne viole plus la règle fait aussi échouer la
 *   garde : l'exemption doit être retirée le jour où elle devient inutile.
 *
 * La dette ne peut donc que décroître, et elle mesure exactement ce qu'il reste
 * à migrer. Quand toutes les listes sont vides, la migration est finie.
 */
class MaterialGuardTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    fun `aucun ecran hors dette ne s ecarte de Material 3`(rule: Rule) {
        val sources = ScreenSources.all()
        assumeTrue(sources.isNotEmpty(), "Racine du dépôt introuvable")

        val offenders = sources
            .filterNot { it.name in rule.debt }
            .flatMap { file -> rule.offencesIn(file) }

        assertTrue(
            offenders.isEmpty(),
            "${rule.title}\n${rule.remedy}\n\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * L'exemption périmée est le poison de ce genre de liste : elle laisse
     * croire qu'il reste du travail là où il n'y en a plus, et surtout elle
     * rouvre la porte sans que personne ne s'en aperçoive.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    fun `la dette ne mentionne que des dettes reelles`(rule: Rule) {
        val sources = ScreenSources.all()
        assumeTrue(sources.isNotEmpty(), "Racine du dépôt introuvable")

        val byName = sources.associateBy { it.name }
        val settled = rule.debt.filter { name ->
            val file = byName[name] ?: return@filter true
            rule.offencesIn(file).isEmpty()
        }

        assertTrue(
            settled.isEmpty(),
            "${rule.title}\nCes fichiers ne violent plus la règle : retirez-les de la dette.\n\n" +
                settled.joinToString("\n"),
        )
    }

    /**
     * La règle qui n'a plus de dette a gagné : elle doit alors être promue en
     * garde sèche. Ce test ne fait que le signaler quand le moment arrive.
     */
    @Test
    fun `une regle sans dette est une regle acquise`() {
        val acquired = rules().filter { it.debt.isEmpty() }.map { it.title }
        println(
            if (acquired.isEmpty()) {
                "Migration Material 3 en cours : aucune règle entièrement acquise."
            } else {
                "Règles Material 3 acquises :\n" + acquired.joinToString("\n") { "  · $it" }
            },
        )
    }

    class Rule(
        val title: String,
        val remedy: String,
        val debt: Set<String>,
        private val offends: (String) -> Boolean,
    ) {
        fun offencesIn(file: File): List<String> = file.readLines()
            .withIndex()
            .filterNot { (_, line) -> line.isComment() }
            .filter { (_, line) -> offends(line) }
            .map { (index, line) -> "  ${file.name}:${index + 1}  ${line.trim()}" }

        override fun toString(): String = title
    }

    private companion object {

        /**
         * Material 2, sauf les icônes.
         *
         * `androidx.compose.material.icons` est la bibliothèque de symboles
         * officielle : elle vit sous l'ancien nom de paquet pour des raisons
         * d'historique, mais c'est bien elle que Material 3 emploie. Tout le
         * reste du paquet est l'ancien kit et n'a plus rien à faire ici.
         */
        val MATERIAL_2 = Regex("""^\s*import\s+androidx\.compose\.material\.(?!icons\.)""")

        /**
         * `BasicText` ne connaît ni `MaterialTheme.typography`, ni la couleur de
         * contenu locale, ni les réglages d'accessibilité que `Text` applique.
         * Un écran qui l'emploie sort de la charte sans le dire.
         */
        val FOUNDATION_TEXT = Regex("""\bBasicText\s*\(""")

        val FOUNDATION_TEXT_FIELD = Regex("""\bBasicTextField\s*\(""")

        /** Les enveloppes Aule qui ne font que redire un composant Material 3. */
        val REDUNDANT_WRAPPER = Regex(
            """\bAule(Button|Card|IconButton|TextField|BusyIndicator|SheetHandle)\s*\(""",
        )

        /**
         * Une forme écrite dans un écran est une forme qui échappe au thème :
         * changer `MaterialTheme.shapes` ne la touchera pas.
         */
        val LOCAL_SHAPE = Regex("""\bRoundedCornerShape\s*\(""")

        @JvmStatic
        fun rules(): List<Rule> = listOf(
            Rule(
                title = "Material 2 n'a plus cours",
                remedy = "Prenez l'équivalent dans androidx.compose.material3.",
                debt = emptySet(),
                offends = { MATERIAL_2.containsMatchIn(it) },
            ),
            Rule(
                title = "Un texte s'écrit avec Text, pas avec Foundation",
                remedy = "Remplacez BasicText par Text et le style par MaterialTheme.typography.",
                debt = emptySet(),
                offends = {
                    FOUNDATION_TEXT.containsMatchIn(it) || FOUNDATION_TEXT_FIELD.containsMatchIn(it)
                },
            ),
            Rule(
                title = "Un composant standard est un composant Material 3",
                remedy = "Button, Card, IconButton, OutlinedTextField, Snackbar, " +
                    "CircularProgressIndicator existent déjà : servez-vous.",
                debt = emptySet(),
                offends = { REDUNDANT_WRAPPER.containsMatchIn(it) },
            ),
            Rule(
                title = "Une forme appartient au thème",
                remedy = "Passez par MaterialTheme.shapes, ou laissez le composant " +
                    "Material 3 prendre la sienne.",
                debt = emptySet(),
                offends = { LOCAL_SHAPE.containsMatchIn(it) },
            ),
        )
    }
}
