package com.osintkit.app.core

import java.io.IOException

/** DNS-over-HTTPS (Google, yedek: Cloudflare) ile DNS sorguları. */
object Dns {
    data class Record(val name: String, val type: String, val ttl: Long, val data: String)

    /** status: 0 = NOERROR, 2 = SERVFAIL, 3 = NXDOMAIN (alan adı yok) */
    data class Answer(val status: Int, val records: List<Record>) {
        val nxDomain get() = status == 3
    }

    private val TYPE_NAMES = mapOf(
        1 to "A", 2 to "NS", 5 to "CNAME", 6 to "SOA", 12 to "PTR", 15 to "MX", 16 to "TXT",
        28 to "AAAA", 33 to "SRV", 43 to "DS", 46 to "RRSIG", 48 to "DNSKEY", 64 to "SVCB",
        65 to "HTTPS", 257 to "CAA",
    )

    fun query(name: String, type: String): Answer {
        val endpoints = listOf(
            "https://dns.google/resolve?name=${urlEncode(name)}&type=$type",
            "https://cloudflare-dns.com/dns-query?name=${urlEncode(name)}&type=$type",
        )
        var lastError: Exception? = null
        for (url in endpoints) {
            try {
                val r = Http.get(url, headers = mapOf("Accept" to "application/dns-json"), timeoutMs = 8_000)
                if (r.code != 200) {
                    lastError = IOException("DNS sunucusu HTTP ${r.code} döndürdü")
                    continue
                }
                return parseAnswer(r.body, name)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("DNS sorgusu başarısız")
    }

    fun parseAnswer(body: String, fallbackName: String): Answer {
        val json = Json.parse(body)
        val status = json.field("Status").long()?.toInt() ?: -1
        val records = json.field("Answer").items().map { a ->
            val typeNum = a.field("type").long()?.toInt()
            val type = TYPE_NAMES[typeNum] ?: typeNum?.toString() ?: "?"
            Record(
                name = (a.field("name").text() ?: fallbackName).trimEnd('.'),
                type = type,
                ttl = a.field("TTL").long() ?: 0,
                data = cleanData(type, a.field("data").text() ?: ""),
            )
        }
        return Answer(status, records)
    }

    /** Cloudflare TXT kayıtlarını tırnaklı döndürür; Google döndürmez. İkisini eşitle. */
    fun cleanData(type: String, data: String): String {
        var d = data.trim()
        if (type == "TXT" && d.length >= 2 && d.startsWith("\"") && d.endsWith("\"")) {
            d = d.substring(1, d.length - 1).replace("\" \"", "")
        }
        if (type in setOf("CNAME", "NS", "PTR", "MX")) d = d.trimEnd('.')
        return d
    }
}
