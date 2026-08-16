package io.aule.android.core.network

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Lecture des dates ISO 8601 du backend.
 *
 * Il écrit tantôt `2026-08-16T06:03:19.000Z`, tantôt `2026-08-16T06:03:19Z`. Sur
 * iOS, cela avait imposé deux formateurs, `withFractionalSeconds` y étant
 * exclusif ; `Instant.parse` de la JVM accepte les deux formes, une seule passe
 * suffit donc. Le repli sur [OffsetDateTime] couvre les décalages explicites
 * (`+02:00`), que `Instant.parse` refuse.
 */
object Iso8601 {

    fun parseOrNull(text: String): Instant? =
        try {
            Instant.parse(text)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(text).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }

    fun format(instant: Instant): String = instant.toString()
}

/** Sérialiseur d'[Instant] pour les charges utiles du BFF. */
object InstantIso8601Serializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.aule.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(Iso8601.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return Iso8601.parseOrNull(text)
            ?: throw SerializationException("Date ISO 8601 illisible : « $text »")
    }
}
