@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.scheduling._shared

/**
 * The canonical Calendarly route paths, nav-arg keys, and path builders —
 * **public** so every feature stream can `onNavigate(SchedulingRoutes.x)` and
 * read nav args via `SavedStateHandle[SchedulingRoutes.ARG_*]` without touching
 * the private `ChildRoutes` in `RootTabScreen` (which references these same
 * constants when registering each `composable(...)`). A0 owns this file; the
 * paths are stable contracts the 18 streams build against.
 *
 * Screens receive `onNavigate: (String) -> Unit` (wired to
 * `navController.navigate`) + `onBack: () -> Unit`, so cross-stream links
 * compile without any stream editing another's files.
 */
object SchedulingRoutes {
    // ── Nav-arg keys ────────────────────────────────────────────────────────
    const val ARG_EVENT_TYPE_ID = "eventTypeId"
    const val ARG_SCHEDULE_ID = "scheduleId"
    const val ARG_SLUG = "slug"
    const val ARG_ONEOFF_TOKEN = "token"
    const val ARG_MANAGE_TOKEN = "manageToken"
    const val ARG_BOOKING_ID = "bookingId"
    const val ARG_POLL_ID = "pollId"
    const val ARG_RESOURCE_ID = "resourceId"
    const val ARG_VISIT_ID = "visitId"
    const val ARG_MEMBER_ID = "memberId"
    const val ARG_PACKAGE_ID = "packageId"
    const val ARG_WORKFLOW_ID = "workflowId"
    const val ARG_TEMPLATE_ID = "templateId"
    const val ARG_INVOICE_ID = "invoiceId"

    // ── A1 Setup & hub ──────────────────────────────────────────────────────
    const val HUB = "scheduling/hub"
    const val SETUP_WIZARD = "scheduling/setup"
    const val SETTINGS = "scheduling/settings"
    const val NOTIFICATIONS = "scheduling/settings/notifications"
    const val ONBOARDING = "scheduling/onboarding"

    // ── A2 Event types ────────────────────────────────────────────────────────
    const val EVENT_TYPE_LIST = "scheduling/event-types"
    const val EVENT_TYPE_EDITOR = "scheduling/event-types/{$ARG_EVENT_TYPE_ID}"
    const val INTAKE_QUESTIONS_EDITOR = "scheduling/event-types/{$ARG_EVENT_TYPE_ID}/questions"
    const val CONNECTED_CALENDARS = "scheduling/connected-calendars"

    fun eventTypeEditor(eventTypeId: String) = "scheduling/event-types/$eventTypeId"

    fun intakeQuestionsEditor(eventTypeId: String) = "scheduling/event-types/$eventTypeId/questions"

    // ── A3 Availability ───────────────────────────────────────────────────────
    const val AVAILABILITY_LIST = "scheduling/availability"
    const val WEEKLY_HOURS_EDITOR = "scheduling/availability/{$ARG_SCHEDULE_ID}"
    const val DATE_OVERRIDES = "scheduling/availability/{$ARG_SCHEDULE_ID}/overrides"
    const val BOOKING_LIMITS = "scheduling/availability/limits"
    const val BLOCK_OFF_TIME = "scheduling/availability/blocks"

    fun weeklyHoursEditor(scheduleId: String) = "scheduling/availability/$scheduleId"

    fun dateOverrides(scheduleId: String) = "scheduling/availability/$scheduleId/overrides"

    // ── A4 Booking page & sharing ─────────────────────────────────────────────
    const val BOOKING_PAGE_MANAGE = "scheduling/booking-page"
    const val PUBLIC_PAGE_PREVIEW = "scheduling/booking-page/preview"
    const val ONE_OFF_LINK_GENERATOR = "scheduling/booking-page/one-off"

    // ── A5 Invitee discovery (public) ─────────────────────────────────────────
    const val PUBLIC_BOOKING = "book/{$ARG_SLUG}"
    const val PUBLIC_BOOKING_ONEOFF = "book/o/{$ARG_ONEOFF_TOKEN}"

    fun publicBooking(slug: String) = "book/$slug"

    fun publicBookingOneOff(token: String) = "book/o/$token"

    // ── A6 Invitee confirm & manage (public) ──────────────────────────────────
    const val MANAGE_BOOKING = "booking/{$ARG_MANAGE_TOKEN}"

    fun manageBooking(manageToken: String) = "booking/$manageToken"

    // ── A7 Invitee edge & customer ────────────────────────────────────────────
    const val MY_BOOKINGS = "scheduling/my-bookings"
    const val OPEN_IN_APP_INTERSTITIAL = "scheduling/open-in-app"
    const val RECURRING_SETUP = "scheduling/my-bookings/recurring"

    // ── A8 Bookings inbox & core ──────────────────────────────────────────────
    const val BOOKINGS_INBOX = "scheduling/bookings"
    const val BOOKING_DETAIL = "scheduling/bookings/{$ARG_BOOKING_ID}"

    fun bookingDetail(bookingId: String) = "scheduling/bookings/$bookingId"

    // ── A9 Bookings extras ────────────────────────────────────────────────────
    const val BOOKING_SEARCH = "scheduling/bookings/search"
    const val GROUP_ROSTER = "scheduling/bookings/{$ARG_BOOKING_ID}/roster"
    const val MANUAL_BOOKING = "scheduling/bookings/manual"
    const val WAITLIST = "scheduling/waitlist"
    const val POST_MEETING_FOLLOWUP = "scheduling/bookings/{$ARG_BOOKING_ID}/followup"

    fun groupRoster(bookingId: String) = "scheduling/bookings/$bookingId/roster"

    fun postMeetingFollowup(bookingId: String) = "scheduling/bookings/$bookingId/followup"

    // ── A10 Home calendar & RSVP (new routes only) ────────────────────────────
    const val HOUSEHOLD_AVAILABILITY = "scheduling/home/availability"
    const val PERMISSION_GATED_SCHEDULER = "scheduling/home/scheduler"

    // ── A11 Find-a-time & who's-free ──────────────────────────────────────────
    const val FIND_A_TIME = "scheduling/find-a-time"
    const val FIND_A_TIME_SLOTS = "scheduling/find-a-time/slots"
    const val MEMBER_POLL_RESPONSE = "scheduling/poll/{$ARG_POLL_ID}"
    const val WHOS_FREE = "scheduling/whos-free"

    fun memberPollResponse(pollId: String) = "scheduling/poll/$pollId"

    // ── A12 Home resources & visits ───────────────────────────────────────────
    const val RESOURCE_LIST = "scheduling/resources"
    const val RESOURCE_EDITOR = "scheduling/resources/{$ARG_RESOURCE_ID}/edit"
    const val RESOURCE_DETAIL = "scheduling/resources/{$ARG_RESOURCE_ID}"
    const val BOOK_RESOURCE = "scheduling/resources/{$ARG_RESOURCE_ID}/book"
    const val VISIT_SETUP = "scheduling/visits/new"
    const val VISIT_DETAIL = "scheduling/visits/{$ARG_VISIT_ID}"

    fun resourceEditor(resourceId: String) = "scheduling/resources/$resourceId/edit"

    fun resourceDetail(resourceId: String) = "scheduling/resources/$resourceId"

    fun bookResource(resourceId: String) = "scheduling/resources/$resourceId/book"

    fun visitDetail(visitId: String) = "scheduling/visits/$visitId"

    // ── A13 Business config & team ────────────────────────────────────────────
    const val BUSINESS_SCHEDULING_SETTINGS = "scheduling/business"
    const val TEAM_BOOKING_AVAILABILITY = "scheduling/business/team-availability"
    const val COLLECTIVE_EVENT_SETUP = "scheduling/business/collective/{$ARG_EVENT_TYPE_ID}"
    const val MEMBER_WORKING_HOURS = "scheduling/business/members/{$ARG_MEMBER_ID}/hours"

    fun collectiveEventSetup(eventTypeId: String) = "scheduling/business/collective/$eventTypeId"

    fun memberWorkingHours(memberId: String) = "scheduling/business/members/$memberId/hours"

    // ── A14 Payments & payouts ────────────────────────────────────────────────
    const val PAYMENTS_SETUP = "scheduling/payments"
    const val PAYOUTS = "scheduling/payments/payouts"
    const val CANCELLATION_REFUND_POLICY = "scheduling/payments/policy"

    // ── A15 Packages & invoices ───────────────────────────────────────────────
    const val PACKAGES_LIST = "scheduling/packages"
    const val PACKAGE_EDITOR = "scheduling/packages/{$ARG_PACKAGE_ID}/edit"
    const val BUY_PACKAGE = "scheduling/packages/{$ARG_PACKAGE_ID}/buy"
    const val MY_PACKAGES = "scheduling/my-packages"
    const val INVOICES_LIST = "scheduling/invoices"
    const val INVOICE_DETAIL = "scheduling/invoices/{$ARG_INVOICE_ID}"

    fun packageEditor(packageId: String) = "scheduling/packages/$packageId/edit"

    fun buyPackage(packageId: String) = "scheduling/packages/$packageId/buy"

    fun invoiceDetail(invoiceId: String) = "scheduling/invoices/$invoiceId"

    // ── A16 Reminders / workflows / templates ─────────────────────────────────
    const val REMINDERS_QUICK_SETUP = "scheduling/reminders"
    const val WORKFLOWS_LIST = "scheduling/workflows"
    const val WORKFLOW_EDITOR = "scheduling/workflows/{$ARG_WORKFLOW_ID}"
    const val MESSAGE_TEMPLATE_EDITOR = "scheduling/templates/{$ARG_TEMPLATE_ID}"
    const val TEMPLATE_LIBRARY = "scheduling/templates"

    fun workflowEditor(workflowId: String) = "scheduling/workflows/$workflowId"

    fun messageTemplateEditor(templateId: String) = "scheduling/templates/$templateId"

    // ── A17 Insights & reports ────────────────────────────────────────────────
    const val INSIGHTS_DASHBOARD = "scheduling/insights"
    const val EVENT_TYPE_PERFORMANCE = "scheduling/insights/event-types"
    const val NO_SHOW_REPORT = "scheduling/insights/no-shows"
    const val TEAM_PERFORMANCE = "scheduling/insights/team"

    // ── A18 Cross-cutting & polish ────────────────────────────────────────────
    const val NOTIFICATION_PERMISSION_PROMPT = "scheduling/notifications-permission"
}
