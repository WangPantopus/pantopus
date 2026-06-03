@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.wallet

import app.pantopus.android.data.api.models.wallet.WalletBalanceResponse
import app.pantopus.android.data.api.models.wallet.WalletPendingReleaseResponse
import app.pantopus.android.data.api.models.wallet.WalletTransactionDto
import java.text.NumberFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * P1-F — projects the read-path wallet DTOs onto [WalletContent]. Mirrors the
 * iOS `WalletViewModel` mapping. The withdraw/payout slots (payout method,
 * tax docs) reuse the P3.2 visual placeholder — they're wired in Phase 3 with
 * Stripe — and `holdState` stays null because the hold banner copy is
 * Stripe-specific.
 */
@Suppress("TooManyFunctions")
object WalletMapper {
    fun build(
        balance: WalletBalanceResponse,
        transactions: List<WalletTransactionDto>,
        pending: WalletPendingReleaseResponse?,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): WalletContent {
        val pendingCents = pending?.totalPendingCents ?: 0L
        val pendingCount = (pending?.inReviewCount ?: 0) + (pending?.releasingSoonCount ?: 0)
        val placeholder = WalletSampleData.populated
        return WalletContent(
            available = centsToPlain(balance.wallet.balance),
            pending = centsToCurrency(pendingCents),
            pendingMeta = pendingMeta(pendingCount, pendingCents),
            monthValue = centsToCurrency(monthIncomeCents(transactions, zone, now)),
            monthMeta = monthMeta(monthIncomeRows(transactions, zone, now).size),
            activity = transactions.map { activityItem(it, zone, now) },
            payoutMethod = placeholder.payoutMethod,
            taxDocs = placeholder.taxDocs,
            holdState = null,
        )
    }

    fun activityItem(
        tx: WalletTransactionDto,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): WalletActivityItem {
        val instant = parseInstant(tx.createdAt)
        val direction = direction(tx.type)
        return WalletActivityItem(
            id = tx.id,
            day = dayLabel(instant, zone, now),
            dateLabel = timeLabel(instant, zone),
            description = tx.description ?: typeLabel(tx.type),
            counterparty = counterpartyLabel(tx.type),
            category = category(tx.type),
            direction = direction,
            status = status(tx.status, direction),
            amount = centsToPlain(tx.amount),
            isFee = tx.type == "cancellation_fee",
        )
    }

    fun direction(type: String): ActivityDirection =
        when (type) {
            "withdrawal", "gig_payment", "tip_sent", "transfer_out", "cancellation_fee" -> ActivityDirection.Out
            else -> ActivityDirection.In
        }

    fun category(type: String): ActivityCategory =
        when (type) {
            "withdrawal", "deposit", "transfer_in", "transfer_out" -> ActivityCategory.Bank
            "cancellation_fee", "refund", "adjustment" -> ActivityCategory.Fee
            else -> ActivityCategory.Handyman
        }

    fun status(
        status: String,
        direction: ActivityDirection,
    ): ActivityStatus =
        when (status) {
            "pending" -> ActivityStatus.Pending("soon")
            else -> if (direction == ActivityDirection.Out) ActivityStatus.Complete else ActivityStatus.Available
        }

    private fun counterpartyLabel(type: String): String =
        when (type) {
            "withdrawal", "deposit", "transfer_in", "transfer_out" -> "Bank"
            "gig_income", "gig_payment" -> "Gig"
            "tip_income", "tip_sent" -> "Tip"
            "refund" -> "Refund"
            "cancellation_fee" -> "Pantopus"
            else -> "Adjustment"
        }

    private fun typeLabel(type: String): String =
        when (type) {
            "withdrawal" -> "Withdrawal"
            "deposit" -> "Deposit"
            "gig_income", "gig_payment" -> "Gig payment"
            "tip_income" -> "Tip received"
            "tip_sent" -> "Tip sent"
            "refund" -> "Refund"
            "cancellation_fee" -> "Cancellation fee"
            else -> "Adjustment"
        }

    private fun monthIncomeCents(
        transactions: List<WalletTransactionDto>,
        zone: ZoneId,
        now: Instant,
    ): Long = monthIncomeRows(transactions, zone, now).sumOf { it.amount }

    private fun monthIncomeRows(
        transactions: List<WalletTransactionDto>,
        zone: ZoneId,
        now: Instant,
    ): List<WalletTransactionDto> {
        val nowDate = now.atZone(zone).toLocalDate()
        return transactions.filter { tx ->
            if (direction(tx.type) != ActivityDirection.In) return@filter false
            val instant = parseInstant(tx.createdAt) ?: return@filter false
            val date = instant.atZone(zone).toLocalDate()
            date.year == nowDate.year && date.monthValue == nowDate.monthValue
        }
    }

    private fun pendingMeta(
        count: Int,
        cents: Long,
    ): String =
        if (cents <= 0L) {
            "Nothing in escrow"
        } else {
            "$count ${if (count == 1) "payment" else "payments"} · releases after review"
        }

    private fun monthMeta(count: Int): String = "$count ${if (count == 1) "task" else "tasks"} this month"

    // Integer cents → grouped 2-dp string with no symbol, e.g. "1,284.50".
    fun centsToPlain(cents: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.format(cents / 100.0)
    }

    fun centsToCurrency(cents: Long): String = "$" + centsToPlain(cents)

    fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrEmpty()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    }

    fun dayLabel(
        instant: Instant?,
        zone: ZoneId,
        now: Instant,
    ): String {
        if (instant == null) return "—"
        val date = instant.atZone(zone).toLocalDate()
        val nowDate = now.atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(date, nowDate)) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
        }
    }

    fun timeLabel(
        instant: Instant?,
        zone: ZoneId,
    ): String {
        if (instant == null) return ""
        val time = instant.atZone(zone).toLocalTime()
        return time
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
            .replace("AM", "am")
            .replace("PM", "pm")
    }
}
