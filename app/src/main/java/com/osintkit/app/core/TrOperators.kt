package com.osintkit.app.core

/**
 * Türkiye mobil numaralarında ön eke göre İLK tahsis edilen operatör.
 * Numara taşıma nedeniyle güncel operatör farklı olabilir.
 */
object TrOperators {
    private val TURK_TELEKOM = setOf("501", "505", "506", "507", "551", "552", "553", "554", "555", "559")

    /** @param national ülke kodu olmadan 10 haneli numara, ör. "5321234567" */
    fun originalOperator(national: String): String? {
        val digits = national.filter { it.isDigit() }
        if (digits.length != 10 || !digits.startsWith("5")) return null
        val prefix = digits.substring(0, 3)
        val p = prefix.toInt()
        return when {
            prefix in TURK_TELEKOM -> "Türk Telekom"
            p in 530..539 -> "Turkcell"
            p in 540..549 -> "Vodafone"
            else -> null
        }
    }
}
