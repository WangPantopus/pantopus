//
//  TimezoneSelectorSheet.swift
//  Pantopus
//
//  Foundation (I0b) — C7 timezone selector. A searchable bottom sheet opened
//  from the slot picker's timezone chip: a search field, a pinned "Detected"
//  row for the device zone, and a Common list — each row a real labelled button
//  showing the GMT offset and current local time. The selected checkmark uses
//  the host pillar accent. Changing the zone re-times the slots beneath.
//

import SwiftUI

/// Searchable IANA timezone picker. The parent owns the selected identifier and
/// re-fetches slots in `onSelect`.
public struct TimezoneSelectorSheet: View {
    private let selectedIdentifier: String
    private let accent: Color
    private let onSelect: (String) -> Void
    private let onDone: () -> Void

    @State private var query: String = ""

    /// A curated common set, in display order.
    private static let common: [String] = [
        "America/Los_Angeles", "America/Denver", "America/Chicago", "America/New_York",
        "Europe/London", "Europe/Paris", "Europe/Berlin",
        "Asia/Kolkata", "Asia/Tokyo", "Australia/Sydney"
    ]

    public init(
        selectedIdentifier: String,
        accent: Color = Theme.Color.primary600,
        onSelect: @escaping (String) -> Void,
        onDone: @escaping () -> Void
    ) {
        self.selectedIdentifier = selectedIdentifier
        self.accent = accent
        self.onSelect = onSelect
        self.onDone = onDone
    }

    private var detected: String {
        SchedulingTime.deviceTimeZoneIdentifier
    }

    private var isOverridden: Bool {
        selectedIdentifier != detected
    }

    public var body: some View {
        VStack(spacing: 0) {
            header
            searchField
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Spacing.s2) {
                    if query.isEmpty {
                        defaultList
                    } else {
                        searchResults
                    }
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.bottom, Spacing.s6)
            }
        }
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("scheduling.timezoneSheet")
    }

    // MARK: - Chrome

    private var header: some View {
        HStack {
            Text("Time zone")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            Button("Done", action: onDone)
                .font(Theme.Font.body)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.primary600)
        }
        .padding(Spacing.s4)
    }

    private var searchField: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.search, size: 16, color: Theme.Color.appTextMuted)
            TextField("Search city or time zone", text: $query)
                .font(Theme.Font.body)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .padding(.horizontal, Spacing.s4)
        .padding(.bottom, Spacing.s2)
    }

    // MARK: - Lists

    @ViewBuilder
    private var defaultList: some View {
        if isOverridden {
            overriddenBanner
        }
        sectionHeader("Detected")
        row(for: detected, detectedChip: true)
        sectionHeader("Common")
        ForEach(Self.common.filter { $0 != detected }, id: \.self) { id in
            row(for: id)
        }
    }

    @ViewBuilder
    private var searchResults: some View {
        let matches = filteredIdentifiers
        if matches.isEmpty {
            noMatch()
        } else {
            ForEach(matches, id: \.self) { id in
                row(for: id)
            }
        }
    }

    private var filteredIdentifiers: [String] {
        let needle = query.lowercased().replacingOccurrences(of: " ", with: "_")
        return Array(
            SchedulingTime.ianaIdentifiers
                .filter { $0.lowercased().contains(needle) }
                .prefix(60)
        )
    }

    private var overriddenBanner: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.info, size: 14, color: Theme.Color.info)
            Text("You changed this from your detected zone")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Spacer(minLength: Spacing.s2)
            Button("Reset to detected") { onSelect(detected) }
                .font(Theme.Font.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.primary600)
        }
        .padding(Spacing.s2)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.infoBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
        .padding(.bottom, Spacing.s1)
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .pantopusTextStyle(.overline)
            .foregroundStyle(Theme.Color.appTextMuted)
            .padding(.top, Spacing.s2)
    }

    private func noMatch() -> some View {
        VStack(spacing: Spacing.s2) {
            Icon(.search, size: 26, color: Theme.Color.appTextMuted)
            Text("No time zones match \u{201C}\(query)\u{201D}")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appText)
            Text("Try a city name")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.s8)
    }

    // MARK: - Row

    private func row(for identifier: String, detectedChip: Bool = false) -> some View {
        let isSelected = identifier == selectedIdentifier
        return Button {
            onSelect(identifier)
        } label: {
            HStack(spacing: Spacing.s3) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: Spacing.s2) {
                        Text(cityName(identifier))
                            .pantopusTextStyle(.body)
                            .foregroundStyle(Theme.Color.appText)
                        if detectedChip {
                            Text("Detected")
                                .pantopusTextStyle(.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.Color.primary700)
                                .padding(.horizontal, Spacing.s2)
                                .padding(.vertical, 1)
                                .background(Theme.Color.primary50)
                                .clipShape(Capsule())
                        }
                    }
                    Text(offsetAndTime(identifier))
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
                Spacer(minLength: Spacing.s2)
                if isSelected {
                    Icon(.check, size: 18, color: accent)
                }
            }
            .padding(.vertical, Spacing.s2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    // MARK: - Formatting

    private func cityName(_ identifier: String) -> String {
        if let tz = TimeZone(identifier: identifier),
           let name = tz.localizedName(for: .generic, locale: .current), !name.isEmpty {
            let city = identifier.split(separator: "/").last.map { $0.replacingOccurrences(of: "_", with: " ") } ?? ""
            return city.isEmpty ? name : "\(name) — \(city)"
        }
        return identifier.replacingOccurrences(of: "_", with: " ")
    }

    private func offsetAndTime(_ identifier: String) -> String {
        guard let tz = TimeZone(identifier: identifier) else { return identifier }
        let seconds = tz.secondsFromGMT()
        let sign = seconds < 0 ? "-" : "+"
        let hours = abs(seconds) / 3600
        let minutes = (abs(seconds) % 3600) / 60
        let offset = minutes == 0 ? "GMT\(sign)\(hours)" : String(format: "GMT%@%d:%02d", sign, hours, minutes)
        let fmt = DateFormatter()
        fmt.timeZone = tz
        fmt.timeStyle = .short
        fmt.dateStyle = .none
        return "\(offset) · \(fmt.string(from: Date()))"
    }
}

#if DEBUG
#Preview {
    TimezoneSelectorSheet(
        selectedIdentifier: "America/New_York",
        onSelect: { _ in },
        onDone: {}
    )
}
#endif
