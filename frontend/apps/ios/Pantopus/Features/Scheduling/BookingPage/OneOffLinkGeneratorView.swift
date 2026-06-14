//
//  OneOffLinkGeneratorView.swift
//  Pantopus
//
//  C4 One-off / Single-use Link Generator · Stream I4. A bottom-sheet form
//  that creates a private booking link for one invitee, collapsing to a
//  result card once generated. Presented locally as a sheet from C1 (and
//  rendered by the C4 routed stub). Tokens only.
//

import SwiftUI

public struct OneOffLinkGeneratorView: View {
    @State private var viewModel: OneOffLinkGeneratorViewModel
    @State private var showCopied = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    public init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: OneOffLinkGeneratorViewModel(owner: owner, push: push))
    }

    init(viewModel: OneOffLinkGeneratorViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        VStack(spacing: 0) {
            header
            content
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .overlay(alignment: .top) {
            if showCopied { CopiedToast().padding(.top, Spacing.s12) }
        }
        .accessibilityIdentifier("oneOffLinkGenerator.screen")
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text("Create a one-off link")
                    .pantopusTextStyle(.h3)
                    .foregroundStyle(Theme.Color.appText)
                Text("Send a private link for one person.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer()
            Button { dismiss() } label: {
                Icon(.x, size: 20, color: Theme.Color.appTextSecondary).frame(width: 32, height: 32)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("oneOffLinkGenerator.close")
        }
        .padding(Spacing.s4)
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            OneOffLoadingView()
        case .configuring, .generating:
            configuringForm
        case let .generated(link):
            generatedCard(link)
        case let .loadError(message):
            OneOffErrorView(message: message) { Task { await viewModel.load() } }
        }
    }

    // MARK: - Configuring

    private var configuringForm: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                if viewModel.eventTypeOptions.isEmpty {
                    BookingCard {
                        InlineNote(
                            tone: .warning,
                            text: "Create a service first so there's something to book.",
                            icon: .alertTriangle
                        )
                        CompactButton(title: "Add a service", variant: .ghost, size: .inlineAction) {
                            viewModel.createService()
                        }
                    }
                } else {
                    EventTypePickerCard(viewModel: viewModel)
                    OfferTimesCard(viewModel: viewModel)
                    ExpiryCard(viewModel: viewModel)
                    SingleUseCard(viewModel: viewModel)
                }
                if let error = viewModel.generateError {
                    InlineNote(tone: .error, text: error, icon: .alertCircle)
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.bottom, Spacing.s4)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(
                title: "Generate link",
                isLoading: viewModel.state == .generating,
                isEnabled: viewModel.canGenerate
            ) { await viewModel.generate() }
                .padding(Spacing.s4)
                .background(Theme.Color.appBg)
                .accessibilityIdentifier("oneOffLinkGenerator.generate")
        }
    }

    // MARK: - Generated

    private func generatedCard(_ link: OneOffGeneratedLink) -> some View {
        ScrollView {
            VStack(spacing: Spacing.s4) {
                BookingCard {
                    Text(link.displayURL)
                        .font(.system(size: 14, weight: .medium, design: .monospaced))
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(1)
                        .truncationMode(.middle)
                    PrimaryButton(title: "Copy") {
                        await MainActor.run {
                            BookingLinkActions.copy(link.shareURL)
                            flashCopied()
                        }
                    }
                    HStack(spacing: Spacing.s2) {
                        ShareTarget(icon: .share, title: "Share") {
                            BookingLinkActions.presentShare([link.shareURL])
                        }
                        ShareTarget(icon: .messageCircle, title: "Messages") {
                            BookingLinkActions.openMessages(with: link.shareURL, openURL: openURL)
                        }
                        ShareTarget(icon: .mail, title: "Email") {
                            BookingLinkActions.openEmail(with: link.shareURL, openURL: openURL)
                        }
                    }
                    HStack(spacing: Spacing.s1) {
                        Icon(.clock, size: 12, color: Theme.Color.appTextSecondary)
                        Text(link.caption)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, Spacing.s1)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
                }
                CompactButton(title: "Create another link", variant: .ghost, size: .footer) {
                    viewModel.reset()
                }
            }
            .padding(Spacing.s4)
        }
        .accessibilityIdentifier("oneOffLinkGenerator.generated")
    }

    private func flashCopied() {
        showCopied = true
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            showCopied = false
        }
    }
}

// MARK: - Event-type picker

private struct EventTypePickerCard: View {
    @Bindable var viewModel: OneOffLinkGeneratorViewModel

    var body: some View {
        BookingCard {
            CardOverline(text: "Service")
            Menu {
                ForEach(viewModel.eventTypeOptions) { option in
                    Button {
                        viewModel.selectEventType(option.id)
                    } label: {
                        Text("\(option.name) · \(option.durationLabel)")
                    }
                }
            } label: {
                HStack(spacing: Spacing.s3) {
                    if let selected = viewModel.selectedEventType {
                        Icon(selected.icon, size: 18, color: viewModel.theme.accent)
                            .frame(width: 32, height: 32)
                            .background(viewModel.theme.accent.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                        Text("\(selected.name) · \(selected.durationLabel)")
                            .pantopusTextStyle(.body)
                            .foregroundStyle(Theme.Color.appText)
                    } else {
                        Text("Choose a service")
                            .pantopusTextStyle(.body)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    Spacer(minLength: Spacing.s2)
                    Icon(.chevronDown, size: 16, color: Theme.Color.appTextMuted)
                }
                .contentShape(Rectangle())
            }
            .accessibilityIdentifier("oneOffLinkGenerator.eventTypePicker")
        }
    }
}

// MARK: - Offer specific times

private struct OfferTimesCard: View {
    @Bindable var viewModel: OneOffLinkGeneratorViewModel

    var body: some View {
        BookingCard {
            ToggleRow(
                title: "Offer specific times",
                isOn: Binding(
                    get: { viewModel.offerSpecificTimes },
                    set: { viewModel.setOfferSpecificTimes($0) }
                ),
                accent: viewModel.theme.accent,
                identifier: "oneOffLinkGenerator.offerTimesToggle"
            )
            if viewModel.offerSpecificTimes {
                if viewModel.slotsLoading {
                    HStack(spacing: Spacing.s2) {
                        ProgressView().controlSize(.small)
                        Text("Finding open times…")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                } else if viewModel.slotOptions.isEmpty {
                    Text("No open times in the next two weeks.")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                } else {
                    ForEach(viewModel.slotOptions) { slot in
                        SlotRow(
                            label: slot.label,
                            isSelected: viewModel.selectedSlotIds.contains(slot.id),
                            accent: viewModel.theme.accent
                        ) { viewModel.toggleSlot(slot.id) }
                    }
                }
            } else {
                Text("We'll show your full availability.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }
}

private struct SlotRow: View {
    let label: String
    let isSelected: Bool
    let accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s2) {
                Icon(isSelected ? .checkCircle : .circle, size: 18, color: isSelected ? accent : Theme.Color.appTextMuted)
                Text(label)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appText)
                Spacer(minLength: 0)
            }
            .padding(.vertical, Spacing.s1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("oneOffLinkGenerator.slotRow")
    }
}

// MARK: - Expiry

private struct ExpiryCard: View {
    @Bindable var viewModel: OneOffLinkGeneratorViewModel

    var body: some View {
        BookingCard {
            CardOverline(text: "Expires")
            FlowChips(
                options: OneOffExpiry.allCases,
                selected: viewModel.expiry,
                label: \.label,
                accent: viewModel.theme.accent
            ) { viewModel.expiry = $0 }
        }
    }
}

private struct FlowChips<Option: Hashable>: View {
    let options: [Option]
    let selected: Option
    let label: (Option) -> String
    let accent: Color
    let onSelect: (Option) -> Void

    var body: some View {
        HStack(spacing: Spacing.s2) {
            ForEach(options, id: \.self) { option in
                let isSelected = option == selected
                Button { onSelect(option) } label: {
                    Text(label(option))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(isSelected ? Theme.Color.appTextInverse : Theme.Color.appTextSecondary)
                        .padding(.horizontal, Spacing.s2)
                        .padding(.vertical, Spacing.s1)
                        .frame(maxWidth: .infinity)
                        .background(isSelected ? accent : Theme.Color.appSurfaceSunken)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .accessibilityIdentifier("oneOffLinkGenerator.expiryChips")
    }
}

// MARK: - Single use

private struct SingleUseCard: View {
    @Bindable var viewModel: OneOffLinkGeneratorViewModel

    var body: some View {
        BookingCard {
            ToggleRow(
                title: "Single use",
                isOn: $viewModel.singleUse,
                accent: viewModel.theme.accent,
                identifier: "oneOffLinkGenerator.singleUseToggle"
            )
            Text("Link stops working after one booking.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }
}

// MARK: - Shared bits

private struct ToggleRow: View {
    let title: String
    @Binding var isOn: Bool
    let accent: Color
    let identifier: String

    var body: some View {
        HStack {
            Text(title)
                .pantopusTextStyle(.body)
                .fontWeight(.medium)
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(accent)
                .accessibilityIdentifier(identifier)
        }
    }
}

private struct ShareTarget: View {
    let icon: PantopusIcon
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.s1) {
                Icon(icon, size: 18, color: Theme.Color.primary600)
                Text(title)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appText)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("oneOffLinkGenerator.shareTarget.\(title)")
    }
}

private struct CopiedToast: View {
    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.checkCircle, size: 16, color: Theme.Color.success)
            Text("Link copied")
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appText)
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        .pantopusShadow(.md)
    }
}

private struct OneOffLoadingView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            ForEach(0..<3, id: \.self) { _ in
                Shimmer(height: 60, cornerRadius: Radii.lg)
            }
            Spacer()
        }
        .padding(Spacing.s4)
        .accessibilityIdentifier("oneOffLinkGenerator.loading")
    }
}

private struct OneOffErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s4) {
            Icon(.alertCircle, size: 36, color: Theme.Color.error)
            Text(message)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            PrimaryButton(title: "Try again") { await MainActor.run { retry() } }
                .frame(maxWidth: 240)
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("oneOffLinkGenerator.error")
    }
}

#if DEBUG
#Preview("Configuring") {
    let viewModel = OneOffLinkGeneratorViewModel(owner: .personal) { _ in }
    viewModel.setStateForPreview(.configuring, options: [
        OneOffEventTypeOption(id: "et_1", name: "Intro call", durationLabel: "30 min", icon: .video, slug: "intro")
    ])
    return OneOffLinkGeneratorView(viewModel: viewModel)
}

#Preview("Generated") {
    let viewModel = OneOffLinkGeneratorViewModel(owner: .personal) { _ in }
    viewModel.setStateForPreview(
        .generated(OneOffGeneratedLink(
            displayURL: "pantopus.com/book/o/abc123",
            shareURL: "https://pantopus.com/book/o/abc123",
            caption: "Expires in 7 days · single use"
        )),
        options: []
    )
    return OneOffLinkGeneratorView(viewModel: viewModel)
}
#endif
