package com.osintkit.app.android

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.osintkit.app.core.InfoRow
import com.osintkit.app.core.NetworkLookup
import com.osintkit.app.core.Section
import com.osintkit.app.core.TrOperators
import com.osintkit.app.core.urlEncode

/** Telefon numarası: tamamen çevrimdışı çözümleme (Google libphonenumber). */
object PhoneAnalyzer {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    fun analyze(input: String, defaultRegion: String = "TR"): List<Section> {
        val raw = input.trim()
        val num = try {
            util.parse(raw, defaultRegion)
        } catch (e: NumberParseException) {
            val why = when (e.errorType) {
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE -> "Geçersiz ülke kodu"
                NumberParseException.ErrorType.NOT_A_NUMBER -> "Bu bir telefon numarası değil"
                NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD,
                NumberParseException.ErrorType.TOO_SHORT_NSN -> "Numara çok kısa"
                NumberParseException.ErrorType.TOO_LONG -> "Numara çok uzun"
                else -> "Çözümlenemedi"
            }
            return listOf(Section.error("Numara", why))
        }

        val valid = util.isValidNumber(num)
        val possible = util.isPossibleNumber(num)
        val type = util.getNumberType(num)
        val region = util.getRegionCodeForNumber(num)
            ?: util.getRegionCodeForCountryCode(num.countryCode).takeIf { it != "ZZ" }
        val e164 = util.format(num, PhoneNumberFormat.E164)
        val intl = util.format(num, PhoneNumberFormat.INTERNATIONAL)
        val national = util.format(num, PhoneNumberFormat.NATIONAL)
        val nsn = util.getNationalSignificantNumber(num)

        val sections = mutableListOf<Section>()

        val main = mutableListOf(
            InfoRow(
                "Geçerlilik",
                when {
                    valid -> "Geçerli numara"
                    possible -> "Uzunluk uygun ama bu ülkede kullanılmayan bir aralık"
                    else -> "Geçersiz numara"
                },
            ),
            InfoRow("Ülke", region?.let { NetworkLookup.countryName(it) } ?: "Bilinmiyor"),
            InfoRow("Ülke kodu", "+${num.countryCode}"),
            InfoRow("Hat türü", typeName(type)),
            InfoRow("E.164", e164),
            InfoRow("Uluslararası biçim", intl),
            InfoRow("Ulusal biçim", national),
        )
        sections += Section("Numara", main)

        if (region == "TR" && (type == PhoneNumberType.MOBILE || type == PhoneNumberType.FIXED_LINE_OR_MOBILE)) {
            val op = TrOperators.originalOperator(nsn)
            sections += Section(
                "Operatör (ilk tahsis)",
                listOf(
                    InfoRow("Ön eke göre", op ?: "Bilinmiyor"),
                    InfoRow("Not", "Numara taşıma nedeniyle şu anki operatör farklı olabilir."),
                ),
            )
        }

        val digits = e164.removePrefix("+")
        val query = listOf(intl, national, e164).distinct().joinToString(" OR ") { "\"$it\"" }
        sections += Section(
            "Devam et",
            listOf(
                InfoRow("WhatsApp", "Sohbet bağlantısını aç", "https://wa.me/$digits"),
                InfoRow("Telegram", "Numara herkese açıksa profili açar", "https://t.me/$e164"),
                InfoRow("Web'de ara", query, "https://www.google.com/search?q=${urlEncode(query)}"),
            ),
        )
        return sections
    }

    private fun typeName(t: PhoneNumberType): String = when (t) {
        PhoneNumberType.MOBILE -> "Mobil"
        PhoneNumberType.FIXED_LINE -> "Sabit hat"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Sabit hat veya mobil"
        PhoneNumberType.TOLL_FREE -> "Ücretsiz arama (0800 vb.)"
        PhoneNumberType.PREMIUM_RATE -> "Ücretli özel servis (0900 vb.)"
        PhoneNumberType.SHARED_COST -> "Paylaşımlı ücret (444 vb.)"
        PhoneNumberType.VOIP -> "İnternet telefonu (VoIP)"
        PhoneNumberType.PERSONAL_NUMBER -> "Kişisel numara"
        PhoneNumberType.PAGER -> "Çağrı cihazı"
        PhoneNumberType.UAN -> "Kurumsal tek numara"
        PhoneNumberType.VOICEMAIL -> "Sesli mesaj"
        else -> "Bilinmiyor"
    }
}
