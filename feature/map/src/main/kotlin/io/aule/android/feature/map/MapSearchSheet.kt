package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.location.LocationFix
import io.aule.android.core.model.MIN_PLACE_QUERY_LENGTH
import io.aule.android.core.model.NearbyDigest
import io.aule.android.core.model.NearbyDigestBuilder
import io.aule.android.core.model.Place
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.SavedPlaceSlot
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopSearchHit
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.contextLabel
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.shortLabel
import io.aule.android.core.model.walkMinutesOver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * La recherche de destination — **le socle de la carte**.
 *
 * ## Ce que c'est, et ce que ce n'était pas
 *
 * C'était une barre flottante : un `SearchBar` de Material posé sur la carte,
 * qui basculait en plein écran à la première touche. Deux états sans rien entre
 * les deux — une bande de 48 points, puis tout l'écran —, et le passage de l'un
 * à l'autre effaçait la ville d'un coup.
 *
 * C'est désormais **le volet du dessous**, porté par le même
 * `BottomSheetScaffold` que les fiches d'arrêt : rien ne l'ouvre et rien ne le
 * ferme, il réapparaît dès qu'aucun autre volet n'est présenté. C'est le port
 * de `Native/Aule/Features/Search/SearchSheet.swift`, et l'argument y est le
 * même : la carte reste manipulable derrière lui, il descend au socle quand on
 * ne s'en sert pas, et il monte à la demande sans jamais faire disparaître ce
 * qu'on regardait.
 *
 * ## Au repos, il ne prend pas toute la largeur
 *
 * C'est une **carte flottante**, écartée des trois bords, comme le socle d'iOS
 * au petit palier : le champ, l'avatar du compte, et la ville qui continue
 * autour. Une bande pleine largeur collée au bord aurait fermé l'écran d'un
 * trait à l'endroit le plus large.
 *
 * Techniquement, c'est le volet qui s'efface : `MapScreen` lui retire sa
 * surface et son ombre tant que la recherche est fermée, et c'est cette carte
 * qui les porte. Déployé, le volet redevient un volet — pleine largeur, coins
 * hauts arrondis, poignée — parce qu'une liste de résultats se lit sur une
 * surface, pas sur une carte posée sur la ville.
 *
 * ## Il ne s'ouvre qu'au doigt posé sur le champ
 *
 * Le glissement est coupé tant qu'il est fermé (`sheetSwipeEnabled`). Une carte
 * flottante ne s'annonce pas comme un volet : lui laisser le geste de montée
 * aurait promis un palier que rien n'indique, et l'aurait ouverte au premier
 * défilement de carte mal visé.
 *
 * ## Ce qu'il montre suit son palier, et non l'état de la recherche
 *
 * Au socle : la question, et l'avatar du compte. Les destinations récentes et
 * les arrêts d'à côté n'apparaissent qu'une fois le volet monté — ils
 * qualifient une question qu'on n'a pas encore posée, et les tenir à l'écran en
 * permanence reviendrait à garder une bande de carte masquée pour rien.
 *
 * C'est [expanded] qui commande, et il ne se lit pas dans la vue : le palier du
 * volet et l'état de la recherche sont tenus d'accord par `MapScreen`, dans les
 * deux sens. Repoussé au socle d'un geste, le volet ne garde pas un contenu
 * trois fois trop grand pour lui, et **le clavier suit le palier** — un champ
 * qu'on ne voit plus ne doit pas garder la saisie. La frappe, elle, reste :
 * repousser n'est pas annuler, voir `MapViewModel.collapseSearch`.
 *
 * ⚠️ **Le clavier ne s'ouvre pas tout seul.** Il vient avec le doigt posé sur
 * le champ — c'est le même geste — ou quand [focusRequested] le demande, quand
 * l'ouverture vient d'ailleurs : du menu d'actions, par exemple, où « Trouver
 * un itinéraire » promet une saisie.
 *
 * ## Ce que le volet porte, et qu'une barre ne portait pas
 *
 * Le catalogue, la position et le dépôt d'arrêts descendent jusqu'ici. Ce n'est
 * pas de la plomberie de confort : ce sont les trois sources de tout ce que
 * [SearchResults] montre en plus d'un nom — les arrêts proches à champ vide,
 * les distances, les lignes desservies. Sans elles, la recherche ne peut
 * répondre qu'avec ce que l'usager vient de taper.
 */
@Composable
internal fun MapSearchSheet(
    search: MapSearchState,
    catalog: List<TransitStop>,
    positions: StateFlow<LocationFix?>,
    repository: StopRepository,
    dispatchers: AuleDispatchers,
    expanded: Boolean,
    focusRequested: Boolean,
    onQueryChange: (String) -> Unit,
    onFieldFocused: () -> Unit,
    onFocusConsumed: () -> Unit,
    onSocleHeightPx: (Float) -> Unit,
    onSelectStop: (StopSearchHit) -> Unit,
    onSelectPlace: (Place) -> Unit,
    onSelectNearbyStop: (TransitStop) -> Unit,
    /**
     * Les adresses favorites, et les trois gestes qu'elles portent : partir,
     * remplir un emplacement encore vide, ouvrir la gestion.
     *
     * Vides, la rangée disparaît **sauf** Domicile et Travail : c'est là qu'on
     * découvre qu'on peut les renseigner.
     */
    savedPlaces: List<SavedPlace> = emptyList(),
    onSelectSaved: (SavedPlace) -> Unit = {},
    onFillSaved: (SavedPlaceSlot) -> Unit = {},
    onManageSaved: () -> Unit = {},
    /**
     * L'avatar du compte, **fourni entier** par `:app`.
     *
     * La carte ne connaît ni la photo ni le nom de qui est connecté — c'est le
     * métier de `:feature:auth`, et le graphe de dépendances interdit de l'y
     * atteindre. Elle reçoit donc un bouton déjà câblé, son libellé compris, et
     * ne fait que lui donner sa place. Voir `AccountAvatarButton`.
     */
    accountAvatar: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.search_hint)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val field = remember { FocusRequester() }

    // Le palier commande le clavier. Redescendre au socle rend la saisie à la
    // carte : sans cela, le volet retombait sous un clavier resté ouvert sur un
    // champ qu'on ne voyait plus.
    LaunchedEffect(expanded) {
        if (!expanded) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(focusRequested, expanded) {
        if (focusRequested && expanded) {
            field.requestFocus()
            keyboard?.show()
            onFocusConsumed()
        }
    }

    // ⚠️ **Un seul emplacement pour le champ, dans les deux états.**
    //
    // La carte flottante et le volet déployé sont deux décors ; ce qu'ils
    // décorent est la même rangée. L'écrire à deux endroits — un `if` qui
    // choisit entre deux arbres — la fait démonter et remonter à chaque
    // bascule : le champ perdait sa mise au point au moment même où le doigt
    // venait de la lui donner, et la frappe partait dans le vide. Ce sont donc
    // les **modificateurs** qui changent ici, jamais la structure.
    val shape = if (expanded) RectangleShape else MaterialTheme.shapes.extraLarge
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expanded) {
                        Modifier
                    } else {
                        // La bande mesurée **est** le palier du socle :
                        // `MapScreen` la reprend telle quelle en hauteur de pic.
                        // Une valeur écrite à la main aurait divergé au premier
                        // réglage de texte agrandi, où le champ grandit et le
                        // palier, lui, serait resté. Elle n'est relevée qu'au
                        // repos : déployée, la recherche occupe l'écran, et
                        // prendre cette hauteur-là pour palier rouvrirait le
                        // volet en grand à chaque fermeture.
                        Modifier
                            .onSizeChanged { onSocleHeightPx(it.height.toFloat()) }
                            .padding(horizontal = AuleSpacing.lg)
                            .padding(bottom = AuleSpacing.sm)
                    },
                ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expanded) {
                            Modifier
                        } else {
                            Modifier
                                .auleShadow(AuleElevation.FLOATING, shape)
                                // **Toute la carte donne la mise au point au
                                // champ.** Ce qui l'entoure est une marge, et
                                // une marge qu'on touche sans rien obtenir est
                                // une marge qui a l'air cassée. La carte
                                // entière répond.
                                //
                                // Par `pointerInput` et non `clickable` : ce
                                // n'est pas un bouton. Un `clickable` poserait
                                // une action de clic sur le nœud qui contient
                                // déjà le champ, et TalkBack annoncerait deux
                                // commandes là où il n'y en a qu'une. Le champ,
                                // lui, reçoit ses propres appuis d'abord — il
                                // les consomme, et ce geste-ci ne se déclenche
                                // que sur ce qu'il laisse passer.
                                .pointerInput(Unit) {
                                    detectTapGestures { field.requestFocus() }
                                }
                        },
                    ),
                shape = shape,
                // Déployé, le volet peint déjà sa surface : une seconde
                // par-dessus n'ajouterait qu'un aplat sur un aplat, et son coin
                // arrondi viendrait doubler celui du volet.
                color = if (expanded) Color.Transparent else MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (expanded) {
                                Modifier.padding(
                                    horizontal = AuleSpacing.lg,
                                    vertical = AuleSpacing.sm,
                                )
                            } else {
                                // La gouttière de la carte. Elle a suivi la
                                // hauteur du champ quand celui-ci est redescendu
                                // au plancher tactile : c'est la proportion
                                // qu'on garde, pas la mesure. Ce qu'elle empêche
                                // n'a pas changé — le champ et l'avatar
                                // touchaient le bord, et la carte se lisait
                                // comme un aplat coupé plutôt que comme une
                                // surface posée.
                                Modifier.padding(AuleSpacing.sm)
                            },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SheetSearchField(
                        query = search.query,
                        onQuery = onQueryChange,
                        placeholder = hint,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(field)
                            // Le doigt posé sur le champ ouvre le volet — c'est
                            // le seul chemin, et il ouvre le clavier du même
                            // geste : on ne tape pas dans un champ qu'on n'a
                            // pas visé.
                            .onFocusChanged { if (it.isFocused) onFieldFocused() },
                    )
                    // L'avatar cède la place au champ dès que la recherche
                    // s'ouvre : deux cibles dans le même coin finissent par se
                    // toucher l'une pour l'autre, et c'est le champ qui gagne
                    // quand on cherche.
                    if (!expanded && accountAvatar != null) {
                        accountAvatar()
                    }
                }
            }
        }
        if (expanded) {
            SearchResults(
                search = search,
                catalog = catalog,
                positions = positions,
                repository = repository,
                dispatchers = dispatchers,
                onSelectStop = onSelectStop,
                onSelectPlace = onSelectPlace,
                onSelectNearbyStop = onSelectNearbyStop,
                savedPlaces = savedPlaces,
                onSelectSaved = onSelectSaved,
                onFillSaved = onFillSaved,
                onManageSaved = onManageSaved,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Ce que la recherche montre sous le champ.
 *
 * ## Ce qu'elle montrait, et pourquoi ça ne suffisait pas
 *
 * Deux intitulés et des `ListItem` de texte nu : un nom, une phrase grise, et
 * l'écran entier en blanc dessous. Trois défauts, et le dernier est le pire :
 *
 * - **elle ne disait rien du réseau.** « Ranzay · Station de tram · 5 quais »
 *   ne dit pas si le tram qui y passe est la 1 ou la 2. C'est pourtant la seule
 *   question qu'on se pose devant deux arrêts qui portent le même nom ;
 * - **elle ne disait rien de la distance.** Cinq résultats de même rang, et
 *   rien pour distinguer celui qui est en bas de la rue de celui qui est à
 *   l'autre bout de la ligne ;
 * - **elle ne montrait rien tant qu'on n'avait pas tapé.** Le champ s'ouvre en
 *   plein écran, prend le clavier, et rend une page blanche — alors que
 *   l'application connaît déjà les arrêts qui sont à deux cents mètres.
 *
 * ## Ce qu'elle montre maintenant, et d'où ça vient
 *
 * Champ vide, elle répond quand même : les arrêts les plus proches, calculés
 * sur le catalogue déjà en mémoire et la position connue — sans un octet de
 * réseau. Une recherche qui s'ouvre sur une réponse est une recherche qu'on
 * n'a parfois pas besoin de faire.
 *
 * Champ saisi, chaque arrêt porte ce qui permet de le reconnaître : sa
 * pastille de mode dans la teinte du marqueur de carte, sa distance, le temps
 * de marche, et les **lignes qui le desservent** en badges — les vraies
 * couleurs GTFS, celles qu'on lit sur le poteau. Ces lignes coûtent un
 * aller-retour : elles ne sont demandées que pour les trois premiers résultats
 * (c'est [NEARBY_DETAIL_LIMIT], la même règle qu'« autour de vous »), et
 * seulement quand la frappe a reposé [SEARCH_DETAIL_DEBOUNCE_MS].
 *
 * Le premier arrêt prend la surface de marque, comme l'arrêt le plus proche du
 * volet d'à côté : c'est la même promesse — « celui-ci, sauf avis contraire ».
 * Une seule par écran, et jamais sur une adresse : le géocodeur propose, il ne
 * recommande pas.
 *
 * ## Les adresses ne répètent plus leur nom
 *
 * Le géocodeur rend « Ranzay, 44000 Nantes », dont le titre garde « Ranzay ».
 * Poser le libellé entier dessous écrivait « Ranzay » deux fois sur deux
 * lignes ; seul le complément reste — [contextLabel] — c'est-à-dire la seule
 * moitié qui apprend quelque chose.
 */
@Composable
internal fun SearchResults(
    search: MapSearchState,
    catalog: List<TransitStop>,
    positions: StateFlow<LocationFix?>,
    repository: StopRepository,
    dispatchers: AuleDispatchers,
    onSelectStop: (StopSearchHit) -> Unit,
    onSelectPlace: (Place) -> Unit,
    onSelectNearbyStop: (TransitStop) -> Unit,
    savedPlaces: List<SavedPlace> = emptyList(),
    onSelectSaved: (SavedPlace) -> Unit = {},
    onFillSaved: (SavedPlaceSlot) -> Unit = {},
    onManageSaved: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val query = search.query.trim()

    // L'abonnement au GPS vit ici, au plus près de ce qui s'en sert : cette
    // liste n'existe que lorsque la recherche est déployée, donc le reste du
    // temps personne ne recompose sur un point de position.
    val fix by positions.collectAsStateWithLifecycle()
    val around = fix?.coordinate

    val model = remember(repository, dispatchers) { NearbyStopsModel(repository, dispatchers) }
    DisposableEffect(model) { onDispose { model.close() } }

    // Les arrêts proches ne sont calculés qu'à champ vide : pendant la frappe,
    // ils ne s'affichent pas, et trier 2 600 arrêts à chaque lettre pour ne rien
    // en montrer serait le meilleur moyen de faire ramer la saisie.
    val suggestions = remember(catalog, around, query.isEmpty()) {
        if (query.isNotEmpty() || around == null) {
            emptyList()
        } else {
            NearbyDigestBuilder.build(
                stops = catalog,
                vehicles = emptyList(),
                around = around,
                limit = SEARCH_NEARBY_LIMIT,
            ).stops
        }
    }

    // Le débrayage vaut pour les deux listes : `LaunchedEffect` annule le délai
    // en cours dès que la tête de liste bouge, donc taper « ranzay » lettre à
    // lettre ne demande les lignes qu'une fois, sur le résultat final.
    val watched = if (query.isEmpty()) {
        suggestions.map { it.stop.departuresKey }
    } else {
        search.stops.map { it.label }
    }
    LaunchedEffect(watched) {
        delay(SEARCH_DETAIL_DEBOUNCE_MS)
        model.watch(watched)
    }

    val select: (StopSearchHit) -> Unit = { hit ->
        keyboard?.hide()
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onSelectStop(hit)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        if (query.isEmpty()) {
            // Les favoris **avant tout le reste**, et l'écart est net : un
            // raccourci est une destination qu'on a choisie une fois pour
            // toutes, là où l'historique est une trace et les arrêts proches une
            // coïncidence. C'est aussi le seul contenu du volet qui réponde sans
            // position, sans réseau et sans frappe.
            SearchSection(title = stringResource(R.string.saved_places_section)) {
                SavedPlacesStrip(
                    places = savedPlaces,
                    onSelect = { place ->
                        keyboard?.hide()
                        onSelectSaved(place)
                    },
                    onFill = { slot ->
                        keyboard?.hide()
                        onFillSaved(slot)
                    },
                    onManage = {
                        keyboard?.hide()
                        onManageSaved()
                    },
                )
            }
            // L'historique avant les arrêts proches : ce qu'on a déjà demandé
            // est une intention, un arrêt à trente mètres n'est qu'une
            // proximité. L'intention passe devant.
            //
            // La condition vient de l'état et non de la vue : c'est une règle
            // du domaine — « l'historique remplace les résultats tant qu'aucune
            // lettre n'est tapée » —, elle est écrite dans [MapSearchState] et
            // c'est là qu'elle est vérifiée.
            if (search.showsHistory) {
                SearchSection(title = stringResource(R.string.search_section_history)) {
                    search.history.forEachIndexed { index, place ->
                        SearchPlaceCard(
                            place = place,
                            distanceMeters = around?.let {
                                GeoMath.distance(it, place.coordinate)
                            },
                            rank = index,
                            onSelect = {
                                keyboard?.hide()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSelectPlace(place)
                            },
                        )
                    }
                }
            }
            SearchIdle(
                suggestions = suggestions,
                details = model.details,
                // L'état vide explique ce que la recherche couvre. Il n'a plus
                // rien à expliquer quand l'historique remplit déjà l'écran, et
                // « commencez à taper » sous huit destinations se lirait comme
                // un reproche.
                showsEmptyState = search.history.isEmpty(),
                onSelect = { stop ->
                    keyboard?.hide()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelectNearbyStop(stop)
                },
            )
            return@Column
        }

        if (search.isEmpty) {
            SearchNothing(query = query)
            return@Column
        }

        if (search.stops.isNotEmpty()) {
            SearchSection(title = stringResource(R.string.search_section_stops)) {
                search.stops.forEachIndexed { index, hit ->
                    SearchStopCard(
                        name = hit.label,
                        mode = hit.mode,
                        kind = hit.sublabel(),
                        distanceMeters = around?.let { GeoMath.distance(it, hit.coordinate) },
                        lines = model.details[hit.label].lines(),
                        accessible = hit.representative.isWheelchairAccessible,
                        // Le meilleur résultat ne se distingue que s'il y a de
                        // quoi le distinguer : seul, il est forcément premier.
                        highlighted = index == 0 && search.stops.size + search.places.size > 1,
                        rank = index,
                        onSelect = { select(hit) },
                    )
                }
            }
        }

        // Deux lettres rendent la moitié du département : le géocodeur n'est pas
        // appelé, et l'écran doit le dire. Sans cette ligne, une recherche courte
        // qui ne rend que des arrêts laisse croire que les adresses ont répondu
        // « rien ».
        if (query.length < MIN_PLACE_QUERY_LENGTH) {
            Text(
                text = stringResource(R.string.search_places_from, MIN_PLACE_QUERY_LENGTH),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AuleSpacing.lg),
            )
        }

        if (search.places.isNotEmpty() || search.isGeocoding) {
            SearchSection(title = stringResource(R.string.search_section_places)) {
                if (search.isGeocoding && search.places.isEmpty()) {
                    AuleLoadingState(
                        label = stringResource(R.string.search_geocoding),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                }
                search.places.forEachIndexed { index, place ->
                    SearchPlaceCard(
                        place = place,
                        distanceMeters = around?.let { GeoMath.distance(it, place.coordinate) },
                        rank = index,
                        onSelect = {
                            keyboard?.hide()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelectPlace(place)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Le champ est vide : on répond quand même.
 *
 * Sans position, il n'y a rien à proposer — on dit alors ce que la recherche
 * couvre, ce qui est plus utile qu'une page blanche et plus honnête qu'une
 * liste inventée.
 */
@Composable
private fun SearchIdle(
    suggestions: List<NearbyDigest.StopEntry>,
    details: Map<String, NearbyStopDetail>,
    onSelect: (TransitStop) -> Unit,
    showsEmptyState: Boolean = true,
) {
    if (suggestions.isEmpty()) {
        if (showsEmptyState) {
            AuleEmptyState(
                title = stringResource(R.string.search_idle_title),
                detail = stringResource(R.string.search_idle_detail),
                icon = AuleGlyph.SEARCH.asImageVector(),
                modifier = Modifier.padding(horizontal = AuleSpacing.lg),
            )
        }
        return
    }
    SearchSection(title = stringResource(R.string.search_section_nearby)) {
        suggestions.forEachIndexed { index, entry ->
            SearchStopCard(
                name = entry.stop.departuresKey,
                mode = entry.stop.mode,
                kind = entry.stop.mode.stationLabel(),
                distanceMeters = entry.distanceMeters,
                lines = details[entry.stop.departuresKey].lines(),
                accessible = entry.stop.isWheelchairAccessible,
                // Rien n'est mis en avant ici : la liste est déjà triée par
                // distance, et le premier l'est parce qu'il est le plus proche —
                // ce que la carte de tête dirait une deuxième fois.
                highlighted = false,
                rank = index,
                onSelect = { onSelect(entry.stop) },
            )
        }
    }
}

/**
 * Rien n'a répondu — et la raison n'est pas toujours la même.
 *
 * Sous [MIN_PLACE_QUERY_LENGTH] lettres, les adresses n'ont pas été
 * *cherchées* : annoncer « aucun résultat » ferait dire à l'application qu'il
 * n'y a rien là où elle n'a pas regardé.
 */
@Composable
private fun SearchNothing(query: String) {
    val short = query.length < MIN_PLACE_QUERY_LENGTH
    AuleEmptyState(
        title = stringResource(
            if (short) R.string.search_short_title else R.string.search_empty_title,
        ),
        detail = if (short) {
            stringResource(R.string.search_short_detail, query, MIN_PLACE_QUERY_LENGTH)
        } else {
            stringResource(R.string.search_empty_detail, query)
        },
        icon = AuleGlyph.SEARCH.asImageVector(),
        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
    )
}

/**
 * Un intitulé, et ce qu'il annonce.
 *
 * C'est [SheetSectionLabel] et non un `Text` de plus : les volets de la carte
 * et la recherche s'ouvrent au même endroit, à une frappe près, et un intitulé
 * qui change de graisse en passant de l'un à l'autre cesse d'être un repère.
 */
@Composable
private fun SearchSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetSectionLabel(
            text = title,
            // 16 dp, comme la gouttière d'une carte : à 12 dp, l'intitulé de
            // section était décalé de quatre points à gauche des résultats
            // qu'il annonce.
            modifier = Modifier.padding(horizontal = AuleSpacing.lg),
        )
        content()
    }
}

/**
 * Un arrêt trouvé, et de quoi le reconnaître sans l'ouvrir.
 *
 * Quatre faits, dans l'ordre où les questions se posent : **où** (le nom),
 * *à quelle distance*, **quoi** (le mode, les quais), **quelles lignes**. Les
 * lignes arrivent après les autres — elles viennent du réseau — et leur rangée
 * n'existe pas tant qu'elles ne sont pas là : une rangée vide qui se remplit
 * fait sauter la liste sous le doigt.
 *
 * @param kind ce qu'est ce lieu, déjà formulé : « Station de tram · 5 quais ».
 * @param highlighted le meilleur résultat, qui prend la surface de marque.
 */
@Composable
private fun SearchStopCard(
    name: String,
    mode: TransportMode,
    kind: String,
    distanceMeters: Double?,
    lines: List<ServingLine>,
    accessible: Boolean,
    highlighted: Boolean,
    rank: Int,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val distance = distanceMeters?.let { formatDistance(it) }
    val walk = distanceMeters
        ?.takeIf { it <= SEARCH_WALK_HORIZON_METERS }
        ?.let { stringResource(R.string.nearby_walk, walkMinutesOver(it)) }
    val hint = stringResource(R.string.search_stop_hint)
    val label = buildString {
        append(name)
        append(", ")
        append(kind)
        if (distance != null) {
            append(", ")
            append(stringResource(R.string.nearby_at_distance, distance))
        }
        if (walk != null) {
            append(", ")
            append(walk)
        }
        if (accessible) {
            append(", ")
            append(stringResource(R.string.nearby_wheelchair))
        }
        if (lines.isNotEmpty()) {
            append(", ")
            append(stringResource(R.string.search_lines, lines.joinToString(", ") { it.line }))
        }
    }

    // Sur le dégradé de marque, `onSurfaceVariant` écrirait en gris sombre sur
    // du teal sombre. C'est l'encre de l'accent, voilée, qui joue ce rôle-là —
    // le même rapport de lecture, transposé sur l'autre fond.
    val secondaryInk = if (highlighted) {
        AuleTheme.tokens.onAccent.color.copy(alpha = AuleAlpha.VEIL)
    } else {
        colors.onSurfaceVariant
    }

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = AuleSpacing.lg)
        .auleEnter(index = rank)
        .semantics(mergeDescendants = true) {
            contentDescription = label
            onClick(label = hint, action = null)
        }

    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(AuleSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            ModeAvatar(mode = mode, onBrand = highlighted)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        // Le nom d'arrêt est la réponse à « où » : c'est le seul
                        // mot de la carte qu'on lit avant tous les autres.
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (distance != null) {
                        Text(
                            text = distance,
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryInk,
                            modifier = Modifier.padding(start = AuleSpacing.sm),
                        )
                    }
                }
                Text(
                    // Le temps de marche voyage sur la ligne du mode : seul, il
                    // coûterait une rangée entière pour trois mots.
                    text = if (walk != null) "$kind · $walk" else kind,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lines.isNotEmpty()) {
                    ServingStrip(lines = lines)
                }
            }
        }
    }

    if (highlighted) {
        AuleBrandSurface(
            modifier = cardModifier,
            shape = MaterialTheme.shapes.medium,
            // `RESTING` et non `FLOATING` : la liste est posée à plat sur la
            // surface de la recherche, et une ombre haute ferait flotter une
            // carte au-dessus d'une page qui n'en est pas une.
            elevation = AuleElevation.RESTING,
            onClick = onSelect,
        ) {
            body()
        }
    } else {
        Card(
            onClick = onSelect,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
            ),
        ) {
            body()
        }
    }
}

/**
 * Une adresse trouvée.
 *
 * Elle porte l'épingle et non une icône de mode : le géocodeur rend des lieux,
 * pas des points du réseau — sauf quand il reconnaît un arrêt, et la pastille
 * prend alors la teinte de ce mode, comme le fait le volet du lieu.
 *
 * Pas de badge de ligne, pas de temps de marche : une adresse n'a ni desserte
 * ni quai, et lui en dessiner l'emplacement ferait attendre une information qui
 * n'existe pas.
 */
@Composable
private fun SearchPlaceCard(
    place: Place,
    distanceMeters: Double?,
    rank: Int,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val title = place.shortLabel()
    val context = place.contextLabel().ifEmpty { stringResource(R.string.search_place_generic) }
    val distance = distanceMeters?.let { formatDistance(it) }
    val hint = stringResource(R.string.search_place_hint)
    val label = buildString {
        append(place.label)
        if (distance != null) {
            append(", ")
            append(stringResource(R.string.nearby_at_distance, distance))
        }
    }

    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.lg)
            .auleEnter(index = rank)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                onClick(label = hint, action = null)
            },
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(AuleSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeAvatar(mode = place.stopMode)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = context,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (distance != null) {
                Text(
                    text = distance,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Les lignes à montrer pour un lieu, dans l'ordre de ce qu'on sait de lui.
 *
 * Les lignes desservies d'abord : elles ne dépendent pas de l'heure, et la nuit
 * elles sont tout ce qui reste. À défaut, celles des passages annoncés — un
 * arrêt qui annonce sans avoir répondu sur sa desserte reste un arrêt dont on
 * peut nommer les lignes.
 */
private fun NearbyStopDetail?.lines(): List<ServingLine> {
    if (this == null) return emptyList()
    val serving = servingLines.distinctBy { it.line }
    if (serving.isNotEmpty()) return serving.take(SEARCH_LINE_COUNT)
    return departures?.departures.orEmpty()
        .distinctBy { it.line }
        .take(SEARCH_LINE_COUNT)
        .map { departure ->
            ServingLine(
                line = departure.line,
                direction = departure.destination,
                lineColor = departure.lineColor,
                mode = departure.mode,
            )
        }
}

@Composable
internal fun StopSearchHit.sublabel(): String {
    val mode = mode.stationLabel()
    return if (quays > 1) stringResource(R.string.search_quays, mode, quays) else mode
}

@Composable
internal fun TransportMode.stationLabel(): String = when (this) {
    TransportMode.BUS -> stringResource(R.string.search_mode_bus)
    TransportMode.TRAM -> stringResource(R.string.search_mode_tram)
    TransportMode.BOAT -> stringResource(R.string.search_mode_boat)
}

/**
 * Combien d'arrêts proches à champ vide.
 *
 * Quatre tiennent au-dessus du clavier sur l'appareil de référence. Le
 * cinquième se devine sous le bord et invite à faire défiler une liste qu'on
 * n'a pas demandée.
 */
private const val SEARCH_NEARBY_LIMIT = 4

/** Au-delà, la rangée de badges devient une liste qu'il faut lire. */
private const val SEARCH_LINE_COUNT = 6

/**
 * Au-delà, on n'y va pas à pied.
 *
 * « Autour de vous » n'a pas ce problème : sa liste s'arrête d'elle-même à ce
 * qui est autour. La recherche, elle, trouve « Commerce » depuis n'importe où,
 * et « 143 min à pied » n'est pas une réponse — c'est un chiffre qui occupe la
 * ligne où la distance disait déjà tout.
 */
private const val SEARCH_WALK_HORIZON_METERS = 2_000.0

/**
 * Le temps que la frappe doit reposer avant qu'on demande les lignes.
 *
 * Un peu plus que le débrayage du géocodeur : les lignes précisent un résultat
 * déjà affiché, là où les adresses en sont un. Elles peuvent attendre le doigt
 * levé.
 */
private const val SEARCH_DETAIL_DEBOUNCE_MS = 450L
