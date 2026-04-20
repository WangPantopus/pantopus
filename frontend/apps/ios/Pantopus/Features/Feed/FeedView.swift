//
//  FeedView.swift
//  Pantopus
//

import SwiftUI

struct FeedView: View {
    @State private var viewModel = FeedViewModel()

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.state {
                case .idle, .loading:
                    ProgressView("Loading feed…")
                case .loaded(let posts):
                    List(posts) { post in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(post.authorName ?? "Anonymous")
                                .font(.headline)
                            Text(post.content)
                                .font(.body)
                            Text(post.createdAt, style: .relative)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .refreshable { await viewModel.load() }
                case .error(let message):
                    ContentUnavailableView(
                        "Couldn't load feed",
                        systemImage: "exclamationmark.triangle",
                        description: Text(message)
                    )
                }
            }
            .navigationTitle("Feed")
            .task { await viewModel.load() }
        }
    }
}

@Observable
@MainActor
final class FeedViewModel {
    enum State {
        case idle
        case loading
        case loaded([FeedPost])
        case error(String)
    }

    var state: State = .idle

    func load() async {
        state = .loading
        do {
            let response: FeedResponse = try await APIClient.shared.request(
                Endpoint(method: .get, path: "/api/posts")
            )
            state = .loaded(response.posts)
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}

#Preview {
    FeedView()
        .environment(AuthManager.previewSignedIn)
}
