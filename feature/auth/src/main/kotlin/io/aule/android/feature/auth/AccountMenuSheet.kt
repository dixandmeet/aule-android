package io.aule.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.aulePress
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleChrome
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.AccountModes
import io.aule.android.core.model.DriverProfile

/**
 * Le menu du compte, **en volet** au-dessus de la carte.
 *
 * Il a été un écran plein, et la carte disparaissait le temps qu'on lise son
 * propre nom. C'est le volet du `BottomSheetScaffold` de la carte qui le porte
 * maintenant, comme sur iOS et comme tout le reste de cet écran : on ouvre le
 * menu **par-dessus** ce qu'on regardait, la ville continue derrière, et le
 * refermer ne coûte pas un aller-retour d'écran. Il s'ouvre depuis l'avatar du
 * socle de recherche — voir [AccountAvatarButton].
 *
 * Ce que le volet lui retire, il l'avait en double : la barre de titre et sa
 * flèche de retour (le volet a sa poignée et le geste de retour), le lavis de
 * marque en fond (un volet est déjà une surface), et la marge des barres
 * système (l'écran la pose pour lui).
 *
 * Port de `SAE/lib/screens/map_menu_screen.dart` : l'identité en carte en
 * haut, la fin de session seule tout en bas. La distance entre les deux est
 * délibérée — un doigt qui dérape en roulant ne doit pas déconnecter — et la
 * confirmation qui suit vient de la même décision
 * (`SAE/docs/carte-app/REPRISE.md`, 09/08/2026).
 *
 * ## Ce que la refonte change, et pourquoi
 *
 * Le menu est un **hub** : on y vient pour aller ailleurs, et on y lit au
 * passage qui l'on est. Trois décisions en découlent, dans cet ordre.
 *
 * 1. **L'identité quitte la surface de marque.** Le dégradé désignait la carte
 *    à coup sûr, mais il criait plus fort que les destinations — et sur un
 *    écran dont c'est le métier de mener ailleurs, la marque ne doit pas être
 *    ce qu'on lit en premier. La hiérarchie passe donc au **relief** : une
 *    seule chose flotte (l'identité, `surfaceContainerHigh` et l'ombre haute),
 *    tout le reste se pose (`surfaceContainer`, l'ombre basse). Le teal ne
 *    disparaît pas pour autant — il reste au lavis du fond, au portrait et aux
 *    tuiles des destinations, c'est-à-dire là où il accentue au lieu de couvrir.
 * 2. **Chaque destination devient une carte, avec sa description.** Deux
 *    entrées d'égale importance empilées dans un même cartouche se lisaient
 *    comme une liste de réglages ; séparées, décrites et posées chacune sur son
 *    aplat, elles se lisent comme deux portes. La description est ce qui
 *    dispense d'ouvrir pour savoir.
 * 3. **Le pied sort du défilement.** La distance de sécurité tenait à un
 *    ressort vertical, donc elle mourait au premier écran trop petit : à grande
 *    taille de police, la fin de session remontait sous le pouce. Le contenu
 *    défile désormais, le pied ne bouge plus, et l'écart est garanti quelle que
 *    soit la hauteur disponible.
 *
 * La cascade d'entrée est conservée — le menu s'ouvre par-dessus la carte, et
 * sans déroulé il se téléporte. Ses rangs sont **fixes** : un rang calculé sur
 * les blocs présents ferait dépendre l'ordre d'arrivée de l'état du réseau.
 */
/**
 * L'avatar du compte, tel qu'il se pose sur la carte.
 *
 * **C'est la porte du menu**, et il a remplacé le bouton hamburger : trois
 * traits ne disent que « il y a un menu », là où un portrait dit *de qui* est
 * la session ouverte — la question qu'on se pose devant un véhicule qu'on
 * prend, et la seule que le socle avait la place de porter. C'est aussi ce que
 * fait l'app iOS, à la même place.
 *
 * Il vit ici et non dans `:feature:map` parce que la photo et l'identité vivent
 * ici : la carte ne connaît pas le compte, et n'a pas à le connaître. Elle
 * reçoit ce bouton **entier**, son libellé compris.
 *
 * Rond, contrairement au portrait carré du menu : posé sur la carte, il est une
 * cible parmi des pastilles rondes, pas une carte d'identité.
 */
@Composable
fun AccountAvatarButton(
    viewModel: AuthViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fallback = stringResource(R.string.menu_local_session)
    val name = state.profile?.displayName()
        ?: state.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: fallback
    val label = stringResource(R.string.menu_open)
    Surface(
        onClick = onClick,
        // Dessiné au cran de la pastille, touché au plancher : Material
        // agrandit lui-même la cible autour d'une surface cliquable, et c'est
        // ce qui permet à l'avatar d'être **plus petit que le doigt** sans rien
        // lui coûter. Il tient ainsi la moitié de la hauteur de la carte du
        // socle — la proportion d'iOS — là où, à la taille de sa cible, il la
        // remplissait d'un bord à l'autre et pesait plus lourd que le champ
        // qu'il accompagne.
        //
        // Le portrait qu'elle porte donne la couleur et la photo ; cette
        // surface-ci ne donne que le geste, et se tait — deux aplats
        // superposés, c'en est un de trop.
        modifier = modifier
            .size(AuleChrome.pill)
            // Fusionnée : sans photo, le portrait écrit deux initiales, et
            // TalkBack les annonçait comme un arrêt de plus après le bouton.
            .semantics(mergeDescendants = true) { contentDescription = label },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        AvatarPortrait(
            name = name,
            bytes = state.avatarBytes,
            size = AuleChrome.pill,
            shape = CircleShape,
            container = MaterialTheme.colorScheme.primary,
            onContainer = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun AccountMenuSheet(
    viewModel: AuthViewModel,
    versionLabel: String,
    onOpenProfile: () -> Unit,
    /**
     * Les réglages du Guet. Ils vivent dans `:feature:map` — c'est là que le Guet
     * s'exerce —, et le menu ne fait que les désigner : lui passer un rappel plutôt
     * qu'un écran garde `:feature:auth` ignorant de la carte.
     */
    onOpenGuet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MenuGutter),
    ) {
        Text(
            text = stringResource(R.string.menu_title),
            // Le titre d'un volet, et non celui d'un écran : c'est le cran que
            // les six autres volets de la carte tiennent déjà, et un menu qui
            // s'annonce plus fort qu'une fiche d'arrêt n'aurait pas de raison.
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        Column(
            modifier = Modifier
                // `fill = false` : le volet se mesure sur son contenu pour en
                // déduire son palier, et un poids qui remplit déclarerait
                // toujours la hauteur de l'écran.
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Le défilement rogne net en haut et en bas. Sans cette
                // respiration, l'ombre de la dernière carte se ferait couper à
                // mi-flou en butée, ce qui se voit comme une arête et non
                // comme une ombre.
                .padding(bottom = AuleSpacing.lg),
        ) {
            IdentityCard(
                email = state.email,
                profile = state.profile,
                depotLabel = state.depot?.label,
                modes = state.access?.modes,
                avatarBytes = state.avatarBytes,
                onOpen = onOpenProfile,
                modifier = Modifier.auleEnter(index = 0),
            )

            Text(
                text = stringResource(R.string.menu_group_account).uppercase(),
                // Le slot appuyé porte déjà la graisse : un `fontWeight` posé à
                // côté du style est la manière la plus sûre de voir deux
                // intertitres diverger.
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(
                        start = AuleSpacing.md,
                        top = AuleSpacing.lg,
                        bottom = AuleSpacing.sm,
                    )
                    .semantics { heading() }
                    .auleEnter(index = 1),
            )
            DestinationCard(
                title = stringResource(R.string.menu_my_profile),
                description = stringResource(R.string.menu_my_profile_desc),
                glyph = AuleGlyph.PERSON,
                onClick = onOpenProfile,
                modifier = Modifier.auleEnter(index = 2),
            )
            DestinationCard(
                title = stringResource(R.string.menu_guet),
                description = stringResource(R.string.menu_guet_desc),
                glyph = AuleGlyph.HEADING,
                onClick = onOpenGuet,
                modifier = Modifier
                    .padding(top = AuleSpacing.md)
                    .auleEnter(index = 3),
            )
        }

        // Le pied est hors du défilement : c'est ce qui tient la fin de session
        // à distance du reste, même quand le contenu déborde. Ses deux lignes
        // partagent un rang de cascade — elles arrivent ensemble, comme le bloc
        // qu'elles forment.
        SignOutButton(
            enabled = !state.isSubmitting,
            onClick = { confirming = true },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .auleEnter(index = 4),
        )
        Text(
            text = stringResource(R.string.menu_version, versionLabel),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            // Centrée : alignée à gauche, la version s'alignait sur le texte des
            // rangées et se lisait comme une dernière entrée du menu.
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AuleSpacing.sm)
                .auleEnter(index = 4),
        )

        if (confirming) {
            SignOutDialog(
                onDismiss = { confirming = false },
                onConfirm = {
                    confirming = false
                    viewModel.signOut()
                },
            )
        }
    }
}

/**
 * Qui est connecté, en tête de l'écran.
 *
 * Le nom porte, le matricule, le dépôt et l'habilitation deviennent des puces —
 * ce sont les trois choses qu'on lit ici pour les dire au régulateur : qui, où,
 * avec quel droit. Toute la carte ouvre le profil : c'est la plus grande cible
 * de la page, et la plus haute.
 *
 * La carte se distingue par le **relief**, non par la couleur. Un cran de
 * surface au-dessus de la page et l'ombre haute suffisent à la désigner, et ce
 * qu'on gagne en la dépeignant est considérable : le nom revient à l'encre
 * ordinaire — donc au contraste maximal des deux ambiances —, les puces
 * reprennent les couleurs du thème au lieu d'une teinte calculée sur l'encre de
 * marque, et le portrait retrouve son aplat par défaut, qui est justement le
 * seul endroit de la carte où l'accent doit se voir.
 *
 * Les puces **restent cliquables**. Les rendre décoratives les sortirait du
 * parcours TalkBack, où elles sont aujourd'hui trois arrêts distincts : le
 * matricule, le dépôt et l'habilitation s'énoncent séparément, et c'est ce
 * qu'on dicte au régulateur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityCard(
    email: String?,
    profile: DriverProfile?,
    depotLabel: String?,
    modes: AccountModes?,
    avatarBytes: ByteArray?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val fallback = stringResource(R.string.menu_local_session)
    val name = profile?.displayName()
        ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: fallback
    val facts = buildList {
        profile?.driverNumber?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(stringResource(R.string.menu_matricule, it))
        }
        depotLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        modes?.let { add(stringResource(it.labelRes())) }
    }
    val openLabel = stringResource(R.string.menu_open_profile)
    val shape = MaterialTheme.shapes.large
    // La même instance des deux côtés : l'enfoncement lit les appuis que le
    // `Surface` cliquable émet. Deux sources, et la carte ne bougerait jamais.
    val interactions = remember { MutableInteractionSource() }
    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AuleSpacing.sm)
            .aulePress(interactions)
            .auleShadow(AuleElevation.FLOATING, shape)
            .semantics { contentDescription = "$name. $openLabel" },
        // Le cran au-dessus des destinations : la surface qui porte l'action de
        // l'écran a le droit d'être la forme la plus ouverte de la page.
        shape = shape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        interactionSource = interactions,
    ) {
        Column(modifier = Modifier.padding(AuleSpacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                AvatarPortrait(name = name, bytes = avatarBytes)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (email != null) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            // L'adresse est une preuve, pas une information de
                            // conduite : elle recule d'un rôle d'encre sans
                            // jamais passer sous le seuil de lisibilité.
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = AuleGlyph.CHEVRON.asImageVector(),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            }
            if (facts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = AuleSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                ) {
                    facts.forEach { fact ->
                        AssistChip(onClick = onOpen, label = { Text(fact) })
                    }
                }
            }
        }
    }
}

/**
 * Comment se dit l'habilitation, sur une puce.
 *
 * Les deux rôles simples reprennent les libellés de l'inscription — c'est la
 * même notion, et deux traductions du même mot finiraient par diverger. Le
 * compte mixte, lui, n'existe pas à l'inscription : on ne coche pas « mixte »,
 * on cumule deux habilitations, et le menu est le premier endroit qui ait à
 * nommer le résultat.
 */
private fun AccountModes.labelRes(): Int = when (this) {
    AccountModes.MIXTE -> R.string.menu_habilitation_mixte
    AccountModes.CONDUCTEUR -> R.string.register_profile_conducteur
    AccountModes.CONTROLE -> R.string.register_profile_controleur
}

/**
 * Une destination : une tuile, un titre, ce qu'on y trouve, un chevron.
 *
 * Chaque destination a sa carte. Le bloc unique d'avant avait sa raison — une
 * rangée seule sur la page n'a pas de bord —, mais il rangeait deux écrans de
 * plein droit dans la silhouette d'une liste de réglages. Séparées, les
 * destinations retrouvent la taille de ce qu'elles ouvrent, et l'aplat de
 * sélection garde le contour qui manquait à la rangée nue.
 *
 * La description n'est pas du remplissage : c'est ce qui évite d'ouvrir pour
 * savoir. « Le Guet » ne dit rien à qui ne l'a jamais ouvert.
 *
 * L'élévation reste au cran bas. Une seule chose flotte sur cet écran, et c'est
 * l'identité ; deux hauteurs de flottement, et la hiérarchie s'aplatit. Le cran
 * de forme est celui des cartes, pas celui des volets : au cran suivant, les
 * deux arrondis d'une rangée de cette hauteur se rejoignent et le conteneur
 * devient une gélule, c'est-à-dire un bouton.
 */
@Composable
private fun DestinationCard(
    title: String,
    description: String,
    glyph: AuleGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val interactions = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aulePress(interactions)
            .auleShadow(AuleElevation.RESTING, shape),
        shape = shape,
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
        interactionSource = interactions,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            },
            supportingContent = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            },
            leadingContent = { DestinationGlyphTile(glyph) },
            trailingContent = {
                // Le chevron n'est pas une décoration — c'est ce qui distingue
                // « ceci ouvre un écran » de « ceci fait quelque chose ici ».
                Icon(
                    imageVector = AuleGlyph.CHEVRON.asImageVector(),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            },
            // Transparent : la couleur vient de la carte qui porte la rangée.
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/**
 * Le glyphe d'une destination, dans sa tuile.
 *
 * Une icône posée nue dans la marge d'une rangée pèse le poids d'un caractère.
 * La tuile lui donne une masse — assez pour ancrer la ligne du titre et celle
 * de la description —, et c'est l'endroit de l'écran où l'accent revient :
 * l'aplat de teinte primaire, en petit, deux fois, plutôt qu'un dégradé qui
 * couvre le tiers de la page.
 */
@Composable
private fun DestinationGlyphTile(glyph: AuleGlyph) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(IconTileSize)
            .clip(MaterialTheme.shapes.small)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph.asImageVector(),
            contentDescription = null,
            tint = colors.onPrimaryContainer,
        )
    }
}

/**
 * La fin de session.
 *
 * Elle descend d'un cran, et c'est le seul changement qui touche à la sécurité
 * de l'écran. La plaque d'erreur pleine largeur avait la silhouette d'un bouton
 * principal en bas de page : la chose la plus voyante du menu était celle qu'on
 * y fait le moins, et qu'on ne veut surtout pas déclencher par erreur. En texte,
 * elle reste parfaitement identifiable — l'encre d'erreur et le glyphe suffisent
 * à ce qu'on ne la confonde avec rien — sans réclamer l'œil.
 *
 * Ce qu'elle perd en masse, elle le regagne en géométrie : le pied ne défile
 * pas, donc la distance qui la sépare des destinations ne dépend plus de la
 * hauteur de l'écran ni de la taille de police. Et la confirmation qui suit n'a
 * pas bougé.
 *
 * Le libellé garde le style du bouton. Repris au slot des titres, il redeviendrait
 * une rangée, c'est-à-dire l'objet dont on vient de le sortir.
 */
@Composable
private fun SignOutButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        enabled = enabled,
        // L'état éteint vient du bouton lui-même : couleurs atténuées et
        // `disabled()` sémantique dans le même geste. La rangée d'avant devait
        // peindre les deux à la main, et TalkBack annonçait une commande
        // disponible pendant tout l'envoi.
        colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        modifier = modifier.heightIn(min = AuleTouch.minimum),
    ) {
        Icon(
            imageVector = AuleGlyph.SIGN_OUT.asImageVector(),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = stringResource(R.string.menu_sign_out))
    }
}

/**
 * La confirmation.
 *
 * L'action destructrice est en tête et la sortie juste dessous : c'est
 * l'ordre de la boîte système Android, et l'inverser ferait rater la sortie à
 * qui l'ouvre par erreur.
 *
 * Le glyphe en tête n'est pas de l'ornement : une boîte de dialogue qui
 * s'ouvre pendant qu'on regarde ailleurs se lit d'abord par sa silhouette, et
 * celle d'un dialogue à icône dit « attention » avant que le titre ne soit lu.
 */
@Composable
private fun SignOutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AuleGlyph.SIGN_OUT.asImageVector(filled = true),
                contentDescription = null,
            )
        },
        iconContentColor = colors.error,
        title = {
            Text(
                text = stringResource(R.string.menu_sign_out_confirm_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = { Text(stringResource(R.string.menu_sign_out_confirm_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.menu_sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_cancel))
            }
        },
    )
}

/**
 * La gouttière du menu, un cran plus large que celle des autres écrans.
 *
 * Le hub n'a que quatre blocs et beaucoup de blanc entre eux : quelques points
 * de marge de plus en font un volet qui respire, là où le même écart sur un
 * écran dense volerait de la place au contenu. C'est une seule ligne à changer
 * si l'écart avec les autres volets de la carte finit par se voir.
 */
private val MenuGutter = 20.dp

/**
 * La tuile d'un glyphe de destination.
 *
 * Assez grande pour ancrer une rangée de deux lignes, assez petite pour ne pas
 * concurrencer le portrait de la carte d'identité juste au-dessus.
 */
private val IconTileSize = 40.dp
