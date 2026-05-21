//
//  EditPersonaSampleData.swift
//  Pantopus
//
//  Deterministic fixtures for the A13.12 Edit persona editor — used by
//  previews, the view-model's stub `load()`, and the snapshot baselines.
//  The backend has been removed from the repo, so the editor renders from
//  these fixtures rather than a network fetch. The two frames mirror the
//  design source: "Elm Park Watch" (live) and "Sourdough Saturdays"
//  (mid-setup).
//

import Foundation

public enum EditPersonaSampleData {
    /// Stable persona id used by the Audience Profile "Edit persona" action
    /// and the editor route.
    public static let personaId = "persona_elmpark_watch"

    /// Total checklist rungs (mirrors the SETUP hero's 7-step gate).
    public static let setupStepsTotal = 7
    /// Completed rungs in the SETUP fixture.
    public static let setupStepsDone = 3

    // MARK: - Frame 1 · LIVE (published, monetized)

    public static let live = EditPersonaContent(
        personaId: personaId,
        handle: "elmpark.watch",
        displayName: "Elm Park Watch",
        bio: "Block-by-block updates for Elm Park. Lost cat? Open hydrant? "
            + "Watch-meeting notes? It's here. Run by Maria K. since 2022.",
        bioCharCount: "129 / 240",
        handleStatus: .reserved,
        handleNote: nil,
        followers: "2,340",
        posts: "46",
        rating: "4.8★",
        liveBadge: "Live",
        categoriesAllow: [
            PersonaCategoryChip(label: "Block-watch updates", icon: .shield),
            PersonaCategoryChip(label: "Lost & found", icon: .helpCircle),
            PersonaCategoryChip(label: "Local events", icon: .calendarDays),
            PersonaCategoryChip(label: "Repair logs", icon: .wrench),
            PersonaCategoryChip(label: "Restoration photos", icon: .image)
        ],
        categoriesAllowSub: "5 of 12",
        categoriesOff: [
            PersonaCategoryChip(label: "Politics", icon: .flag),
            PersonaCategoryChip(label: "Off-block listings", icon: .ban)
        ],
        categoriesOffSub: "2 of 12",
        policyNote: "Pantopus won't auto-suggest blocked categories when you compose.",
        stripe: .connected(account: "acct_1Lw…q9P"),
        tiers: [
            PersonaTierCard(
                id: "neighbor",
                name: "Neighbor",
                kind: .free,
                blurb: "Public posts, weekly digest, lost & found alerts."
            ),
            PersonaTierCard(
                id: "block_member",
                name: "Block Member",
                kind: .paid,
                priceLabel: "3",
                period: "mo",
                blurb: "Restoration photo set + member-only repair logs.",
                perks: ["Members-only photos", "Monthly Q&A thread"],
                stripeState: .ready
            ),
            PersonaTierCard(
                id: "patron",
                name: "Patron",
                kind: .paid,
                priceLabel: "8",
                period: "mo",
                blurb: "Everything in Block Member plus quarterly print zine.",
                perks: ["Quarterly zine, mailed", "Name in masthead"],
                stripeState: .ready
            )
        ],
        canAddTier: true,
        cap: .weekly3,
        quietHoursOn: true,
        quietHoursRange: "10:00 PM → 7:00 AM · America/New_York",
        shareUrl: "pantopus.app/@elmpark.watch",
        shareIsPublic: true,
        analyticsOn: true,
        analyticsScope: ["Follower growth", "Reach (aggregate)", "Tier conversion"]
    )

    // MARK: - Frame 2 · SETUP (draft, pre-Stripe)

    public static let setup = EditPersonaContent(
        personaId: "persona_sourdough_sat",
        handle: "sourdough.sat",
        displayName: "Sourdough Saturdays",
        bio: "Weekend bake-swap on Elm Park. Trade a loaf, take a loaf. "
            + "Bench fee feeds the starter.",
        bioCharCount: "91 / 240",
        handleStatus: .available,
        handleNote: "Reserved for 24h while you finish setup.",
        checklist: [
            PersonaChecklistStep(id: "handle", label: "Handle reserved", done: true),
            PersonaChecklistStep(id: "name", label: "Display name + bio", done: true),
            PersonaChecklistStep(id: "policy", label: "Category policy", done: true),
            PersonaChecklistStep(id: "stripe", label: "Connect Stripe", done: false, isNext: true),
            PersonaChecklistStep(id: "prices", label: "Set tier prices", done: false),
            PersonaChecklistStep(id: "schedule", label: "Broadcast schedule", done: false),
            PersonaChecklistStep(id: "publish", label: "Publish persona", done: false)
        ],
        checklistSummary: "3 of 7 steps · 4 more before you can publish",
        categoriesAllow: [
            PersonaCategoryChip(label: "Block-watch updates", icon: .shield),
            PersonaCategoryChip(label: "Lost & found", icon: .helpCircle),
            PersonaCategoryChip(label: "Local events", icon: .calendarDays)
        ],
        categoriesAllowSub: "3 of 12",
        categoriesOff: [
            PersonaCategoryChip(label: "Politics", icon: .flag),
            PersonaCategoryChip(label: "Sponsored", icon: .megaphone)
        ],
        categoriesOffSub: "2 of 12",
        policyNote: nil,
        stripe: .notConnected,
        tiers: [
            PersonaTierCard(
                id: "crumb",
                name: "Crumb (free)",
                kind: .free,
                blurb: "Saturday swap location + bake-log photos."
            ),
            PersonaTierCard(
                id: "loaf_patron",
                name: "Loaf Patron",
                kind: .paidLocked,
                priceLabel: "—",
                period: "mo",
                blurb: "Set after Stripe is connected. Suggested: $4/mo.",
                stripeState: .needsStripe,
                isFresh: true
            )
        ],
        canAddTier: false,
        cap: .weekly1,
        quietHoursOn: false,
        quietHoursRange: "",
        shareUrl: "pantopus.app/@sourdough.sat (draft)",
        shareIsPublic: false,
        analyticsOn: false,
        analyticsScope: []
    )
}
