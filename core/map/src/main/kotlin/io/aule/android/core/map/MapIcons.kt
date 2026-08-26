package io.aule.android.core.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleRgba
import io.aule.android.core.designsystem.token.AuleTokens
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.TransportMode
import org.maplibre.android.maps.Style

/**
 * Les icônes de la carte, dessinées à l'exécution.
 *
 * Le style n'a **aucun sprite** : tout ce qui se pose sur la carte est peint ici,
 * au `Canvas`, puis enregistré dans le style. C'est ce qui permet aux marqueurs
 * de suivre l'ambiance sans qu'on maintienne deux jeux d'images, et de rester
 * nets à n'importe quelle densité.
 *
 * Elles sont **ré-enregistrées à chaque changement d'ambiance** : un rechargement
 * de style vide aussi le registre d'images, et un `iconImage` qui pointe sur une
 * image absente ne dessine rien — sans erreur.
 */
internal object MapIcons {

    private const val DENSITY_SCALE = 3f

    /**
     * Le jeton d'un **lieu**, et celui d'un **quai** — un peu plus grand.
     *
     * Le pictogramme fixe le plancher : sous une vingtaine de points, la caisse
     * et ses roues se confondent en une tache. C'est la taille dessinée, pas
     * celle affichée — la couche la fait respirer avec le zoom, du demi-jeton
     * au jeton plein.
     */
    private const val PLACE_DIAMETER_DP = 22f
    private const val QUAY_DIAMETER_DP = 26f

    /** De quoi ne pas rogner le flou de l'ombre au bord du bitmap. */
    private const val PILL_SHADOW_DP = 3f

    /** L'épaisseur de l'anneau, en part du rayon — la proportion du jeton web. */
    private const val RING_RATIO = 0.21f

    /**
     * La boîte du véhicule. La silhouette n'en occupe que la moitié — le nez
     * monte à seize unités sur vingt-quatre — et ce qui reste sert à la
     * rotation : MapLibre fait pivoter l'image, pas la forme, et une pointe
     * calée au bord se ferait rogner dès que le cap quitte le nord.
     */
    private const val VEHICLE_SIZE_DP = 32f

    /**
     * Le halo du puck, à son ampleur maximale.
     *
     * ⚠️ **Il est contraint par l'anneau d'incertitude, pas par l'esthétique.**
     * Il valait soixante-dix points — plus de deux fois et demie le disque,
     * là où le web n'en met que deux. Trente-cinq points de rayon, c'est
     * l'anneau à **vingt mètres** de précision au zoom d'ouverture : autrement
     * dit, tout ce qu'un GPS de ville annonce d'ordinaire disparaissait sous
     * l'aura, et le seul moment où l'anneau se voyait était celui où plus rien
     * n'était fiable.
     *
     * Quarante-quatre le ramènent à vingt-deux de rayon, soit une douzaine de
     * mètres. L'aura reste une aura — elle déborde du disque de cinq points au
     * repos et de treize au sommet de sa respiration — mais elle laisse
     * désormais passer l'anneau bien avant que la position ne devienne
     * douteuse.
     */
    internal const val PUCK_HALO_DP = 44f

    /**
     * Le faisceau de direction, et son ouverture.
     *
     * ⚠️ **Il doit sortir du halo.** À vingt-huit points de rayon, il tenait
     * tout entier sous les trente-cinq qu'occupait alors le halo — teal sur
     * teal, à travers un dégradé qui monte à 0,55 : il n'y avait rien à voir,
     * et c'est pourquoi personne ne l'a jamais remarqué sur la carte.
     * Quarante-six l'en font sortir franchement, et le resserrement du halo à
     * vingt-deux lui a depuis rendu le double de marge — mais c'est le cône
     * qui doit tenir seul, le halo pouvant toujours regrossir.
     *
     * Il démarre à [PUCK_CONE_START] du rayon, soit au bord exact du disque du
     * puck : un faisceau qui partirait du centre serait masqué sur sa première
     * moitié, et un faisceau détaché du puck se lirait comme un objet de plus
     * sur la carte plutôt que comme une émanation de la position.
     */
    private const val PUCK_CONE_DP = 92f
    private const val PUCK_CONE_HALF_ANGLE = 24f
    private const val PUCK_CONE_START = 0.2f

    /**
     * L'ombre du jeton : assez pour le décoller de la ville, pas assez pour
     * qu'on la voie. Un marqueur qui projette une ombre franche flotte, et une
     * carte pleine de marqueurs qui flottent fatigue.
     */
    private const val SHADOW_COLOR = 0x38000000

    fun stopPlaceName(mode: TransportMode) = "stop-place-${mode.name.lowercase()}"
    fun stopQuayName(mode: TransportMode) = "stop-quay-${mode.name.lowercase()}"
    /**
     * Le véhicule, en deux images par mode : la position **mesurée** et la
     * position **calculée**. Voir [vehicleArrow] pour ce qui les sépare.
     */
    fun vehicleName(mode: TransportMode, live: Boolean) =
        "vehicle-${mode.name.lowercase()}" + if (live) "" else "-scheduled"
    const val STOP_SELECTED = "stop-selected"
    const val DESTINATION = "destination"
    const val VEHICLE_HEADING = "vehicle-heading"
    const val PUCK = "user-puck"
    const val PUCK_HALO = "user-puck-halo"
    const val PUCK_MOVING = "user-puck-moving"
    const val PUCK_HEADING = "user-puck-heading"
    const val HANDOVER_VEHICLE = "handover-vehicle"
    const val HANDOVER_VEHICLE_STALE = "handover-vehicle-stale"
    const val HANDOVER_STOP = "handover-stop"
    const val HANDOVER_STOP_ARRIVED = "handover-stop-arrived"

    /** Pose (ou repose) toutes les images dans le style. */
    fun register(style: Style, night: Boolean) {
        val tokens = AuleTokens.of(night)
        for (mode in TransportMode.entries) {
            style.addImage(
                stopPlaceName(mode),
                stopPill(mode, mode.markerColor(night), tokens, PLACE_DIAMETER_DP),
            )
            style.addImage(
                stopQuayName(mode),
                stopPill(mode, mode.markerColor(night), tokens, QUAY_DIAMETER_DP),
            )
            val vehicle = mode.markerColor(night)
            style.addImage(
                vehicleName(mode, live = true),
                vehicleArrow(mode, vehicle, tokens, live = true),
            )
            style.addImage(
                vehicleName(mode, live = false),
                vehicleArrow(mode, vehicle, tokens, live = false),
            )
        }
        style.addImage(STOP_SELECTED, selectionRing(tokens))
        style.addImage(DESTINATION, destinationPin(tokens))
        style.addImage(VEHICLE_HEADING, headingChevron(tokens))
        style.addImage(PUCK, puckDot())
        style.addImage(PUCK_HALO, puckHalo(tokens))
        style.addImage(PUCK_MOVING, puckArrow())
        style.addImage(PUCK_HEADING, puckCone(tokens))
        style.addImage(HANDOVER_VEHICLE, handoverVehicle(filled = true, tokens))
        style.addImage(HANDOVER_VEHICLE_STALE, handoverVehicle(filled = false, tokens))
        style.addImage(HANDOVER_STOP, handoverStop(arrived = false, tokens))
        style.addImage(HANDOVER_STOP_ARRIVED, handoverStop(arrived = true, tokens))
    }

    /**
     * Le véhicule : une silhouette **orientée**, pleine ou creuse.
     *
     * Elle remplace un disque que surmontait un chevron séparé. Deux défauts s'y
     * cumulaient, visibles à « Talensac » sur le S21 : le chevron, peint à
     * l'encre neutre, se perdait sur la chaussée claire — un véhicule sans cap
     * lisible, alors que le cap est la moitié de ce qu'on lui demande ; et le
     * disque théorique, posé à 0,55 d'opacité, laissait voir la rue au travers.
     * Le mobilier fixe se lisait mieux que la flotte, ce qui est l'inverse de
     * l'ordre des choses sur une carte de transport.
     *
     * La forme porte donc le cap — MapLibre la fait tourner, il n'y a plus rien
     * à poser à côté — et **le plein dit la mesure, le creux dit l'horaire**. Le
     * retrait du théorique quitte l'opacité pour la forme, à pleine opacité :
     * une flotte du soir presque entièrement théorique se lisait délavée, le
     * même constat qui avait déjà fait passer les volumes à une teinte mêlée
     * plutôt qu'à une transparence. C'est aussi le choix du web, au trait près.
     *
     * Le navibus est plus large et moins effilé que les autres : une coque, pas
     * une flèche.
     */
    private fun vehicleArrow(
        mode: TransportMode,
        fill: AuleRgba,
        tokens: AuleTokens,
        live: Boolean,
    ): Bitmap =
        bitmap(sizeDp = VEHICLE_SIZE_DP) { canvas, size ->
            val center = size / 2f
            // L'unité du canvas web, dont les coordonnées sont reprises telles
            // quelles pour que les deux silhouettes restent la même.
            val u = size / 48f
            val nose = if (mode == TransportMode.BOAT) 14f * u else 16f * u
            val tail = 12f * u
            val wing = if (mode == TransportMode.BOAT) 12f * u else 11f * u

            val hull = Path().apply {
                moveTo(center, center - nose)
                quadTo(center + wing, center + tail * 0.2f, center + wing * 0.7f, center + tail)
                quadTo(center, center + tail * 0.55f, center - wing * 0.7f, center + tail)
                quadTo(center - wing, center + tail * 0.2f, center, center - nose)
                close()
            }
            val halo = tokens.surfaceSolid.argb
            val shadow = { paint: Paint ->
                paint.setShadowLayer(2f * DENSITY_SCALE, 0f, 0.75f * DENSITY_SCALE, SHADOW_COLOR)
            }

            if (live) {
                // Le liseré **sous** le remplissage, comme la flèche du puck :
                // un contour centré sur ce tracé rognerait la pointe, qui est
                // précisément ce qui dit la direction.
                canvas.drawPath(
                    hull,
                    paint(halo).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 5f * u
                        strokeJoin = Paint.Join.ROUND
                        shadow(this)
                    },
                )
                canvas.drawPath(hull, paint(fill.argb))
            } else {
                canvas.drawPath(hull, paint(halo).apply { shadow(this) })
                canvas.drawPath(
                    hull,
                    paint(fill.argb).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 3f * u
                        strokeJoin = Paint.Join.ROUND
                    },
                )
                // Un point au cœur du creux : sans lui, la silhouette évidée se
                // lit comme un trou dans la carte plutôt que comme un véhicule.
                canvas.drawCircle(center, center + tail * 0.1f, 2.6f * u, paint(fill.argb))
            }
        }

    /**
     * Le chevron de cap.
     *
     * Dessiné pointe en haut : MapLibre le fera tourner de `heading` degrés, et
     * un cap de 0° veut dire « vers le nord ».
     */
    private fun headingChevron(tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 34f) { canvas, size ->
            val center = size / 2f
            val path = android.graphics.Path().apply {
                moveTo(center, 1f * DENSITY_SCALE)
                lineTo(center + 4.5f * DENSITY_SCALE, 8f * DENSITY_SCALE)
                lineTo(center, 6f * DENSITY_SCALE)
                lineTo(center - 4.5f * DENSITY_SCALE, 8f * DENSITY_SCALE)
                close()
            }
            canvas.drawPath(path, paint(tokens.onSurface.argb))
        }

    /**
     * Le véhicule du collègue relevé : teal Aule, plus gros que la flotte,
     * pour qu'on le trouve d'un coup d'œil. Ouvert quand le dernier point
     * n'est plus une mesure à l'instant.
     */
    private fun handoverVehicle(filled: Boolean, tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 28f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(center, center, center, paint(tokens.surfaceSolid.argb))
            canvas.drawCircle(center, center, center - 2.5f * DENSITY_SCALE, paint(AuleBrand.teal.argb))
            if (!filled) {
                canvas.drawCircle(
                    center,
                    center,
                    center * 0.38f,
                    paint(tokens.surfaceSolid.argb),
                )
            }
        }

    /**
     * Le point de relève : un anneau teal, distinct du véhicule. Plein
     * seulement une fois arrivé — ici on n'a pas encore le moteur d'ETA,
     * l'icône ouverte suffit tant que le collègue n'est pas au rendez-vous.
     */
    private fun handoverStop(arrived: Boolean, tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 32f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(center, center, center, paint(tokens.surfaceSolid.argb))
            canvas.drawCircle(center, center, center - 2.5f * DENSITY_SCALE, paint(AuleBrand.teal.argb))
            if (!arrived) {
                canvas.drawCircle(
                    center,
                    center,
                    center * 0.42f,
                    paint(tokens.surfaceSolid.argb),
                )
            }
        }

    /**
     * La pastille d'un arrêt : un jeton clair, cerclé de la couleur du mode, avec
     * au centre le pictogramme de ce qui s'y arrête.
     *
     * Le disque plein qu'elle remplace disait « il y a quelque chose ici », et
     * rien de plus. Trois pastilles côte à côte n'étaient alors départagées que
     * par leur teinte — or la teinte est justement ce qui se perd sur une carte
     * claire en plein soleil, et le bus (`#55665F`) comme le tram y tirent vers
     * le même gris-vert. Le jeton clair, lui, tient sur n'importe quel fond
     * parce que ce n'est plus la couleur qui le détache mais le contraste ; et
     * le pictogramme dit ce qu'on attend là sans qu'il faille connaître un code
     * couleur.
     *
     * C'est la forme de la carte web, reprise volontairement au trait près :
     * l'objet le plus fréquent des deux cartes gagne à y avoir la même
     * silhouette, et deux dessins de bus « équivalents » finissent toujours par
     * ne plus se ressembler.
     */
    private fun stopPill(
        mode: TransportMode,
        fill: AuleRgba,
        tokens: AuleTokens,
        diameterDp: Float,
    ): Bitmap =
        // La marge n'est pas décorative : l'ombre est peinte **dans** le bitmap,
        // et sans elle le flou serait coupé net au bord — un jeton posé sur un
        // carré gris.
        bitmap(sizeDp = diameterDp + 2f * PILL_SHADOW_DP) { canvas, size ->
            val center = size / 2f
            val radius = diameterDp / 2f * DENSITY_SCALE
            val ring = radius * RING_RATIO
            // L'unité du dessin web, rapportée au **disque intérieur** et non au
            // jeton entier.
            //
            // Le web cercle son jeton d'un trait centré sur le bord : son anneau
            // déborde, et le glyphe garde en dessous un disque de 13,25 pour 3,5
            // de trait. En posant l'anneau à l'intérieur — ce qu'il faut ici pour
            // que l'ombre suive le contour peint — ce disque se resserrait à
            // 11,6, et le glyphe, lui, ne rétrécissait pas : la perche du tram
            // traversait l'anneau par le haut, son rail le frôlait de part et
            // d'autre à un dixième d'unité près. Vérifié sur le S21 à « Commerce ».
            val u = (radius - ring) / 13.25f

            // L'ombre est portée par le jeton lui-même plutôt que par une passe
            // séparée : deux disques superposés laissent un liseré là où
            // l'antialiasing de l'un ne recouvre pas tout à fait l'autre.
            canvas.drawCircle(
                center,
                center,
                radius,
                paint(tokens.surfaceSolid.argb).apply {
                    setShadowLayer(
                        2.5f * DENSITY_SCALE,
                        0f,
                        0.75f * DENSITY_SCALE,
                        SHADOW_COLOR,
                    )
                },
            )
            canvas.drawCircle(
                center,
                center,
                radius - ring / 2f,
                paint(fill.argb).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ring
                },
            )
            modeGlyph(canvas, mode, center, u, ink = fill, hollow = tokens.surfaceSolid)
        }

    /**
     * Le pictogramme du mode, centré dans le jeton.
     *
     * [u] est l'unité du dessin web — un treizième et quart du disque intérieur.
     * Toutes les coordonnées sont celles de `drawStopIcon`, ce qui rend les deux
     * fichiers comparables ligne à ligne.
     *
     * Rien n'en sort : la perche du tram monte à 12 u et son rail descend à
     * 9,3 u, pour 13,25 u de disque.
     */
    private fun modeGlyph(
        canvas: Canvas,
        mode: TransportMode,
        center: Float,
        u: Float,
        ink: AuleRgba,
        hollow: AuleRgba,
    ) {
        val solid = paint(ink.argb)
        val line = paint(ink.argb).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * u
            strokeCap = Paint.Cap.ROUND
        }

        if (mode == TransportMode.BOAT) {
            // Le seul endroit où ce dessin **s'écarte** du web, et il le fait sur
            // pièce : à « Trentemoult Sablières », le navibus web — pont, cabine
            // et coque de largeurs voisines — se lisait comme trois barres
            // empilées, sur un fond d'eau qui plus est de la même famille de
            // bleus. On garde la coque et la cabine, on remplace le trait de pont
            // par une **ligne d'eau** : c'est le seul des trois modes qui flotte,
            // et l'onde le dit sans qu'aucune barre supplémentaire n'entre en
            // concurrence avec la coque.
            canvas.drawRoundRect(
                RectF(center - 3.6f * u, center - 7f * u, center + 3.6f * u, center - 1.5f * u),
                1f * u,
                1f * u,
                solid,
            )
            canvas.drawPath(
                Path().apply {
                    moveTo(center - 8f * u, center - 1.5f * u)
                    lineTo(center + 8f * u, center - 1.5f * u)
                    lineTo(center + 5f * u, center + 3.5f * u)
                    lineTo(center - 5f * u, center + 3.5f * u)
                    close()
                },
                solid,
            )
            val water = center + 6.5f * u
            canvas.drawPath(
                Path().apply {
                    moveTo(center - 7.5f * u, water)
                    quadTo(center - 5.6f * u, water - 1.6f * u, center - 3.75f * u, water)
                    quadTo(center - 1.9f * u, water + 1.6f * u, center, water)
                    quadTo(center + 1.9f * u, water - 1.6f * u, center + 3.75f * u, water)
                    quadTo(center + 5.6f * u, water + 1.6f * u, center + 7.5f * u, water)
                },
                line,
            )
            return
        }

        val tram = mode == TransportMode.TRAM
        val halfWidth = if (tram) 5.5f * u else 6.5f * u
        val top = center - (if (tram) 8f else 6.5f) * u
        val bottom = center + 5f * u

        canvas.drawRoundRect(
            RectF(center - halfWidth, top, center + halfWidth, bottom),
            2f * u,
            2f * u,
            solid,
        )
        // Le pare-brise, évidé dans la caisse. Sans lui le pictogramme n'est
        // qu'un rectangle arrondi, et un rectangle arrondi n'est le véhicule de
        // personne.
        canvas.drawRect(
            center - halfWidth + 1.4f * u,
            top + 1.8f * u,
            center + halfWidth - 1.4f * u,
            top + 5.2f * u,
            paint(hollow.argb),
        )

        if (tram) {
            // Perche vers la caténaire et rail au sol : les deux traits qui, à
            // cette taille, séparent un tram d'un bus.
            // 11 et non 12 comme le web : à 12, la pointe de la perche atteint
            // 13,17 u une fois son épaisseur comptée, pour 13,25 u de disque —
            // elle mordait l'anneau, visible sur le jeton de « 50 Otages ».
            canvas.drawLine(center, top, center + 3f * u, center - 11f * u, line)
            canvas.drawLine(center - 7f * u, center + 8.5f * u, center + 7f * u, center + 8.5f * u, line)
        } else {
            canvas.drawCircle(center - 3.4f * u, bottom + 1.4f * u, 1.7f * u, solid)
            canvas.drawCircle(center + 3.4f * u, bottom + 1.4f * u, 1.7f * u, solid)
        }
    }

    /**
     * La position de l'utilisateur : un disque net, cerclé de blanc, en vert Aule.
     *
     * Pas le bleu de Google Maps — le brief l'exclut, et un puck vert au
     * milieu d'une carte claire se repère aussi bien.
     */
    private fun puckDot(): Bitmap =
        bitmap(sizeDp = 26f) { canvas, size ->
            val center = size / 2f
            val ring = paint(0xFFFFFFFF.toInt()).apply {
                setShadowLayer(4f * DENSITY_SCALE / 3f, 0f, 1f * DENSITY_SCALE / 3f, 0x4D000000)
            }
            canvas.drawCircle(center, center, 9f * DENSITY_SCALE, ring)
            canvas.drawCircle(
                center,
                center,
                6.5f * DENSITY_SCALE,
                paint(AuleBrand.teal.argb),
            )
        }

    /**
     * Le halo qui respire sous le puck.
     *
     * Le puck disait où l'on est, et le disait une fois pour toutes : posé sur
     * une carte immobile, rien ne le distinguait d'un marqueur de plus. Le halo
     * est ce qui le rend **vivant** — c'est le seul objet de la carte qui parle
     * de soi, et il doit se retrouver d'un coup d'œil après qu'on a fait défiler
     * la ville.
     *
     * Un **dégradé** et non un aplat : à 0,5 d'opacité uniforme, la teinte
     * fait une tache qui mange la rue. Le web l'a en `radial-gradient`
     * transparent aux sept dixièmes, et c'est cette douceur qui lui permet
     * d'entourer le puck sans peser — d'autant plus depuis qu'il est resserré.
     *
     * Peint à sa taille **haute** : la respiration réduit l'image plutôt que de
     * l'agrandir, et un dégradé qu'on rétrécit reste net là où un dégradé
     * étiré se met à baver.
     *
     * ⚠️ **`accentGlow`, pour la raison qui vaut déjà pour [puckCone].** Le
     * halo était peint au teal de marque, cran 30 : sombre, il n'éclairait
     * rien en transparence — il **assombrissait**, et une aura qui assombrit
     * ne se lit pas comme une lueur mais comme la tache d'humidité sous le
     * puck. C'est ce qu'on voyait sur le S21 : un rond gris qui respirait.
     * Le halo et le cône sont les deux émanations du puck et partagent donc
     * la même encre ; le disque et la flèche, eux, restent au teal de marque,
     * qui est un aplat et se tient très bien comme tel.
     *
     * 0,50 et non 0,55 : sur une teinte claire, le même chiffre pèse plus.
     */
    private fun puckHalo(tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = PUCK_HALO_DP) { canvas, size ->
            val center = size / 2f
            val glow = tokens.accentGlow
            canvas.drawCircle(
                center,
                center,
                center,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        center,
                        center,
                        center,
                        intArrayOf(glow.opacity(0.50).argb, glow.opacity(0.0).argb),
                        // La teinte s'éteint au **bord** de l'image, et non aux
                        // sept dixièmes comme sur le web. Là-bas, le halo
                        // entoure une pastille immobile ; ici il entoure une
                        // flèche qui en masque le cœur — la partie dense du
                        // dégradé était sous le puck, et il ne restait qu'une
                        // couronne trop mince pour se voir sur un toit gris.
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }

    /**
     * Le puck **en mouvement** : une flèche, et non plus un disque.
     *
     * Un disque dit « je suis là ». Une flèche dit « je suis là **et je vais
     * par là** », et c'est la seconde moitié qui manquait : le cône de cap,
     * seul, se lit comme une incertitude — un halo autour du point — là où la
     * question posée en marchant est une question de direction. On garde donc
     * les deux : le cône dit vers où le téléphone regarde, la flèche dit vers
     * où l'on va.
     *
     * Elle n'apparaît **que** quand le cap est connu, c'est-à-dire au-dessus
     * de 0,7 m/s. En dessous, le disque revient : une flèche qui pointerait
     * dans la direction où l'on marchait il y a une minute est un mensonge
     * plus coûteux qu'une absence de direction.
     *
     * Dessinée pointe en haut, comme le cône : MapLibre la fera tourner.
     */
    private fun puckArrow(): Bitmap =
        bitmap(sizeDp = 30f) { canvas, size ->
            val center = size / 2f
            val path = Path().apply {
                moveTo(center, 3.5f * DENSITY_SCALE)
                lineTo(center + 8.5f * DENSITY_SCALE, 24f * DENSITY_SCALE)
                lineTo(center, 18.5f * DENSITY_SCALE)
                lineTo(center - 8.5f * DENSITY_SCALE, 24f * DENSITY_SCALE)
                close()
            }
            // Le liseré blanc d'abord, sous le remplissage : c'est lui qui
            // tient la flèche au-dessus d'un toit sombre comme d'un parc
            // clair, et le peindre par-dessus mangerait la pointe.
            canvas.drawPath(
                path,
                paint(0xFFFFFFFF.toInt()).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * DENSITY_SCALE
                    strokeJoin = Paint.Join.ROUND
                    setShadowLayer(4f * DENSITY_SCALE / 3f, 0f, 1f * DENSITY_SCALE / 3f, 0x4D000000)
                },
            )
            canvas.drawPath(path, paint(AuleBrand.teal.argb))
        }

    /**
     * Le faisceau de direction, en dégradé — il indique un cap, pas une
     * certitude.
     *
     * Dessiné pointe en haut : MapLibre le fera tourner du cap fusionné, et
     * le posera **à plat sur la chaussée**. C'est l'entonnoir des guidages,
     * et sa forme dit deux choses à la fois : d'où l'on regarde, par sa
     * pointe posée sur le puck ; et avec quelle assurance, par une ouverture
     * qui reste large — quarante-huit degrés, l'ordre de grandeur de ce
     * qu'une boussole de téléphone sait vraiment.
     *
     * **Un `drawPath` avec le dégradé pour encre, et non un `clipPath` sur un
     * disque.** Le découpage d'un `Canvas` logiciel n'est pas antialiasé :
     * les deux bords du secteur sortaient en escalier, ce qui se voit d'autant
     * plus qu'ils sont longs. Un tracé, lui, est lissé.
     *
     * Trois arrêts et non deux : à deux, la teinte a déjà perdu la moitié de
     * sa force à mi-chemin, et le faisceau se réduit à une auréole autour du
     * puck. Le palier intermédiaire lui fait tenir les deux premiers tiers,
     * puis l'éteint franchement — un faisceau qui porte, sans bord net au bout.
     *
     * ⚠️ **`accentGlow` et non le teal de marque**, seul endroit du puck où
     * les deux divergent. Le teal Aule est un cran 30 : très sombre. En aplat
     * il est l'identité, mais **en transparence il n'est plus une couleur, il
     * est une ombre** — posé à 0,44 sur la chaussée beige de la carte claire,
     * il rendait un gris que rien ne distinguait de l'ombre d'un immeuble.
     * Vérifié sur le S21 : la couche était bien là, à la bonne taille et au
     * bon cap, et on ne la voyait pas. `accentGlow` est le cran de chroma
     * maximale, celui que le design system réserve précisément aux lueurs, et
     * il suit déjà l'ambiance — clair sur la carte de jour, plus clair encore
     * sur celle de nuit.
     */
    private fun puckCone(tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = PUCK_CONE_DP) { canvas, size ->
            val center = size / 2f
            val radius = center
            val wedge = Path().apply {
                moveTo(center, center)
                arcTo(
                    RectF(
                        center - radius,
                        center - radius,
                        center + radius,
                        center + radius,
                    ),
                    // `arcTo` compte depuis l'est : -90° met la pointe au nord,
                    // qui est le cap zéro.
                    -90f - PUCK_CONE_HALF_ANGLE,
                    2f * PUCK_CONE_HALF_ANGLE,
                )
                close()
            }
            val glow = tokens.accentGlow
            canvas.drawPath(
                wedge,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        center,
                        center,
                        radius,
                        intArrayOf(
                            glow.opacity(0.62).argb,
                            glow.opacity(0.42).argb,
                            glow.opacity(0.0).argb,
                        ),
                        floatArrayOf(PUCK_CONE_START, 0.70f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }

    /**
     * L'épingle d'une adresse. Ancrée en bas : la pointe pose sur le point,
     * pas le centre du bitmap — sinon le lieu visé flotterait d'une dizaine
     * de mètres.
     */
    private fun destinationPin(tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 36f) { canvas, size ->
            val cx = size / 2f
            val tipY = size - 1f * DENSITY_SCALE
            val headR = 8f * DENSITY_SCALE
            val headCy = 11f * DENSITY_SCALE
            val path = Path().apply {
                moveTo(cx, tipY)
                lineTo(cx + headR * 0.78f, headCy + headR * 0.45f)
                arcTo(
                    RectF(cx - headR, headCy - headR, cx + headR, headCy + headR),
                    25f,
                    310f,
                    false,
                )
                close()
            }
            canvas.drawPath(path, paint(tokens.accent.argb))
            canvas.drawCircle(cx, headCy, headR * 0.38f, paint(tokens.onAccent.argb))
        }

    /** L'anneau de sélection, posé sous le marqueur choisi. */
    private fun selectionRing(tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 46f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(
                center,
                center,
                center - 2f * DENSITY_SCALE,
                paint(tokens.accentOnSurface.opacity(0.22).argb),
            )
            canvas.drawCircle(
                center,
                center,
                center - 2f * DENSITY_SCALE,
                paint(tokens.accentOnSurface.argb).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * DENSITY_SCALE
                },
            )
        }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private inline fun bitmap(sizeDp: Float, draw: (Canvas, Float) -> Unit): Bitmap {
        val size = sizeDp * DENSITY_SCALE
        val bitmap = createBitmap(size.toInt())
        draw(Canvas(bitmap), size)
        return bitmap
    }

    private fun createBitmap(size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
}
