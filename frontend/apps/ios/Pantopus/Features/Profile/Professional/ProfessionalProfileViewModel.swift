//
//  ProfessionalProfileViewModel.swift
//  Pantopus
//
//  A.5 (A13.11) / P1-F — drives the Professional Profile editor.
//
//  The production initializer hydrates from `GET /api/professional/profile/me`
//  (route `professional.js:164`) plus `GET /api/professional/verification/status`
//  (route `professional.js:372`). The backend record is intentionally thin
//  (headline / categories / pricing / verification), so the editor maps the
//  overlapping fields — title ← headline, skills ← categories, the
//  verification pill ← verification_status, and the visibility toggles ←
//  is_public / is_active. Sections the backend doesn't store on `profile/me`
//  (company name, certifications, portfolio) start empty until their
//  dedicated endpoints are wired. Save issues a best-effort `PATCH
//  /profile/me` for the safe fields (headline + public/active flags);
//  `categories` is enum-constrained server-side so free-text skills are not
//  written here.
//
//  Previews / tests still seed deterministic content via `init(seed:)` /
//  `init(simulateFailure:)`, bypassing the network.
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

    private let api: APIClient
    private let mode: Mode

    private enum Mode {
        case live
        case sample(seed: ProfessionalProfileContent, baseline: ProfessionalProfileContent)
        case failure
    }

    /// Production initializer — live `GET /api/professional/profile/me`.
    /// Public-safe: no `APIClient` parameter (the client is module-internal).
    public convenience init() {
        self.init(api: .shared)
    }

    /// Designated live initializer. `api` injectable for tests.
    init(api: APIClient) {
        self.api = api
        mode = .live
    }

    /// Sample/preview path. `baseline` defaults to `seed`; pass `.published`
    /// when seeding the pending frame so Discard rolls back to the clean copy.
    public init(seed: ProfessionalProfileContent, baseline: ProfessionalProfileContent? = nil) {
        api = .shared
        mode = .sample(seed: seed, baseline: baseline ?? seed)
    }

    /// Failure path — `load()` resolves to `.error` (previews / tests).
    public init(simulateFailure: Bool) {
        api = .shared
        mode = simulateFailure ? .failure : .live
    }

    // MARK: - Loading

    public func load() async {
        state = .loading
        switch mode {
        case .failure:
            state = .error(message: "We couldn't load your professional profile.")
        case let .sample(seed, base):
            content = seed
            baseline = base
            recompute()
        case .live:
            await fetchLive()
        }
    }

    public func refresh() async {
        await load()
    }

    private func fetchLive() async {
        do {
            let response: ProfessionalProfileResponse = try await api.request(
                ProfessionalEndpoints.profileMe()
            )
            let verification: ProfessionalVerificationStatusResponse? = try? await api.request(
                ProfessionalEndpoints.verificationStatus()
            )
            let mapped = Self.makeContent(
                from: response.profile,
                verification: verification,
                proName: currentProName()
            )
            content = mapped
            baseline = mapped
            recompute()
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription
                    ?? "We couldn't load your professional profile."
            )
        }
    }

    private func currentProName() -> String {
        if case let .signedIn(user) = AuthManager.shared.state {
            return user.displayName ?? ""
        }
        return ""
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

    /// Append a fresh trade chip. The point is to exercise the fresh-dot +
    /// verified→pending transition.
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

    /// Submit edits for verification. Commits the working copy as the new
    /// baseline (clearing dirty/fresh markers); pending claim statuses stay
    /// pending — they await server confirmation. On the live path this also
    /// fires a best-effort `PATCH /profile/me` for the safe fields.
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
        if case .live = mode { persist(working) }
        toast = ToastMessage(
            text: pending > 0
                ? "Submitted — \(pending) \(pending == 1 ? "claim" : "claims") in review."
                : "Professional profile published.",
            kind: .success
        )
    }

    /// Best-effort write of the safe, unambiguous fields.
    private func persist(_ content: ProfessionalProfileContent) {
        let request = ProfessionalProfileUpdateRequest(
            headline: content.title.value,
            isPublic: content.visibility.first { $0.id == "publicProfile" }?.isOn,
            isActive: content.visibility.first { $0.id == "activeForHire" }?.isOn
        )
        let api = api
        Task {
            _ = try? await api.request(
                ProfessionalEndpoints.updateProfileMe(request),
                as: ProfessionalProfileResponse.self
            )
        }
    }

    // MARK: - Mapping (pure — unit-test surface)

    /// Project the backend professional record into editor content. Fields
    /// the backend doesn't store on `profile/me` start empty.
    public static func makeContent(
        from dto: ProfessionalProfileDTO?,
        verification: ProfessionalVerificationStatusResponse?,
        proName: String
    ) -> ProfessionalProfileContent {
        let status = verificationStatus(dto?.verificationStatus ?? verification?.status)
        let locality = [dto?.serviceArea?.city, dto?.serviceArea?.state]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
        let skills = (dto?.categories ?? []).map {
            ProSkill(id: $0, label: categoryLabel($0), icon: categoryIcon($0))
        }
        return ProfessionalProfileContent(
            proName: proName,
            strength: strength(for: dto),
            title: FormFieldState(id: "title", originalValue: dto?.headline ?? ""),
            yearsInRole: FormFieldState(id: "yearsInRole", originalValue: ""),
            company: CompanyClaim(name: "", locality: locality, status: status),
            skills: skills,
            certifications: [],
            portfolio: [],
            visibility: visibilityRows(isPublic: dto?.isPublic ?? false, isActive: dto?.isActive ?? false)
        )
    }

    static func verificationStatus(_ raw: String?) -> ProVerificationStatus {
        switch raw {
        case "verified": .verified
        case "pending": .pending
        default: .unverified
        }
    }

    /// `pet_care` → `Pet Care`.
    static func categoryLabel(_ key: String) -> String {
        key.split(separator: "_")
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
            .joined(separator: " ")
    }

    static func categoryIcon(_ key: String) -> PantopusIcon {
        switch key {
        case "plumber": .droplet
        case "electrician": .zap
        case "carpentry": .hammer
        case "cleaning": .sparkles
        case "pet_care", "childcare", "elder_care": .users
        default: .wrench
        }
    }

    /// Coarse 0–100 completeness heuristic — the backend record has no
    /// strength field, so it's derived from filled fields + verification.
    static func strength(for dto: ProfessionalProfileDTO?) -> Int {
        guard let dto else { return 0 }
        var score = 40
        if !(dto.headline ?? "").isEmpty { score += 15 }
        if !(dto.bio ?? "").isEmpty { score += 10 }
        if !(dto.categories ?? []).isEmpty { score += 15 }
        switch dto.verificationStatus {
        case "verified": score += 20
        case "pending": score += 10
        default: break
        }
        return min(score, 100)
    }

    private static func visibilityRows(isPublic: Bool, isActive: Bool) -> [ProVisibilityRow] {
        [
            ProVisibilityRow(
                id: "publicProfile",
                label: "Public profile",
                sub: "Neighbors can open your professional profile from search and gigs.",
                isOn: isPublic
            ),
            ProVisibilityRow(
                id: "activeForHire",
                label: "Active for hire",
                sub: "Show as available to take on new work.",
                isOn: isActive
            )
        ]
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
