package de.worktime.domain

/**
 * Berechnet die Netto-Arbeitszeit nach deutschem Arbeitszeitgesetz (ArbZG §4).
 *
 * Pausenregelung (Brutto-Schwellenwerte):
 *  - Brutto ≤ 6 Std.         → keine Pause, Netto = Brutto
 *  - Brutto > 6 Std. bis ≤ 9 Std. 30 Min. → 30 Min. Pflichtpause
 *  - Brutto > 9 Std. 30 Min. → 45 Min. Pflichtpause
 */
object WorkTimeCalculator {

    private const val THRESHOLD_30_MIN = 6 * 60        // 360 Min. Brutto
    private const val THRESHOLD_45_MIN = 9 * 60 + 30   // 570 Min. Brutto

    data class BreakConfig(
        val firstBreakMinutes: Int = 30,
        val secondBreakMinutes: Int = 45
    ) {
        init {
            require(firstBreakMinutes >= 0) { "First break must not be negative" }
            require(secondBreakMinutes >= firstBreakMinutes) {
                "Second break must be at least as long as the first break"
            }
        }
    }

    /** Tagesarbeitszeit-Maximum nach ArbZG §3 (normal 8 Std., maximal 10 Std.). */
    const val MAX_NET_MINUTES = 10 * 60

    /** Berechnet Netto-Minuten aus Brutto-Minuten (= verstrichene Zeit seit Start). */
    fun calculateNetMinutes(
        grossMinutes: Int,
        breakConfig: BreakConfig = BreakConfig()
    ): Int = when {
        grossMinutes <= THRESHOLD_30_MIN -> grossMinutes
        grossMinutes <= THRESHOLD_45_MIN -> grossMinutes - breakConfig.firstBreakMinutes
        else -> grossMinutes - breakConfig.secondBreakMinutes
    }.coerceAtLeast(0)

    /** Gibt die gesetzlich erforderliche Pausendauer in Minuten zurück. */
    fun requiredBreakMinutes(
        grossMinutes: Int,
        breakConfig: BreakConfig = BreakConfig()
    ): Int = when {
        grossMinutes <= THRESHOLD_30_MIN -> 0
        grossMinutes <= THRESHOLD_45_MIN -> breakConfig.firstBreakMinutes
        else -> breakConfig.secondBreakMinutes
    }

    /** Gibt die früheste Brutto-Minute zurück, in der das Netto-Ziel erreicht ist. */
    fun grossMinutesToReachNetTarget(
        targetNetMinutes: Int,
        breakConfig: BreakConfig = BreakConfig()
    ): Int {
        val target = targetNetMinutes.coerceAtLeast(0)
        val latestCandidate = target + breakConfig.secondBreakMinutes
        return (0..latestCandidate).first { grossMinutes ->
            calculateNetMinutes(grossMinutes, breakConfig) >= target
        }
    }

    /** Prüft ob die gesetzliche Höchstarbeitszeit (10 Std.) überschritten wurde. */
    fun isOverMaximum(netMinutes: Int): Boolean = netMinutes > MAX_NET_MINUTES

    /** Formatiert Minuten als „HH:MM". */
    fun formatDuration(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /**
     * Berechnet Netto-Minuten aus Startzeit und Endzeit
     * (beide als Minuten seit Mitternacht).
     */
    fun calculateFromStartEnd(
        startMinutes: Int,
        endMinutes: Int,
        breakConfig: BreakConfig = BreakConfig()
    ): Int {
        val gross = (endMinutes - startMinutes).coerceAtLeast(0)
        return calculateNetMinutes(gross, breakConfig)
    }
}
