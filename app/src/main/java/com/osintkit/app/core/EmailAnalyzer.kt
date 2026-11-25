package com.osintkit.app.core

import java.util.Locale

/** E-posta: biçim, alan adı/MX, sağlayıcı, tek kullanımlık kontrolü, Gravatar. */
object EmailAnalyzer {

    private val EMAIL_RE =
        Regex("""^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$""")

    private val DISPOSABLE = setOf(
        "mailinator.com", "guerrillamail.com", "guerrillamail.net", "guerrillamail.org", "sharklasers.com",
        "grr.la", "10minutemail.com", "10minutemail.net", "temp-mail.org", "temp-mail.io", "tempmail.com",
        "tempmailo.com", "tempail.com", "tempr.email", "yopmail.com", "yopmail.net", "yopmail.fr",
        "trashmail.com", "trashmail.de", "getnada.com", "nada.email", "dispostable.com", "maildrop.cc",
        "throwawaymail.com", "fakeinbox.com", "mintemail.com", "mohmal.com", "emailondeck.com",
        "mailnesia.com", "mytemp.email", "discard.email", "spamgourmet.com", "burnermail.io", "mail.tm",
        "1secmail.com", "1secmail.net", "1secmail.org", "moakt.com", "emailfake.com", "generator.email",
        "inboxkitten.com", "mailcatch.com", "mailpoof.com", "spambox.us", "33mail.com", "anonaddy.me",
        "simplelogin.co", "duck.com", "relay.firefox.com", "mozmail.com",
    )

    /** Bunlar tek kullanımlık değil, "gizleyici/yönlendirici" adresler. */
    private val RELAY = setOf("duck.com", "relay.firefox.com", "mozmail.com", "simplelogin.co", "anonaddy.me", "33mail.com")

    private val FREE = setOf(
        "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "hotmail.com.tr", "live.com", "msn.com",
        "yahoo.com", "yahoo.com.tr", "ymail.com", "icloud.com", "me.com", "mac.com", "aol.com",
        "yandex.com", "yandex.ru", "yandex.com.tr", "mail.ru", "gmx.com", "gmx.net", "gmx.de",
        "proton.me", "protonmail.com", "pm.me", "zoho.com", "tutanota.com", "tuta.io", "mynet.com",
    )

    private val MX_PROVIDERS = listOf(
        "google.com" to "Google (Gmail / Workspace)",
        "googlemail.com" to "Google (Gmail / Workspace)",
        "outlook.com" to "Microsoft (Outlook / 365)",
        "yahoodns.net" to "Yahoo",
        "yandex" to "Yandex",
        "zoho" to "Zoho",
        "protonmail.ch" to "Proton Mail",
        "icloud.com" to "Apple iCloud",
        "mail.ru" to "Mail.ru",
        "mimecast" to "Mimecast (kurumsal güvenlik ağ geçidi)",
        "pphosted.com" to "Proofpoint (kurumsal güvenlik ağ geçidi)",
        "messagelabs" to "Broadcom / Symantec Email Security",
        "barracudanetworks" to "Barracuda (güvenlik ağ geçidi)",
        "secureserver.net" to "GoDaddy",
        "hostinger" to "Hostinger",
        "fastmail" to "Fastmail",
        "migadu" to "Migadu",
        "mailgun" to "Mailgun",
        "amazonaws.com" to "Amazon SES",
        "gmx" to "GMX",
        "ovh" to "OVH",
        "ionos" to "IONOS",
        "yandex.net" to "Yandex",
    )

    fun isValid(email: String) = email.length <= 254 && EMAIL_RE.matches(email)

    fun analyze(input: String, emit: (Section) -> Unit) {
        val email = input.trim().removePrefix("mailto:").lowercase(Locale.ROOT)
        val valid = isValid(email)
        val local = email.substringBefore('@')
        val domain = email.substringAfter('@', "")

        // 1) Biçim
        val fmt = mutableListOf(
            InfoRow("Biçim", if (valid) "Geçerli" else "Geçersiz e-posta biçimi"),
            InfoRow("Yerel kısım", local),
            InfoRow("Alan adı", domain, if (domain.isNotBlank()) InternalLinks.network(domain) else null),
        )
        val baseLocal = local.substringBefore('+')
        if (local.contains('+')) {
            fmt += InfoRow("Alt adres etiketi", "+" + local.substringAfter('+') + " (asıl adres: $baseLocal@$domain)")
        }
        if (domain == "gmail.com" || domain == "googlemail.com") {
            val norm = baseLocal.replace(".", "")
            if (norm != local) fmt += InfoRow("Gmail normal biçimi", "$norm@gmail.com (Gmail noktaları yok sayar)")
        }
        emit(Section("Adres", fmt))
        if (!valid) return

        // 2) Sağlayıcı türü
        val type = mutableListOf<InfoRow>()
        val parentMatch = { set: Set<String> -> set.any { domain == it || domain.endsWith(".$it") } }
        when {
            parentMatch(RELAY) -> type += InfoRow("Tür", "Gizleyici / yönlendirici adres — gerçek kutu gizli")
            parentMatch(DISPOSABLE) -> type += InfoRow("Tür", "Tek kullanımlık (geçici) e-posta servisi")
            domain in FREE -> type += InfoRow("Tür", "Ücretsiz kişisel e-posta sağlayıcısı")
            else -> type += InfoRow("Tür", "Kurumsal veya özel alan adı")
        }
        emit(Section("Sağlayıcı", type))

        // 3) Posta sunucuları
        try {
            val mx = Dns.query(domain, "MX")
            val title = "Posta sunucuları (MX)"
            when {
                mx.nxDomain -> emit(Section.error(title, "Alan adı yok (NXDOMAIN) — bu adrese e-posta gidemez"))
                mx.records.none { it.type == "MX" } -> {
                    val a = runCatching { Dns.query(domain, "A").records.any { it.type == "A" } }.getOrDefault(false)
                    emit(
                        Section(
                            title,
                            listOf(InfoRow("Sonuç", if (a) "MX yok; A kaydına teslim denenebilir (zayıf)" else "MX yok — e-posta alamaz")),
                        ),
                    )
                }
                else -> {
                    val rows = mutableListOf<InfoRow>()
                    val sorted = mx.records.filter { it.type == "MX" }
                        .sortedBy { it.data.substringBefore(' ').toIntOrNull() ?: 0 }
                    val hosts = sorted.map { it.data.substringAfter(' ').lowercase(Locale.ROOT) }
                    val provider = hosts.firstNotNullOfOrNull { h -> MX_PROVIDERS.firstOrNull { h.contains(it.first) }?.second }
                    rows += InfoRow("E-posta alabilir", "Evet")
                    rows += InfoRow("Barındıran (tahmini)", provider ?: "Bilinmiyor / kendi sunucusu")
                    sorted.forEach { r ->
                        val host = r.data.substringAfter(' ')
                        rows += InfoRow("MX öncelik ${r.data.substringBefore(' ')}", host, InternalLinks.network(host))
                    }
                    emit(Section(title, rows))
                }
            }
        } catch (e: Exception) {
            emit(Section.fromException("Posta sunucuları (MX)", e))
        }

        // 4) Gravatar (herkese açık profil fotoğrafı/profili)
        emit(gravatar(email))

        // 5) Pivotlar ve harici kaynaklar
        emit(
            Section(
                "Devam et",
                listOf(
                    InfoRow("Kullanıcı adı olarak tara", baseLocal, InternalLinks.username(baseLocal)),
                    InfoRow("Alan adını incele", domain, InternalLinks.network(domain)),
                    InfoRow("Veri sızıntısı kontrolü", "Have I Been Pwned", "https://haveibeenpwned.com/"),
                    InfoRow("Web'de ara", "\"$email\"", "https://www.google.com/search?q=${urlEncode("\"$email\"")}"),
                ),
            ),
        )
    }

    fun gravatar(email: String): Section {
        val title = "Gravatar"
        return try {
            val sha = hashHex("SHA-256", email)
            val md5 = hashHex("MD5", email)
            val avatarUrl = "https://gravatar.com/avatar/$sha?s=400&d=404"
            val r = Http.get(avatarUrl, maxBytes = 1)
            if (r.code == 404) {
                Section(title, listOf(InfoRow("Sonuç", "Bu adrese bağlı Gravatar yok")))
            } else if (r.code !in 200..299) {
                Section.error(title, "HTTP ${r.code}")
            } else {
                val rows = mutableListOf(InfoRow("Profil fotoğrafı", "Var — görmek için dokunun", avatarUrl))
                runCatching {
                    val p = Http.get("https://en.gravatar.com/$md5.json", headers = mapOf("Accept" to "application/json"))
                    if (p.code == 200) {
                        val e = Json.parse(p.body).field("entry").items().firstOrNull()
                        e.field("displayName").text()?.let { rows += InfoRow("Görünen ad", it) }
                        e.field("preferredUsername").text()?.let { rows += InfoRow("Kullanıcı adı", it, InternalLinks.username(it)) }
                        e.field("profileUrl").text()?.let { rows += InfoRow("Profil", it, it) }
                        e.field("currentLocation").text()?.takeIf { it.isNotBlank() }?.let { rows += InfoRow("Belirttiği konum", it) }
                        e.field("aboutMe").text()?.takeIf { it.isNotBlank() }?.let { rows += InfoRow("Hakkında", it.truncate(300)) }
                        e.field("accounts").items().forEach { a ->
                            val url = a.field("url").text()
                            val name = a.field("shortname").text() ?: a.field("domain").text() ?: "Hesap"
                            if (url != null) rows += InfoRow("Bağlı hesap: $name", url, url)
                        }
                        e.field("urls").items().forEach { u ->
                            u.field("value").text()?.let { rows += InfoRow(u.field("title").text() ?: "Bağlantı", it, it) }
                        }
                    }
                }
                Section(title, rows)
            }
        } catch (e: Exception) {
            Section.fromException(title, e)
        }
    }
}
