package com.athletebridge.android.crossplatform

import com.athletebridge.android.data.model.CoachTier
import com.athletebridge.android.data.model.TournamentParticipantInfo
import com.athletebridge.android.data.repository.TournamentRepository
import com.athletebridge.android.util.PaymentLinks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The shared contract (cross-platform/contract.json), found by walking up from the module directory. */
object Contract {
    val dir: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "cross-platform") }
            .firstOrNull { File(it, "contract.json").isFile }
            ?: error("cross-platform/contract.json not found above ${System.getProperty("user.dir")}")
    }

    @Suppress("UNCHECKED_CAST")
    val json: Map<String, Any?> by lazy { Json.parse(File(dir, "contract.json").readText()) as Map<String, Any?> }
}

/**
 * Logic both apps implement separately must give the same answers. The expected answers live in
 * contract.json ("vectors"), and the iOS suite checks its own implementations against the same table.
 */
class SharedLogicParityTest {

    private fun cases(name: String): List<Map<*, *>> =
        ((Contract.json["vectors"] as Map<*, *>)[name] as List<*>).map { it as Map<*, *> }

    private fun strings(v: Any?): List<String> = (v as List<*>).map { it as String }

    @Test
    fun `payment links match the contract`() {
        val wrong = cases("paymentLinks").mapNotNull { c ->
            val platform = c["platform"] as String
            val handle = c["handle"] as String
            val expected = c["url"] as String?
            val actual = PaymentLinks.deepLink(platform, handle)
            if (actual == expected) null else "$platform \"$handle\": expected $expected, got $actual"
        }
        assertEquals("Payment links that differ from the contract (and so from iOS)", emptyList<String>(), wrong)
    }

    @Test
    fun `effective coach tier matches the contract`() {
        val wrong = cases("effectiveTier").mapNotNull { c ->
            val sub = c["subscriptionTier"] as String?
            val life = c["lifetimeTier"] as String?
            val actual = CoachTier.effective(sub, life).value
            if (actual == c["tier"]) null else "subscription=$sub lifetime=$life: expected ${c["tier"]}, got $actual"
        }
        assertEquals("Tier rules that differ from the contract (and so from iOS)", emptyList<String>(), wrong)
    }

    @Test
    fun `tournament partner matching matches the contract`() {
        val wrong = cases("partnersCompatible").mapNotNull { c ->
            val a = c["a"] as Map<*, *>
            val b = c["b"] as Map<*, *>
            val other = TournamentParticipantInfo(
                gender = b["gender"] as String, events = strings(b["events"]), skillLevels = strings(b["skillLevels"])
            )
            val actual = TournamentRepository.partnersCompatible(
                a["gender"] as String, strings(a["events"]).toSet(), strings(a["skillLevels"]).toSet(), other
            )
            if (actual == c["compatible"]) null else "$a with $b: expected ${c["compatible"]}, got $actual"
        }
        assertEquals("Partner matches that differ from the contract (and so from iOS)", emptyList<String>(), wrong)
    }
}
