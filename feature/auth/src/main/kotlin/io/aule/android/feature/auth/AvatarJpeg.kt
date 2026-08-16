package io.aule.android.feature.auth

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.aule.android.core.model.AvatarException
import io.aule.android.core.model.AvatarFailureKind
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Réduit une photo au contrat Flutter : 1200 px de côté, JPEG 85.
 *
 * `:data` est JVM et ne décode pas de Bitmap. Le redimensionnement reste
 * donc ici, dans le module qui tient déjà le sélecteur.
 */
internal fun jpegFromUri(resolver: ContentResolver, uri: Uri): ByteArray {
    val source = decodeBitmap(resolver, uri)
    val scaled = scaleDown(source, MAX_EDGE)
    try {
        val out = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (!ok) throw AvatarException(AvatarFailureKind.UNSUPPORTED)
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) throw AvatarException(AvatarFailureKind.EMPTY)
        return bytes
    } finally {
        if (scaled !== source) scaled.recycle()
        source.recycle()
    }
}

internal fun avatarCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "avatars").apply { mkdirs() }
    val file = File(dir, "capture.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private fun decodeBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(resolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            if (longest > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } ?: throw AvatarException(AvatarFailureKind.UNSUPPORTED)
    return if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)?.also { bitmap.recycle() }
            ?: throw AvatarException(AvatarFailureKind.UNSUPPORTED)
    } else {
        bitmap
    }
}

private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= maxEdge) return bitmap
    val scale = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

private const val MAX_EDGE = 1200
private const val JPEG_QUALITY = 85
