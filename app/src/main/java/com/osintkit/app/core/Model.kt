package com.osintkit.app.core

/** Ekranda bir satır: etiket, değer ve isteğe bağlı bağlantı. */
data class InfoRow(
    val label: String,
    val value: String,
    val link: String? = null,
)

/** Başlıklı bir sonuç kartı. */
data class Section(
    val title: String,
    val rows: List<InfoRow>,
    val isError: Boolean = false,
) {
    companion object {
        fun error(title: String, message: String) =
            Section(title, listOf(InfoRow("Hata", message)), isError = true)

        fun fromException(title: String, e: Throwable): Section =
            error(title, describeError(e))
    }
}

fun describeError(e: Throwable): String = when (e) {
    is java.net.UnknownHostException -> "Sunucu bulunamadı veya internet bağlantısı yok"
    is java.net.SocketTimeoutException -> "Zaman aşımı"
    is java.net.ConnectException -> "Bağlantı kurulamadı"
    is javax.net.ssl.SSLException -> "Güvenli bağlantı hatası (SSL)"
    else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
}

/** Sonuçları paylaşılabilir düz metin rapora çevirir. */
fun List<Section>.toReport(header: String): String = buildString {
    appendLine(header)
    appendLine()
    for (s in this@toReport) {
        appendLine("## ${s.title}")
        for (r in s.rows) {
            append("- ")
            if (r.label.isNotEmpty()) append("${r.label}: ")
            append(r.value)
            if (r.link != null && !InternalLinks.isInternal(r.link) && r.link != r.value) append("  <${r.link}>")
            appendLine()
        }
        appendLine()
    }
}

/**
 * Uygulama içi "pivot" bağlantıları: bir sonuçtan diğer modüle geçmek için
 * (ör. e-postanın alan adını Ağ sekmesinde açmak).
 */
object InternalLinks {
    private const val SCHEME = "osint://"

    const val USERNAME = "username"
    const val NETWORK = "network"
    const val EMAIL = "email"
    const val PHONE = "phone"

    data class Target(val module: String, val query: String)

    fun username(q: String) = "$SCHEME$USERNAME/${urlEncode(q)}"
    fun network(q: String) = "$SCHEME$NETWORK/${urlEncode(q)}"
    fun email(q: String) = "$SCHEME$EMAIL/${urlEncode(q)}"
    fun phone(q: String) = "$SCHEME$PHONE/${urlEncode(q)}"

    fun isInternal(link: String) = link.startsWith(SCHEME)

    fun parse(link: String): Target? {
        if (!isInternal(link)) return null
        val rest = link.removePrefix(SCHEME)
        val slash = rest.indexOf('/')
        if (slash <= 0) return null
        val module = rest.substring(0, slash)
        val query = java.net.URLDecoder.decode(rest.substring(slash + 1), "UTF-8")
        return Target(module, query)
    }
}
