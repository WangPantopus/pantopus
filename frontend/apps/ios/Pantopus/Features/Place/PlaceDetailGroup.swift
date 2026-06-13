//
//  PlaceDetailGroup.swift
//  Pantopus
//
//  The seven Place detail destinations (W2.3). Maps the contract's
//  curated `PlaceGroup`s onto the detail pages a dashboard card taps
//  through to — `health_environment` folds into Risk (it has no detail
//  page of its own), mirroring the web `sections.ts` GROUP_TO_SLUG.
//

import Foundation

/// A tappable Place detail page. The `slug` matches the web route
/// (`/app/place/<slug>`) for parity.
public enum PlaceDetailGroup: String, Hashable, CaseIterable, Sendable {
    case today
    case yourHome = "your-home"
    case risk
    case block
    case money
    case civic
    case identity

    /// Page title in the detail header.
    public var title: String {
        switch self {
        case .today: return "Today"
        case .yourHome: return "Your home"
        case .risk: return "Risk & readiness"
        case .block: return "Your block"
        case .money: return "Money signals"
        case .civic: return "Civic"
        case .identity: return "Identity"
        }
    }

    /// The contract groups whose sections this detail page renders.
    public var groups: [PlaceGroup] {
        switch self {
        case .today: return [.today]
        case .yourHome: return [.yourHome]
        case .risk: return [.riskReadiness, .healthEnvironment]
        case .block: return [.yourBlock]
        case .money: return [.moneySignals]
        case .civic: return [.civic]
        case .identity: return [.identity]
        }
    }

    /// The detail page a dashboard card in `group` taps through to.
    public static func forGroup(_ group: PlaceGroup) -> PlaceDetailGroup? {
        switch group {
        case .today: return .today
        case .yourHome: return .yourHome
        case .riskReadiness, .healthEnvironment: return .risk
        case .yourBlock: return .block
        case .moneySignals: return .money
        case .civic: return .civic
        case .identity: return .identity
        case .unknown: return nil
        }
    }
}
