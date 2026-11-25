package com.osintkit.app.core

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Alan adı / IP: DNS, RDAP, ham WHOIS, IP konum-ağ bilgisi, ters DNS, crt.sh alt alan adları. */
object NetworkLookup {

    private val DOMAIN_RE =
        Regex("""^(?=.{1,253}$)(?:[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{0,62}$""")

    /** ccTLD altında kayıt yapılan ikinci seviyeler (ör. ornek.com.tr, ornek.co.uk). */
    private val SECOND_LEVEL = setOf(
        "com", "net", "org", "gov", "edu", "gen", "biz", "info", "av", "bel", "pol", "k12", "tv",
        "web", "tel", "name", "bbs", "tsk", "dr", "kep", "nc", "co", "ac", "or", "ne", "go", "gob",
        "mil", "nic", "ltd", "plc", "sch", "nom",
    )

    private val TR = Locale("tr", "TR")

    fun normalizeTarget(input: String): String {
        var s = input.trim()
        s = s.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        if (s.contains('@')) s = s.substringAfterLast('@') // e-posta veya kullanıcı@host
        if (s.startsWith("[")) {
            s = s.substringAfter('[').substringBefore(']')
        } else if (s.count { it == ':' } == 1) {
            s = s.substringBefore(':') // port
        }
        s = s.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (!IpUtil.isIp(s)) s = runCatching { java.net.IDN.toASCII(s) }.getOrDefault(s)
        return s
    }

    fun isValidDomain(s: String) = DOMAIN_RE.matches(s)

    fun guessRegistrable(domain: String): String {
        val labels = domain.split('.')
        if (labels.size <= 2) return domain
        val tld = labels.last()
        val sld = labels[labels.size - 2]
        val take = if (tld.length == 2 && sld in SECOND_LEVEL) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    fun lookup(input: String, emit: (Section) -> Unit) {
        val target = normalizeTarget(input)
        when {
            IpUtil.isIp(target) -> lookupIp(target, emit)
            isValidDomain(target) -> lookupDomain(target, emit)
            else -> emit(Section.error("Geçersiz hedef", "Bir alan adı (ornek.com) veya IP adresi (8.8.8.8) girin."))
        }
    }

    // ---------------------------------------------------------------- Alan adı

    fun lookupDomain(domain: String, emit: (Section) -> Unit) {
        val registrable = guessRegistrable(domain)
        val head = mutableListOf(InfoRow("Alan adı", domain))
        val unicode = runCatching { java.net.IDN.toUnicode(domain) }.getOrDefault(domain)
        if (unicode != domain) head += InfoRow("Unicode biçimi", unicode)
        if (registrable != domain) head += InfoRow("Kayıtlı alan adı (tahmini)", registrable, InternalLinks.network(registrable))
        emit(Section("Hedef", head))

        // 1) DNS kayıtları
        var firstIp: String? = null
        val txt = mutableListOf<String>()
        var nx = false
        try {
            val rows = mutableListOf<InfoRow>()
            for (type in listOf("A", "AAAA", "CNAME", "MX", "NS", "TXT", "SOA", "CAA")) {
                val ans = Dns.query(domain, type)
                if (ans.nxDomain) {
                    nx = true
                    break
                }
                for (r in ans.records.filter { it.type == type }) {
                    val link = when (r.type) {
                        "A", "AAAA", "CNAME", "NS" -> InternalLinks.network(r.data)
                        "MX" -> r.data.substringAfter(' ', "").takeIf { it.isNotBlank() }?.let { InternalLinks.network(it) }
                        else -> null
                    }
                    if (r.type == "A" && firstIp == null) firstIp = r.data
                    if (r.type == "TXT") txt += r.data
                    rows += InfoRow("${r.type} · TTL ${r.ttl}", r.data, link)
                }
            }
            when {
                nx -> emit(Section.error("DNS kayıtları", "Alan adı DNS'te yok (NXDOMAIN). Kayıtlı olmayabilir ya da süresi dolmuş olabilir."))
                rows.isEmpty() -> emit(Section("DNS kayıtları", listOf(InfoRow("Sonuç", "Kayıt bulunamadı"))))
                else -> emit(Section("DNS kayıtları", rows))
            }
        } catch (e: Exception) {
            emit(Section.fromException("DNS kayıtları", e))
        }

        if (!nx) emit(mailSecurity(domain, txt))

        // 2) RDAP (yapılandırılmış WHOIS)
        try {
            emit(rdapDomain(registrable))
        } catch (e: Exception) {
            emit(Section.fromException("Kayıt bilgisi (RDAP)", e))
        }

        // 3) Ham WHOIS (TCP 43) — .tr gibi RDAP'ı olmayan uzantılar için
        try {
            emit(rawWhois(registrable))
        } catch (e: Exception) {
            emit(Section.fromException("WHOIS (ham)", e))
        }

        // 4) Ana IP
        firstIp?.let { lookupIp(it, emit, prefix = "Ana IP · ") }

        // 5) Harici araçlar
        emit(
            Section(
                "Harici araçlar",
                listOf(
                    InfoRow("Sertifika kayıtları (crt.sh)", "Alt alan adları", "https://crt.sh/?q=${urlEncode("%.$registrable")}"),
                    InfoRow("Wayback Machine", "Sitenin eski halleri", "https://web.archive.org/web/*/$domain"),
                    InfoRow("VirusTotal", "Güvenlik raporu", "https://www.virustotal.com/gui/domain/$domain"),
                    InfoRow("urlscan.io", "Tarama geçmişi", "https://urlscan.io/domain/$domain"),
                    InfoRow("SecurityTrails", "DNS geçmişi", "https://securitytrails.com/domain/$domain/dns"),
                ),
            ),
        )
    }

    private fun mailSecurity(domain: String, txt: List<String>): Section {
        val rows = mutableListOf<InfoRow>()
        val spf = txt.firstOrNull { it.startsWith("v=spf1", ignoreCase = true) }
        rows += InfoRow("SPF", spf ?: "Yok — bu alan adı adına sahte e-posta gönderilmesi kolaylaşır")
        try {
            val dmarc = Dns.query("_dmarc.$domain", "TXT").records
                .map { it.data }
                .firstOrNull { it.startsWith("v=DMARC1", ignoreCase = true) }
            if (dmarc == null) {
                rows += InfoRow("DMARC", "Yok")
            } else {
                rows += InfoRow("DMARC", dmarc)
                val policy = Regex("""(?i)(?:^|;)\s*p\s*=\s*(\w+)""").find(dmarc)?.groupValues?.get(1)?.lowercase()
                val desc = when (policy) {
                    "reject" -> "reject — sahte e-postalar reddedilir"
                    "quarantine" -> "quarantine — sahte e-postalar spam'e düşer"
                    "none" -> "none — yalnızca izleme, koruma yok"
                    else -> policy ?: "belirsiz"
                }
                rows += InfoRow("DMARC politikası", desc)
            }
        } catch (e: Exception) {
            rows += InfoRow("DMARC", "Sorgulanamadı: ${describeError(e)}")
        }
        return Section("E-posta güvenliği (SPF / DMARC)", rows)
    }

    fun rdapDomain(domain: String): Section {
        val title = "Kayıt bilgisi (RDAP)"
        val r = Http.get(
            "https://rdap.org/domain/${urlEncode(domain)}",
            headers = mapOf("Accept" to "application/rdap+json, application/json"),
        )
        if (r.code == 404) {
            return Section.error(title, "RDAP kaydı yok: alan adı kayıtlı değil ya da bu uzantı RDAP desteklemiyor. Ham WHOIS'e bakın.")
        }
        if (r.code != 200) return Section.error(title, "RDAP sunucusu HTTP ${r.code} döndürdü")
        return Section(title, parseRdapDomain(Json.parse(r.body), r.finalUrl))
    }

    fun parseRdapDomain(j: Any?, sourceUrl: String?): List<InfoRow> {
        val rows = mutableListOf<InfoRow>()
        j.field("ldhName").text()?.let { rows += InfoRow("Alan adı", it.lowercase(Locale.ROOT)) }

        findEntities(j, "registrar").firstOrNull()?.let { reg ->
            vcard(reg, "fn")?.let { rows += InfoRow("Kayıt firması", it) }
            reg.field("publicIds").items().firstOrNull()?.field("identifier").text()
                ?.let { rows += InfoRow("Firma IANA ID", it) }
        }

        val events = j.field("events").items()
        fun event(action: String) =
            events.firstOrNull { it.field("eventAction").text() == action }?.field("eventDate").text()

        event("registration")?.let { d ->
            rows += InfoRow("Oluşturulma", shortDate(d) ?: d)
            ageText(d)?.let { rows += InfoRow("Alan adı yaşı", it) }
        }
        event("expiration")?.let { d ->
            rows += InfoRow("Bitiş", shortDate(d) ?: d)
            daysUntil(d)?.let { rows += InfoRow("Kalan süre", if (it >= 0) "$it gün" else "Süresi ${-it} gün önce dolmuş") }
        }
        event("last changed")?.let { rows += InfoRow("Son değişiklik", shortDate(it) ?: it) }

        val status = j.field("status").items().mapNotNull { it.text() }
        if (status.isNotEmpty()) rows += InfoRow("Durum", status.joinToString(", "))

        j.field("nameservers").items().mapNotNull { it.field("ldhName").text()?.lowercase(Locale.ROOT) }
            .forEach { rows += InfoRow("Ad sunucusu", it, InternalLinks.network(it)) }

        j.field("secureDNS").field("delegationSigned").bool()?.let {
            rows += InfoRow("DNSSEC", if (it) "Etkin" else "Kapalı")
        }

        findEntities(j, "registrant").firstOrNull()?.let { reg ->
            listOf("fn" to "Kayıt sahibi", "org" to "Kayıt sahibi kurum", "adr" to "Kayıt sahibi adresi", "email" to "Kayıt sahibi e-posta")
                .forEach { (prop, label) -> vcard(reg, prop)?.let { rows += InfoRow(label, it) } }
        }

        findEntities(j, "abuse").firstOrNull()?.let { ab ->
            vcard(ab, "email")?.let { rows += InfoRow("Kötüye kullanım e-postası", it) }
            vcard(ab, "tel")?.let { rows += InfoRow("Kötüye kullanım telefonu", it.removePrefix("tel:")) }
        }

        sourceUrl?.let { u ->
            runCatching { java.net.URI(u).host }.getOrNull()?.let { rows += InfoRow("RDAP sunucusu", it, u) }
        }
        return rows
    }

    fun rawWhois(domain: String): Section {
        val title = "WHOIS (ham)"
        val tld = domain.substringAfterLast('.')
        val iana = Http.whois("whois.iana.org", tld)
        val server = Regex("""(?im)^\s*(?:refer|whois):\s*(\S+)""").find(iana)?.groupValues?.get(1)
            ?: return Section.error(title, "Bu uzantı (.$tld) için WHOIS sunucusu bulunamadı")
        var text = Http.whois(server, domain)
        var used = server
        // .com/.net gibi "ince" kayıtlarda ayrıntı kayıt firmasının sunucusundadır
        val registrarServer = Regex("""(?im)^\s*Registrar WHOIS Server:\s*(\S+)""").find(text)?.groupValues?.get(1)
            ?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')
        if (registrarServer != null && registrarServer.contains('.') && !registrarServer.equals(server, ignoreCase = true)) {
            val more = runCatching { Http.whois(registrarServer, domain) }.getOrNull()
            if (more != null && more.contains("Domain Name", ignoreCase = true)) {
                text = more
                used = registrarServer
            }
        }
        val cleaned = cleanWhois(text)
        return Section(
            title,
            listOf(InfoRow("Sunucu", used), InfoRow("", cleaned.ifBlank { "(boş yanıt)" }.truncate(8000))),
        )
    }

    fun cleanWhois(text: String): String {
        val out = mutableListOf<String>()
        for (raw in text.replace("\r", "").lines()) {
            val line = raw.trimEnd()
            val t = line.trimStart()
            if (t.startsWith("%") || t.startsWith("#")) continue
            if (t.startsWith(">>> Last update", ignoreCase = true)) {
                out += t
                break
            }
            if (t.startsWith("NOTICE:", true) || t.startsWith("TERMS OF USE:", true) ||
                t.startsWith("URL of the ICANN Whois", true) || t.startsWith("For more information on Whois status", true)
            ) break
            if (t.isEmpty() && (out.isEmpty() || out.last().isEmpty())) continue
            out += line
        }
        return out.joinToString("\n").trim()
    }

    // ---------------------------------------------------------------- IP

    fun lookupIp(ip: String, emit: (Section) -> Unit, prefix: String = "") {
        // 1) Konum ve ağ (ipinfo.io, anahtarsız)
        val geoTitle = "${prefix}IP konumu ve ağ"
        try {
            val r = Http.get("https://ipinfo.io/$ip/json", headers = mapOf("Accept" to "application/json"))
            when (r.code) {
                200 -> emit(Section(geoTitle, parseIpInfo(ip, Json.parse(r.body))))
                429 -> emit(Section.error(geoTitle, "ipinfo.io sorgu sınırına ulaşıldı, biraz sonra tekrar deneyin"))
                else -> emit(Section.error(geoTitle, "ipinfo.io HTTP ${r.code}"))
            }
        } catch (e: Exception) {
            emit(Section.fromException(geoTitle, e))
        }

        // 2) Ters DNS
        val ptrTitle = "${prefix}Ters DNS (PTR)"
        try {
            val ptrs = Dns.query(IpUtil.ptrName(ip), "PTR").records.filter { it.type == "PTR" }
            emit(
                Section(
                    ptrTitle,
                    if (ptrs.isEmpty()) listOf(InfoRow("Sonuç", "PTR kaydı yok"))
                    else ptrs.map { InfoRow("PTR", it.data, InternalLinks.network(it.data)) },
                ),
            )
        } catch (e: Exception) {
            emit(Section.fromException(ptrTitle, e))
        }

        // 3) RDAP (IP bloğu sahibi)
        val rdapTitle = "${prefix}IP bloğu sahibi (RDAP)"
        try {
            val r = Http.get("https://rdap.org/ip/$ip", headers = mapOf("Accept" to "application/rdap+json, application/json"))
            if (r.code == 200) emit(Section(rdapTitle, parseRdapIp(Json.parse(r.body))))
            else emit(Section.error(rdapTitle, "RDAP HTTP ${r.code}"))
        } catch (e: Exception) {
            emit(Section.fromException(rdapTitle, e))
        }

        if (prefix.isEmpty()) {
            emit(
                Section(
                    "Harici araçlar",
                    listOf(
                        InfoRow("Shodan", "Açık portlar ve servisler", "https://www.shodan.io/host/$ip"),
                        InfoRow("AbuseIPDB", "Kötüye kullanım bildirimleri", "https://www.abuseipdb.com/check/$ip"),
                        InfoRow("VirusTotal", "Güvenlik raporu", "https://www.virustotal.com/gui/ip-address/$ip"),
                        InfoRow("Hurricane Electric BGP", "Yönlendirme ve ASN", "https://bgp.he.net/ip/$ip"),
                    ),
                ),
            )
        }
    }

    fun parseIpInfo(ip: String, j: Any?): List<InfoRow> {
        val rows = mutableListOf(InfoRow("IP", ip))
        if (j.field("bogon").bool() == true) {
            rows += InfoRow("Tür", "Özel / ayrılmış adres — internette yönlendirilmez")
            return rows
        }
        j.field("hostname").text()?.let { rows += InfoRow("Ana makine adı", it, InternalLinks.network(it)) }
        val place = listOfNotNull(
            j.field("city").text(),
            j.field("region").text(),
            j.field("country").text()?.let { countryName(it) },
        ).filter { it.isNotBlank() }.joinToString(", ")
        if (place.isNotBlank()) rows += InfoRow("Konum (yaklaşık, şehir düzeyi)", place)
        j.field("loc").text()?.let { loc ->
            val (lat, lon) = loc.split(',').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
            rows += InfoRow("Koordinat (yaklaşık)", loc, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=10/$lat/$lon")
        }
        j.field("org").text()?.let { org ->
            val asn = Regex("""^AS\d+""").find(org)?.value
            rows += InfoRow("Ağ / ASN", org, asn?.let { "https://bgp.he.net/$it" })
        }
        j.field("postal").text()?.let { rows += InfoRow("Posta kodu", it) }
        j.field("timezone").text()?.let { rows += InfoRow("Saat dilimi", it) }
        if (j.field("anycast").bool() == true) rows += InfoRow("Anycast", "Evet — aynı IP birçok ülkede; konum yanıltıcı olabilir")
        return rows
    }

    fun parseRdapIp(j: Any?): List<InfoRow> {
        val rows = mutableListOf<InfoRow>()
        j.field("name").text()?.let { rows += InfoRow("Ağ adı", it) }
        j.field("handle").text()?.let { rows += InfoRow("Kayıt kimliği", it) }
        val start = j.field("startAddress").text()
        val end = j.field("endAddress").text()
        if (start != null && end != null) rows += InfoRow("Adres aralığı", "$start – $end")
        j.field("cidr0_cidrs").items().mapNotNull { c ->
            val p = c.field("v4prefix").text() ?: c.field("v6prefix").text()
            val l = c.field("length").text()
            if (p != null && l != null) "$p/$l" else null
        }.takeIf { it.isNotEmpty() }?.let { rows += InfoRow("CIDR", it.joinToString(", ")) }
        j.field("type").text()?.let { rows += InfoRow("Tahsis türü", it) }
        j.field("country").text()?.let { rows += InfoRow("Kayıt ülkesi", countryName(it)) }

        val holder = findEntities(j, "registrant").firstOrNull() ?: findEntities(j, "administrative").firstOrNull()
        holder?.let { h -> vcard(h, "fn")?.let { rows += InfoRow("Sahip kurum", it) } }

        findEntities(j, "abuse").firstOrNull()?.let { ab ->
            vcard(ab, "email")?.let { rows += InfoRow("Kötüye kullanım e-postası", it) }
        }

        j.field("port43").text()?.let { w ->
            val rir = when {
                w.contains("ripe") -> "RIPE NCC (Avrupa, Orta Doğu)"
                w.contains("arin") -> "ARIN (Kuzey Amerika)"
                w.contains("apnic") -> "APNIC (Asya-Pasifik)"
                w.contains("lacnic") -> "LACNIC (Latin Amerika)"
                w.contains("afrinic") -> "AFRINIC (Afrika)"
                else -> w
            }
            rows += InfoRow("Bölgesel kayıt kuruluşu", rir)
        }
        val events = j.field("events").items()
        events.firstOrNull { it.field("eventAction").text() == "registration" }?.field("eventDate").text()
            ?.let { rows += InfoRow("Tahsis tarihi", shortDate(it) ?: it) }
        events.firstOrNull { it.field("eventAction").text() == "last changed" }?.field("eventDate").text()
            ?.let { rows += InfoRow("Son değişiklik", shortDate(it) ?: it) }
        return rows
    }

    // ---------------------------------------------------------------- crt.sh

    fun subdomains(input: String): Section {
        val title = "Alt alan adları (sertifika kayıtları)"
        val target = normalizeTarget(input)
        if (!isValidDomain(target)) return Section.error(title, "Geçerli bir alan adı girin")
        val domain = guessRegistrable(target)
        val r = Http.get(
            "https://crt.sh/?q=${urlEncode("%.$domain")}&output=json",
            headers = mapOf("Accept" to "application/json"),
            timeoutMs = 45_000,
            maxBytes = 8_000_000,
        )
        if (r.code != 200) return Section.error(title, "crt.sh HTTP ${r.code} (sunucu yoğun olabilir, tekrar deneyin)")
        val names = parseCrtShNames(r.body, domain)
        if (names.isEmpty()) return Section(title, listOf(InfoRow("Sonuç", "Kayıt bulunamadı")))
        val rows = mutableListOf(InfoRow("Toplam", "${names.size} benzersiz ad" + if (names.size > 400) " (ilk 400 gösteriliyor)" else ""))
        names.take(400).forEach { rows += InfoRow("", it, InternalLinks.network(it)) }
        return Section(title, rows)
    }

    /** JSON kesik gelse bile çalışsın diye düzenli ifadeyle çıkarılır. */
    fun parseCrtShNames(body: String, domain: String): List<String> =
        Regex(""""name_value"\s*:\s*"([^"]*)"""").findAll(body)
            .flatMap { it.groupValues[1].split("\\n").asSequence() }
            .map { it.trim().lowercase(Locale.ROOT).removePrefix("*.") }
            .filter { it == domain || it.endsWith(".$domain") }
            .toSortedSet()
            .toList()

    // ---------------------------------------------------------------- yardımcılar

    fun findEntities(root: Any?, role: String, depth: Int = 0): List<Any?> {
        if (depth > 5) return emptyList()
        val out = mutableListOf<Any?>()
        for (e in root.field("entities").items()) {
            if (e.field("roles").items().any { it.text() == role }) out.add(e)
            out.addAll(findEntities(e, role, depth + 1))
        }
        return out
    }

    /** jCard (RFC 7095) içinden bir alanı okur: ["vcard", [["fn", {}, "text", "Ad"], ...]] */
    fun vcard(entity: Any?, prop: String): String? {
        val card = entity.field("vcardArray").items()
        if (card.size < 2) return null
        for (p in card[1].items()) {
            val a = p.items()
            if (a.size >= 4 && a[0].text().equals(prop, ignoreCase = true)) {
                val v = a[3]
                val s = if (v is List<*>) {
                    v.flatMap { if (it is List<*>) it else listOf(it) }
                        .mapNotNull { it.text()?.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
                } else {
                    v.text()?.trim()
                }
                val cc = a[1].field("cc").text()
                val result = listOfNotNull(s?.takeIf { it.isNotEmpty() }, cc).joinToString(" ")
                if (result.isNotBlank()) return result
            }
        }
        return null
    }

    fun countryName(code: String): String {
        val cc = code.uppercase(Locale.ROOT)
        val name = Locale("", cc).getDisplayCountry(TR)
        return if (name.isNullOrBlank() || name.equals(cc, ignoreCase = true)) cc else "$name ($cc)"
    }

    private fun parseDate(iso: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(iso) }.getOrNull()

    fun ageText(iso: String, now: OffsetDateTime = OffsetDateTime.now()): String? {
        val d = parseDate(iso) ?: return null
        val days = ChronoUnit.DAYS.between(d, now)
        if (days < 0) return null
        if (days < 90) return "$days gün — yeni kayıt (oltalama/dolandırıcılık sitelerinde sık görülür)"
        val years = days / 365
        val months = (days % 365) / 30
        return if (years > 0) "$years yıl $months ay" else "$months ay"
    }

    fun daysUntil(iso: String, now: OffsetDateTime = OffsetDateTime.now()): Long? {
        val d = parseDate(iso) ?: return null
        return ChronoUnit.DAYS.between(now, d)
    }
}
