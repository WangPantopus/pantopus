//
//  ProfessionalProfileViewModel.swift
//  Pantopus
//
//  A.5 (A13.11) — drives the Professional Profile editor. Backend was
//  removed from the repo, so `load()` hydrates from
//  `ProfessionalProfileSampleData` instead of hitting a route; the state
//  machine and edit semantics still mirror the eventual API contract.
//
//  State derives purely from the working copy: a clean copy (no unsaved
//  edits) is `.verified`; any unsaved edit flips it to `.pending`, carrying
//  the edit count and the number of new claims awaiting 1–2 day review.
//

import Foundation
import Observation

@Observable
@MainActor
public final class ProfessionalProfileViewModel {
    public private(set) var state: ProfessionalProfileState = .loading
    /// Surfaced by the view after submit / discard; cleared on auto-dismiss.
    public var toast: ToastMessage?

    /// Live working copy + the last-saved baseline used by Discard.
    private var content: ProfessionalProfileContent?
    private var baseline: ProfessionalProfileContent?

    private let seed: ProfessionalProfileContent
    private let baselineSeed: ProfessionalProfileContent
    private let simulateFailure: Bool

    /// - Parameters:
    ///   - seed: Sample content to hydrate. Defaults to the published
    ///     (verified) fixture; pass `.pendingEdits` for the dirty frame.
    ///   - baseline: The last-saved snapshot that Discard reverts to.
    ///     Defaults to `seed`. When seeding the pending frame, pass
    ///     `.published` so Discard rolls back to the clean profile.
    ///   - simulateFailure: When true, `load()` resolves to `.error` so the
    ///     error state can be exercised in previews / tests.
    public init(
        seed: ProfessionalProfileContent = ProfessionalProfileSampleData.published,
        baseline: ProfessionalProfileContent? = nil,
        simulateFailure: Bool = false
    ) {
        self.seed = seed
        baselineSeed = baseline ?? seed
        self.simulateFailure = simulateFailure
    }

    // MARK: - Loading

    /// Hydrate from sample data. No network (backend removed).
    public func load() async {
        state = .loading
        guard !simulateFailure else {
            state = .error(message: "We couldn't load your professional profile.")
            return
        }
        content = seed
        baseline = baselineSeed
        recompute()
    }

    public func refresh() async {
        await load()
    }

    // MARK: - Field edits

    public func updateTitle(_ value: String) {
        mutate { $0.title.value = value
            $0.title.touched = true
        }
    }

    public func updateYearsInRole(_ value: String) {
        let digitsOnly = value.filter(\.isNumber)
        mutate { $0.yearsInRole.value = digitsOnly
            $0.yearsInRole.touched = true
        }
    }

    public func setVisibility(_ id: String, isOn: Bool) {
        mutate { content in
            guard let index = content.visibility.firstIndex(where: { $0.id == id }) else { return }
            content.visibility[index].isOn = isOn
        }
    }

    public func removeSkill(_ id: String) {
        mutate { $0.skills.removeAll { $0.id == id } }
    }

    public func removeCertification(_ id: String) {
        mutate { $0.certifications.removeAll { $0.id == id } }
    }

    /// Append a fresh trade chip. With no backend the chip is a placeholder;
    /// the point is to exercise the fresh-dot + verified→pending transition.
    public func addSkill() {
        mutate {
            $0.skills.append(
                ProSkill(id: "skill-\(UUID().uuidString)", label: "New skill", icon: .plus, isFresh: true)
            )
        }
    }

    /// Append a fresh, pending certification placeholder.
    public func addCertification() {
        mutate {
            $0.certifications.append(
                Certification(
                    id: "cert-\(UUID().uuidString)",
                    name: "New certification",
                    issuer: "Awaiting upload",
                    issued: "—",
                    expires: "—",
                    status: .pending,
                    isFresh: true
                )
            )
        }
    }

    /// Append a fresh portfolio link whose preview is still resolving.
    public func addPortfolioLink() {
        mutate {
            $0.portfolio.append(
                PortfolioLink(
                    id: "link-\(UUID().uuidString)",
                    host: "link",
                    title: "New link",
                    url: "Fetching preview…",
                    state: .loading,
                    isFresh: true
                )
            )
        }
    }

    // MARK: - Commit / revert

    /// Revert all unsaved edits back to the last-saved baseline.
    public func discard() {
        guard let baseline else { return }
        content = baseline
        recompute()
        toast = ToastMessage(text: "Edits discarded.", kind: .neutral)
    }

    /// Submit edits for verification. With no backend this commits the
    /// working copy as the new baseline (clearing the dirty/fresh markers);
    /// claim statuses that are pending stay pending — they await server
    /// confirmation — so the strength caption and sticky bar still read
    /// "in review".
    public func saveAndSubmit() {
        guard var working = content, working.isDirty else { return }
        let pending = working.pendingCount
        working.title.commit()
        working.yearsInRole.commit()
        working.company.isDirty = false
        for index in working.skills.indices {
            working.skills[index].isFresh = false
        }
        for index in working.certifications.indices {
            working.certifications[index].isFresh = false
        }
        for index in working.portfolio.indices {
            working.portfolio[index].isFresh = false
        }
        for index in working.visibility.indices {
            working.visibility[index].originalOn = working.visibility[index].isOn
        }
        content = working
        baseline = working
        recompute()
        toast = ToastMessage(
            text: pending > 0
                ? "Submitted — \(pending) \(pending == 1 ? "claim" : "claims") in review."
                : "Professional profile published.",
            kind: .success
        )
    }

    // MARK: - Private

    private func mutate(_ transform: (inout ProfessionalProfileContent) -> Void) {
        guard var working = content else { return }
        transform(&working)
        content = working
        recompute()
    }

    private func recompute() {
        guard let content else { state = .loading
            return
        }
        let dirty = content.dirtyCount
        state = dirty == 0
            ? .verified(content)
            : .pending(content, dirtyCount: dirty, pendingCount: content.pendingCount)
    }
}
