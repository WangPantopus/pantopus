//
//  BusinessProfileView.swift
//  Pantopus
//
//  P1.6 — Typed Business Profile screen. Forks PublicProfileView, then
//  swaps the personal-pillar chrome for the violet business pillar:
//  flat business-bg hero band, identity-ring avatar tinted to
//  `.business`, the three-tab body (Overview / Services / Reviews),
//  and a sticky Message + Save + Visit footer.
//
// swiftlint:disable file_length type_body_length

import SwiftUI

/// Top-level entry point for the Business Profile screen.
@MainActor
public struct BusinessProfileView: View {
    @State private var viewModel: BusinessProfileViewModel
    private let onBack: @MainActor () -> Void
    private let onOpenMessages: @MainActor () -> Void
    private let onShare: @MainActor () -> Void
    private let onOpenReport: @MainActor () -> Void
    private let onOpenWebsite: @MainActor (URL) -> Void

    public init(
        businessId: String,
        onBack: @escaping @MainActor () -> Void,
        onOpenMessages: @escaping @MainActor () -> Void = {},
        onShare: @escaping @MainActor () -> Void = {},
        onOpenReport: @escaping @MainActor () -> Void = {},
        onOpenWebsite: @escaping @MainActor (URL) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: BusinessProfileViewModel(businessId: businessId))
        self.onBack = onBack
        self.onOpenMessages = onOpenMessages
        self.onShare = onShare
        self.onOpenReport = onOpenReport
        self.onOpenWebsite = onOpenWebsite
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            content
            if let toast = viewModel.toastMessage {
                ToastView(message: ToastMessage(text: toast, kind: .neutral))
                    .padding(.bottom, Spacing.s12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: toast) {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.toastMessage = nil
                    }
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .confirmationDialog(
            "More",
            isPresented: Binding(
                get: { viewModel.showOverflow },
                set: { viewModel.showOverflow = $0 }
            ),
            titleVisibility: .hidden
        ) {
            Button("Share business") { onShare() }
            Button("Report", role: .destructive) { onOpenReport() }
            Button("Cancel", role: .cancel) {}
        }
        .accessibilityIdentifier("businessProfile")
        .task { await viewModel.load() }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingLayout(onBack: onBack)
        case let .loaded(payload):
            loadedLayout(payload)
        case .notFound:
            NotFoundLayout(onBack: onBack) {
                Task { await viewModel.refresh() }
            }
        case let .error(message):
            ErrorLayout(message: message, onBack: onBack) {
                Task { await viewModel.refresh() }
            }
        }
    }

    private func loadedLayout(_ payload: BusinessProfileContent) -> some View {
        ContentDetailShell(
            title: nil,
            onBack: onBack,
            topBarAction: ContentDetailTopBarAction(
                icon: .moreHorizontal,
                accessibilityLabel: "More actions"
            ) { Task { @MainActor in viewModel.showOverflow = true } },
            header: {
                BusinessProfileHero(
                    header: payload.header,
                    onShare: { onShare() }
                )
            },
            body: {
                BusinessProfileBody(
                    content: payload,
                    selectedTab: Binding(
                        get: { viewModel.selectedTab },
                        set: { viewModel.selectedTab = $0 }
                    ),
                    onOpenWebsite: { onOpenWebsite($0) }
                )
            },
            cta: {
                BusinessProfileActionFooter(
                    saveState: viewModel.saveState,
                    websiteURL: payload.websiteURL,
                    onMessage: { onOpenMessages() },
                    onSave: { Task { await viewModel.save() } },
                    onVisit: { url in onOpenWebsite(url) }
                )
                .padding(.bottom, Spacing.s2)
            }
        )
    }
}

// MARK: - Hero band

@MainActor
private struct BusinessProfileHero: View {
    let header: BusinessProfileHeader
    let onShare: @MainActor () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Flat violet wash — explicit "gradient OFF" per spec.
            ZStack(alignment: .topTrailing) {
                Theme.Color.businessBg
                    .frame(height: 132)
                    .accessibilityHidden(true)

                Button(action: { onShare() }) {
                    Icon(.share, size: 18, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                        .background(Theme.Color.appSurface.opacity(0.92))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s2)
                .accessibilityLabel("Share")
                .accessibilityIdentifier("businessProfile.share")
            }

            // Identity card overlaps the bottom of the band.
            IdentityCard(header: header)
                .padding(.horizontal, Spacing.s4)
                .offset(y: -44)
                .padding(.bottom, -44)
        }
        .frame(maxWidth: .infinity)
    }
}

@MainActor
private struct IdentityCard: View {
    let header: BusinessProfileHeader

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(alignment: .top, spacing: Spacing.s3) {
                ZStack(alignment: .bottomTrailing) {
                    AvatarWithIdentityRing(
                        name: header.displayName,
                        imageURL: header.logoURL,
                        identity: .business,
                        ringProgress: 1,
                        size: 72
                    )
                    if header.isVerified {
                        VerifiedBadge(size: 24).offset(x: 2, y: 2)
                    }
                }
                .frame(width: 80, height: 80)

                VStack(alignment: .leading, spacing: 2) {
                    Text(header.displayName)
                        .font(.system(size: PantopusTextStyle.h2.size, weight: .bold))
                        .tracking(-0.5)
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(2)
                        .accessibilityAddTraits(.isHeader)

                    if let handle = header.handle {
                        Text("@\(handle)")
                            .font(.system(size: PantopusTextStyle.caption.size))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .lineLimit(1)
                    }

                    if let locality = header.locality, !locality.isEmpty {
                        HStack(spacing: Spacing.s1) {
                            Icon(.mapPin, size: 12, color: Theme.Color.appTextSecondary)
                            Text(locality)
                                .font(.system(size: PantopusTextStyle.caption.size))
                                .foregroundStyle(Theme.Color.appTextSecondary)
                                .lineLimit(1)
                        }
                        .padding(.top, 2)
                    }
                }

                Spacer(minLength: 0)
            }

            if !header.categoryChips.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.s2) {
                        ForEach(header.categoryChips, id: \.self) { chip in
                            StatusChip(chip, variant: .business)
                        }
                    }
                }
            }
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .pantopusShadow(.sm)
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Body (stats + tabs + tab content)

@MainActor
private struct BusinessProfileBody: View {
    let content: BusinessProfileContent
    @Binding var selectedTab: BusinessProfileTab
    let onOpenWebsite: @MainActor (URL) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            statsStrip
                .padding(.horizontal, Spacing.s4)

            tabStrip
                .padding(.horizontal, Spacing.s4)

            Group {
                switch selectedTab {
                case .overview: overviewTab
                case .services: servicesTab
                case .reviews: reviewsTab
                }
            }
            .padding(.horizontal, Spacing.s4)

            Spacer().frame(height: Spacing.s16)
        }
    }

    private var statsStrip: some View {
        HStack(alignment: .center, spacing: Spacing.s2) {
            ForEach(content.stats) { stat in
                VStack(spacing: 2) {
                    Text(stat.value)
                        .font(.system(size: PantopusTextStyle.h3.size, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(stat.label.uppercased())
                        .font(.system(size: 10, weight: .semibold))
                        .tracking(0.5)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(stat.value) \(stat.label)")
            }
        }
        .padding(.vertical, Spacing.s3)
        .padding(.horizontal, Spacing.s3)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .pantopusShadow(.sm)
    }

    private var tabStrip: some View {
        HStack(spacing: 0) {
            ForEach(BusinessProfileTab.allCases) { tab in
                Button {
                    selectedTab = tab
                } label: {
                    VStack(spacing: Spacing.s1) {
                        Text(tab.label)
                            .font(.system(
                                size: PantopusTextStyle.small.size,
                                weight: tab == selectedTab ? .semibold : .regular
                            ))
                            .foregroundStyle(
                                tab == selectedTab
                                    ? Theme.Color.business
                                    : Theme.Color.appTextSecondary
                            )
                        Rectangle()
                            .fill(tab == selectedTab ? Theme.Color.business : Color.clear)
                            .frame(height: 2)
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("businessProfile.tab.\(tab.rawValue)")
                .accessibilityLabel(tab.label)
                .accessibilityAddTraits(
                    tab == selectedTab ? [.isButton, .isSelected] : .isButton
                )
            }
        }
    }

    // MARK: Overview

    @ViewBuilder private var overviewTab: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            if let about = content.about, !about.isEmpty {
                BusinessSection(title: "About") {
                    Text(about)
                        .font(.system(size: PantopusTextStyle.body.size))
                        .foregroundStyle(Theme.Color.appText)
                        .lineSpacing(4)
                }
            }

            if !content.hours.isEmpty {
                BusinessSection(title: "Hours") {
                    HoursTable(rows: content.hours)
                }
            }

            if let address = content.address {
                BusinessSection(title: "Address") {
                    AddressBlock(address: address)
                }
            }

            if !content.contact.isEmpty {
                BusinessSection(title: "Contact") {
                    VStack(spacing: 0) {
                        ForEach(Array(content.contact.enumerated()), id: \.element.id) { idx, row in
                            ContactRowView(row: row, onTap: { url in onOpenWebsite(url) })
                            if idx != content.contact.count - 1 {
                                Divider().padding(.leading, Spacing.s10)
                            }
                        }
                    }
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.lg)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
                }
            }

            if content.about == nil
                && content.hours.isEmpty
                && content.address == nil
                && content.contact.isEmpty {
                EmptyState(
                    icon: .building2,
                    headline: "Nothing here yet",
                    subcopy: "This business hasn't filled in their public profile."
                )
                .frame(minHeight: 220)
            }
        }
        .accessibilityIdentifier("businessProfile.overview")
    }

    // MARK: Services

    @ViewBuilder private var servicesTab: some View {
        if content.services.isEmpty {
            EmptyState(
                icon: .tag,
                headline: "No services listed yet",
                subcopy: "When this business adds services or products, you'll see them here."
            )
            .frame(minHeight: 260)
            .accessibilityIdentifier("businessProfile.services.empty")
        } else {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                ForEach(content.services) { service in
                    ServiceRowView(service: service)
                }
            }
            .accessibilityIdentifier("businessProfile.services")
        }
    }

    // MARK: Reviews

    @ViewBuilder private var reviewsTab: some View {
        if content.reviews.isEmpty {
            EmptyState(
                icon: .star,
                headline: "No reviews yet",
                subcopy: "Reviews show up here after a completed gig or purchase."
            )
            .frame(minHeight: 260)
            .accessibilityIdentifier("businessProfile.reviews.empty")
        } else {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                ForEach(content.reviews) { card in
                    ReviewRowView(card: card)
                }
            }
            .accessibilityIdentifier("businessProfile.reviews")
        }
    }
}

// MARK: - Sub-views

@MainActor
private struct BusinessSection<Body: View>: View {
    let title: String
    @ViewBuilder let body: () -> Body

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(title.uppercased())
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.6)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
            body()
        }
    }
}

@MainActor
private struct HoursTable: View {
    let rows: [BusinessHoursRow]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { idx, row in
                HStack {
                    Text(row.dayLabel)
                        .font(.system(size: PantopusTextStyle.body.size, weight: .medium))
                        .foregroundStyle(Theme.Color.appText)
                    Spacer()
                    Text(row.timeLabel)
                        .font(.system(size: PantopusTextStyle.body.size))
                        .foregroundStyle(
                            row.isClosed ? Theme.Color.appTextSecondary : Theme.Color.appText
                        )
                }
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, Spacing.s2)
                if idx != rows.count - 1 {
                    Divider()
                }
            }
        }
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
    }
}

@MainActor
private struct AddressBlock: View {
    let address: BusinessAddress

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            mapPreview
            VStack(alignment: .leading, spacing: 2) {
                ForEach(address.lines, id: \.self) { line in
                    Text(line)
                        .font(.system(size: PantopusTextStyle.body.size))
                        .foregroundStyle(Theme.Color.appText)
                }
            }
            .padding(Spacing.s3)
        }
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel(address.lines.joined(separator: ", "))
    }

    @ViewBuilder private var mapPreview: some View {
        if address.hasCoordinates {
            // Static "map preview" tile — a flat sunken band with a
            // pin to convey that the business has a mappable address.
            // The real map view ships with the Nearby integration.
            ZStack {
                Theme.Color.appSurfaceSunken
                Icon(.mapPin, size: 28, color: Theme.Color.business)
            }
            .frame(height: 96)
        } else {
            EmptyView()
        }
    }
}

@MainActor
private struct ContactRowView: View {
    let row: BusinessContactRow
    let onTap: @MainActor (URL) -> Void

    var body: some View {
        Button {
            if let url = row.actionURL {
                onTap(url)
            }
        } label: {
            HStack(spacing: Spacing.s3) {
                Icon(icon, size: 18, color: Theme.Color.business)
                    .frame(width: 28)
                Text(row.value)
                    .font(.system(size: PantopusTextStyle.body.size))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Spacer()
                if row.actionURL != nil {
                    Icon(.chevronRight, size: 14, color: Theme.Color.appTextSecondary)
                }
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s3)
            .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(row.kind.rawValue.capitalized): \(row.value)")
    }

    private var icon: PantopusIcon {
        switch row.kind {
        case .phone: .phone
        case .email: .mail
        case .website: .link
        }
    }
}

@MainActor
private struct ServiceRowView: View {
    let service: BusinessServiceRow

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            HStack(alignment: .firstTextBaseline) {
                Text(service.name)
                    .font(.system(size: PantopusTextStyle.body.size, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                Text(service.priceLabel)
                    .font(.system(size: PantopusTextStyle.body.size, weight: .semibold))
                    .foregroundStyle(Theme.Color.business)
            }
            if let detail = service.detail, !detail.isEmpty {
                Text(detail)
                    .font(.system(size: PantopusTextStyle.small.size))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineSpacing(2)
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(service.name), \(service.priceLabel)")
    }
}

@MainActor
private struct ReviewRowView: View {
    let card: BusinessReviewCard

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                AvatarWithIdentityRing(
                    name: card.reviewerName,
                    imageURL: card.reviewerAvatarURL,
                    identity: .personal,
                    ringProgress: 1,
                    size: 40
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(card.reviewerName)
                        .font(.system(size: PantopusTextStyle.small.size, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    HStack(spacing: 2) {
                        ForEach(0..<5) { idx in
                            Icon(
                                .star,
                                size: 12,
                                color: idx < card.rating
                                    ? Theme.Color.warning
                                    : Theme.Color.appTextMuted
                            )
                        }
                    }
                }
                Spacer()
                Text(card.timestamp)
                    .font(.system(size: PantopusTextStyle.caption.size))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            if !card.body.isEmpty {
                Text(card.body)
                    .font(.system(size: PantopusTextStyle.small.size))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineSpacing(3)
            }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .pantopusShadow(.sm)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(card.reviewerName), \(card.rating) star review, \(card.timestamp)"
        )
    }
}

// MARK: - Sticky CTA

@MainActor
private struct BusinessProfileActionFooter: View {
    let saveState: BusinessProfileActionState
    let websiteURL: URL?
    let onMessage: @MainActor () -> Void
    let onSave: @MainActor () -> Void
    let onVisit: @MainActor (URL) -> Void

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Button(action: { onMessage() }) {
                HStack(spacing: Spacing.s1) {
                    Icon(.messageCircle, size: 16, color: Theme.Color.appTextInverse)
                    Text("Message")
                        .font(.system(size: PantopusTextStyle.small.size, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(Theme.Color.business)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Message")
            .accessibilityIdentifier("businessProfile.message")

            Button(action: { onSave() }) {
                HStack(spacing: Spacing.s1) {
                    Icon(
                        saveState == .saved ? .checkCircle : .bookmark,
                        size: 16,
                        color: saveState == .saved ? Theme.Color.business : Theme.Color.appText
                    )
                    Text(saveState == .saved ? "Saved" : "Save")
                        .font(.system(size: PantopusTextStyle.small.size, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.lg)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(saveState == .saved ? "Saved" : "Save")
            .accessibilityIdentifier("businessProfile.save")

            if let websiteURL {
                Button(action: { onVisit(websiteURL) }) {
                    HStack(spacing: Spacing.s1) {
                        Icon(.link, size: 16, color: Theme.Color.business)
                        Text("Visit")
                            .font(.system(size: PantopusTextStyle.small.size, weight: .semibold))
                            .foregroundStyle(Theme.Color.business)
                    }
                    .frame(minWidth: 80, minHeight: 44)
                    .padding(.horizontal, Spacing.s3)
                    .background(Theme.Color.businessBg)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Visit website")
                .accessibilityIdentifier("businessProfile.visit")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s2)
        .background(
            Theme.Color.appSurface
                .opacity(0.97)
                .clipShape(RoundedRectangle(cornerRadius: Radii.xl2))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.xl2)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .pantopusShadow(.md)
        )
    }
}

// MARK: - Loading / Not-found / Error layouts

@MainActor
private struct LoadingLayout: View {
    let onBack: @MainActor () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    Theme.Color.businessBg
                        .frame(height: 168)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: Spacing.s2) {
                        Shimmer(width: 72, height: 72, cornerRadius: 36)
                        Shimmer(width: 180, height: 24, cornerRadius: Radii.sm)
                        Shimmer(width: 120, height: 14, cornerRadius: Radii.sm)
                    }
                    .padding(.horizontal, Spacing.s4)
                    .offset(y: -48)
                    Shimmer(height: 64, cornerRadius: Radii.lg)
                        .padding(.horizontal, Spacing.s4)
                    Shimmer(height: 44, cornerRadius: Radii.md)
                        .padding(.horizontal, Spacing.s4)
                    Shimmer(height: 120, cornerRadius: Radii.lg)
                        .padding(.horizontal, Spacing.s4)
                }
                .padding(.vertical, Spacing.s4)
            }
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("businessProfile.loading")
    }
}

@MainActor
private struct NotFoundLayout: View {
    let onBack: @MainActor () -> Void
    let onRetry: @MainActor () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            EmptyState(
                icon: .building2,
                headline: "Business not found",
                subcopy: "This business may have moved or unpublished their profile.",
                cta: EmptyState.CTA(title: "Try again") {
                    await MainActor.run { onRetry() }
                }
            )
            .frame(maxHeight: .infinity)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("businessProfile.notFound")
    }
}

@MainActor
private struct ErrorLayout: View {
    let message: String
    let onBack: @MainActor () -> Void
    let onRetry: @MainActor () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            EmptyState(
                icon: .alertCircle,
                headline: "Couldn't load this business",
                subcopy: message,
                cta: EmptyState.CTA(title: "Try again") {
                    await MainActor.run { onRetry() }
                }
            )
            .frame(maxHeight: .infinity)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("businessProfile.error")
    }
}

#Preview {
    BusinessProfileView(businessId: "preview") {}
}
