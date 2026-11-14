package site.casiopea.cruizer

/**
 * Sunrise/sunset estimation for the AUTO night mode. Standard solar-position
 * approximation (+/- a few minutes accuracy — plenty for a screen-theme flag).
 */
object SunPhase {

    /** true when the sun is below the horizon at [now] (millis) for a given position. */
    fun isNight(lat: Double, lon: Double, now: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR).toDouble() - 1
        val rad = Math.PI / 180
        val latR = lat * rad

        // Solar declination (simplified NOAA Fourier series, radians).
        val g = 0.0172028 * dayOfYear // day angle
        val decl = 0.006918 - 0.399912 * kotlin.math.cos(g) + 0.070257 * kotlin.math.sin(g) -
            0.006758 * kotlin.math.cos(2 * g) + 0.000907 * kotlin.math.sin(2 * g) -
            0.002697 * kotlin.math.cos(3 * g) + 0.001480 * kotlin.math.sin(3 * g)

        // Hour angle for sunrise/sunset, zenith 90.83 deg (accounts for refraction).
        val cosH = (kotlin.math.cos(90.83 * rad) - kotlin.math.sin(latR) * kotlin.math.sin(decl)) /
            (kotlin.math.cos(latR) * kotlin.math.cos(decl))
        // Polar day/night: the sun never crosses the horizon.
        if (cosH > 1) return true // polar night: sun never rises
        if (cosH < -1) return false // midnight sun: sun never sets

        // Day length in minutes: 2 * acos(cosH) hours, and 1 hour = 15 degrees.
        val dayLengthMin = 8 * kotlin.math.acos(cosH) / rad
        // Solar noon in local minutes: UTC noon minus 4 min per degree of longitude,
        // then shifted into the phone's zone (raw offset + DST). The equation of
        // time corrects the up-to-16-minute difference between mean and solar time.
        val zoneMin = (cal.get(java.util.Calendar.ZONE_OFFSET) +
            cal.get(java.util.Calendar.DST_OFFSET)) / 60000
        val b = 2 * Math.PI * (dayOfYear - 81) / 364
        val eotMin = 9.87 * kotlin.math.sin(2 * b) - 7.53 * kotlin.math.cos(b) -
            1.5 * kotlin.math.sin(b)
        val solarNoonMin = 720 - 4 * lon + zoneMin - eotMin
        val sunriseMin = solarNoonMin - dayLengthMin / 2
        val sunsetMin = solarNoonMin + dayLengthMin / 2
        val minutesOfDay =
            cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return minutesOfDay < sunriseMin || minutesOfDay > sunsetMin
    }
}
