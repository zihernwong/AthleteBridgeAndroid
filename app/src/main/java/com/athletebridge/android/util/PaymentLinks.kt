package com.athletebridge.android.util

/**
 * "Open in app" links for a coach's payment handles. Handles are typed by hand, so accept the
 * formats people actually use — "@name", "name", "paypal.me/name", "$cashtag", or a full profile URL —
 * and reduce them to the bare handle before building the link.
 */
object PaymentLinks {

    fun deepLink(platform: String, handle: String): String? =
        when (platform.lowercase().replace(" ", "")) {
            "venmo" -> bareHandle(handle, "venmo.com/u/", "venmo.com/")
                ?.removePrefix("@")?.takeIf { it.isUsername() }
                ?.let { "https://venmo.com/u/$it" }
            // An email (what many coaches save for PayPal) has no paypal.me page — no link rather than a dead one
            "paypal" -> bareHandle(handle, "paypal.me/", "paypal.com/paypalme/")
                ?.removePrefix("@")?.takeIf { it.isUsername() }
                ?.let { "https://paypal.me/$it" }
            "cashapp" -> bareHandle(handle, "cash.app/")
                ?.removePrefix("$")?.takeIf { it.isUsername() }
                ?.let { "https://cash.app/\$$it" }
            else -> null
        }

    /** Letters, digits, '-', '_' and '.', which covers Venmo, PayPal.Me and Cash App names (not emails). */
    private fun String.isUsername(): Boolean =
        isNotEmpty() && all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' }

    /** Strip whitespace, scheme, "www."/"account." and a known profile prefix, leaving just the handle. */
    private fun bareHandle(handle: String, vararg profilePrefixes: String): String? {
        var s = handle.trim()
        for (scheme in listOf("https://", "http://")) {
            if (s.startsWith(scheme, ignoreCase = true)) s = s.substring(scheme.length)
        }
        for (sub in listOf("www.", "account.")) {
            if (s.startsWith(sub, ignoreCase = true)) s = s.substring(sub.length)
        }
        for (prefix in profilePrefixes) {
            if (s.startsWith(prefix, ignoreCase = true)) {
                s = s.substring(prefix.length)
                break
            }
        }
        return s.substringBefore('?').trim('/').trim().takeIf { it.isNotEmpty() }
    }
}
