package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.ReviewItem
import com.athletebridge.android.data.model.TournamentParticipantInfo
import com.athletebridge.android.testutil.FakeFirestore
import com.athletebridge.android.testutil.ts
import com.google.firebase.Timestamp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ReviewRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = ReviewRepository(fake.instance)

    private fun review(id: String, coachId: String?, rating: Any, at: Long = 1_000) = fake.seed(
        "reviews/$id",
        buildMap {
            coachId?.let { put("coachId", it) }
            put("rating", rating)
            put("reviewerId", "u-$id")
            put("comment", "comment $id")
            put("createdAt", Timestamp(Date(at)))
        }
    )

    @Test
    fun `rating summaries accept numbers and strings and ignore out-of-range values`() = runBlocking {
        review("r1", "c1", 5L)
        review("r2", "c1", "4")
        review("r3", "c1", 9L)
        review("r4", "c2", 3.0)
        review("r5", null, 5L)
        val summaries = repo.fetchRatingSummaries()
        assertEquals(4.5, summaries.getValue("c1").average, 0.0)
        assertEquals(2, summaries.getValue("c1").count)
        assertEquals(3.0, summaries.getValue("c2").average, 0.0)
        assertEquals(setOf("c1", "c2"), summaries.keys)
    }

    @Test
    fun `a coach's review list is newest first`() = runBlocking {
        review("old", "c1", 4L, at = 1_000)
        review("new", "c1", 5L, at = 9_000)
        assertEquals(listOf("new", "old"), repo.fetchReviewsForCoach("c1").map { it.id })
    }

    @Test
    fun `the review list shows the same stars the rating summary counted`() = runBlocking {
        // fetchRatingSummaries already handles ratings stored as strings; the list must agree.
        review("r2", "c1", "4")
        assertEquals(4, repo.fetchReviewsForCoach("c1").single().rating)
    }

    @Test
    fun `a client's own reviews are newest first with text ratings parsed`() = runBlocking {
        fake.seed("reviews/a", mapOf("reviewerId" to "u1", "coachId" to "c1", "rating" to "5", "createdAt" to Timestamp(Date(1_000))))
        fake.seed("reviews/b", mapOf("reviewerId" to "u1", "coachId" to "c2", "rating" to 3L, "createdAt" to Timestamp(Date(9_000))))
        fake.seed("reviews/c", mapOf("reviewerId" to "someone", "coachId" to "c1", "rating" to 4L, "createdAt" to Timestamp(Date(5_000))))
        val mine = repo.fetchReviewsByReviewer("u1")
        assertEquals(listOf("b", "a"), mine.map { it.id })
        assertEquals(5, mine.last().rating)
    }

    @Test
    fun `submitted reviews round-trip`() = runBlocking {
        repo.submitReview(ReviewItem(reviewerId = "u1", reviewerName = "Alex", coachId = "c1", rating = 5, comment = "Great drills")).getOrThrow()
        val r = repo.fetchReviewsForCoach("c1").single()
        assertEquals(5, r.rating)
        assertEquals("Great drills", r.comment)
    }
}

class TournamentRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = TournamentRepository(fake.instance)

    private fun info(gender: String, events: List<String>, skills: List<String> = emptyList()) =
        TournamentParticipantInfo(gender, events, skills)

    private fun compatible(gender: String, events: Set<String>, skills: Set<String>, other: TournamentParticipantInfo) =
        TournamentRepository.partnersCompatible(gender, events, skills, other)

    @Test
    fun `partner compatibility follows the event gender rules`() {
        val md = setOf("Men's Doubles")
        val wd = setOf("Women's Doubles")
        val xd = setOf("Mixed Doubles")
        assertTrue(compatible("Male", md, emptySet(), info("Male", md.toList())))
        assertFalse(compatible("Male", md, emptySet(), info("Female", md.toList())))
        assertTrue(compatible("Female", wd, emptySet(), info("Female", wd.toList())))
        assertTrue(compatible("Male", xd, emptySet(), info("Female", xd.toList())))
        assertFalse(compatible("Female", xd, emptySet(), info("Female", xd.toList())))
        assertTrue(compatible("Female", setOf("Open Doubles"), emptySet(), info("Male", listOf("Open Doubles"))))
    }

    @Test
    fun `partners need a shared event and overlapping skill levels`() {
        val xd = setOf("Mixed Doubles")
        assertFalse(compatible("Male", setOf("Men's Doubles"), emptySet(), info("Female", xd.toList())))
        assertFalse(compatible("Male", xd, setOf("A"), info("Female", xd.toList(), listOf("C"))))
        assertTrue(compatible("Male", xd, setOf("A", "B"), info("Female", xd.toList(), listOf("B"))))
        assertTrue(compatible("Male", xd, emptySet(), info("Female", xd.toList(), listOf("C")))) // empty = any level
    }

    private fun seedTournament() = fake.seed(
        "tournaments/t1", mapOf(
            "name" to "MN Open", "startDate" to ts(Date(1_900_000_000_000L)), "endDate" to ts(Date(1_900_100_000_000L)),
            "location" to "St. Paul",
            "participants" to mapOf(
                "p1" to mapOf("gender" to "Female", "events" to listOf("Mixed Doubles"), "skillLevels" to listOf("B")),
                "p2" to mapOf("gender" to "Male", "events" to listOf("Mixed Doubles"), "skillLevels" to listOf("B"))
            )
        )
    )

    @Test
    fun `joining notifies only compatible partner seekers`() = runBlocking {
        seedTournament()
        repo.joinTournament("t1", "me", "Male", listOf("Mixed Doubles"), listOf("B"), joinerName = "Alex").getOrThrow()
        assertEquals(1, fake.docsIn("pendingNotifications/p1/notifications").size)
        assertTrue(fake.docsIn("pendingNotifications/p2/notifications").isEmpty())
        assertTrue(repo.fetchTournament("t1")!!.participants.containsKey("me"))
    }

    @Test
    fun `updating preferences does not re-notify anyone`() = runBlocking {
        seedTournament()
        repo.joinTournament("t1", "me", "Male", listOf("Mixed Doubles"), emptyList()).getOrThrow()
        repo.joinTournament("t1", "me", "Male", listOf("Mixed Doubles"), listOf("B")).getOrThrow()
        assertEquals(1, fake.docsIn("pendingNotifications/p1/notifications").size)
    }
}

class AwayTimeRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = AwayTimeRepository(fake.instance)
    private val dayStart = Date(1_900_000_000_000L)
    private val dayEnd = Date(dayStart.time + 24 * 3_600_000)

    @Test
    fun `saved away time is readable by both apps`() = runBlocking {
        repo.saveAwayTime("c1", Date(dayStart.time + 3_600_000), Date(dayStart.time + 7_200_000), "  ").getOrThrow()
        val doc = fake.docsIn("coaches/c1/awayTimes").values.single()
        assertEquals(doc["startAt"], doc["StartAt"])
        assertEquals(doc["endAt"], doc["EndAt"])
        assertFalse("blank notes are not stored", doc.containsKey("notes"))
    }

    @Test
    fun `day window includes iOS blocks and multi-day blocks that started earlier`() = runBlocking {
        fake.seed("coaches/c1/awayTimes/ios", mapOf("StartAt" to ts(Date(dayStart.time + 3_600_000)), "EndAt" to ts(Date(dayStart.time + 7_200_000))))
        fake.seed("coaches/c1/awayTimes/vacation", mapOf("startAt" to ts(Date(dayStart.time - 3 * 86_400_000L)), "endAt" to ts(Date(dayStart.time + 86_400_000L))))
        fake.seed("coaches/c1/awayTimes/lastWeek", mapOf("startAt" to ts(Date(dayStart.time - 7 * 86_400_000L)), "endAt" to ts(Date(dayStart.time - 6 * 86_400_000L))))
        val found = repo.fetchAwayTimes("c1", dayStart, dayEnd)
        assertEquals(setOf("ios", "vacation"), found.map { it.id }.toSet())
        assertEquals(Date(dayStart.time + 3_600_000), found.single { it.id == "ios" }.startAt)
    }
}
