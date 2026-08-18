package io.aule.android.core.model

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class GpsTraceTest {

    private val defaultLocale = Locale.getDefault()

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun point(
        course: Double? = 142.0,
        mocked: Boolean = false,
    ) = GpsTracePoint(
        timestampMillis = 1_760_000_000_000,
        latitude = 47.256012,
        longitude = -1.532034,
        accuracyMeters = 4.5,
        speedMetersPerSecond = 8.4,
        courseDegrees = course,
        isMocked = mocked,
    )

    @Test
    fun `une ligne porte autant de colonnes que l en-tete`() {
        val columns = point().toCsvRow().split(",")

        assertEquals(GPS_TRACE_CSV_HEADER.split(",").size, columns.size)
        assertEquals("2025-10-09T08:53:20Z", columns.first())
    }

    /**
     * Le piège de la maison : un appareil réglé en français écrit
     * « 47,256012 ». Dans un fichier séparé par des virgules, une seule
     * coordonnée décale toute la ligne — et le fichier ne se relit plus.
     */
    @Test
    fun `la locale du telephone ne s invite pas dans les decimales`() {
        Locale.setDefault(Locale.FRANCE)

        val row = point().toCsvRow()

        assertTrue(row.contains("47.256012"), row)
        assertTrue(row.contains("-1.532034"), row)
        assertEquals(GPS_TRACE_CSV_HEADER.split(",").size, row.split(",").size)
    }

    /** À l'arrêt, le GPS n'a pas de cap — et « 0 » se lirait comme un cap au nord. */
    @Test
    fun `un cap absent laisse sa colonne vide`() {
        val columns = point(course = null).toCsvRow().split(",")

        assertEquals("", columns[COURSE_COLUMN])
        assertEquals(GPS_TRACE_CSV_HEADER.split(",").size, columns.size)
    }

    @Test
    fun `une position simulee se reconnait dans le fichier`() {
        assertEquals("1", point(mocked = true).toCsvRow().split(",").last())
        assertEquals("0", point(mocked = false).toCsvRow().split(",").last())
    }

    private companion object {
        val COURSE_COLUMN = GPS_TRACE_CSV_HEADER.split(",").indexOf("courseDegrees")
    }
}
