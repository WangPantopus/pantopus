//
//  BusinessWaitlistView.swift
//  Pantopus
//
//  P6.6 — "Register a business · coming soon" surface. The full multi-step
//  registration wizard is deferred to Phase 9; until then, "Notify me"
//  records interest locally (no backend endpoint yet) and flips to a
//  confirmation. When `POST /api/business-waitlist` ships, swap the local
//  toggle for the real call.
//

import SwiftUI

struct BusinessWaitlistView: View {
    @State private var joined = false
    private let onBack: () -> Void

    init(onBack: @escaping () -> Void = {}) {
        self.onBack = onBack
    }

    var body: some View {
        ContentDetailShell(
            title: "Register a business",
            onBack: onBack,
            header: { EmptyView() },
            body: {
                VStack(spacing: Spacing.s4) {
                    ZStack {
                        Circle().fill(Theme.Color.businessBg).frame(width: 72, height: 72)
                        Icon(joined ? .checkCircle : .building2, size: 32, color: Theme.Color.business)
                    }
                    .accessibilityHidden(true)

                    Text(joined ? "You're on the list" : "Register a business · coming soon")
                        .pantopusTextStyle(.h3)
                        .foregroundStyle(Theme.Color.appText)
                        .multilineTextAlignment(.center)

                    Text(joined
                        ? "We'll let you know the moment business registration opens. Thanks for your interest."
                        : "Business registration isn't open yet. Join the waitlist and we'll notify you when you can set up your business on Pantopus.")
                        .pantopusTextStyle(.small)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Spacing.s5)

                    if !joined {
                        Button {
                            withAnimation { joined = true }
                        } label: {
                            Text("Notify me")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Theme.Color.appTextInverse)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(Theme.Color.business)
                                .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, Spacing.s5)
                        .accessibilityIdentifier("businessWaitlistNotifyButton")
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.s16)
                .padding(.horizontal, Spacing.s4)
            }
        )
        .accessibilityIdentifier("businessWaitlist")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
    }
}

#Preview {
    BusinessWaitlistView()
}
