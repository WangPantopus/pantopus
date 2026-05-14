//
//  PulsePostCard.swift
//  Pantopus
//
//  One row in the Pulse vertical feed. Header (avatar + meta + intent
//  chip), optional title (Event only), body clamped to 3 lines (2 with
//  a title), intent-shaped reaction strip, optional Event attendee row
//  with RSVP chip. Pure-render view — all state lives upstream.
//

import SwiftUI

/// VM-prepared content for a single Pulse card.
public struct PulsePostCardContent: Sendable, Hashable, Identifiable {
    public let id: String
    public let authorName: String
    public let authorInitials: String
    public let authorVerified: Bool
    public let meta: String
    public let intent: PulseIntent
    public let title: String?
    public let body: String
    public let reactions: [PulseReaction]
    public let attendees: PulseAttendeeStrip?
    public let userHasReacted: Bool

    public init(
        id: String,
        authorName: String,
        authorInitials: String,
        authorVerified: Bool,
        meta: String,
        intent: PulseIntent,
        title: String?,
        body: String,
        reactions: [PulseReaction],
        attendees: PulseAttendeeStrip?,
        userHasReacted: Bool
    ) {
        self.id = id
        self.authorName = authorName
        self.authorInitials = authorInitials
        self.authorVerified = authorVerified
        self.meta = meta
        self.intent = intent
        self.title = title
        self.body = body
        self.reactions = reactions
        self.attendees = attendees
        self.userHasReacted = userHasReacted
    }
}

/// Event card attendee strip — stacked avatars + going count + RSVP CTA.
public struct PulseAttendeeStrip: Sendable, Hashable {
    public let avatars: [String]
    public let goingCount: Int
    public let userIsGoing: Bool

    public init(avatars: [String], goingCount: Int, userIsGoing: Bool) {
        self.avatars = avatars
        self.goingCount = goingCount
        self.userIsGoing = userIsGoing
    }
}

/// Pulse post card — entirely render-only; tap dispatch is parent-controlled.
public struct PulsePostCard: View {
    private let content: PulsePostCardContent
    private let onTap: @MainActor () -> Void
    private let onPrimaryReaction: @MainActor () -> Void
    private let onRSVP: (@MainActor () -> Void)?

    public init(
        content: PulsePostCardContent,
        onTap: @escaping @MainActor () -> Void,
        onPrimaryReaction: @escaping @MainActor () -> Void,
        onRSVP: (@MainActor () -> Void)? = nil
    ) {
        self.content = content
        self.onTap = onTap
        self.onPrimaryReaction = onPrimaryReaction
        self.onRSVP = onRSVP
    }

    public var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: Spacing.s2) {
                header
                if let title = content.title, !title.isEmpty {
                    Text(title)
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(1)
                }
                if !content.body.isEmpty {
                    Text(content.body)
                        .font(.system(size: 12.5))
                        .foregroundStyle(Theme.Color.appTextStrong)
                        .lineLimit(content.title?.isEmpty == false ? 2 : 3)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if let attendees = content.attendees {
                    attendeeStrip(attendees)
                }
                reactionStrip
            }
            .padding(Spacing.s3)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(a11yLabel)
        .accessibilityIdentifier("pulsePostCard_\(content.id)")
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 9) {
            AvatarWithIdentityRing(
                name: content.authorInitials,
                identity: .personal,
                ringProgress: content.authorVerified ? 1 : 0.35,
                size: 32
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(content.authorName)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Text(content.meta)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: Spacing.s2)
            PulseIntentChip(intent: content.intent)
        }
    }

    @ViewBuilder
    private func attendeeStrip(_ strip: PulseAttendeeStrip) -> some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(Theme.Color.appBorderSubtle)
                .frame(height: 1)
                .padding(.top, Spacing.s2)
            HStack(alignment: .center, spacing: Spacing.s2) {
                HStack(spacing: -8) {
                    ForEach(Array(strip.avatars.prefix(4).enumerated()), id: \.offset) { _, initials in
                        AvatarWithIdentityRing(
                            name: initials,
                            identity: .personal,
                            ringProgress: 1,
                            size: 22
                        )
                        .overlay(
                            Circle().stroke(Theme.Color.appSurface, lineWidth: 2)
                        )
                    }
                }
                Text("+ \(strip.goingCount) going")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer(minLength: Spacing.s1)
                if let onRSVP {
                    Button(action: onRSVP) {
                        HStack(spacing: 4) {
                            Icon(
                                strip.userIsGoing ? .check : .plusCircle,
                                size: 10,
                                strokeWidth: 3,
                                color: Theme.Color.business
                            )
                            Text(strip.userIsGoing ? "Going" : "RSVP")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(Theme.Color.business)
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 26)
                        .background(Theme.Color.businessBg)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(strip.userIsGoing ? "Going" : "RSVP")
                    .accessibilityIdentifier("pulseRSVP_\(content.id)")
                }
            }
            .padding(.top, Spacing.s2)
        }
    }

    private var reactionStrip: some View {
        HStack(alignment: .center, spacing: 14) {
            ForEach(content.reactions) { reaction in
                reactionPill(reaction)
            }
            Spacer()
            HStack(spacing: 4) {
                Icon(.send, size: 12, color: Theme.Color.appTextSecondary)
                Text("Reply")
                    .font(.system(size: 11.5, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .padding(.top, Spacing.s2)
    }

    @ViewBuilder
    private func reactionPill(_ reaction: PulseReaction) -> some View {
        let active = reaction.kind == content.reactions.first?.kind && content.userHasReacted
        if reaction.isInteractive {
            Button(action: onPrimaryReaction) { reactionLabel(reaction, active: active) }
                .buttonStyle(.plain)
                .accessibilityLabel("\(reaction.label.isEmpty ? "React" : reaction.label), \(reaction.count)")
                .accessibilityIdentifier("pulseReaction_\(content.id)_\(reaction.id.rawValue)")
        } else {
            reactionLabel(reaction, active: false)
                .accessibilityElement()
                .accessibilityLabel("\(reaction.label.isEmpty ? "Count" : reaction.label), \(reaction.count)")
        }
    }

    private func reactionLabel(_ reaction: PulseReaction, active: Bool) -> some View {
        HStack(spacing: 4) {
            Icon(reaction.icon, size: 12, color: active ? Theme.Color.primary600 : Theme.Color.appTextSecondary)
            HStack(spacing: 3) {
                if !reaction.label.isEmpty {
                    Text(reaction.label)
                        .font(.system(size: 11.5, weight: .medium))
                        .foregroundStyle(active ? Theme.Color.primary600 : Theme.Color.appTextSecondary)
                }
                Text("\(reaction.count)")
                    .font(.system(size: 11.5))
                    .foregroundStyle(active ? Theme.Color.primary600 : Theme.Color.appTextSecondary)
            }
        }
    }

    private var a11yLabel: String {
        var parts = [content.authorName, content.intent.cardChipLabel]
        if let title = content.title, !title.isEmpty { parts.append(title) }
        if !content.body.isEmpty { parts.append(content.body) }
        return parts.joined(separator: ". ")
    }
}

/// Right-aligned colored chip in the post header. Resolves intent →
/// foreground/background tokens against the existing design system.
public struct PulseIntentChip: View {
    private let intent: PulseIntent

    public init(intent: PulseIntent) {
        self.intent = intent
    }

    public var body: some View {
        HStack(spacing: 4) {
            Icon(intent.icon, size: 10, strokeWidth: 2.5, color: foreground)
            Text(intent.cardChipLabel.uppercased())
                .font(.system(size: 10, weight: .bold))
                .tracking(0.4)
                .foregroundStyle(foreground)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 2)
        .background(background)
        .clipShape(Capsule())
        .accessibilityLabel("\(intent.label) post")
    }

    private var foreground: Color {
        switch intent {
        case .all: return Theme.Color.appTextSecondary
        case .ask: return Theme.Color.warning
        case .recommend: return Theme.Color.success
        case .event: return Theme.Color.business
        case .lost: return Theme.Color.error
        case .announce: return Theme.Color.appTextStrong
        }
    }

    private var background: Color {
        switch intent {
        case .all: return Theme.Color.appSurfaceSunken
        case .ask: return Theme.Color.warningBg
        case .recommend: return Theme.Color.successBg
        case .event: return Theme.Color.businessBg
        case .lost: return Theme.Color.errorBg
        case .announce: return Theme.Color.appSurfaceSunken
        }
    }
}
