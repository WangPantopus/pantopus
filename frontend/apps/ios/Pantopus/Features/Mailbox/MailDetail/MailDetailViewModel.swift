//
//  MailDetailViewModel.swift
//  Pantopus
//
//  T6.5b (P20) — Drives the **generic A17.1 mail item detail** screen.
//  Sits on the shared `MailItemDetailShell` (P19) and projects the
//  `GET /api/mailbox/:id` payload into the shell's typed slots:
//
//    - top bar: back / eyebrow trust dot / overflow (Forward, Archive,
//      Mark unread, Delete, Report)
//    - hero: category accent + sender overline + title + excerpt
//    - aiElf: when the DTO carries an `ai_summary` (V2 surface — falls
//      back to nil for V1 items so the shell skips the strip)
//    - keyFacts: derived from `created_at`, `expires_at`, mail type,
//      and the sender's business name when present
//    - body: rich-text `mail.content` rendered as paragraphs
//    - attachments: when `mail.attachments` is non-empty
//    - sender card: always
//    - actions: Acknowledge (when `ack_required` and not yet acked) +
//      Forward / Archive / Reply secondary tiles
//
//  P21-P23 will replace the body / actions for the package / coupon /
//  booklet / certified variants by composing the same shell with their
//  variant-specific slot views. The generic VM here owns the
//  "everything else" rendering.
//

import Foundation
import Observation

@Observable
@MainActor
public final class MailDetailViewModel {
    public private(set) var state: MailDetailState = .loading
    /// Transient banner; the view clears it after display.
    public var toast: String?
    public private(set) var ackInFlight: Bool = false
    /// Community RSVP mutation is in-flight; disables the chip row.
    public private(set) var rsvpInFlight: Bool = false
    /// Coupon redeem mutation is in-flight; disables the redeem CTA.
    public private(set) var couponRedeemInFlight: Bool = false
    /// Gig accept-bid mutation is in-flight; disables the action row.
    public private(set) var gigBidInFlight: Bool = false
    /// Party RSVP mutation is in-flight; disables the three-way cluster.
    public private(set) var partyRsvpInFlight: Bool = false
    /// T6.5e — Save-to-vault picker visibility. The view binds a
    /// confirmation dialog to this flag; tapping a folder calls
    /// `saveToVault(folderId:)`.
    public var showsSaveToVaultPicker: Bool = false
    /// Vault folders fetched lazily the first time the overflow item
    /// is tapped, then cached for the lifetime of the screen.
    public private(set) var saveToVaultFolders: [VaultFolderDTO] = []
    /// Save mutation in-flight.
    public private(set) var saveToVaultInFlight: Bool = false

    private let mailId: String
    private let api: APIClient
    private let now: @Sendable () -> Date

    init(
        mailId: String,
        api: APIClient = .shared,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.mailId = mailId
        self.api = api
        self.now = now
    }

    // MARK: - Lifecycle

    public func load() async {
        if case .loaded = state {} else { state = .loading }
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    private func fetch() async {
        do {
            let response: MailDetailResponse = try await api.request(
                MailboxEndpoints.detail(mailId: mailId)
            )
            state = .loaded(Self.project(detail: response.mail, now: now()))
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription ?? "Couldn't load this item."
            )
        }
    }

    // MARK: - Mutations

    /// Acknowledge the mail item. Optimistic — flips the local
    /// `isAcknowledged` state then rolls back on transport failure.
    public func acknowledge() async {
        guard case let .loaded(content) = state, !ackInFlight else { return }
        ackInFlight = true
        defer { ackInFlight = false }
        let previous = content
        let optimistic = MailDetailContent.replacingAck(content, with: true)
        state = .loaded(optimistic)
        do {
            let _: AckResponse = try await api.request(
                MailboxEndpoints.acknowledge(mailId: mailId)
            )
            toast = "Acknowledged"
        } catch {
            state = .loaded(previous)
            toast = (error as? APIError)?.errorDescription ?? "Couldn't acknowledge"
        }
    }

    /// Set the user's RSVP status on a Community mail item.
    /// Optimistic — flips the local state and rolls back on transport
    /// failure. "Going" wires to the existing `POST /community/rsvp`
    /// route (backend stores it as a `will_attend` reaction); other
    /// states are stored locally until the backend exposes a typed
    /// per-status route (P22 scope note in the parity audit).
    public func setRsvp(_ status: CommunityRsvpStatus) async {
        guard case let .loaded(content) = state,
              let community = content.communityDetail,
              !rsvpInFlight else { return }
        rsvpInFlight = true
        defer { rsvpInFlight = false }
        let previous = content
        let optimistic = MailDetailContent.replacingRsvp(content, with: status)
        state = .loaded(optimistic)
        // Local-only states don't currently round-trip; just toast.
        guard status == .going else {
            toast = Self.rsvpToast(for: status)
            return
        }
        do {
            let _: CommunityRsvpResponse = try await api.request(
                MailboxV2Endpoints.communityRsvp(communityItemId: community.communityItemId)
            )
            toast = "You're going"
        } catch {
            state = .loaded(previous)
            toast = (error as? APIError)?.errorDescription ?? "Couldn't update RSVP"
        }
    }

    private static func rsvpToast(for status: CommunityRsvpStatus) -> String {
        switch status {
        case .going: "You're going"
        case .maybe: "Saved as maybe"
        case .notGoing: "Marked as can't make it"
        case .undecided: "RSVP cleared"
        }
    }

    // MARK: - Ceremonial variant mutations (A17.5–A17.8)

    /// A17.5 — Mark a coupon redeemed. Backend redemption is not yet
    /// wired; the projection flips locally so the barcode card collapses
    /// and the redeemed ribbon takes its place. Mirrors the acknowledge
    /// shape so subsequent backend wiring can drop in without a UI churn.
    public func redeemCoupon() async {
        guard case let .loaded(content) = state,
              content.category == .coupon,
              content.couponDetail != nil,
              !couponRedeemInFlight else { return }
        couponRedeemInFlight = true
        defer { couponRedeemInFlight = false }
        // Treat redemption as a one-way acknowledgement until backend
        // exposes a typed coupon redemption endpoint. The optimistic
        // ack flips both `isAcknowledged` and the read-status label.
        let optimistic = MailDetailContent.replacingAck(content, with: true)
        state = .loaded(optimistic)
        toast = "Redeemed"
    }

    /// A17.6 — Accept the incoming bid on a gig. Backend acceptance is
    /// not yet wired through the mail detail endpoint; the projection
    /// flips locally so the gig variant swaps into its accepted body
    /// (next-steps timeline + Open thread CTA). Mirrors acknowledge.
    public func acceptGigBid() async {
        guard case let .loaded(content) = state,
              content.category == .gig,
              let gig = content.gigDetail,
              !gigBidInFlight else { return }
        gigBidInFlight = true
        defer { gigBidInFlight = false }
        let optimistic = MailDetailContent.replacingGigAccepted(content, with: gig.accepted())
        state = .loaded(optimistic)
        toast = "Bid accepted"
    }

    /// A17.9 — Set the user's RSVP on a Party mail item. Backend wiring
    /// is not yet exposed for personal invites; the projection flips
    /// locally so the variant swaps into the going-state hero / elf /
    /// potluck-claim affordances. Mirrors the community RSVP shape so a
    /// future personal-invite endpoint slots in without a UI churn.
    public func setPartyRsvp(_ status: PartyRsvpStatus) async {
        guard case let .loaded(content) = state,
              content.category == .party,
              content.partyDetail != nil,
              !partyRsvpInFlight else { return }
        partyRsvpInFlight = true
        defer { partyRsvpInFlight = false }
        let confirmedAtLabel = status == .going ? Self.partyRsvpStamp(now: now()) : nil
        let optimistic = MailDetailContent.replacingPartyRsvp(
            content,
            with: status,
            confirmedAtLabel: confirmedAtLabel
        )
        state = .loaded(optimistic)
        toast = Self.partyRsvpToast(for: status)
    }

    /// A17.9 — Adjust the plus-one stepper. Clamped to `0...4` so the
    /// stepper can't underflow or pile on unbounded headcount in local
    /// state. Only meaningful in the `going` RSVP state.
    public func setPartyPlusOneCount(_ count: Int) async {
        guard case let .loaded(content) = state,
              content.category == .party,
              content.partyDetail != nil else { return }
        let clamped = max(0, min(count, 4))
        let optimistic = MailDetailContent.replacingPartyPlusOneCount(content, with: clamped)
        state = .loaded(optimistic)
    }

    /// A17.9 — Claim (or release) a potluck bring-item. Passing `name == nil`
    /// releases the claim — the design uses this to flip the "I'll bring it"
    /// pill back to the unclaimed style.
    public func togglePartyBringClaim(at index: Int, byName name: String?) async {
        guard case let .loaded(content) = state,
              content.category == .party,
              content.partyDetail != nil else { return }
        let optimistic = MailDetailContent.replacingPartyBringClaim(content, at: index, by: name)
        state = .loaded(optimistic)
        toast = name == nil ? "Released" : "Claimed"
    }

    private static func partyRsvpToast(for status: PartyRsvpStatus) -> String {
        switch status {
        case .going: "You're in"
        case .maybe: "Saved as maybe"
        case .notGoing: "Sent regrets"
        case .undecided: "RSVP cleared"
        }
    }

    private static func partyRsvpStamp(now: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "h:mm a"
        return "Today \(formatter.string(from: now))"
    }

    /// A17.7 — Save the memory keepsake straight to the user's default
    /// memories vault folder, bypassing the picker. If we have no
    /// cached folders yet, fall through to the picker so the user can
    /// choose; once they're cached we prefer the "Memories" folder when
    /// one exists, falling back to the first folder.
    public func saveMemoryToVault() async {
        guard case let .loaded(content) = state,
              content.category == .memory,
              let memory = content.memoryDetail,
              !memory.isSaved,
              !saveToVaultInFlight else { return }
        // Optimistic flip so the saved banner + vault card take over the
        // body without waiting for the network round-trip.
        let optimistic = MailDetailContent.replacingMemorySaved(content, with: true)
        state = .loaded(optimistic)
        if saveToVaultFolders.isEmpty {
            await openSaveToVaultPicker()
            return
        }
        let memoryFolder = saveToVaultFolders.first { $0.label.lowercased().contains("memor") }
        let folderId = (memoryFolder ?? saveToVaultFolders.first)?.id
        guard let folderId else {
            await openSaveToVaultPicker()
            return
        }
        await saveToVault(folderId: folderId)
    }

    // MARK: - Save to vault (T6.5e / P19.5)

    /// Open the save-to-vault picker. Fetches folders on the first
    /// call; cached after.
    public func openSaveToVaultPicker() async {
        if saveToVaultFolders.isEmpty {
            do {
                let response: VaultFoldersResponse = try await api.request(
                    MailboxVaultEndpoints.folders(drawer: "personal")
                )
                saveToVaultFolders = response.folders
            } catch {
                toast = (error as? APIError)?.errorDescription
                    ?? "Couldn't load your vault folders."
                return
            }
        }
        guard !saveToVaultFolders.isEmpty else {
            toast = "Add a folder in your Vault first."
            return
        }
        showsSaveToVaultPicker = true
    }

    /// POST the current mail to the supplied vault folder. Optimistic
    /// toast on success; surfaces a readable error on failure.
    public func saveToVault(folderId: String) async {
        guard !saveToVaultInFlight else { return }
        saveToVaultInFlight = true
        defer { saveToVaultInFlight = false }
        do {
            let _: FileToVaultResponse = try await api.request(
                MailboxVaultEndpoints.file(
                    body: FileToVaultBody(mailId: mailId, folderId: folderId)
                )
            )
            let folderLabel = saveToVaultFolders.first { $0.id == folderId }?.label
            toast = folderLabel.map { "Saved to \($0)" } ?? "Saved to vault"
        } catch {
            toast = (error as? APIError)?.errorDescription
                ?? "Couldn't save to vault. Try again."
        }
        showsSaveToVaultPicker = false
    }
}
