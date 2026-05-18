//
//  ReviewClaimDetailView.swift
//  Pantopus
//
//  P1.1 — Admin claim-detail screen. Reads a claim id, renders four
//  sections (Home · Claimant · Claim Details · Evidence) and surfaces
//  Approve / Reject / Request Info actions for claims in a reviewable
//  state. Mirrors the right-pane overlay on the web review-claims page.
//

import SwiftUI

// swiftlint:disable file_length type_body_length function_body_length

public struct ReviewClaimDetailView: View {
    @State private var viewModel: ReviewClaimDetailViewModel
    @State private var showRejectSheet = false
    @State private var rejectNote: String = ""
    @State private var showRequestInfoSheet = false
    @State private var requestInfoNote: String = "Please upload additional documents."
    private let onClose: @MainActor () -> Void

    public init(
        viewModel: ReviewClaimDetailViewModel,
        onClose: @escaping @MainActor () -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onClose = onClose
    }

    public var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                loadingShell
            case let .loaded(detail):
                loadedShell(detail)
            case let .error(message):
                errorShell(message: message)
            }
        }
        .accessibilityIdentifier("reviewClaimDetail")
        .navigationBarBackButtonHidden(true)
        .task { await viewModel.load() }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastView(message: toast)
                    .padding(.bottom, Spacing.s10)
                    .task {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.clearToast()
                    }
            }
        }
        .sheet(isPresented: $showRejectSheet) {
            NoteCaptureSheet(
                title: "Reject claim",
                body: "Optionally include a reason — the claimant sees this in their notification.",
                placeholder: "e.g. The deed doesn't match the address.",
                primaryTitle: "Reject claim",
                primaryRole: .destructive,
                note: $rejectNote,
                isSubmitting: viewModel.reviewingAction == .reject
            ) {
                Task {
                    let ok = await viewModel.review(.reject, note: rejectNote.isEmpty ? nil : rejectNote)
                    if ok { showRejectSheet = false; rejectNote = "" }
                }
            } onCancel: {
                showRejectSheet = false
                rejectNote = ""
            }
        }
        .sheet(isPresented: $showRequestInfoSheet) {
            NoteCaptureSheet(
                title: "Request more info",
                body: "Tell the claimant what's missing — they'll receive a notification.",
                placeholder: "e.g. Please upload a recent utility bill.",
                primaryTitle: "Send request",
                primaryRole: nil,
                note: $requestInfoNote,
                isSubmitting: viewModel.reviewingAction == .requestMoreInfo
            ) {
                Task {
                    let ok = await viewModel.review(.requestMoreInfo, note: requestInfoNote.isEmpty ? nil : requestInfoNote)
                    if ok { showRequestInfoSheet = false }
                }
            } onCancel: {
                showRequestInfoSheet = false
            }
        }
    }

    // MARK: - Shells

    private var loadingShell: some View {
        ContentDetailShell(
            title: "Review claim",
            onBack: onClose,
            header: {
                Shimmer(height: 96, cornerRadius: Radii.xl)
                    .padding(.horizontal, Spacing.s4)
            },
            body: {
                VStack(spacing: Spacing.s3) {
                    Shimmer(height: 96, cornerRadius: Radii.xl)
                    Shimmer(height: 160, cornerRadius: Radii.xl)
                    Shimmer(height: 200, cornerRadius: Radii.xl)
                }
                .padding(.horizontal, Spacing.s4)
            }
        )
    }

    private func errorShell(message: String) -> some View {
        ContentDetailShell(
            title: "Review claim",
            onBack: onClose,
            header: { EmptyView() },
            body: {
                EmptyState(
                    icon: .alertCircle,
                    headline: "Couldn't load this claim",
                    subcopy: message,
                    cta: EmptyState.CTA(title: "Try again") {
                        await viewModel.load()
                    }
                )
                .padding(.horizontal, Spacing.s4)
            }
        )
    }

    @ViewBuilder
    private func loadedShell(_ detail: AdminClaimDetailResponse) -> some View {
        let isReviewable = Self.reviewableStates.contains(detail.claim.state)
        ContentDetailShell(
            title: "Review claim",
            onBack: onClose,
            header: {
                HomeCard(home: detail.home)
                    .padding(.horizontal, Spacing.s4)
                    .accessibilityIdentifier("reviewClaimDetail_home")
            },
            body: {
                VStack(alignment: .leading, spacing: Spacing.s5) {
                    OverlineSection(title: "Claimant") {
                        ClaimantCard(claimant: detail.claimant)
                            .accessibilityIdentifier("reviewClaimDetail_claimant")
                    }
                    OverlineSection(title: "Claim details") {
                        DetailGrid(claim: detail.claim)
                            .accessibilityIdentifier("reviewClaimDetail_grid")
                    }
                    OverlineSection(title: "Evidence (\(detail.evidence.count))") {
                        EvidenceList(evidence: detail.evidence)
                            .accessibilityIdentifier("reviewClaimDetail_evidence")
                    }
                    if !isReviewable {
                        TerminalStateBanner(state: detail.claim.state)
                            .accessibilityIdentifier("reviewClaimDetail_terminal")
                    }
                }
                .padding(.horizontal, Spacing.s4)
            },
            cta: {
                if isReviewable {
                    ActionFooter(
                        reviewingAction: viewModel.reviewingAction,
                        onApprove: {
                            Task { _ = await viewModel.review(.approve) }
                        },
                        onReject: { showRejectSheet = true },
                        onRequestInfo: { showRequestInfoSheet = true }
                    )
                } else {
                    EmptyView()
                }
            }
        )
    }

    private static let reviewableStates: Set<String> = [
        "submitted",
        "pending_review",
        "needs_more_info",
        "pending_challenge_window",
        "disputed"
    ]
}

// MARK: - Section header

private struct OverlineSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(title)
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.06 * 11) // overline tracking +0.06em
                .textCase(.uppercase)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
            content()
        }
    }
}

// MARK: - Home card

private struct HomeCard: View {
    let home: AdminClaimHomeDTO?

    var body: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.md)
                    .fill(Theme.Color.businessBg)
                    .frame(width: 40, height: 40)
                Icon(.mapPin, size: 20, color: Theme.Color.business)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(home?.name?.isEmpty == false ? (home?.name ?? "") : (home?.address ?? "Unknown home"))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                let extras = [home?.address, home?.city, home?.state, home?.zipcode]
                    .compactMap { $0?.isEmpty == false ? $0 : nil }
                if !extras.isEmpty {
                    Text(extras.joined(separator: ", "))
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
    }
}

// MARK: - Claimant card

private struct ClaimantCard: View {
    let claimant: AdminClaimUserDTO?

    var body: some View {
        HStack(spacing: Spacing.s3) {
            let name = claimant?.name ?? claimant?.username ?? "Unknown"
            let gradient = AdminClaimAvatarGradient.gradient(for: claimant?.id ?? name)
            AvatarTile(
                name: name,
                imageURL: claimant?.profilePictureURL.flatMap(URL.init(string:)),
                gradient: gradient
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                if let email = claimant?.email, !email.isEmpty {
                    Text(email)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
                if let createdAt = claimant?.createdAt, !createdAt.isEmpty {
                    Text("Account created: \(AdminClaimTimeFormat.longDate(createdAt))")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
    }
}

private struct AvatarTile: View {
    let name: String
    let imageURL: URL?
    let gradient: GradientPair

    var body: some View {
        ZStack {
            Circle()
                .fill(LinearGradient(
                    colors: [gradient.start, gradient.end],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ))
                .frame(width: 40, height: 40)
            if let url = imageURL {
                AsyncImage(url: url) { phase in
                    if case let .success(image) = phase {
                        image.resizable().scaledToFill()
                    } else {
                        initialsText
                    }
                }
                .frame(width: 40, height: 40)
                .clipShape(Circle())
            } else {
                initialsText
            }
        }
    }

    private var initialsText: some View {
        Text(initials)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
    }

    private var initials: String {
        let parts = name.split(separator: " ").prefix(2)
        return parts.map { String($0.first ?? Character("")) }.joined().uppercased()
    }
}

// MARK: - Detail grid

private struct DetailGrid: View {
    let claim: AdminClaimRecordDTO

    private struct Item: Identifiable {
        let id = UUID()
        let label: String
        let value: String
        let danger: Bool
    }

    var body: some View {
        let items: [Item] = [
            Item(
                label: "Type",
                value: friendlyType(claim.claimType),
                danger: false
            ),
            Item(
                label: "Method",
                value: AdminClaimMethodLabel.display(for: claim.method),
                danger: false
            ),
            Item(
                label: "Risk score",
                value: claim.riskScore.map(String.init) ?? "—",
                danger: (claim.riskScore ?? 0) > 50
            ),
            Item(
                label: "Submitted",
                value: AdminClaimTimeFormat.longDate(claim.createdAt),
                danger: false
            )
        ]
        let columns = [
            GridItem(.flexible(), spacing: Spacing.s2),
            GridItem(.flexible(), spacing: Spacing.s2)
        ]
        return LazyVGrid(columns: columns, spacing: Spacing.s2) {
            ForEach(items) { item in
                GridTile(label: item.label, value: item.value, danger: item.danger)
            }
        }
    }

    private func friendlyType(_ type: String?) -> String {
        switch type {
        case "owner": "Ownership"
        case "resident": "Residency"
        case let other?: other.capitalized
        case nil: "Unknown"
        }
    }
}

private struct GridTile: View {
    let label: String
    let value: String
    let danger: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextMuted)
            Text(value)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(danger ? Theme.Color.error : Theme.Color.appText)
                .lineLimit(1)
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
    }
}

// MARK: - Evidence list

private struct EvidenceList: View {
    let evidence: [AdminClaimEvidenceDTO]

    var body: some View {
        if evidence.isEmpty {
            HStack(spacing: Spacing.s2) {
                Icon(.alertCircle, size: 20, color: Theme.Color.warning)
                Text("No documents uploaded yet")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.Color.warning)
                Spacer(minLength: 0)
            }
            .padding(Spacing.s3)
            .background(Theme.Color.warningBg)
            .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        } else {
            VStack(spacing: Spacing.s2) {
                ForEach(evidence) { item in
                    EvidenceRow(item: item)
                }
            }
        }
    }
}

private struct EvidenceRow: View {
    let item: AdminClaimEvidenceDTO

    var body: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.md)
                    .fill(Theme.Color.businessBg)
                    .frame(width: 40, height: 40)
                Icon(isImage ? .file : .fileText, size: 20, color: Theme.Color.business)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(AdminClaimEvidenceLabel.display(for: item.evidenceType))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                if let name = item.fileName, !name.isEmpty {
                    Text(name)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
                Text(metaLine)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            Spacer(minLength: 0)
            if let urlString = item.fileURL, let url = URL(string: urlString) {
                Link(destination: url) {
                    Text("View")
                        .font(.system(size: 12, weight: .semibold))
                        .padding(.horizontal, Spacing.s3)
                        .frame(height: 32)
                        .foregroundStyle(Theme.Color.primary600)
                        .background(Theme.Color.primary50)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.md))
                }
                .accessibilityLabel("View \(AdminClaimEvidenceLabel.display(for: item.evidenceType))")
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
    }

    private var isImage: Bool {
        item.mimeType?.hasPrefix("image/") == true
    }

    private var metaLine: String {
        var parts: [String] = []
        if let size = item.fileSize, size > 0 {
            parts.append("\(size / 1_024) KB")
        }
        parts.append(AdminClaimTimeFormat.longDate(item.createdAt))
        return parts.joined(separator: " · ")
    }
}

// MARK: - Terminal-state banner

private struct TerminalStateBanner: View {
    let state: String

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.info, size: 20, color: Theme.Color.appTextSecondary)
            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appTextSecondary)
            Spacer(minLength: 0)
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
    }

    private var message: String {
        switch state {
        case "approved": "This claim has been approved. No further action."
        case "rejected": "This claim has been rejected. No further action."
        default: "This claim is in state \"\(state)\" and isn't reviewable from here."
        }
    }
}

// MARK: - Action footer

private struct ActionFooter: View {
    let reviewingAction: AdminClaimReviewAction?
    let onApprove: () -> Void
    let onReject: () -> Void
    let onRequestInfo: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s2) {
            Button(action: onApprove) {
                HStack(spacing: Spacing.s2) {
                    if reviewingAction == .approve {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Theme.Color.appTextInverse))
                    } else {
                        Icon(.checkCircle, size: 18, color: Theme.Color.appTextInverse)
                    }
                    Text(reviewingAction == .approve ? "Approving…" : "Approve")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(Theme.Color.success)
                .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
            }
            .buttonStyle(.plain)
            .disabled(reviewingAction != nil)
            .accessibilityIdentifier("reviewClaimDetail_approve")
            .accessibilityLabel("Approve claim")

            HStack(spacing: Spacing.s2) {
                Button(action: onReject) {
                    HStack(spacing: Spacing.s1) {
                        Icon(.circleSlash, size: 16, color: Theme.Color.error)
                        Text("Reject")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.Color.error)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
                    .background(Theme.Color.errorBg)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.xl)
                            .stroke(Theme.Color.error.opacity(0.5), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
                }
                .buttonStyle(.plain)
                .disabled(reviewingAction != nil)
                .accessibilityIdentifier("reviewClaimDetail_reject")
                .accessibilityLabel("Reject claim")

                Button(action: onRequestInfo) {
                    HStack(spacing: Spacing.s1) {
                        Icon(.helpCircle, size: 16, color: Theme.Color.warning)
                        Text("Request info")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.Color.warning)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
                    .background(Theme.Color.warningBg)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.xl)
                            .stroke(Theme.Color.warning.opacity(0.5), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
                }
                .buttonStyle(.plain)
                .disabled(reviewingAction != nil)
                .accessibilityIdentifier("reviewClaimDetail_requestInfo")
                .accessibilityLabel("Request more info")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Divider().background(Theme.Color.appBorderSubtle)
        }
    }
}

// MARK: - Note capture sheet (reject / request info)

private struct NoteCaptureSheet: View {
    let title: String
    let body: String
    let placeholder: String
    let primaryTitle: String
    let primaryRole: ButtonRole?
    @Binding var note: String
    let isSubmitting: Bool
    let onPrimary: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            HStack {
                Text(title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer(minLength: 0)
                Button(action: onCancel) {
                    Icon(.x, size: 22, color: Theme.Color.appTextSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }
            Text(body)
                .font(.system(size: 14))
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextEditor(text: $note)
                .frame(minHeight: 120)
                .padding(Spacing.s2)
                .background(Theme.Color.appBg)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .accessibilityIdentifier("reviewClaimDetail_noteEditor")
            Button(role: primaryRole, action: onPrimary) {
                HStack(spacing: Spacing.s2) {
                    if isSubmitting {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Theme.Color.appTextInverse))
                    }
                    Text(isSubmitting ? "Sending…" : primaryTitle)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(primaryBackground)
                .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
            }
            .buttonStyle(.plain)
            .disabled(isSubmitting)
            .accessibilityIdentifier("reviewClaimDetail_notePrimary")

            Button(action: onCancel) {
                Text("Cancel")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.Color.appSurface)
        .presentationDetents([.medium, .large])
    }

    private var primaryBackground: Color {
        primaryRole == .destructive ? Theme.Color.error : Theme.Color.primary600
    }
}

// swiftlint:enable file_length type_body_length function_body_length

#Preview {
    NavigationStack {
        ReviewClaimDetailView(
            viewModel: ReviewClaimDetailViewModel(claimId: "preview-claim"),
            onClose: {}
        )
    }
}
