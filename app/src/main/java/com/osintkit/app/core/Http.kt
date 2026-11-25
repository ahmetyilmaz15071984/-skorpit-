package com.osintkit.app.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Mobile Safari/537.36"

    class Response(
        val code: Int,
        val body: String,
        val requestUrl: String,
        val finalUrl: String,
        val redirected: Boolean,
    )

    /**
     * Basit GET. Yönlendirmeler elle izlenir (http -> https ve farklı alan adları dahil),
     * böylece son adres ve yönlendirme olup olmadığı bilinir.
     */
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 12_000,
        maxBytes: Int = 1_000_000,
        followRedirects: Boolean = true,
        maxRedirects: Int = 6,
    ): Response {
        var current = url
        var hops = 0
        while (true) {
            val conn = URI(current).toURL().openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,tr;q=0.8")
                for ((k, v) in headers) conn.setRequestProperty(k, v)

                val code = conn.responseCode
                if (code in 300..399 && followRedirects && hops < maxRedirects) {
                    val location = conn.getHeaderField("Location")
                    val next = location?.let { runCatching { URI(current).resolve(it.trim()).toString() }.getOrNull() }
                    if (next != null) {
                        current = next
                        hops++
                        continue
                    }
                }
                val stream = try {
                    if (code >= 400) conn.errorStream else conn.inputStream
                } catch (e: IOException) {
                    conn.errorStream
                }
                val body = stream?.use { readLimited(it, maxBytes) } ?: ""
                return Response(code, body, url, current, hops > 0)
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Klasik WHOIS (TCP 43) sorgusu. */
    fun whois(server: String, query: String, timeoutMs: Int = 10_000): String {
        Socket().use { s ->
            s.connect(InetSocketAddress(server, 43), timeoutMs)
            s.soTimeout = timeoutMs
            val out = s.getOutputStream()
            out.write("$query\r\n".toByteArray(Charsets.UTF_8))
            out.flush()
            return readLimited(s.getInputStream(), 300_000)
        }
    }
}
