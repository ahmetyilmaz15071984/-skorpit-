package com.osintkit.app.core

/**
 * Küçük, bağımlılıksız JSON ayrıştırıcı.
 * Nesneler -> Map<String, Any?>, diziler -> List<Any?>, sayılar -> Long veya Double.
 */
object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.skipWs()
        if (p.i != text.length) throw IllegalArgumentException("JSON sonunda beklenmeyen veri (konum ${p.i})")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(): Any? {
            skipWs()
            if (i >= s.length) throw IllegalArgumentException("Beklenmeyen JSON sonu")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("Geçersiz JSON (konum $i)")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') throw IllegalArgumentException("Anahtar bekleniyordu (konum $i)")
                val k = str()
                skipWs()
                if (i >= s.length || s[i] != ':') throw IllegalArgumentException("':' bekleniyordu (konum $i)")
                i++
                m[k] = value()
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("Beklenmeyen JSON sonu")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw IllegalArgumentException("',' veya '}' bekleniyordu (konum $i)")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("Beklenmeyen JSON sonu")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw IllegalArgumentException("',' veya ']' bekleniyordu (konum $i)")
                }
            }
        }

        private fun str(): String {
            i++ // "
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("Kapanmamış metin")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("Kapanmamış kaçış")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("Eksik \\u kaçışı")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("Geçersiz kaçış: \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '-' || s[i] == '+')) i++
            val t = s.substring(start, i)
            if (t.isEmpty() || t == "-") throw IllegalArgumentException("Geçersiz JSON değeri (konum $start)")
            return if (t.any { it == '.' || it == 'e' || it == 'E' }) t.toDouble() else (t.toLongOrNull() ?: t.toDouble())
        }
    }
}

// --- Güvenli erişim yardımcıları ---

fun Any?.field(key: String): Any? = (this as? Map<*, *>)?.get(key)

fun Any?.items(): List<Any?> = (this as? List<*>)?.toList() ?: emptyList()

fun Any?.text(): String? = when (this) {
    null -> null
    is String -> this
    is Long, is Int, is Boolean -> this.toString()
    is Double -> if (this % 1.0 == 0.0 && kotlin.math.abs(this) < 1e15) this.toLong().toString() else this.toString()
    else -> null
}

fun Any?.long(): Long? = when (this) {
    is Long -> this
    is Int -> this.toLong()
    is Double -> this.toLong()
    is String -> this.toLongOrNull()
    else -> null
}

fun Any?.bool(): Boolean? = when (this) {
    is Boolean -> this
    is String -> this.toBooleanStrictOrNull()
    else -> null
}
