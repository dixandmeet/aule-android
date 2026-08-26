package io.aule.android.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.DepartureRow
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.Wait
import io.aule.android.core.model.repository.StopRepository
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Le panneau d'un arrêt : ce qui passe, et quand.
 *
 * L'horloge du panneau bat toutes les dix secondes. Sans elle, « 3 min »
 * resterait « 3 min » pendant dix minutes.
 *
 * ## Ce qu'on lit en deux secondes
 *
 * L'information vitale de cette fiche est **l'attente du prochain passage** —
 * pas le nom de l'arrêt, qu'on vient de toucher, ni l'itinéraire, qu'on
 * demandera peut-être. Elle est donc écrite au rôle `DATA`, en chiffres
 * tabulaires : un compteur qui rétrécit d'un caractère en passant de « 10 min »
 * à « 9 min » fait danser toute la colonne.
 *
 * « Y aller » voyage pour cette raison dans l'en-tête plutôt que sur sa propre
 * rangée : pleine largeur, le bouton coûtait une rangée entière — donc un
 * passage de moins — dans le cran partiel du volet, qui est le seul cran qu'on
 * regarde sans lâcher le volant.
 *
 * ## Le prochain passage prend l'aplat de marque
 *
 * Le tableau donnait ses attentes au même poids, de la première à la dernière :
 * une colonne de chiffres gris où il faut compter les rangées pour savoir
 * laquelle arrive d'abord. Or elles sont **déjà triées** — `grouped()` rend les
 * lignes dans l'ordre de leur prochain départ — et la première est donc la
 * réponse à la question qu'on est venu poser. La donner au même poids que les
 * autres, c'est faire relire au conducteur un tableau dont on connaissait le
 * résultat avant de l'afficher.
 *
 * Cette attente-là prend le cartouche de marque, et c'est la **seule** surface
 * de marque du volet : le reste de l'écran n'a rien de saturé, donc l'œil s'y
 * pose sans chercher, et lit ensuite la ligne et la destination qui vont avec.
 * Les attentes suivantes gardent leur encre — verte si la donnée est mesurée,
 * neutre sinon — parce qu'un tableau où tout est désigné ne désigne plus rien.
 *
 * ## Le point temps réel ne suffisait pas seul
 *
 * Six points de diamètre, verts ou gris : c'était toute la différence entre une
 * donnée mesurée et un horaire théorique. En plein soleil derrière une vitre
 * teintée, cette différence n'existe pas ; pour un daltonien, elle n'a jamais
 * existé. Le libellé qui accompagne le point prend donc lui aussi l'encre du
 * temps réel et le poids appuyé — « Temps réel » se lit vert et gras, « Horaire »
 * reste gris et ordinaire. Deux canaux plutôt qu'un, pour la nuance qui décide
 * si l'on fait confiance au chiffre d'à côté.
 */
@Composable
internal fun StopDetailSheet(
    stop: TransitStop,
    repository: StopRepository,
    dispatchers: AuleDispatchers,
    onRoute: () -> Unit,
    onSelectLine: (DepartureRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(stop.id) { StopDetailModel(repository, dispatchers) }
    DisposableEffect(model) {
        model.load(stop.departuresKey)
        onDispose { model.close() }
    }

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(stop.id) {
        while (isActive) {
            delay(STOP_TICK_MS)
            now = Instant.now()
        }
    }

    SheetBody(modifier = modifier) {
        StopIdentity(stop = stop, onRoute = onRoute)

        when {
            model.isLoading && model.departures == null -> {
                AuleLoadingState(label = stringResource(R.string.stop_loading))
            }
            model.departures != null -> {
                DeparturesSection(model.departures!!, now, onSelectLine)
            }
            // Le message de l'exception ne sort pas d'ici : « Failed to connect
            // to /10.0.2.2:8080 » est une trace, pas une phrase. La panne se dit
            // en français, et le détail technique reste dans le journal.
            model.error != null -> {
                AuleEmptyState(
                    title = stringResource(R.string.stop_unavailable_title),
                    detail = stringResource(R.string.stop_unavailable_detail),
                )
            }
        }

        if (model.servingLines.isNotEmpty()) {
            ServingSection(model.servingLines)
        }
    }
}

/**
 * Qui est cet arrêt, et la seule action qu'on puisse faire dessus.
 *
 * Le nom passe par [SheetTitle], la grammaire commune des six volets : c'est là
 * que se décide le cran, et un volet qui titrerait à sa façon ferait bouger le
 * cadre quand on passe d'un arrêt à un véhicule.
 *
 * La rangée coule (`FlowRow`) : à 200 % de taille de police, le bouton passe
 * sous les métadonnées au lieu de les écraser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopIdentity(stop: TransitStop, onRoute: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetTitle(stop.departuresKey)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TransportBadge(
                mode = stop.mode,
                label = stop.mode.label(),
                tint = stop.mode.markerColor(AuleTheme.night).color,
            )
            if (stop.isWheelchairAccessible) {
                Text(
                    text = stringResource(R.string.stop_accessible),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            stop.code?.let { code ->
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Button(
                onClick = onRoute,
                modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
                colors = auleAccentButtonColors(),
                // Les crans d'un bouton à icône sont ceux de Material : ni la
                // taille de l'icône ni son écart au texte ne se décident ici.
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
}

@Composable
private fun DeparturesSection(
    departures: StopDepartures,
    now: Instant,
    onSelectLine: (DepartureRow) -> Unit,
) {
    val rows = departures.grouped(from = now)
    if (rows.isEmpty()) {
        AuleEmptyState(
            title = departures.outcome.title(),
            detail = departures.outcome.detail(),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetSectionLabel(stringResource(R.string.stop_next_departures))
        SheetCard(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                DepartureRowItem(
                    row = row,
                    onSelect = { onSelectLine(row) },
                    // Le rang porte deux choses à la fois, et c'est voulu :
                    // l'ordre d'entrée en cascade, et le fait d'être ou non le
                    // prochain passage. Les deux se lisent sur le même chiffre
                    // parce qu'ils disent la même chose — ce qui arrive d'abord
                    // arrive d'abord.
                    rank = index,
                )
                if (index < rows.lastIndex) {
                    SheetRowDivider()
                }
            }
        }
    }
}

/**
 * Un passage : quelle ligne, où elle va, dans combien de temps.
 *
 * Lue d'un trait par TalkBack. Séparés, le badge, la destination, le point
 * temps réel et l'attente feraient quatre arrêts de curseur pour une phrase
 * qu'on dit d'un souffle — « ligne C6, vers Beaujoire, temps réel, 3 min ».
 *
 * Le facteur de police est plafonné comme ailleurs sur les rangées denses : à
 * 200 %, la destination et l'attente se disputeraient une largeur qu'aucune des
 * deux n'a, et c'est l'attente qui perdrait.
 *
 * ## Qui domine la rangée
 *
 * L'attente, seule. La destination reste au slot ordinaire alors que le reste
 * du dépôt est passé aux slots appuyés, et c'est une décision et non un oubli :
 * une rangée avec deux graisses fortes n'a plus de premier regard. On vient
 * chercher **quand**, la ligne et la destination répondent ensuite à **quoi** et
 * **où** — la hiérarchie de lecture est celle-là, la typographie la copie.
 *
 * @param rank le rang de la rangée : le rang zéro est le prochain passage de
 *   l'arrêt, et c'est lui qui porte le cartouche de marque. Le même chiffre
 *   règle la cascade d'apparition.
 */
@Composable
private fun DepartureRowItem(row: DepartureRow, rank: Int, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isNext = rank == 0
    val feedLabel = stringResource(
        if (row.isRealtime) R.string.stop_realtime else R.string.stop_scheduled,
    )
    val wait = row.nextWait?.label()
    val isApproaching = row.nextWait is Wait.Approaching
    val following = row.followingWaits.joinToString(WAIT_SEPARATOR)
    val hint = stringResource(R.string.stop_line_hint)
    val spoken = buildString {
        append(stringResource(R.string.line_badge, row.line))
        append(", ")
        append(stringResource(R.string.nearby_towards, row.destination))
        append(", ")
        append(feedLabel)
        if (wait != null) {
            append(", ")
            append(wait)
        }
        if (following.isNotEmpty()) {
            append(", ")
            append(stringResource(R.string.stop_following_waits, following))
        }
    }

    AuleCappedFontScale {
        // ## Pourquoi ce n'est pas un `ListItem`
        //
        // C'en était un, et le dépôt interdit par ailleurs de redire à la main
        // un composant Material. L'exception est assumée ici, pour une raison
        // qui tient en un chiffre : `ListItem` plancher ses rangées à deux
        // lignes à **72 dp**, quand celle-ci n'a besoin que de 56 — le badge
        // fait 22 points, la destination et sa mention 36, la colonne d'attente
        // 48. Vingt points d'air par rangée, sur la seule liste de
        // l'application qu'on parcourt en cherchant un chiffre, c'est deux
        // passages de moins à l'écran à chaque ouverture du volet.
        //
        // Le plancher n'est pas contournable depuis l'appel : il est calculé
        // avant les contraintes entrantes, et la surcharge expressive — celle
        // qui expose `contentPadding` — le réapplique à l'identique tant que
        // `isExpressiveListItemHeightBasedOnTextLinesFixEnabled` vaut `true`,
        // ce qui est son défaut. Passer ce drapeau à `false` réglerait la
        // rangée en changeant **toutes** les listes de l'application depuis un
        // interrupteur global : le remède serait pire.
        //
        // Ce qui se perd en écrivant la rangée à la main, ce sont les marges de
        // Material et ses couleurs de contenu. Les deux sont reposées
        // explicitement ci-dessous — gouttières de cartouche, encre secondaire
        // sur la mention et sur les passages suivants — et rien d'autre du
        // composant n'était utilisé : le cartouche porte déjà le fond, et la
        // rangée porte déjà sa propre sémantique.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Un plancher, pas une hauteur : le contenu la dépasse dès que
                // la destination passe sur deux lignes, ou que le facteur de
                // police monte vers son plafond. La rangée grandit alors, elle
                // ne rogne pas — c'est toute la différence avec un `heightIn`
                // qui plafonnerait.
                .heightIn(min = AuleTouch.minimum)
                .auleEnter(index = rank)
                .clickable(onClick = onSelect)
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    // L'indice dit ce que le geste **fait**, séparément de ce
                    // que la rangée **est** : sans lui, TalkBack annonce un
                    // bouton dont l'effet ne s'apprend qu'en appuyant.
                    onClick(label = hint, action = null)
                }
                .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(
                line = row.line,
                colorHex = row.lineColor,
                contentDescription = stringResource(R.string.line_badge, row.line),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.destination,
                    // Le cran du corps, et non celui d'un titre. « Mendès
                    // France - Bellevue » passait sur deux lignes à seize
                    // points, ce qui faisait grandir la rangée d'un tiers
                    // pour un nom sur trois : sept passages dont trois
                    // hauteurs différentes, dans un tableau qu'on parcourt
                    // à la verticale. À quatorze, la destination tient sur
                    // sa ligne et le tableau retrouve un pas régulier.
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RealtimeDot(
                        isLive = row.isRealtime,
                        liveDescription = stringResource(R.string.stop_realtime),
                        scheduledDescription = stringResource(R.string.stop_scheduled),
                    )
                    Text(
                        text = feedLabel,
                        // Le libellé **nomme** la nuance, il ne la colorie pas :
                        // « Temps réel » et « Horaire » sont deux mots
                        // différents, et un mot n'a pas besoin d'être vert pour
                        // se lire. Il l'a été — vert et appuyé — et sur un
                        // tableau de sept passages cela faisait sept blocs de
                        // vert soutenu sous sept chiffres verts, à côté d'un
                        // cartouche teal : trois canaux pour un seul fait, et
                        // une colonne qu'on ne regarde plus parce qu'elle crie
                        // partout à la fois.
                        //
                        // Ce qui reste vert est le **point**, qui n'a que la
                        // couleur pour parler, et le chiffre, qui est ce qu'on
                        // vient chercher.
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (wait != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                ) {
                    if (isNext) {
                        NextWaitPlate(wait)
                    } else {
                        Text(
                            text = wait,
                            // `titleLargeEmphasized` : le rôle `DATA`, donc
                            // les chiffres à chasse fixe — c'est le seul
                            // endroit de la fiche où un chiffre change tout
                            // seul sous les yeux — et la graisse du slot
                            // appuyé, qui ne coûte pas un point de hauteur.
                            //
                            // Sauf pour « à l'approche », qui est un
                            // libellé et non un nombre : au cran des
                            // données il occupait deux fois la largeur de
                            // la colonne, rognait la destination jusqu'à la
                            // faire passer sur deux lignes, et donnait à
                            // trois véhicules pourtant dans le même état
                            // trois tailles de texte différentes selon leur
                            // rang. Le cartouche avait déjà baissé le cran
                            // pour cette raison exacte ; c'est le même ici,
                            // si bien que l'approche s'écrit pareil à tous
                            // les rangs et que le cartouche continue de
                            // dire « prochain » plutôt qu'« en approche ».
                            style = if (isApproaching) {
                                MaterialTheme.typography.titleMediumEmphasized
                            } else {
                                MaterialTheme.typography.titleLargeEmphasized
                            },
                            maxLines = 1,
                            color = if (row.isRealtime) realtimeInk() else colors.onSurface,
                        )
                    }
                    if (following.isNotEmpty()) {
                        Text(
                            text = following,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Le chiffre qu'on est venu chercher, sur l'aplat de marque.
 *
 * ## Pourquoi un cartouche et pas seulement une encre
 *
 * Colorer le premier chiffre aurait suffi à le distinguer des autres — et
 * l'aurait rendu illisible en tant que temps réel, puisque c'est déjà la
 * couleur qui porte « mesuré ou théorique » dans cette colonne. Deux sens sur
 * un seul canal, et le conducteur arbitre. Le cartouche change de **plan** au
 * lieu de changer de teinte : le chiffre se détache par sa profondeur, la
 * colonne garde sa sémantique de couleur intacte, et la ligne « Temps réel »
 * juste à gauche continue de dire d'où vient la donnée.
 *
 * C'est la seule surface de marque du volet, comme la carte de tête est la
 * seule d'« Autour de vous ». La deuxième la banaliserait.
 *
 * ## Ce qu'on accepte de payer
 *
 * Le cartouche vaut environ seize points de hauteur de plus que le texte nu, et
 * la rangée du prochain passage est donc la plus haute du tableau. Dans le cran
 * partiel — celui qu'on lit sans lâcher le volant — ce n'est pas une rangée
 * perdue, c'est une rangée moins serrée à l'endroit exact où l'œil se pose. Le
 * même écart pris en pleine largeur, lui, aurait bien coûté un passage : c'est
 * la raison pour laquelle « Y aller » ne vit pas sur sa propre rangée.
 *
 * Le relief vient du dégradé et du reflet, pas d'une ombre : [AuleElevation.NONE]
 * parce qu'on est déjà dans un cartouche, lui-même posé dans un volet qui porte
 * son ombre. Une troisième ombre empilée, et plus rien ne dit quelle surface
 * supporte laquelle.
 */
@Composable
private fun NextWaitPlate(wait: String) {
    AuleBrandSurface(
        shape = MaterialTheme.shapes.small,
        elevation = AuleElevation.NONE,
    ) {
        // Aucune encre n'est passée : la surface de marque a posé la sienne. Le
        // vert du temps réel y perdrait de toute façon son contraste **et** son
        // sens, exactement comme il le perd sur la carte de tête d'« Autour de
        // vous » — et la mention « Temps réel » de la rangée le porte déjà.
        Text(
            text = wait,
            // Le cran des titres, pas celui des chiffres. La plaque porte déjà
            // l'emphase par son aplat : au cran `DATA`, elle la portait deux
            // fois — et « à l'approche », qui est un libellé et non un nombre,
            // débordait de la colonne jusqu'à sortir du cartouche. Un aplat de
            // marque n'a pas besoin qu'on grossisse ce qu'on écrit dessus ;
            // c'est même le contraire, puisqu'il est déjà la seule chose
            // colorée de la liste.
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = AuleSpacing.sm,
                vertical = AuleSpacing.xs,
            ),
        )
    }
}

/**
 * Les lignes desservies, et où elles vont.
 *
 * Elles restent vraies quand les passages ne le sont plus : la nuit, un arrêt
 * n'annonce rien mais dessert toujours les mêmes lignes.
 *
 * Elles entrent en cascade comme les passages, et leur rang repart de zéro : les
 * deux listes vivent dans deux cartouches distincts, et une cascade qui
 * continuerait d'un cartouche à l'autre ferait arriver la première ligne
 * desservie une demi-seconde après le dernier passage — un retard qu'on
 * regarderait au lieu de le sentir. C'est le traitement qu'ont déjà les arrêts
 * et les véhicules d'« Autour de vous ».
 */
@Composable
private fun ServingSection(lines: List<ServingLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetSectionLabel(stringResource(R.string.stop_serving_lines))
        SheetCard(modifier = Modifier.fillMaxWidth()) {
            lines.forEachIndexed { index, line ->
                ListItem(
                    modifier = Modifier
                        .defaultMinSize(minHeight = AuleTouch.minimum)
                        .auleEnter(index = index)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${line.line}, ${line.direction}"
                        },
                    leadingContent = {
                        LineBadge(
                            line = line.line,
                            colorHex = line.lineColor,
                            contentDescription = stringResource(R.string.line_badge, line.line),
                        )
                    },
                    headlineContent = {
                        Text(
                            text = line.direction,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (index < lines.lastIndex) {
                    SheetRowDivider()
                }
            }
        }
    }
}

/** Assez pour que « 3 min » devienne « 2 min » sans qu'on l'ait vu vieillir. */
private const val STOP_TICK_MS = 10_000L

/** Ce qui sépare deux attentes d'une même ligne, à l'œil comme à l'oreille. */
private const val WAIT_SEPARATOR = " · "
