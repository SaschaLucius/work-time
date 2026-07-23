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
    private const val BREAK_30 = 30
    private const val BREAK_45 = 45

    /** Tagesarbeitszeit-Maximum nach ArbZG §3 (normal 8 Std., maximal 10 Std.). */
    const val MAX_NET_MINUTES = 10 * 60

    /** Berechnet Netto-Minuten aus Brutto-Minuten (= verstrichene Zeit seit Start). */
    fun calculateNetMinutes(grossMinutes: Int): Int = when {
        grossMinutes <= THRESHOLD_30_MIN -> grossMinutes
        grossMinutes <= THRESHOLD_45_MIN -> grossMinutes - BREAK_30
        else -> grossMinutes - BREAK_45
    }

    /** Gibt die gesetzlich erforderliche Pausendauer in Minuten zurück. */
    fun requiredBreakMinutes(grossMinutes: Int): Int = when {
        grossMinutes <= THRESHOLD_30_MIN -> 0
        grossMinutes <= THRESHOLD_45_MIN -> BREAK_30
        else -> BREAK_45
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
    fun calculateFromStartEnd(startMinutes: Int, endMinutes: Int): Int {
        val gross = (endMinutes - startMinutes).coerceAtLeast(0)
        return calculateNetMinutes(gross)
    }
}
