//
//  SupportTrainDetailViewModel.swift
//  Pantopus
//
//  A10.9 — VM for the participant-facing Support Train detail screen.
//  Distinct from the organizer-only `ReviewSignupsViewModel`. The
//  detail payload is not yet projected by the backend's
//  `GET /api/support-trains/:id` handler, so the VM resolves from a
//  deterministic stub (`SupportTrainDetailSampleData`) and chooses
//  between `populated` and `fullyCovered` off the recipient's
//  coverage fixture, keyed by `trainId`. The state machine matches
//  the established four-state mobile contract (loading / loaded /
//  empty / error); fully-covered is *not* empty — it's a celebrated
//  loaded variant.
//

import Foundation
import Observation

@Observable
@MainActor
public final class SupportTrainDetailViewModel {
    public enum State: Equatable, Sendable {
        case loading
        case loaded(SupportTrainDetailContent)
        case error(message: String)
    }

    public private(set) var state: State = .loading

    private let trainId: String
    private let resolver: @MainActor @Sendable (String) -> SupportTrainDetailContent?
    /// When a caller seeds an explicit state (previews / tests for the
    /// loading + error chrome), `load()` becomes a no-op so the seed
    /// sticks.
    private let seeded: Bool

    public init(
        trainId: String,
        resolver: @escaping @MainActor @Sendable (String) -> SupportTrainDetailContent? = SupportTrainDetailViewModel
            .defaultResolver
    ) {
        self.trainId = trainId
        self.resolver = resolver
        seeded = false
    }

    /// Seed an explicit state — used by previews and tests to exercise
    /// the loading / error chrome deterministically without a network
    /// layer. `load()` does nothing once seeded.
    public init(seedState: State, trainId: String = "seeded") {
        self.trainId = trainId
        resolver = { _ in nil }
        state = seedState
        seeded = true
    }

    /// Convenience for previews — seed with a known content payload
    /// directly.
    public convenience init(content: SupportTrainDetailContent) {
        self.init(seedState: .loaded(content), trainId: content.trainId)
    }

    public func load() async {
        guard !seeded else { return }
        if case .loading = state {} else { state = .loading }
        guard let content = resolver(trainId) else {
            state = .error(message: "Couldn't load this support train.")
            return
        }
        state = .loaded(content)
    }

    public func refresh() async {
        guard !seeded else { return }
        await load()
    }

    /// Convenience accessor used by the dock-handler hook in the view.
    public var currentContent: SupportTrainDetailContent? {
        if case let .loaded(content) = state { return content }
        return nil
    }

    public var isFullyCovered: Bool {
        currentContent?.isFullyCovered ?? false
    }

    // MARK: - Default resolver

    /// Static default resolver — returns the fully-covered fixture
    /// when the caller passes a `covered` / `full` train id, otherwise
    /// the populated fixture. Lets `SupportTrainsView` row taps drive
    /// the right variant for QA without a backend round-trip.
    @MainActor
    public static let defaultResolver: @MainActor @Sendable (String) -> SupportTrainDetailContent? = { trainId in
        let lowered = trainId.lowercased()
        if lowered.contains("covered") || lowered.contains("full") {
            return SupportTrainDetailSampleData.fullyCovered
        }
        return SupportTrainDetailSampleData.populated
    }
}
