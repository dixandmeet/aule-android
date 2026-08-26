package io.aule.android.core.designsystem

import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleRgba
import io.aule.android.core.designsystem.token.AuleTokens
import java.io.File
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Le fond de fenêtre ne doit pas diverger des jetons.
 *
 * Android peint la fenêtre **avant** que la moindre ligne de Kotlin ne tourne :
 * pendant les quelques images qui séparent le lancement de la première
 * composition, c'est `@color/aule_surface` qui remplit l'écran, pas
 * `AuleTokens.surfaceSolid`. Les deux doivent donc dire la même chose, et il
 * n'y a aucun moyen de le garantir autrement qu'en le vérifiant : le XML ne
 * peut pas lire le Kotlin.
 *
 * ## Pourquoi ce test existe à nouveau
 *
 * Il a existé, il a disparu, et la valeur a dérivé aussitôt. À la refonte de la
 * palette, `values-night/colors.xml` tenait encore `#0D1512` — le vert d'avant
 * la recoloration en teal — quand les jetons étaient passés à `#0A1313`. Sur une
 * capture d'écran, l'écart est invisible ; au lancement de l'application, il
 * donne un fond vert qui bascule au teal dès que Compose prend la main. C'est le
 * genre exact de défaut qu'on ne trouve jamais en relecture et qu'on remarque
 * dix fois par jour sans savoir le nommer.
 *
 * La garde porte sur les **trois** couleurs déclarées, parce que la marque a
 * dérivé de la même façon la fois d'avant.
 */
class WindowBackgroundTest {

    @Test
    fun `le fond de fenetre du jour est la surface du jour`() {
        assertColor("values", "aule_surface", AuleTokens.day.surfaceSolid)
    }

    @Test
    fun `le fond de fenetre de nuit est la surface de nuit`() {
        assertColor("values-night", "aule_surface", AuleTokens.night.surfaceSolid)
    }

    @Test
    fun `la marque declaree au systeme est la marque`() {
        assertColor("values", "aule_brand_teal", AuleBrand.teal)
    }

    private fun assertColor(qualifier: String, name: String, expected: AuleRgba) {
        val declared = readColor(qualifier, name) ?: return
        assertEquals(
            expected.hex(),
            declared,
            "@color/$name ($qualifier) a dérivé du jeton correspondant",
        )
    }

    /**
     * Renvoie `null` — et le test s'abstient — quand le module `app` est
     * introuvable. Un test de design system ne doit pas échouer parce qu'il a
     * été lancé depuis un répertoire inattendu ; il doit échouer parce que les
     * couleurs divergent.
     */
    private fun readColor(qualifier: String, name: String): String? {
        val root = ScreenSources.repositoryRoot()
        assumeTrue(root != null, "Racine du dépôt introuvable")
        val file = File(root, "app/src/main/res/$qualifier/colors.xml")
        assumeTrue(file.isFile, "${file.path} introuvable")
        val match = COLOR.findAll(file.readText()).firstOrNull { it.groupValues[1] == name }
        assumeTrue(match != null, "@color/$name absent de $qualifier/colors.xml")
        return match!!.groupValues[2].uppercase()
    }

    /**
     * Les couleurs Android s'écrivent en `#AARRGGBB` — l'alpha d'abord, ce que
     * le format web ne fait pas. Le fond de fenêtre est toujours opaque, donc
     * on compare sur `FF` suivi du triplet.
     */
    private fun AuleRgba.hex(): String = "#FF%02X%02X%02X".format(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
    )

    private companion object {
        val COLOR = Regex("""<color\s+name="([^"]+)"\s*>\s*(#[0-9A-Fa-f]{8})\s*</color>""")
    }
}
