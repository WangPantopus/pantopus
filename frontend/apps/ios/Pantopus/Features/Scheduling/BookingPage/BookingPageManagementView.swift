//
//  BookingPageManagementView.swift
//  Pantopus
//
//  C1 Booking Link / Public Page Management · Stream I4. A FormShell-based
//  editor for the owner's public booking page: live/paused status, the public
//  slug with live availability check, header fields, per-service visibility,
//  intro/confirmation copy, page visibility, a payments entry, and the
//  share/copy/preview footer. Presents C3 (ShareLinkSheet), C4 (one-off
//  generator) and C2 (preview) locally. Tokens only.
//
// swiftlint:disable file_length

import SwiftUI

public struct BookingPageManagementView: View {
    @State private var viewModel: BookingPageManagementViewModel
    @State private var activeSheet: ManagementSheet?
    @State private var previewRequest: PreviewRequest?
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    public init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        _viewModel = State(initialValue: BookingPageManagementViewModel(owner: owner, push: push))
    }

    /// Test/preview seam.
    init(viewModel: BookingPageManagementViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        content
            .background(Theme.Color.appBg)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .toolbar(.hidden, for: .navigationBar)
            .task { await viewModel.load() }
            .sheet(item: $activeSheet, content: sheetBody)
            .fullScreenCover(item: $previewRequest) { request in
                BookingPagePreviewView(owner: viewModel.owner, slug: request.slug)
            }
            .accessibilityIdentifier("bookingPageManagement.screen")
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            ManagementLoadingView()
        case .loaded:
            form
        case .empty:
            EmptyState(
                icon: .link,
                headline: "Set up your booking link",
                subcopy: "Create a link people can use to book time with you.",
                cta: EmptyState.CTA(title: "Set up booking link") {
                    await MainActor.run { viewModel.push(.firstRunWizard(owner: viewModel.owner)) }
                },
                tint: viewModel.theme.accentBg,
                accent: viewModel.theme.accent
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.Color.appBg)
            .accessibilityIdentifier("bookingPageManagement.empty")
        case let .error(message):
            ManagementErrorView(message: message) { Task { await viewModel.refresh() } }
        }
    }

    // MARK: - Loaded form

    private var form: some View {
        FormShell(
            title: "Booking link",
            leading: .back,
            rightActionLabel: nil,
            bottomActionLabel: "Save changes",
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: { dismiss() },
            onCommit: { Task { await viewModel.save() } },
            content: {
                Group {
                    IdentityHeaderRow(theme: viewModel.theme)
                    StatusCard(viewModel: viewModel)
                    SlugCard(viewModel: viewModel)
                    HeaderFieldsCard(viewModel: viewModel)
                    ServiceVisibilityCard(viewModel: viewModel)
                    CopyCard(viewModel: viewModel)
                    navRows
                    VisibilityCard(viewModel: viewModel)
                    if viewModel.showPaymentsRow { paymentsRow }
                    footerButtons
                    if let saveError = viewModel.saveError {
                        InlineNote(tone: .error, text: saveError, icon: .alertCircle)
                    }
                }
                .padding(.horizontal, Spacing.s4)
            }
        )
        .overlay(alignment: .top) {
            if viewModel.showSavedToast { SavedToast().padding(.top, Spacing.s2) }
        }
    }

    private var navRows: some View {
        BookingCard(padding: Spacing.s2) {
            NavRow(icon: .helpCircle, title: "Intake questions", subtitle: "Set per service") {
                viewModel.openIntakeQuestions()
            }
        }
    }

    private var paymentsRow: some View {
        BookingCard(padding: Spacing.s2) {
            NavRow(
                icon: .creditCard,
                title: "Connect payments",
                subtitle: "Connect Stripe to take paid bookings"
            ) { viewModel.openPayments() }
        }
    }

    private var footerButtons: some View {
        HStack(spacing: Spacing.s2) {
            FooterButton(icon: .copy, title: "Copy link") {
                BookingLinkActions.copy(viewModel.shareURL)
            }
            FooterButton(icon: .share, title: "Share") {
                activeSheet = .share
            }
            FooterButton(icon: .eye, title: "Preview") {
                previewRequest = PreviewRequest(slug: viewModel.savedSlug)
            }
        }
    }

    // MARK: - Sheets

    @ViewBuilder private func sheetBody(_ sheet: ManagementSheet) -> some View {
        switch sheet {
        case .share:
            ShareLinkSheet(
                url: viewModel.savedDisplayURL,
                theme: viewModel.theme,
                isLive: viewModel.isAcceptingBookings,
                showOnProfile: viewModel.showOnProfile,
                addToSignature: viewModel.addToSignature,
                onCopy: {},
                onShare: { BookingLinkActions.presentShare([viewModel.shareURL]) },
                onMessages: { BookingLinkActions.openMessages(with: viewModel.shareURL, openURL: openURL) },
                onEmail: { BookingLinkActions.openEmail(with: viewModel.shareURL, openURL: openURL) },
                onToggleShowOnProfile: { value in
                    Task { @MainActor in viewModel.showOnProfile = value }
                },
                onToggleSignature: { value in
                    Task { @MainActor in viewModel.addToSignature = value }
                },
                onRegenerate: { Task { await viewModel.regenerateLink() } },
                onTurnOnPage: viewModel.isAcceptingBookings ? nil : { Task { await viewModel.turnOnPage() } }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        case .oneOff:
            OneOffLinkGeneratorView(owner: viewModel.owner)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    enum ManagementSheet: Identifiable {
        case share
        case oneOff
        var id: String { self == .share ? "share" : "oneOff" }
    }

    struct PreviewRequest: Identifiable {
        let slug: String
        var id: String { slug }
    }
}

// MARK: - Identity header

private struct IdentityHeaderRow: View {
    let theme: SchedulingIdentityTheme

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Circle().fill(theme.accent).frame(width: 8, height: 8)
            Text("\(theme.title) booking link")
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(theme.accent)
            Spacer()
        }
        .accessibilityIdentifier("bookingPageManagement.identity")
    }
}

// MARK: - Status card

private struct StatusCard: View {
    @Bindable var viewModel: BookingPageManagementViewModel

    var body: some View {
        BookingCard {
            CardOverline(text: "Status")
            HStack(alignment: .center, spacing: Spacing.s3) {
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    SchedulingStatusPill(viewModel.isAcceptingBookings ? .active : .paused)
                    Text(viewModel.isAcceptingBookings
                        ? "Anyone with the link can book you."
                        : "Page is paused. People see a short note and cannot book.")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: Spacing.s2)
                Toggle("", isOn: Binding(
                    get: { viewModel.isAcceptingBookings },
                    set: { value in Task { await viewModel.setAcceptingBookings(value) } }
                ))
                .labelsHidden()
                .tint(viewModel.theme.accent)
                .accessibilityIdentifier("bookingPageManagement.statusToggle")
            }
            if !viewModel.isAcceptingBookings {
                InlineNote(
                    tone: .warning,
                    text: "Resume to start taking bookings again.",
                    icon: .pause
                )
            }
        }
    }
}

// MARK: - Slug card

private struct SlugCard: View {
    @Bindable var viewModel: BookingPageManagementViewModel

    var body: some View {
        BookingCard {
            CardOverline(text: "Your link")
            HStack(spacing: 0) {
                Text("\(BookingLinkURL.displayOrigin)/book/")
                    .font(.system(size: 14, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                TextField("your-handle", text: $viewModel.slugText)
                    .font(.system(size: 14, weight: .semibold, design: .monospaced))
                    .foregroundStyle(Theme.Color.appText)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onChange(of: viewModel.slugText) { _, _ in viewModel.slugTextChanged() }
                    .accessibilityIdentifier("bookingPageManagement.slugField")
                trailingStatusIcon
            }
            .padding(Spacing.s3)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            slugStatusLine
        }
    }

    @ViewBuilder private var trailingStatusIcon: some View {
        switch viewModel.slugState {
        case .checking:
            ProgressView().controlSize(.small)
        case .available:
            Icon(.checkCircle, size: 16, color: Theme.Color.success)
        case .taken, .invalid:
            Icon(.alertCircle, size: 16, color: Theme.Color.error)
        case .unchanged:
            EmptyView()
        }
    }

    @ViewBuilder private var slugStatusLine: some View {
        switch viewModel.slugState {
        case .available:
            statusText("Available", color: Theme.Color.success, icon: .check)
        case .checking:
            statusText("Checking…", color: Theme.Color.appTextSecondary, icon: nil)
        case let .invalid(message):
            statusText(message, color: Theme.Color.error, icon: .alertCircle)
        case let .taken(suggestions):
            VStack(alignment: .leading, spacing: Spacing.s2) {
                statusText("That handle is taken. Try another.", color: Theme.Color.error, icon: .alertCircle)
                if !suggestions.isEmpty {
                    SuggestionChips(suggestions: suggestions) { viewModel.applySuggestion($0) }
                }
            }
        case .unchanged:
            EmptyView()
        }
    }

    @ViewBuilder private func statusText(_ text: String, color: Color, icon: PantopusIcon?) -> some View {
        HStack(spacing: Spacing.s1) {
            if let icon { Icon(icon, size: 13, color: color) }
            Text(text)
                .pantopusTextStyle(.caption)
                .foregroundStyle(color)
        }
        .accessibilityIdentifier("bookingPageManagement.slugStatus")
    }
}

private struct SuggestionChips: View {
    let suggestions: [String]
    let onTap: (String) -> Void

    var body: some View {
        HStack(spacing: Spacing.s2) {
            ForEach(suggestions, id: \.self) { suggestion in
                Button { onTap(suggestion) } label: {
                    Text(suggestion)
                        .font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundStyle(Theme.Color.primary600)
                        .padding(.horizontal, Spacing.s3)
                        .padding(.vertical, Spacing.s1)
                        .background(Theme.Color.primary50)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("bookingPageManagement.slugSuggestion")
            }
            Spacer(minLength: 0)
        }
    }
}

// MARK: - Header fields

private struct HeaderFieldsCard: View {
    @Bindable var viewModel: BookingPageManagementViewModel

    var body: some View {
        BookingCard {
            CardOverline(text: "Page header")
            HStack(spacing: Spacing.s3) {
                BookingAvatar(name: viewModel.titleText.isEmpty ? "You" : viewModel.titleText,
                              size: 48, accent: viewModel.theme.accent)
                LabeledField(label: "Display name", placeholder: "Your name", text: $viewModel.titleText,
                             identifier: "bookingPageManagement.titleField")
            }
            LabeledField(label: "Tagline", placeholder: "What you do", text: $viewModel.taglineText,
                         identifier: "bookingPageManagement.taglineField")
        }
    }
}

// MARK: - Service visibility

private struct ServiceVisibilityCard: View {
    @Bindable var viewModel: BookingPageManagementViewModel

    var body: some View {
        BookingCard {
            CardOverline(text: "Services people can book")
            if viewModel.serviceRows.isEmpty {
                InlineNote(
                    tone: .warning,
                    text: "Turn on at least one service so people can book.",
                    icon: .alertTriangle
                )
                CompactButton(title: "Add a service", variant: .ghost, size: .inlineAction) {
                    viewModel.createService()
                }
            } else {
                ForEach(viewModel.serviceRows) { row in
                    ServiceRow(row: row, accent: viewModel.theme.accent) { visible in
                        Task { await viewModel.setServiceVisible(eventTypeId: row.id, visible: visible) }
                    } onEdit: {
                        viewModel.editService(row.id)
                    }
                }
                if !viewModel.hasVisibleService {
                    InlineNote(
                        tone: .warning,
                        text: "No services are visible. Turn one on so people can book.",
                        icon: .alertTriangle
                    )
                }
            }
        }
    }
}

private struct ServiceRow: View {
    let row: BookingServiceRow
    let accent: Color
    let onToggle: (Bool) -> Void
    let onEdit: () -> Void

    var body: some View {
        HStack(spacing: Spacing.s3) {
            Button(action: onEdit) {
                HStack(spacing: Spacing.s3) {
                    Icon(row.locationIcon, size: 18, color: accent)
                        .frame(width: 32, height: 32)
                        .background(accent.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(row.name)
                            .pantopusTextStyle(.body)
                            .fontWeight(.medium)
                            .foregroundStyle(Theme.Color.appText)
                            .lineLimit(1)
                        Text(row.durationLabel)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    Spacer(minLength: Spacing.s2)
                }
            }
            .buttonStyle(.plain)
            Toggle("", isOn: Binding(get: { row.isVisible }, set: onToggle))
                .labelsHidden()
                .tint(accent)
                .accessibilityIdentifier("bookingPageManagement.serviceToggle.\(row.id)")
        }
        .padding(.vertical, Spacing.s1)
    }
}

// MARK: - Intro & confirmation

private struct CopyCard: View {
    @Bindable var viewModel: BookingPageManagementViewModel

    var body: some View {
        BookingCard {
            CardOverline(text: "Intro & confirmation")
            MultilineField(label: "Intro", placeholder: "A short welcome shown on your page",
                           text: $viewModel.introText, identifier: "bookingPageManagement.introField")
            MultilineField(label: "Confirmation message", placeholder: "Shown after someone books",
                           text: $viewModel.confirmationText, identifier: "bookingPageManagement.confirmationField")
        }
    }
}

// MARK: - Page visibility

private struct VisibilityCard: View {
    @Bindable var viewModel: BookingPageManagementViewModel

    var body: some View {
        BookingCard {
            CardOverline(text: "Visibility")
            SegmentedPair(
                options: [("Listed", BookingPageVisibility.listed), ("Link-only", .unlisted)],
                selection: $viewModel.visibility,
                accent: viewModel.theme.accent
            )
            Text(viewModel.visibility == .listed
                ? "Shown on your profile and discoverable."
                : "Only people with the link can find it.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }
}

private struct SegmentedPair<Value: Equatable>: View {
    let options: [(String, Value)]
    @Binding var selection: Value
    let accent: Color

    var body: some View {
        HStack(spacing: Spacing.s1) {
            ForEach(options.indices, id: \.self) { index in
                let option = options[index]
                let isSelected = selection == option.1
                Button { selection = option.1 } label: {
                    Text(option.0)
                        .pantopusTextStyle(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(isSelected ? Theme.Color.appTextInverse : Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.s2)
                        .background(isSelected ? accent : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("bookingPageManagement.visibility.\(option.0)")
            }
        }
        .padding(Spacing.s1)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }
}

// MARK: - Shared field bits

private struct LabeledField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let identifier: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text(label)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextField(placeholder, text: $text)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
                .padding(Spacing.s3)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .accessibilityIdentifier(identifier)
        }
    }
}

private struct MultilineField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let identifier: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text(label)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextField(placeholder, text: $text, axis: .vertical)
                .lineLimit(2 ... 5)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
                .padding(Spacing.s3)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .accessibilityIdentifier(identifier)
        }
    }
}

private struct NavRow: View {
    let icon: PantopusIcon
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                Icon(icon, size: 18, color: Theme.Color.appTextSecondary)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .pantopusTextStyle(.body)
                        .fontWeight(.medium)
                        .foregroundStyle(Theme.Color.appText)
                    Text(subtitle)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer(minLength: Spacing.s2)
                Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
            }
            .padding(Spacing.s2)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct FooterButton: View {
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
            .padding(.vertical, Spacing.s3)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("bookingPageManagement.footer.\(title)")
    }
}

// MARK: - Toast / loading / error

private struct SavedToast: View {
    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.checkCircle, size: 16, color: Theme.Color.success)
            Text("Saved")
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appText)
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        .pantopusShadow(.md)
        .accessibilityIdentifier("bookingPageManagement.savedToast")
    }
}

private struct ManagementLoadingView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                ForEach(0 ..< 4, id: \.self) { _ in
                    VStack(alignment: .leading, spacing: Spacing.s2) {
                        Shimmer(width: 90, height: 11, cornerRadius: Radii.sm)
                        Shimmer(height: 52, cornerRadius: Radii.lg)
                    }
                }
            }
            .padding(Spacing.s4)
        }
        .accessibilityIdentifier("bookingPageManagement.loading")
    }
}

private struct ManagementErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s4) {
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load your booking page")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.center)
            Text(message)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            PrimaryButton(title: "Try again") { await MainActor.run { retry() } }
                .frame(maxWidth: 240)
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("bookingPageManagement.error")
    }
}

#if DEBUG
#Preview("Loaded") {
    let viewModel = BookingPageManagementViewModel(owner: .personal, push: { _ in })
    viewModel.hydrateForPreview(page: BookingPageSampleData.livePage, eventTypes: BookingPageSampleData.eventTypes)
    return NavigationStack { BookingPageManagementView(viewModel: viewModel) }
}
#endif
// swiftlint:enable file_length
