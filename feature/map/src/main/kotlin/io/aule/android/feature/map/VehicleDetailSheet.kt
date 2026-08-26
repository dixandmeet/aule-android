package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleAnimatedValue
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleShape
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.delayInk
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleLoad
import io.aule.android.core.model.runFor
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Le panneau d'un véhicule : sa ligne, où il va, ce qu'il dessert ensuite.
 *
 * Court par construction. Quand on touche un bus sur la carte, on veut savoir
 * en trois secondes si c'est le bon — pas ouvrir un dossier.
 *
 * ## Ce qui vient après le nom
 *
 * Une fiche qui ne donne que la ligne et la destination laisse sans réponse
 * les deux questions qu'on se pose vraiment devant un véhicule qu'on voit
 * bouger : *est-ce que la position que je regarde est encore vraie*, et
 * *est-ce que je vais pouvoir monter dedans*. Le réseau publie de quoi y
 * répondre — l'heure du relevé, la charge, la vitesse, le temps restant à
 * quai — et rien de tout cela n'était affiché.
 *
 * Chaque fait ne s'affiche que si le réseau le publie : une fiche qui montre
 * « Affluence — inconnue » a l'air en panne, alors qu'une fiche qui n'en parle
 * pas a simplement l'air courte.
 *
 * ## Le seul volet où le temps réel est le sujet
 *
 * Les cinq autres volets décrivent des choses immobiles — un arrêt, un lieu, un
 * itinéraire. Celui-ci décrit un objet qu'on regarde **bouger**, et c'est la
 * seule différence qui compte : tout ce qu'il affiche a une date de péremption.
 * Trois décisions en découlent, et elles ne se recopient pas ailleurs.
 *
 * 1. L'origine de la position **vieillit sous les yeux** : elle est verte et
 *    appuyée tant que le relevé est frais, grise et ordinaire dès qu'il ne l'est
 *    plus, qu'il vienne d'un horaire ou d'un véhicule qu'on a perdu de vue. Sur
 *    les cinq autres volets, cette mention est un fait stable ; ici c'est la
 *    seule information de l'écran qui se périme toute seule.
 * 2. La pastille du prochain arrêt **change de silhouette** selon que l'arrivée
 *    est mesurée ou calculée. Le petit point qui pulse le disait déjà, mais il
 *    le disait en six points de large, par la couleur, et un conducteur lit cet
 *    écran en plein soleil.
 * 3. Le volet **se déroule** à l'ouverture — en-tête, faits, action — au lieu
 *    d'arriver d'un bloc. Trois rangées suffisent à ce que l'œil parte du nom
 *    plutôt que du bouton.
 *
 * ## Une seule surface de marque, et c'est le bouton
 *
 * « Suivre ce véhicule » est la seule chose qu'on fasse ici : le reste se lit.
 * L'aplat de marque va donc à l'action, comme sur l'écran de connexion, et non
 * à l'en-tête. Un en-tête en teal profond aurait été plus spectaculaire d'un
 * dixième de seconde, puis aurait laissé le bouton se débrouiller sous lui — or
 * c'est le bouton qu'on cherche du pouce, en tenant le téléphone d'une main.
 *
 * Quand le véhicule est déjà suivi, l'action redevient un bouton tonal : ce
 * qu'on désigne, c'est ce qui reste à faire, pas ce qui est fait. Le volet n'a
 * alors plus aucune surface de marque, et c'est le bon nombre.
 */
@Composable
internal fun VehicleDetailSheet(
    vehicle: TransportVehicle,
    lineColor: String?,
    isFollowing: Boolean,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
    trip: VehicleTripUiState = VehicleTripUiState(),
) {
    // L'horloge du volet, comme celle d'un arrêt. Sans elle, « il y a 8 s »
    // resterait « il y a 8 s » pendant que la position, elle, vieillit — et
    // c'est précisément l'âge qui dit si on peut encore s'y fier.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(vehicle.id) {
        while (isActive) {
            delay(VEHICLE_TICK_MS)
            now = Instant.now()
        }
    }

    // Le volet s'ouvre à mi-hauteur : ce qui déborde de ce cran demande un geste
    // de plus. Les trois blocs et le bouton doivent y tenir, donc on s'en tient
    // à l'écart courant de la grammaire, sans respirer davantage.
    SheetBody(modifier = modifier) {
        // Trois rangs, ou quatre pendant un suivi, décalés de 40 ms chacun. La
        // cascade ne coûte aucune couleur et aucune mesure, et c'est pourtant
        // elle qui fait la différence entre un volet qui s'ouvre et un volet
        // qui se téléporte. L'ordre des rangs est l'ordre de lecture : qui
        // c'est, ce qu'il fait, ce qu'il dessert, ce qu'on peut en faire.
        VehicleIdentity(
            vehicle = vehicle,
            lineColor = lineColor,
            now = now,
            modifier = Modifier.auleEnter(index = 0),
        )

        VehicleStatusCard(
            vehicle = vehicle,
            modifier = Modifier.auleEnter(index = 1),
        )

        // Le suivi, et lui seul, mérite le plan de ligne : c'est le moment où
        // l'on cesse de regarder « ce bus-là » pour regarder « la suite ».
        // Le plan est **recalculé à chaque position reçue** — la coupure entre
        // desservi et à desservir est la seule chose qui bouge — mais la
        // desserte, elle, n'a été demandée qu'une fois.
        if (isFollowing) {
            VehicleRunSection(
                vehicle = vehicle,
                trip = trip,
                now = now,
                modifier = Modifier.auleEnter(index = 2),
            )
        }

        FollowAction(
            isFollowing = isFollowing,
            onFollow = onFollow,
            modifier = Modifier.auleEnter(index = if (isFollowing) 3 else 2),
        )
    }
}

/**
 * Qui il est : la ligne, le mode, la destination, et d'où sort sa position.
 *
 * Trois rangs, du plus reconnaissable au plus nuancé : les deux badges, qu'on
 * repère à leur couleur avant de les lire ; la destination, qui est la réponse ;
 * l'origine de la position, qui dit ce que cette réponse vaut.
 */
@Composable
private fun VehicleIdentity(
    vehicle: TransportVehicle,
    lineColor: String?,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(
                line = vehicle.lineName,
                colorHex = lineColor,
                contentDescription = stringResource(R.string.line_badge, vehicle.lineName),
            )
            TransportBadge(
                mode = vehicle.mode,
                label = vehicle.mode.label(),
                tint = vehicle.mode.markerColor(AuleTheme.night).color,
            )
        }
        SheetTitle(vehicle.destination ?: stringResource(R.string.vehicle_unknown_destination))
        VehicleFeedLine(vehicle = vehicle, now = now)
    }
}

/**
 * D'où sort la position, et depuis quand.
 *
 * C'est la chose dont dépend tout le reste du volet : si la position vient d'un
 * véhicule ou d'un horaire, le prochain arrêt et l'heure d'arrivée ne valent pas
 * la même chose. La ligne se lit donc comme celle du cartouche de relève et
 * comme celle d'un passage d'arrêt — même point, même slot appuyé, même encre.
 * Trois écrans qui disent le même fait de trois façons apprendraient au
 * conducteur qu'il n'y a pas de règle.
 *
 * ## Pourquoi pas un aplat de couleur
 *
 * L'étiquette a porté un instant le conteneur du rôle sémantique — vert quand
 * la position est mesurée, ambre quand elle a vieilli. Le texte y était lisible,
 * mais rien d'autre : [RealtimeDot] pose sa propre encre, celle du temps réel,
 * qui tombe à 2:1 sur le conteneur vert — un point invisible dans sa propre
 * pastille — et reste vert sur le conteneur ambre, où il contredit ce que
 * l'aplat vient d'annoncer.
 *
 * L'aplat vert avait un second défaut, moins visible et plus tenace : c'est
 * exactement `secondaryContainer`, donc exactement le fond du bouton tonal
 * « Ne plus suivre ». Un véhicule déjà suivi affichait deux blocs verts
 * identiques disant deux choses sans rapport.
 *
 * ## Ce que le vieillissement change
 *
 * Une position mesurée il y a quatre minutes n'est plus un fait mesuré, c'est un
 * souvenir : le point redevient un anneau et l'encre repasse au gris, comme sur
 * le cartouche de relève. La bascule tombe là où `positionAgeText` cesse de
 * compter les secondes — la phrase et l'encre changent au même instant, sinon on
 * aurait du vert qui annonce des minutes, ce qui est un aveu poli qu'on ne sait
 * plus.
 *
 * Le point n'est pas annoncé : le texte qu'il précède porte déjà la nuance, et
 * le répéter n'apprend rien.
 */
@Composable
private fun VehicleFeedLine(vehicle: TransportVehicle, now: Instant) {
    val colors = MaterialTheme.colorScheme
    val feed = stringResource(
        if (vehicle.isLive) R.string.vehicle_live else R.string.vehicle_estimated,
    )
    val age = vehicle.positionAgeSeconds(now)?.takeIf { vehicle.isLive }
    // Mesurée **et** récente : c'est la seule combinaison qui autorise à parler
    // au présent. Un flux qui ne date pas ses points reste au présent lui aussi —
    // on n'a rien qui permette d'en douter.
    val fresh = vehicle.isLive && (age == null || age < STALE_POSITION_SECONDS)

    Row(
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RealtimeDot(
            isLive = fresh,
            liveDescription = stringResource(R.string.vehicle_live),
            scheduledDescription = stringResource(R.string.vehicle_estimated),
        )
        Text(
            text = if (age == null) feed else "$feed · ${positionAgeText(age)}",
            // Le libellé **nomme** l'origine, il ne la colorie pas : « Position
            // mesurée » et « Position estimée sur l'horaire » sont deux phrases
            // différentes, et une phrase se lit sans couleur. Le point voisin,
            // lui, n'a que la couleur et la forme pour parler — c'est à lui que
            // revient le vert, et à lui seul.
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * Le bloc de faits : ce qu'il dessert ensuite, quand, et dans quel état.
 *
 * Un seul cartouche plutôt que deux. Deux boîtes empilées se disputent le
 * regard ; celle-ci n'en réclame qu'un, et le volet garde un seul point
 * d'ancrage entre le nom et le bouton.
 */
@Composable
private fun VehicleStatusCard(vehicle: TransportVehicle, modifier: Modifier = Modifier) {
    val hasNextStop = vehicle.nextStop != null || vehicle.etaSeconds != null
    val hasState = vehicle.load != null || vehicle.isStopped || vehicle.speedKmh != null
    if (!hasNextStop && !hasState) return

    SheetCard(modifier = modifier.fillMaxWidth()) {
        if (hasNextStop) {
            NextStopRow(vehicle)
        }
        if (hasNextStop && hasState) {
            SheetRowDivider()
        }
        if (hasState) {
            VehicleStateRow(vehicle)
        }
    }
}

/**
 * Le prochain arrêt, et dans combien de temps.
 *
 * L'attente est écrite au rôle `DATA` : ses chiffres sont à chasse fixe, donc
 * « 10 min » ne fait pas danser la ligne en devenant « 9 min ». Le slot appuyé
 * conserve cette chasse — il ne change que la graisse et le tracking — donc le
 * chiffre gagne du poids sans que la colonne bouge d'un pixel. L'encre temps
 * réel ne s'allume que sur une position mesurée : sur un horaire, elle
 * promettrait une exactitude qui n'existe pas.
 *
 * Le nom d'arrêt monte lui aussi d'un cran de graisse. C'est la réponse à
 * « où », et elle était écrite du même poids que l'intitulé qui l'annonce.
 *
 * ## Le seul chiffre de l'écran qui se périme en le regardant
 *
 * L'attente **descend** : la valeur sortante monte en s'effaçant, la nouvelle
 * arrive par le bas. C'est le seul endroit du volet où une donnée change alors
 * qu'on la regarde — le nom d'arrêt tient plusieurs minutes, l'attente une seule
 * — et elle changeait sans rien dire. Le glissement porte le sens du compte à
 * rebours, ce qu'un fondu sur place n'aurait pas fait ; la chasse fixe du rôle
 * `DATA` garantit que la colonne ne bouge pas pendant qu'il joue.
 *
 * Lue d'un trait par TalkBack : quatre nœuds séparés feraient quatre arrêts de
 * curseur pour une seule phrase, « prochain arrêt Ranzay, arrivée maintenant ».
 */
@Composable
private fun NextStopRow(vehicle: TransportVehicle) {
    val colors = MaterialTheme.colorScheme
    val stopName = vehicle.nextStop
    val stopLabel = stringResource(R.string.vehicle_next_stop)
    val eta = vehicle.etaSeconds
    val arriving = eta != null && eta < 60
    val etaLabel = stringResource(
        if (arriving) R.string.vehicle_eta_arrival else R.string.vehicle_eta_in,
    )
    val etaValue = when {
        eta == null -> null
        arriving -> stringResource(R.string.vehicle_eta_arriving)
        else -> stringResource(R.string.vehicle_eta_minutes, (eta / 60).toInt())
    }
    val spoken = listOfNotNull(
        stopName?.let { "$stopLabel $it" },
        etaValue?.let { "$etaLabel $it" },
    ).joinToString(", ")

    AuleCappedFontScale {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StopGlyph(mode = vehicle.mode, isLive = vehicle.isLive)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stopLabel,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = stopName ?: stringResource(R.string.value_unknown),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (etaValue != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = etaLabel,
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = colors.onSurfaceVariant,
                    )
                    AuleAnimatedValue(
                        value = etaValue,
                        color = if (vehicle.isLive) realtimeInk() else colors.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Dans quel état il roule : le monde à bord, et s'il avance.
 *
 * Une rangée lue d'un seul tenant par TalkBack : « Affluence, bien rempli, à
 * l'arrêt » est une phrase ; trois nœuds séparés sont trois arrêts de curseur
 * pour la même information.
 *
 * Le verdict d'affluence est appuyé, la mention de mouvement ne l'est pas.
 * « Complet » décide si on monte ; « 34 km/h » ne décide de rien, c'est un
 * détail qu'on lit après, et il garde le rang qui va avec.
 */
@Composable
private fun VehicleStateRow(vehicle: TransportVehicle) {
    val colors = MaterialTheme.colorScheme
    val load = vehicle.load
    val loadText = load?.label()
    val loadName = stringResource(R.string.vehicle_load)
    // L'arrêt prime sur la vitesse : les deux se lisent sur le même relevé, et
    // « 2 km/h » n'apprend rien que « À l'arrêt » ne dise mieux.
    val motion = when {
        vehicle.isStopped -> stringResource(R.string.vehicle_at_stop)
        else -> vehicle.speedKmh?.let { stringResource(R.string.vehicle_speed, it) }
    }
    val spoken = listOfNotNull(loadText?.let { "$loadName, $it" }, motion).joinToString(", ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (load != null && loadText != null) {
            LoadMeter(load = load)
            Text(
                text = loadText,
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (motion != null) {
            if (load != null) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                text = motion,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Le plan de ligne, ou ce qui en tient lieu tant qu'on ne l'a pas.
 *
 * Trois états, et un seul mot pour les deux derniers : on charge, on a la
 * desserte, on ne l'aura pas. Une course absente du catalogue n'est pas une
 * panne — service spécial, ligne renforcée, dépôt pas encore publié — donc la
 * phrase reste basse et le volet garde tout le reste de ce qu'il savait.
 *
 * La coupure se recalcule ici, à chaque position et à chaque battement de
 * l'horloge du volet : `remember` la borne à ces deux entrées, sinon la
 * projection sur le tracé se referait à chaque recomposition, pour un résultat
 * qui ne change qu'au sondage suivant.
 */
@Composable
private fun VehicleRunSection(
    vehicle: TransportVehicle,
    trip: VehicleTripUiState,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val schedule = trip.trip
    if (schedule == null) {
        val message = when {
            trip.isLoading -> stringResource(R.string.vehicle_plan_loading)
            trip.isUnavailable -> stringResource(R.string.vehicle_plan_unavailable)
            else -> return
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val run = remember(schedule, vehicle.id, vehicle.coordinate, vehicle.nextStop, now) {
        schedule.runFor(vehicle, now)
    }
    VehicleLinePlan(run = run, mode = vehicle.mode, modifier = modifier)
}

/**
 * Suivre, ou cesser de suivre.
 *
 * ## Pourquoi l'aplat de marque et non un bouton coloré
 *
 * Le bouton portait déjà l'accent, en aplat uni. C'était correct et plat : un
 * rectangle teal au bas d'un volet gris, qu'on voit sans le chercher mais qui
 * ne pèse pas plus que la carte au-dessus de lui. La surface de marque lui
 * donne les trois choses qu'un aplat n'a pas — le dégradé en diagonale, qui lui
 * suppose une source de lumière ; le reflet sur son tiers haut ; l'ombre
 * teintée, une lueur d'accent plutôt qu'un cerne noir sous une couleur.
 *
 * Elle reste à `RESTING` : le volet porte déjà son ombre, et une surface qui
 * flotte haut à l'intérieur d'une surface qui flotte fait perdre de vue laquelle
 * supporte l'autre.
 *
 * ## Et pourquoi elle disparaît quand on suit déjà
 *
 * « Ne plus suivre » est un retour en arrière, pas une action à désigner. Le
 * bouton tonal de Material dit exactement cela : disponible, jamais réclamé.
 * Les deux états gardent la **même forme et la même hauteur**, sinon le bas du
 * volet sauterait à chaque appui — un mouvement qu'on n'a pas demandé, sur le
 * seul contrôle de l'écran.
 *
 * Le libellé prend le titre appuyé et non le libellé de bouton de Material :
 * à 14 points sur un contrôle de 52, l'étiquette flotte dans sa propre cible.
 *
 * Le rôle, lui, est écrit à la main, comme sur l'envoi de l'écran de connexion.
 * Il venait de `Button`, qui le pose sur sa surface ; `Surface` seule ne le pose
 * pas, et TalkBack annoncerait alors un texte là où il y a la seule commande de
 * l'écran — le contrôle qu'un conducteur cherche justement sans regarder.
 */
@Composable
private fun FollowAction(
    isFollowing: Boolean,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isFollowing) {
        FilledTonalButton(
            onClick = onFollow,
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = stringResource(R.string.vehicle_unfollow),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    } else {
        AuleBrandSurface(
            modifier = modifier
                .fillMaxWidth()
                .semantics { role = Role.Button },
            shape = MaterialTheme.shapes.large,
            elevation = AuleElevation.RESTING,
            onClick = onFollow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AuleControl.height)
                    .padding(horizontal = AuleSpacing.lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.vehicle_follow),
                    // Aucune couleur : la surface a posé la sienne. Un libellé
                    // qui irait chercher `onSurface` écrirait en sombre sur le
                    // teal.
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
        }
    }
}

/**
 * La jauge de remplissage : quatre barres qui montent.
 *
 * Un taux nu — « 62 % » — se lit avec la tête ; quatre barres se lisent avec
 * l'œil, et c'est tout ce qu'on demande à un chiffre qu'on regarde une seconde
 * avant de décider de monter. La couleur porte le même verdict que la
 * hauteur : elle passe à l'ambre puis au rouge quand il n'y a plus de place,
 * pour que la jauge reste lisible sans compter les barres.
 */
@Composable
private fun LoadMeter(load: VehicleLoad) {
    val filled = load.ordinal + 1
    val tint = load.ink()
    val idle = MaterialTheme.colorScheme.outlineVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(METER_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Autant de barres que de paliers : un palier ajouté au modèle se voit
        // dans la jauge sans qu'on y revienne.
        repeat(VehicleLoad.entries.size) { index ->
            Box(
                modifier = Modifier
                    .size(
                        width = METER_BAR_WIDTH,
                        height = METER_BAR_FLOOR + METER_BAR_STEP * index,
                    )
                    .background(
                        color = if (index < filled) tint else idle,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * L'encre d'un palier de remplissage.
 *
 * Rien de tout cela n'est un rôle Material : « il reste de la place » et « il
 * est complet » sont des faits transport, comme le temps réel et le retard, et
 * ils empruntent les mêmes encres.
 */
@Composable
private fun VehicleLoad.ink(): Color = when (this) {
    VehicleLoad.QUIET, VehicleLoad.STEADY -> realtimeInk()
    VehicleLoad.BUSY -> delayInk()
    VehicleLoad.FULL -> AuleTheme.tokens.alert.color
}

/**
 * La pastille de l'arrêt, dans la teinte du mode — et dans la silhouette du feu.
 *
 * La teinte est celle du marqueur sur la carte : c'est ce qui relie le véhicule
 * qu'on vient de toucher à la fiche qui s'ouvre.
 *
 * ## Deux silhouettes pour deux natures d'arrivée
 *
 * La pastille était un rond dans les deux cas : rien, à gauche de la rangée, ne
 * distinguait « il arrive dans 3 minutes, mesuré » de « il devrait arriver dans
 * 3 minutes, d'après l'horaire ». Elle prend donc [AuleShape.live] — le soleil,
 * la seule forme du kit qui ait une direction sans avoir de pointe — quand la
 * position est mesurée, et le biscuit à neuf lobes d'[AuleShape.modeAvatar]
 * quand elle est calculée. Les deux tiennent dans le même carré de 40 dp : la
 * rangée ne bouge pas quand le réseau se met enfin à remonter des positions.
 *
 * C'est une **redite discrète**, et il ne faut pas lui demander plus. Le lavis
 * est à 12 % : de loin, les deux silhouettes se lisent comme le même rond, et
 * c'est du reste ce que le kit dit de la pastille de mode. Ce qui porte
 * vraiment la distinction reste la ligne d'origine sous le titre et l'encre du
 * chiffre — la forme n'est là que pour ceux qui regardent la rangée, et elle ne
 * dispense pas les deux autres de la dire.
 *
 * C'est le seul emploi du soleil dans le volet. Deux, et il ne signifierait
 * déjà plus rien.
 */
@Composable
private fun StopGlyph(mode: TransportMode, isLive: Boolean) {
    val tint = mode.markerColor(AuleTheme.night).color
    Box(
        modifier = Modifier
            .size(GLYPH_SLOT)
            .background(
                color = tint.copy(alpha = AuleAlpha.TINT),
                shape = if (isLive) AuleShape.live() else AuleShape.modeAvatar(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AuleGlyph.PIN.asImageVector(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(GLYPH_SIZE),
        )
    }
}

/** Assez pour que « il y a 8 s » devienne « il y a 18 s » sans qu'on l'ait vu vieillir. */
private const val VEHICLE_TICK_MS = 10_000L

/**
 * Passé la minute, le relevé a cessé d'être frais.
 *
 * Le seuil n'est pas choisi ici : c'est celui où `positionAgeText` arrête de
 * compter les secondes. La phrase et la couleur basculent donc au même instant,
 * et il n'existe qu'un seul endroit où l'on décide de ce qu'« encore vrai »
 * veut dire.
 */
private const val STALE_POSITION_SECONDS = 60L

/** La pastille du prochain arrêt : un cran de plus que l'icône, pour qu'elle respire. */
private val GLYPH_SLOT = 40.dp
private val GLYPH_SIZE = 22.dp

/** La plus basse des barres de la jauge, et le cran qui les sépare. */
private val METER_BAR_FLOOR = 7.dp
private val METER_BAR_STEP = 4.dp
private val METER_BAR_WIDTH = 4.dp
private val METER_GAP = 3.dp
