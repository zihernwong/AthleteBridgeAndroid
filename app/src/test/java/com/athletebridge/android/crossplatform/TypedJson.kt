package com.athletebridge.android.crossplatform

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * The fixture format shared with the iOS tests (cross-platform/README.md): plain JSON for strings, booleans,
 * null, integers, maps and arrays, and one-key tags for the Firestore types JSON can't tell apart —
 * {"$double": 45.0}, {"$timestamp": "2031-03-12T17:00:00Z"}, {"$ref": "coaches/{{coach}}"}, {"$geo": [lat, lng]},
 * {"$bytes": "base64"}. Integer vs double matters: `as? Long` on Android and `as? Int` on iOS both miss the other.
 */
object TypedJson {

    /** A Firestore value as the Android SDK hands it to app code → typed JSON. */
    fun encode(value: Any?): Any? = when (value) {
        null -> null
        is String, is Boolean -> value
        is Int, is Long, is Short, is Byte -> (value as Number).toLong()
        is Double, is Float -> mapOf("\$double" to (value as Number).toDouble())
        is Timestamp -> mapOf("\$timestamp" to iso(value))
        is Date -> mapOf("\$timestamp" to iso(Timestamp(value)))
        is DocumentReference -> mapOf("\$ref" to value.path)
        is GeoPoint -> mapOf("\$geo" to listOf(value.latitude, value.longitude))
        is Blob -> mapOf("\$bytes" to Base64.getEncoder().encodeToString(value.toBytes()))
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to encode(v) }
        is List<*> -> value.map { encode(it) }
        is Array<*> -> value.map { encode(it) }
        else -> error("No typed-JSON encoding for ${value.javaClass.name} ($value)")
    }

    /** Typed JSON → the values the Android SDK returns from DocumentSnapshot.getData(). */
    fun decode(json: Any?, ref: (String) -> DocumentReference): Any? = when (json) {
        null, is String, is Boolean -> json
        is Long -> json
        is Double -> error("Untagged double $json in a fixture: doubles must be written as {\"\$double\": x}")
        is List<*> -> json.map { decode(it, ref) }
        is Map<*, *> -> {
            val tag = json.keys.singleOrNull() as? String
            val v = json.values.singleOrNull()
            when (tag) {
                "\$double" -> (v as Number).toDouble()
                "\$timestamp" -> Timestamp(Date.from(Instant.parse(v as String)))
                "\$ref" -> ref(v as String)
                "\$geo" -> (v as List<*>).let { GeoPoint((it[0] as Number).toDouble(), (it[1] as Number).toDouble()) }
                "\$bytes" -> Blob.fromBytes(Base64.getDecoder().decode(v as String))
                else -> json.entries.associateTo(linkedMapOf<String, Any?>()) { (k, x) -> k as String to decode(x, ref) }
            }
        }
        else -> error("Unexpected JSON value ${json.javaClass}")
    }

    fun iso(ts: Timestamp): String = Instant.ofEpochSecond(ts.seconds, ts.nanoseconds.toLong()).toString()

    fun isTagged(m: Map<*, *>, tag: String) = m.size == 1 && m.containsKey(tag)
}

/** Just enough JSON for the fixtures: parse to Map/List/String/Long/Double/Boolean/null, print with sorted keys. */
object Json {

    fun parse(text: String): Any? = Parser(text).run { val v = value(); skipWs(); require(pos == text.length) { "Trailing JSON at $pos" }; v }

    fun print(value: Any?): String = StringBuilder().also { write(it, value, 0) }.append('\n').toString()

    /** One line, sorted keys — for messages. */
    fun compact(value: Any?): String = when (value) {
        is Map<*, *> -> value.entries.sortedBy { it.key as String }
            .joinToString(", ", "{", "}") { (k, v) -> "${compact(k)}: ${compact(v)}" }
        is List<*> -> value.joinToString(", ", "[", "]") { compact(it) }
        else -> StringBuilder().also { write(it, value, 0) }.toString()
    }

    private fun write(sb: StringBuilder, v: Any?, indent: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Long, is Int -> sb.append(v)
            is Double -> sb.append(if (v.isFinite()) v.toString() else error("Non-finite double $v"))
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append("{\n")
                v.entries.sortedBy { it.key as String }.forEachIndexed { i, (k, x) ->
                    pad(sb, indent + 1); quote(sb, k as String); sb.append(": "); write(sb, x, indent + 1)
                    sb.append(if (i < v.size - 1) ",\n" else "\n")
                }
                pad(sb, indent); sb.append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                // Short scalar lists stay on one line so fixtures read like the documents they describe
                if (v.all { it == null || it is String || it is Number || it is Boolean } && v.size <= 8) {
                    sb.append('[')
                    v.forEachIndexed { i, x -> if (i > 0) sb.append(", "); write(sb, x, indent) }
                    sb.append(']')
                    return
                }
                sb.append("[\n")
                v.forEachIndexed { i, x ->
                    pad(sb, indent + 1); write(sb, x, indent + 1)
                    sb.append(if (i < v.size - 1) ",\n" else "\n")
                }
                pad(sb, indent); sb.append(']')
            }
            else -> error("Can't print ${v.javaClass}")
        }
    }

    private fun pad(sb: StringBuilder, n: Int) { repeat(n) { sb.append("  ") } }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }

        fun value(): Any? {
            skipWs()
            require(pos < s.length) { "Unexpected end of JSON" }
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("Unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, pos)) { "Bad literal at $pos" }
            pos += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val out = linkedMapOf<String, Any?>()
            pos++
            skipWs()
            if (s[pos] == '}') { pos++; return out }
            while (true) {
                skipWs()
                val k = str()
                skipWs(); require(s[pos] == ':') { "Expected : at $pos" }; pos++
                out[k] = value()
                skipWs()
                when (s[pos++]) { ',' -> continue; '}' -> return out; else -> error("Expected , or } at ${pos - 1}") }
            }
        }

        private fun arr(): List<Any?> {
            val out = mutableListOf<Any?>()
            pos++
            skipWs()
            if (s[pos] == ']') { pos++; return out }
            while (true) {
                out += value()
                skipWs()
                when (s[pos++]) { ',' -> continue; ']' -> return out; else -> error("Expected , or ] at ${pos - 1}") }
            }
        }

        private fun str(): String {
            require(s[pos] == '"') { "Expected string at $pos" }
            pos++
            val sb = StringBuilder()
            while (true) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[pos++]) {
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val text = s.substring(start, pos)
            return if (text.any { it in ".eE" }) text.toDouble() else text.toLong()
        }
    }
}
