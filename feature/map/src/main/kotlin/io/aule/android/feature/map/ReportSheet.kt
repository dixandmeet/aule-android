package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleConnectedButtonGroup
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.model.DriverReport
import io.aule.android.core.model.DriverReportException
import io.aule.android.core.model.DriverReportFailureKind
import io.aule.android.core.model.DriverReportType
import io.aule.android.core.model.DriverReportUrgency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * « Signaler un événement » — dix types, trois urgences, et le moins de
 * gestes possible.
 *
 * Un type suffit : au volant, exiger une phrase reviendrait à ne rien
 * recevoir. Rien ne se referme sans confirmation d'envoi — un signalement
 * qu'on croit parti et qui n'est pas parti est pire que pas de bouton.
 *
 * ## Le type prend l'aplat de marque
 *
 * Le chip retenu portait le conteneur secondaire de Material. Or dans Aule,
 * `secondary` **n'est pas** une nuance de la marque : c'est le vert du temps
 * réel, un fait métier. Choisir « Accident » allumait donc une pastille vert
 * menthe qui, deux écrans plus loin, veut dire « cette donnée vient du
 * véhicule » — et qui, posée au milieu de neuf pastilles grises, se voyait
 * sans peser.
 *
 * Le type retenu prend l'aplat de marque **plein**, encre claire. C'est le
 * même parti pris que la variante d'itinéraire ou l'étape en cours : le pastel
 * dit « voici la case cochée », le teal profond dit « voici ce que vous
 * envoyez ». Sur un formulaire dont tout le reste est facultatif, c'est la
 * seule chose que le conducteur doit vérifier avant d'appuyer.
 *
 * ## L'urgence n'est pas un onzième type
 *
 * Elle portait les mêmes chips que le type, à la même taille, dans la même
 * grille. Deux choix de même apparence l'un sous l'autre, et rien ne dit
 * lequel est le geste principal — alors qu'un seul des deux est obligatoire.
 *
 * L'urgence est un **choix exhaustif déjà répondu** : « Normal » est retenu
 * d'entrée, et l'une des trois valeurs part toujours. C'est la définition du
 * bouton segmenté, et l'inverse de celle du chip, qui suppose qu'on puisse
 * n'en cocher aucun. Trois segments côte à côte se lisent en outre comme une
 * **échelle**, ce que dix pastilles réparties sur quatre lignes ne font pas.
 *
 * ## Pourquoi « Urgent » n'est pas rouge
 *
 * Ce serait l'idée évidente, et elle se retourne : ce volet a déjà un rouge, le
 * bandeau d'échec d'envoi. Un signalement urgent dont l'envoi échoue afficherait
 * alors deux rouges qui ne disent pas la même chose — l'un « c'est grave »,
 * l'autre « ça n'est pas parti ». Le rouge reste ce qui a échoué et qu'on peut
 * corriger ; l'urgence se lit à sa position dans l'échelle et à son libellé.
 *
 * ## Le bouton d'envoi sort du défilement
 *
 * Il fermait la colonne qui défile. Dix types en grille, l'échelle d'urgence et
 * un champ de saisie plus haut, il passait sous le pli dès que le clavier
 * montait ou que le conducteur avait grossi les caractères — et une action
 * qu'il faut aller chercher n'est pas l'action principale, quelle que soit sa
 * couleur. Il est maintenant ancré au bas du volet, **hors** de la zone de
 * défilement, à l'endroit exact où le pouce se trouve déjà. Le bandeau d'échec
 * l'accompagne, pour la même raison : une erreur qui défile hors de vue est une
 * erreur que personne ne lit.
 *
 * ## Trois états, trois lectures
 *
 * - **envoi en cours** : la grille et le champ se figent, la roue prend la
 *   place du pictogramme *dans* le bouton, et le libellé passe d'« Envoyer » à
 *   « Envoi… ». Le bouton garde donc un nom — un bouton dont le texte
 *   disparaît au profit d'une roue est annoncé « bouton » sans qu'on sache
 *   lequel — et ce nom **change**, ce qui est la seule façon dont cet état
 *   s'entend : une roue ne porte aucun texte, et un libellé immobile ferait
 *   croire à un appui qui n'a pas pris. C'est déjà ce que fait le bouton de
 *   connexion ;
 * - **échec** : le bandeau d'alerte, qui porte déjà sa région vivante et son
 *   `error()` sémantique. Il reste posé au-dessus d'un bouton toujours
 *   actionnable : l'échec de ce volet est celui qu'on réessaie ;
 * - **succès** : le formulaire disparaît au profit de la surface de marque et
 *   de sa coche. C'est le seul moment où ce volet n'a qu'une chose à dire, donc
 *   le seul endroit où il peut se permettre la surface de marque — le bouton
 *   d'envoi, lui, n'existe plus à cet instant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSheetHost(
    onSubmit: suspend (DriverReport) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        ReportSheet(onSubmit = onSubmit, onClose = onClose)
    }
}

@Composable
private fun ReportSheet(
    onSubmit: suspend (DriverReport) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf<DriverReportType?>(null) }
    var urgency by remember { mutableStateOf(DriverReportUrgency.MEDIUM) }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<DriverReportFailureKind?>(null) }
    val title = stringResource(
        if (sent) R.string.report_sent_title else R.string.report_title,
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        ReportHeader(title = title, onClose = onClose)

        // `fill = false` : le volet reste à la taille de son contenu tant qu'il
        // tient à l'écran, et ne se met à défiler qu'au-delà. Avec le poids
        // seul, un formulaire court étirerait le volet sur toute la hauteur
        // pour ne rien y mettre.
        SheetBody(
            modifier = Modifier.weight(1f, fill = false),
            // La barre d'action porte déjà la marge de pied du volet.
            footer = false,
            spacing = AuleSpacing.lg,
        ) {
            if (sent) {
                ReportSentPanel()
            } else {
                ReportForm(
                    type = type,
                    urgency = urgency,
                    message = message,
                    locked = sending,
                    onType = { type = it },
                    onUrgency = { urgency = it },
                    onMessage = { message = it },
                )
            }
        }

        ReportActionBar(
            sent = sent,
            sending = sending,
            armed = type != null,
            failure = failure,
            onClose = onClose,
            onSend = {
                val chosen = type ?: return@ReportActionBar
                if (sending) return@ReportActionBar
                sending = true
                failure = null
                scope.launch {
                    try {
                        onSubmit(
                            DriverReport(
                                type = chosen,
                                urgency = urgency,
                                message = message,
                            ),
                        )
                        sent = true
                    } catch (cancelled: CancellationException) {
                        sending = false
                        throw cancelled
                    } catch (thrown: DriverReportException) {
                        sending = false
                        failure = thrown.kind
                    } catch (_: Throwable) {
                        sending = false
                        failure = DriverReportFailureKind.UNKNOWN
                    }
                }
            },
        )
    }
}

/**
 * Le titre du volet et sa sortie, sur une ligne qui ne défile pas.
 *
 * C'était une `TopAppBar`, dont le conteneur valait déjà la surface du volet —
 * une barre invisible, qui n'existait que pour porter deux choses. Le titre
 * prend donc la grammaire des autres volets de la carte, [SheetTitle] : même
 * graisse appuyée, même annonce en tant qu'en-tête, et le volet de signalement
 * cesse d'être le seul écrit dans un autre corps.
 */
@Composable
private fun ReportHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AuleSpacing.lg, end = AuleSpacing.sm)
            .padding(bottom = AuleSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetTitle(text = title, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.sheet_dismiss),
            )
        }
    }
}

/**
 * Le formulaire : ce qu'on choisit, puis ce qu'on précise.
 *
 * Les deux blocs sont serrés à l'intérieur ([AuleSpacing.sm] entre un intitulé
 * et ce qu'il annonce) et espacés entre eux ([AuleSpacing.lg]). C'est ce qui
 * fait lire « une phrase, une grille » puis « un intitulé, une échelle » plutôt
 * qu'une pile de cinq éléments équidistants où plus rien n'appartient à rien.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReportForm(
    type: DriverReportType?,
    urgency: DriverReportUrgency,
    message: String,
    locked: Boolean,
    onType: (DriverReportType) -> Unit,
    onUrgency: (DriverReportUrgency) -> Unit,
    onMessage: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current

    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        // La consigne est une phrase, pas un intitulé de section : elle dit ce
        // qu'on attend du conducteur — un appui — et non le nom de ce qui
        // suit. En `labelSmall` gris, elle avait la taille d'une mention
        // légale et se lisait après la grille qu'elle est censée introduire.
        Text(
            text = stringResource(R.string.report_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            DriverReportType.entries.forEach { candidate ->
                val chosen = type == candidate
                FilterChip(
                    selected = chosen,
                    onClick = {
                        // Le retour tactile est ici la moitié de la réponse :
                        // un conducteur qui vise une pastille sans quitter la
                        // route des yeux sait au doigt qu'il en a touché une,
                        // avant même de vérifier laquelle.
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onType(candidate)
                    },
                    label = { Text(candidate.label()) },
                    enabled = !locked,
                    leadingIcon = if (chosen) selectedChipIcon else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = colors.onPrimary,
                        selectedLeadingIconColor = colors.onPrimary,
                    ),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetSectionLabel(stringResource(R.string.report_urgency))
        AuleConnectedButtonGroup(
            options = DriverReportUrgency.entries,
            selected = urgency,
            label = { candidate -> candidate.label() },
            onSelect = onUrgency,
            modifier = Modifier.fillMaxWidth(),
            enabled = !locked,
            colors = ToggleButtonDefaults.toggleButtonColors(
                checkedContainerColor = colors.primary,
                checkedContentColor = colors.onPrimary,
            ),
        )
    }

    OutlinedTextField(
        value = message,
        onValueChange = onMessage,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleControl.field),
        enabled = !locked,
        label = { Text(stringResource(R.string.report_message)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
        ),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
}

/**
 * Le signalement est parti.
 *
 * La seule surface de marque du volet, et elle n'apparaît qu'ici : à cet
 * instant le formulaire n'existe plus, le bouton d'envoi non plus, et rien ne
 * lui dispute le regard. Un conducteur qui rouvre l'écran trois secondes plus
 * tard doit savoir en un coup d'œil périphérique — teal plein, coche pleine —
 * qu'il n'a pas à recommencer.
 *
 * Le titre n'est pas répété : l'en-tête du volet dit déjà « Signalement
 * envoyé », et le cartouche confirme la réception. L'annonce, elle, reprend les
 * deux — TalkBack lit une phrase, il ne décrit pas une mise en page, et un
 * conducteur qui n'a pas l'écran sous les yeux doit entendre le fait entier.
 *
 * L'ombre est au premier cran, pas au cran flottant : le cartouche est posé
 * **dans** un volet, lequel porte déjà la sienne. Une ombre haute ferait
 * flotter une surface au-dessus d'une surface, et l'œil ne saurait plus
 * laquelle est le support de l'autre — c'est la règle que suit déjà la carte de
 * tête d'« autour de vous ».
 */
@Composable
private fun ReportSentPanel() {
    val title = stringResource(R.string.report_sent_title)
    val detail = stringResource(R.string.report_sent_detail)
    AuleBrandSurface(
        modifier = Modifier
            .fillMaxWidth()
            .auleEnter()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "$title. $detail"
            },
        shape = MaterialTheme.shapes.large,
        elevation = AuleElevation.RESTING,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AuleSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AuleGlyph.CHECK.asImageVector(filled = true),
                contentDescription = null,
                modifier = Modifier.size(SENT_MARK),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}

/**
 * La barre d'action, ancrée sous la zone qui défile.
 *
 * Elle porte toujours **une** action et une seule : envoyer tant que le
 * signalement n'est pas parti, fermer une fois qu'il l'est. La croix de
 * l'en-tête reste, mais elle est en haut à droite d'un volet pleine largeur,
 * c'est-à-dire à l'endroit qu'un pouce n'atteint pas sans changer la prise du
 * téléphone.
 *
 * Le bouton de sortie est **tonal**, pas de marque : dans l'état « envoyé », la
 * bonne nouvelle est le cartouche, pas le bouton qui referme. Deux aplats teal
 * l'un sous l'autre, et le regard hésiterait entre l'information et la porte.
 *
 * L'ombre du bouton d'envoi est teintée d'accent et ne vient qu'avec un type
 * choisi : une lueur de marque désigne l'action, et la même lueur sous un
 * bouton qui ne répond pas promettrait un geste qui ne marche pas.
 *
 * Elle reste au cran [AuleElevation.RESTING] et non au cran flottant. Ce bouton
 * ne survole rien : il s'appuie sur le bas du volet, et l'échelle dit
 * précisément qu'une ombre trop haute sous une barre collée à un bord se lit
 * comme un décollement raté. C'est aussi le cran que prend le bouton de fin de
 * service, qui occupe la même place dans le même genre de volet.
 */
@Composable
private fun ReportActionBar(
    sent: Boolean,
    sending: Boolean,
    armed: Boolean,
    failure: DriverReportFailureKind?,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.lg)
            .padding(top = AuleSpacing.md, bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        if (failure != null) {
            AuleBanner(message = failure.label(), tone = AuleTone.ALERT)
        }
        if (sent) {
            FilledTonalButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AuleControl.height),
                shape = shape,
            ) {
                Text(
                    text = stringResource(R.string.sheet_dismiss),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
        } else {
            Button(
                onClick = onSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .auleShadow(
                        level = if (armed) AuleElevation.RESTING else AuleElevation.NONE,
                        shape = shape,
                        tint = AuleShadowTint.ACCENT,
                    )
                    .defaultMinSize(minHeight = AuleControl.height),
                enabled = armed,
                shape = shape,
                colors = auleAccentButtonColors(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // La roue prend la place du pictogramme, jamais celle du
                    // libellé : le bouton garde un nom pendant tout l'envoi. Et
                    // comme il est pleine largeur, rien ne se déplace sous le
                    // doigt quand le mot change.
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AuleControl.icon),
                            color = AuleTheme.tokens.onAccent.color,
                            strokeWidth = AuleStroke.glyph,
                        )
                    } else {
                        // L'avion de papier. « Envoyer » se lit ; le glyphe se
                        // reconnaît sans lire, ce qui est tout ce qu'on peut
                        // demander à vingt secondes entre deux manœuvres.
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(AuleControl.icon),
                        )
                    }
                    // Le libellé **change**, et c'est ce qui rend l'envoi
                    // audible. Une roue est muette : elle ne porte aucun texte,
                    // et un bouton qui garde le mot « Envoyer » pendant qu'il
                    // envoie ne dit rien de neuf à TalkBack — le conducteur
                    // qui n'a pas l'écran sous les yeux ne peut alors pas
                    // distinguer « c'est parti » de « mon appui n'a pas pris »,
                    // et réappuie. Le nom du bouton est ici le seul canal
                    // d'annonce dont on dispose ; c'est celui d'AuthScreen.
                    Text(
                        text = stringResource(
                            if (sending) R.string.report_sending else R.string.report_send,
                        ),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
    }
}

/**
 * La coche du chip retenu.
 *
 * Elle comptait double quand le chip sélectionné n'était qu'une pastille
 * teintée à 12 % : un daltonien deutan ne voyait alors aucune différence entre
 * « Circulation » et « Accident ». L'aplat de marque plein a réglé ce
 * cas-là — teal profond sous encre claire, personne ne le rate.
 *
 * Elle reste pourtant, et pour un autre moment : pendant l'envoi, la grille se
 * fige et Material éteint l'aplat retenu comme tous les autres. Ne subsiste
 * alors que la coche — un signe de **forme**, qui survit au gris — pour dire ce
 * qui part.
 */
private val selectedChipIcon: @Composable () -> Unit = {
    Icon(
        imageVector = AuleGlyph.CHECK.asImageVector(),
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize),
    )
}

/**
 * La coche de confirmation.
 *
 * 32 dp, contre 24 pour la grille d'icône ordinaire : ce n'est pas une commande
 * mais le sujet du cartouche, et à la taille d'une icône de barre d'outils elle
 * se lit comme une puce devant un texte. Au-delà, elle prend la place de la
 * phrase qu'elle accompagne.
 */
private val SENT_MARK = 32.dp

@Composable
private fun DriverReportType.label(): String = stringResource(
    when (this) {
        DriverReportType.TRAFFIC -> R.string.report_type_traffic
        DriverReportType.DELAY -> R.string.report_type_delay
        DriverReportType.DETOUR -> R.string.report_type_detour
        DriverReportType.CROWDED -> R.string.report_type_crowded
        DriverReportType.STOP_SKIPPED -> R.string.report_type_stop_skipped
        DriverReportType.BREAKDOWN -> R.string.report_type_breakdown
        DriverReportType.ACCIDENT -> R.string.report_type_accident
        DriverReportType.PASSENGER_ILLNESS -> R.string.report_type_passenger_illness
        DriverReportType.INCIVILITY -> R.string.report_type_incivility
        DriverReportType.OTHER -> R.string.report_type_other
    },
)

@Composable
private fun DriverReportUrgency.label(): String = stringResource(
    when (this) {
        DriverReportUrgency.LOW -> R.string.report_urgency_low
        DriverReportUrgency.MEDIUM -> R.string.report_urgency_medium
        DriverReportUrgency.HIGH -> R.string.report_urgency_high
    },
)

@Composable
private fun DriverReportFailureKind.label(): String = stringResource(
    when (this) {
        DriverReportFailureKind.NOT_SIGNED_IN -> R.string.report_error_session
        DriverReportFailureKind.NO_DRIVER -> R.string.report_error_driver
        DriverReportFailureKind.NOT_CONFIGURED -> R.string.report_error_config
        DriverReportFailureKind.NETWORK -> R.string.report_error_network
        DriverReportFailureKind.REJECTED -> R.string.report_error_rejected
        DriverReportFailureKind.UNKNOWN -> R.string.report_error_unknown
    },
)
