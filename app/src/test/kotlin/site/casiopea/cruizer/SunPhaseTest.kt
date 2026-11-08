package site.casiopea.cruizer

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AUTO night mode: sunrise/sunset must bracket the day, in the phone's own time zone. */
class SunPhaseTest {

    private fun <T> inKolkata(block: () -> T): T {
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        return try {
            block()
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    private fun at(month: Int, day: Int, hour: Int): Long =
        GregorianCalendar(2026, month, day, hour, 0).timeInMillis

    @Test
    fun trivandrumDayAndNight() = inKolkata {
        // 8.5N 77E on 7 Nov 2026: sunrise ~06:11, sunset ~17:59 IST.
        assertFalse(SunPhase.isNight(8.5, 77.0, at(Calendar.NOVEMBER, 7, 12)))
        assertFalse(SunPhase.isNight(8.5, 77.0, at(Calendar.NOVEMBER, 7, 17)))
        assertTrue(SunPhase.isNight(8.5, 77.0, at(Calendar.NOVEMBER, 7, 20)))
        assertTrue(SunPhase.isNight(8.5, 77.0, at(Calendar.NOVEMBER, 7, 3)))
    }

    @Test
    fun polarNightAndMidnightSun() {
        assertTrue(SunPhase.isNight(78.0, 15.0, at(Calendar.DECEMBER, 21, 12)))
        assertFalse(SunPhase.isNight(78.0, 15.0, at(Calendar.JUNE, 21, 12)))
    }
}
