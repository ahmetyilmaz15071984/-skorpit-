package com.osintkit.app.core

import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest

fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

fun hashHex(algorithm: String, s: String): String =
    MessageDigest.getInstance(algorithm)
        .digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** "2024-05-01T10:00:00Z" -> "2024-05-01" */
fun shortDate(iso: String?): String? =
    iso?.takeIf { it.isNotBlank() }?.let { if (it.length >= 10) it.substring(0, 10) else it }

fun readLimited(input: InputStream, maxBytes: Int): String {
    val buf = ByteArray(16 * 1024)
    val out = java.io.ByteArrayOutputStream()
    while (out.size() < maxBytes) {
        val n = input.read(buf, 0, minOf(buf.size, maxBytes - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toString("UTF-8")
}

fun String.truncate(max: Int): String = if (length <= max) this else take(max) + "…"

object IpUtil {
    private val V4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val V6_CHARS = Regex("""^[0-9a-fA-F:.]+$""")

    fun isIPv4(s: String): Boolean {
        val m = V4.matchEntire(s) ?: return false
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    fun isIPv6(s: String): Boolean {
        if (!s.contains(':') || !V6_CHARS.matches(s)) return false
        if (s.count { it == ':' } < 2 || s.contains(":::")) return false
        return try {
            java.net.InetAddress.getByName(s) is java.net.Inet6Address
        } catch (e: Exception) {
            false
        }
    }

    fun isIp(s: String) = isIPv4(s) || isIPv6(s)

    /** Ters DNS (PTR) sorgusu için ad: 8.8.4.4 -> 4.4.8.8.in-addr.arpa */
    fun ptrName(ip: String): String {
        if (isIPv4(ip)) return ip.split('.').reversed().joinToString(".") + ".in-addr.arpa"
        val bytes = java.net.InetAddress.getByName(ip).address
        val nibbles = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xff
            nibbles.append("%x".format(v shr 4)).append("%x".format(v and 0xf))
        }
        return nibbles.reversed().toString().toCharArray().joinToString(".") + ".ip6.arpa"
    }
}
