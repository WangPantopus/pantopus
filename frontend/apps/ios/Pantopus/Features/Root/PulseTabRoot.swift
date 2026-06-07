//
//  PulseTabRoot.swift
//  Pantopus
//
//  Pulse tab — neighborhood feed. Mirrors the React Native Pulse tab;
//  hosts its own NavigationStack so drill-down survives tab switches.
//

import SwiftUI

/// Typed routes within the Pulse tab's NavigationStack.
public enum PulseRoute: Hashable {
    case post(postId: String)
    case compose(intent: String)
    case editPost(postId: String)
    case publicProfile(userId: String)
}

/// NavigationStack wrapper for the Pulse tab.
public struct PulseTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var path = RouteStack<PulseRoute>()
    @State private var router = DeepLinkRouter.shared

    public init() {}

    private var currentUserId: String {
        if case let .signedIn(user) = auth.state { return user.id }
        return ""
    }

    public var body: some View {
        NavigationStack(path: navigationPathBinding) {
            FeedView(
                onOpenPost: { postId in
                    path.append(.post(postId: postId))
                },
                onCompose: { intent in
                    path.append(.compose(intent: intent.rawValue))
                },
                onBack: nil
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: PulseRoute.self) { route in
                destination(for: route)
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
        .onChange(of: router.pending) { _, pending in
            consumeDeepLinkIfNeeded(pending: pending)
        }
        .task {
            consumeDeepLinkIfNeeded(pending: router.pending)
        }
    }

    private var navigationPathBinding: Binding<NavigationPath> {
        Binding(
            get: { path.navigationPath },
            set: { path.replaceNavigationPath($0) }
        )
    }

    private func consumeDeepLinkIfNeeded(pending: DeepLinkRouter.Destination?) {
        guard let pending else { return }
        switch pending {
        case .feed:
            path.replaceNavigationPath(NavigationPath())
            _ = router.consume()
        case let .post(id):
            path.replaceNavigationPath(NavigationPath())
            path.append(.post(postId: id))
            _ = router.consume()
        default:
            break
        }
    }

    @MainActor
    private func pop() {
        if !path.isEmpty { path.removeLast() }
    }

    @ViewBuilder
    private func destination(for route: PulseRoute) -> some View {
        switch route {
        case let .post(postId):
            PulsePostDetailView(
                postId: postId,
                currentUserId: currentUserId.isEmpty ? nil : currentUserId,
                onBack: pop,
                onOpenProfile: { userId in
                    Task { @MainActor in path.append(.publicProfile(userId: userId)) }
                },
                onEdit: { id in
                    Task { @MainActor in path.append(.editPost(postId: id)) }
                }
            )
        case let .compose(intent):
            PulseComposeFlowView(
                prefillFeedIntent: PulseIntent(rawValue: intent),
                onCancel: { pop() },
                onPosted: { _ in pop() }
            )
        case let .editPost(postId):
            PulseComposeFlowView(
                editingPostId: postId,
                onCancel: { pop() },
                onPosted: { _ in pop() }
            )
        case let .publicProfile(userId):
            PublicProfileView(
                userId: userId,
                onBack: pop,
                onOpenMessages: { _ in }
            )
        }
    }
}

#Preview {
    PulseTabRoot()
        .environment(AuthManager.previewSignedIn)
}
