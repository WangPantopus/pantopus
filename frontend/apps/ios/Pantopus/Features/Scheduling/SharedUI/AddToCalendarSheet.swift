//
//  AddToCalendarSheet.swift
//  Pantopus
//
//  Foundation (I0b) — D8 Add to calendar. Slides up over any booking/event
//  detail with a one-line event recap, then a vertical list of provider rows
//  (Apple Calendar, Google, Outlook, Download .ics) in the Gig Picker Sheets
//  idiom. The .ics row fetches the raw file via `APIClient.requestData` and
//  hands the `Data` back for a system share/save. Tokens only.
//

import SwiftUI

/// Fetches the booking's `.ics` artifact on demand. Internal because it depends
/// on the internal `APIClient`; consumed within the app module.
@Observable
@MainActor
final class AddToCalendarViewModel {
    enum Phase: Equatable { case idle, generating, ready, failed }

    private(set) var phase: Phase = .idle
    private(set) var icsData: Data?

    let manageToken: String
    private let client: APIClient

    init(manageToken: String, client: APIClient) {
        self.manageToken = manageToken
        self.client = client
    }

    /// Downloads the raw `.ics` for the booking. Returns the data so the caller
    /// can present a share sheet; also cached on `icsData`.
    @discardableResult
    func downloadICS() async -> Data? {
        phase = .generating
        do {
            let data = try await client.requestData(SchedulingPublicEndpoints.ics(token: manageToken))
            icsData = data
            phase = .ready
            return data
        } catch {
            phase = .failed
            return nil
        }
    }
}

/// The add-to-calendar action sheet. Provider hand-offs (EventKit / Google /
/// Outlook) are wired by the presenting stream via the callbacks.
struct AddToCalendarSheet: View {
    @State private var viewModel: AddToCalendarViewModel

    private let eventRecap: String
    private let onAppleCalendar: () -> Void
    private let onGoogle: () -> Void
    private let onOutlook: () -> Void
    private let onICSReady: (Data) -> Void
    private let onDone: () -> Void

    init(
        viewModel: AddToCalendarViewModel,
        eventRecap: String,
        onAppleCalendar: @escaping () -> Void,
        onGoogle: @escaping () -> Void,
        onOutlook: @escaping () -> Void,
        onICSReady: @escaping (Data) -> Void,
        onDone: @escaping () -> Void
    ) {
        _viewModel = State(wrappedValue: viewModel)
        self.eventRecap = eventRecap
        self.onAppleCalendar = onAppleCalendar
        self.onGoogle = onGoogle
        self.onOutlook = onOutlook
        self.onICSReady = onICSReady
        self.onDone = onDone
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text("Add to your calendar")
                    .pantopusTextStyle(.h3)
                    .foregroundStyle(Theme.Color.appText)
                Text(eventRecap)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }

            VStack(spacing: 0) {
                providerRow(icon: .calendar, label: "Apple Calendar", sub: "Add with one tap", action: onAppleCalendar)
                Divider().background(Theme.Color.appBorderSubtle)
                providerRow(icon: .calendar, label: "Google Calendar", sub: "Open in browser", action: onGoogle)
                Divider().background(Theme.Color.appBorderSubtle)
                providerRow(icon: .calendar, label: "Outlook", sub: "Open in browser", action: onOutlook)
                Divider().background(Theme.Color.appBorderSubtle)
                icsRow
            }
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))

            Text("We'll add the event with the join link and a reminder.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextMuted)

            GhostButton(title: "Done", action: onDone)
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("scheduling.addToCalendarSheet")
    }

    @ViewBuilder
    private var icsRow: some View {
        let isGenerating = viewModel.phase == .generating
        Button {
            Task { @MainActor in
                if let data = await viewModel.downloadICS() {
                    onICSReady(data)
                }
            }
        } label: {
            rowContent(
                icon: .download,
                label: viewModel.phase == .ready ? "Saved .ics file" : "Download .ics file",
                sub: isGenerating ? "Preparing your file" : "For any other calendar",
                trailing: isGenerating ? .spinner : (viewModel.phase == .ready ? .check : .chevron)
            )
        }
        .buttonStyle(.plain)
        .disabled(isGenerating)
    }

    private func providerRow(icon: PantopusIcon, label: String, sub: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            rowContent(icon: icon, label: label, sub: sub, trailing: .chevron)
        }
        .buttonStyle(.plain)
    }

    private enum Trailing { case chevron, spinner, check }

    private func rowContent(icon: PantopusIcon, label: String, sub: String, trailing: Trailing) -> some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.primary50).frame(width: 36, height: 36)
                Icon(icon, size: 18, color: Theme.Color.primary600)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Text(sub)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            Spacer(minLength: Spacing.s2)
            switch trailing {
            case .chevron: Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
            case .spinner: ProgressView().controlSize(.small)
            case .check: Icon(.checkCircle, size: 18, color: Theme.Color.success)
            }
        }
        .padding(.horizontal, Spacing.s4)
        .frame(minHeight: 56)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

#if DEBUG
#Preview {
    AddToCalendarSheet(
        viewModel: AddToCalendarViewModel(manageToken: "preview", client: .shared),
        eventRecap: "Intro call · Jul 1 · 11:00 AM PDT",
        onAppleCalendar: {},
        onGoogle: {},
        onOutlook: {},
        onICSReady: { _ in },
        onDone: {}
    )
}
#endif
