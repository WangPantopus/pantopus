//
//  PulseComposeView.swift
//  Pantopus
//
//  Pulse compose form — replaces the `NotYetAvailableView` placeholder
//  previously routed under `HubRoute.composePost(intent:)`. Wraps a
//  `FormShell` whose body is `PulseComposeContent` and which routes
//  submit through `PulseComposeViewModel`.
//

import PhotosUI
import SwiftUI

/// Entry point for the Pulse compose flow. The `intent` argument
/// pre-selects which sub-form renders below the intent picker.
public struct PulseComposeView: View {
    @State private var viewModel: PulseComposeViewModel
    @State private var photosPickerSelection: [PhotosPickerItem] = []
    @State private var showsPhotosPicker = false
    @Environment(\.dismiss) private var dismiss

    private let onPosted: @MainActor (String?) -> Void

    public init(
        intent: PulseComposeIntent = .ask,
        identity: PulseComposeIdentity = .personal,
        onPosted: @escaping @MainActor (String?) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: PulseComposeViewModel(intent: intent, identity: identity))
        self.onPosted = onPosted
    }

    /// Test seam — accept a pre-built view-model so tests can drive
    /// state without touching the network.
    init(viewModel: PulseComposeViewModel, onPosted: @escaping @MainActor (String?) -> Void = { _ in }) {
        _viewModel = State(initialValue: viewModel)
        self.onPosted = onPosted
    }

    public var body: some View {
        FormShell(
            title: "New post",
            rightActionLabel: viewModel.activeIntent.ctaLabel,
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSubmitting,
            onClose: { dismiss() },
            onCommit: {
                Task { await viewModel.submit() }
            },
            content: {
                PulseComposeContent(
                    state: viewModel.contentState,
                    actions: actions
                )
            }
        )
        .formShakeOnChange(of: viewModel.shakeTrigger)
        .accessibilityIdentifier("composePulseShell")
        .photosPicker(
            isPresented: $showsPhotosPicker,
            selection: $photosPickerSelection,
            maxSelectionCount: pulseComposeMaxPhotos,
            matching: .images
        )
        .onChange(of: photosPickerSelection) { _, newItems in
            handlePicked(newItems)
        }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastView(message: toast)
                    .padding(.bottom, Spacing.s10)
                    .task {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.toast = nil
                    }
                    .transition(.opacity)
                    .accessibilityIdentifier("composePulseToast")
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.toast)
        .onAppear {
            Analytics.track(.screenPulseComposeViewed(intent: viewModel.activeIntent.rawValue))
        }
        .onChange(of: viewModel.shouldDismiss) { _, newValue in
            guard newValue else { return }
            let postId: String? = {
                if case let .success(id) = viewModel.state { return id }
                return nil
            }()
            viewModel.acknowledgeDismiss()
            Task {
                // Hold the success toast briefly so the user sees it.
                try? await Task.sleep(nanoseconds: 700_000_000)
                onPosted(postId)
                dismiss()
            }
        }
    }

    private var actions: PulseComposeContentActions {
        PulseComposeContentActions(
            onSelectIntent: { viewModel.selectIntent($0) },
            onSelectIdentity: { viewModel.identity = $0 },
            onSelectVisibility: { viewModel.visibility = $0 },
            onSelectLostFoundKind: { viewModel.lostFoundKind = $0 },
            onSelectAnnounceAudience: { viewModel.announceAudience = $0 },
            onSelectAskCategory: { viewModel.askCategory = $0 },
            onSelectRecommendRating: { viewModel.recommendRating = $0 },
            onUpdateField: { viewModel.update($0, to: $1) },
            onPickPhotos: { showsPhotosPicker = true },
            onRemovePhoto: { viewModel.remove(photo: $0) }
        )
    }

    private func handlePicked(_ items: [PhotosPickerItem]) {
        guard !items.isEmpty else { return }
        Task {
            var loaded: [PulseComposePhoto] = []
            for item in items.prefix(pulseComposeMaxPhotos) {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    loaded.append(PulseComposePhoto(data: data))
                }
            }
            await MainActor.run {
                viewModel.setPhotos(loaded)
                photosPickerSelection = []
            }
        }
    }
}

/// View-model → content-state projection. Lives on the view-model so
/// fixture tests can produce the same shape without `@Observable`
/// plumbing.
extension PulseComposeViewModel {
    public var contentState: PulseComposeContentState {
        PulseComposeContentState(
            activeIntent: activeIntent,
            identity: identity,
            visibility: visibility,
            lostFoundKind: lostFoundKind,
            announceAudience: announceAudience,
            askCategory: askCategory,
            recommendRating: recommendRating,
            fields: fields,
            photos: photos
        )
    }
}

#Preview {
    PulseComposeView(intent: .ask)
}
