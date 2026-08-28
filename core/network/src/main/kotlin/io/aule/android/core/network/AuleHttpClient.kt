package io.aule.android.core.network

import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class RawHttpResponse(
    val code: Int,
    val body: String,
)

data class RawHttpBytes(
    val code: Int,
    val body: ByteArray,
)

/**
 * Le client HTTP. Une seule façon de parler au réseau dans toute l'application.
 *
 * Il **lève** plutôt que de rendre une liste vide. Côté Flutter, l'inverse avait
 * produit une carte d'apparence normale, sans véhicules et sans message, pendant
 * une panne — le genre de défaut qu'on ne voit qu'en production.
 *
 * La fabrique d'appels est injectée : MapLibre embarque déjà OkHttp pour ses
 * tuiles et ses glyphes, et lui passer ce même client donne un seul pool de
 * connexions, un seul délai d'attente et un seul point de journalisation.
 */
class AuleHttpClient(
    private val callFactory: Call.Factory,
    private val logger: AuleLogger,
    private val json: Json = defaultJson,
) {

    suspend fun <T> get(
        url: String,
        query: Map<String, String?> = emptyMap(),
        deserializer: DeserializationStrategy<T>,
    ): T {
        val body = getText(url, query)
        return try {
            json.decodeFromString(deserializer, body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            logger.error(LogDomain.NET, "Décodage impossible pour $url", failure)
            throw ApiException.Decoding(failure)
        }
    }

    suspend fun getText(url: String, query: Map<String, String?> = emptyMap()): String {
        val requestUrl = build(url, query)
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .build()
        return executeForText(request, requestUrl.encodedPath)
    }

    /**
     * GET brut — pour PostgREST, dont un 200 `[]` est une absence, pas une
     * panne, et dont l'appelant doit poser lui-même `Authorization`.
     */
    suspend fun getRaw(
        url: String,
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?> = emptyMap(),
    ): RawHttpResponse {
        val requestUrl = build(url, query)
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        return executeRaw(request)
    }

    /**
     * POST JSON brut — pour GoTrue et tout endpoint qui porte ses erreurs
     * dans le corps d'un 4xx que l'appelant doit lire lui-même.
     */
    suspend fun postRaw(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?> = emptyMap(),
    ): RawHttpResponse {
        val requestUrl = build(url, query)
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeRaw(request)
    }

    /**
     * PATCH JSON brut — pour PostgREST, dont un 204 sans corps et un 200
     * `[{…}]` sont tous les deux un succès, et dont l'appelant pose
     * `Prefer: return=representation` s'il veut relire la ligne.
     */
    suspend fun patchRaw(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?> = emptyMap(),
    ): RawHttpResponse {
        val requestUrl = build(url, query)
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .patch(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeRaw(request)
    }

    /**
     * PUT JSON brut — pour GoTrue `/user`, qui ne connaît que ce verbe pour
     * modifier le compte courant. PATCH y répond 405, et non 200 : le détour
     * par `patchRaw` n'existe pas.
     */
    suspend fun putRaw(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?> = emptyMap(),
    ): RawHttpResponse {
        val requestUrl = build(url, query)
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .put(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeRaw(request)
    }

    /**
     * POST binaire — pour Storage, dont le corps est l'octet de l'image et
     * dont un 4xx JSON doit rester lisible par l'appelant.
     */
    suspend fun postBytes(
        url: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?> = emptyMap(),
    ): RawHttpResponse {
        val requestUrl = build(url, query)
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        return executeRaw(request)
    }

    /**
     * DELETE JSON brut — pour Storage `remove`, qui porte la liste des
     * préfixes dans le corps, pas dans l'URL.
     */
    suspend fun deleteRaw(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?> = emptyMap(),
    ): RawHttpResponse {
        val requestUrl = build(url, query)
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .delete(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeRaw(request)
    }

    /**
     * GET binaire — pour une photo publique. Le lire en texte corromprait
     * le JPEG, et un 404 n'est pas une panne de fiche : l'appelant décide.
     */
    suspend fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?> = emptyMap(),
    ): RawHttpBytes {
        val requestUrl = build(url, query)
        val request = Request.Builder()
            .url(requestUrl)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        return executeBytes(request)
    }

    private suspend fun executeRaw(request: Request): RawHttpResponse {
        val response = try {
            callFactory.newCall(request).await()
        } catch (cancellation: CancellationException) {
            throw ApiException.Cancelled()
        } catch (failure: IOException) {
            logger.warn(LogDomain.NET, "Transport en échec sur ${request.url.encodedPath}", failure)
            throw ApiException.Transport(failure)
        }
        response.use {
            return RawHttpResponse(code = it.code, body = it.body.string())
        }
    }

    private suspend fun executeBytes(request: Request): RawHttpBytes {
        val response = try {
            callFactory.newCall(request).await()
        } catch (cancellation: CancellationException) {
            throw ApiException.Cancelled()
        } catch (failure: IOException) {
            logger.warn(LogDomain.NET, "Transport en échec sur ${request.url.encodedPath}", failure)
            throw ApiException.Transport(failure)
        }
        response.use {
            return RawHttpBytes(code = it.code, body = it.body.bytes())
        }
    }

    private suspend fun executeForText(request: Request, pathForLog: String): String {
        val response = try {
            callFactory.newCall(request).await()
        } catch (cancellation: CancellationException) {
            throw ApiException.Cancelled()
        } catch (failure: IOException) {
            logger.warn(LogDomain.NET, "Transport en échec sur $pathForLog", failure)
            throw ApiException.Transport(failure)
        }

        response.use {
            val body = it.body.string()
            when (val status = it.code) {
                in 200..299 -> return body
                404 -> throw ApiException.NotFound(serverMessage(body))
                502, 503, 504 -> throw ApiException.UpstreamUnavailable(status)
                in 400..499 -> throw ApiException.BadRequest(status, serverMessage(body))
                else -> throw ApiException.Server(status)
            }
        }
    }

    /**
     * Les paramètres sont triés par nom.
     *
     * Deux requêtes identiques doivent produire la même URL, sinon les caches —
     * celui d'OkHttp comme celui du BFF — voient deux ressources là où il n'y en
     * a qu'une.
     */
    private fun build(url: String, query: Map<String, String?>): HttpUrl {
        val builder = url.toHttpUrl().newBuilder()
        query.entries
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .sortedBy { it.first }
            .forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    /**
     * Le backend Aule met ses messages d'erreur dans `{"error": "..."}`. Quand il
     * y en a un, il est plus utile que tout ce qu'on pourrait écrire à sa place.
     */
    private fun serverMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body)
            .let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("error")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
    }.getOrNull()

    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = false
            explicitNulls = false
        }

        /**
         * Le client partagé.
         *
         * `retryOnConnectionFailure` reste actif : en mobilité, une bascule
         * Wi-Fi → 4G casse une connexion établie, et réessayer est exactement la
         * bonne réponse.
         */
        fun defaultOkHttp(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = if (this is kotlinx.serialization.json.JsonNull) null else content

/**
 * Pont entre l'API à rappel d'OkHttp et les coroutines.
 *
 * L'annulation de la coroutine annule l'appel HTTP : sans cela, un écran quitté
 * laisserait sa requête courir jusqu'au bout, et son décodage avec.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
}
