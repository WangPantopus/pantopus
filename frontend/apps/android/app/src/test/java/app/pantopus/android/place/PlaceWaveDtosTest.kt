package app.pantopus.android.place

import app.pantopus.android.data.api.models.place.MailboxCheckResponse
import app.pantopus.android.data.api.models.place.MailboxCheckVerdict
import app.pantopus.android.data.api.models.place.MailboxFindingSeverity
import app.pantopus.android.data.api.models.place.MailboxPhysicalStatus
import app.pantopus.android.data.api.models.place.PlaceEnumAdapterFactory
import app.pantopus.android.data.api.models.place.RecordWatchResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding contract for the Wave endpoint DTOs that ride beside the
 * Place sections: the Mailbox Reality Check and the Record Watch.
 * Vocabulary enums must fall back to safe constants rather than
 * failing. Parity: iOS `PlaceWaveDTOsTests.swift`.
 */
class PlaceWaveDtosTest {
    private val moshi: Moshi =
        Moshi
            .Builder()
            .add(PlaceEnumAdapterFactory)
            .addLast(KotlinJsonAdapterFactory())
            .build()

    private inline fun <reified T> decode(json: String): T = checkNotNull(moshi.adapter(T::class.java).fromJson(json)) { "decoded null" }

    @Test
    fun `decodes the mailbox check`() {
        val json =
            """
            {"check":{"verdict":"needs_attention",
              "findings":[
                {"severity":"attention","title":"A unit number is missing","detail":"USPS confirms the building but expects a unit."},
                {"severity":"ok","title":"USPS confirms this exact address","detail":"Full match."}],
              "physical":{"status":"proven","title":"Mail physically reaches this mailbox","detail":"A postcard was delivered here."},
              "checked_at":"2026-08-01T00:00:00.000Z"}}
            """.trimIndent()
        val check = decode<MailboxCheckResponse>(json).check
        assertEquals(MailboxCheckVerdict.NEEDS_ATTENTION, check.verdict)
        assertEquals(2, check.findings.size)
        assertEquals(MailboxFindingSeverity.ATTENTION, check.findings[0].severity)
        assertEquals(MailboxPhysicalStatus.PROVEN, check.physical.status)
    }

    @Test
    fun `mailbox vocabulary additions fall back safely`() {
        val json =
            """
            {"check":{"verdict":"catastrophic",
              "findings":[{"severity":"apocalyptic","title":"t","detail":"d"}],
              "physical":{"status":"teleported","title":"t","detail":"d"},
              "checked_at":null}}
            """.trimIndent()
        val check = decode<MailboxCheckResponse>(json).check
        assertEquals(MailboxCheckVerdict.UNKNOWN, check.verdict)
        assertEquals(MailboxFindingSeverity.INFO, check.findings[0].severity)
        assertEquals(MailboxPhysicalStatus.NOT_RUN, check.physical.status)
        assertNull(check.checkedAt)
    }

    @Test
    fun `decodes the record watch with and without an evaluation`() {
        val withEval =
            """
            {"watch":{"id":"w1","home_id":"home-1","loan_recorded_month":"2023-03",
              "baseline_rate":6.6,"created_at":"2026-08-01T00:00:00.000Z",
              "evaluation":{"baseline_rate":6.6,"current_rate":5.7,"current_as_of":"2026-08-20",
                "delta_pp":-0.9,"refi_window":true}}}
            """.trimIndent()
        val response = decode<RecordWatchResponse>(withEval)
        assertNotNull(response.watch)
        val watch = response.watch!!
        assertEquals("2023-03", watch.loanRecordedMonth)
        assertNotNull(watch.evaluation)
        val ev = watch.evaluation!!
        assertEquals(-0.9, ev.deltaPp, 0.0001)
        assertTrue(ev.refiWindow)

        // GET with no watch is {"watch": null}; a watch whose rate
        // history is momentarily unreachable ships evaluation: null.
        assertNull(decode<RecordWatchResponse>("""{"watch":null}""").watch)
        val noEval =
            """
            {"watch":{"id":"w1","home_id":"home-1","loan_recorded_month":"2023-03",
              "baseline_rate":6.6,"created_at":"2026-08-01T00:00:00.000Z","evaluation":null}}
            """.trimIndent()
        assertNull(decode<RecordWatchResponse>(noEval).watch!!.evaluation)
    }
}
