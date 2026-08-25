//
//  PlaceMoneyDetailContent.swift
//  Pantopus
//
//  C7 — Money signals. Utility bill benchmark (peer track), incentive
//  programs (DSIRE), the HUD rent band, and the informational
//  "how property-tax appeals work" note (legal-gated: information only).
//

import SwiftUI

// swiftlint:disable line_length

// MARK: - Rate watch VM (Wave 2b)
// One user-entered fact (the month the loan was recorded) held against
// Freddie Mac's weekly PMMS average. Averages and deltas only — the
// copy never says "refinance". Watches are personal per home+user.

@Observable
@MainActor
final class PlaceRateWatchViewModel {
    enum State {
        case loading
        case none
        case loaded(RecordWatch)
        case error(message: String)
    }

    private(set) var state: State = .loading
    private(set) var isSaving = false
    var monthInput = ""
    let homeId: String
    private let api: APIClient

    init(homeId: String, api: APIClient = .shared) {
        self.homeId = homeId
        self.api = api
    }

    func load() async {
        do {
            let response: RecordWatchResponse = try await api.request(
                RecordWatchEndpoints.get(homeId: homeId)
            )
            state = response.watch.map(State.loaded) ?? .none
        } catch let error as APIError {
            state = .error(message: error.errorDescription ?? "Couldn't load your watch.")
        } catch {
            state = .error(message: "Couldn't load your watch.")
        }
    }

    func save() async {
        let month = monthInput.trimmingCharacters(in: .whitespaces)
        guard !month.isEmpty else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            let response: RecordWatchResponse = try await api.request(
                RecordWatchEndpoints.set(homeId: homeId, request: SetRecordWatchRequest(loanRecordedMonth: month))
            )
            monthInput = ""
            state = response.watch.map(State.loaded) ?? .none
        } catch let error as APIError {
            state = .error(message: error.errorDescription ?? "Couldn't save the watch.")
        } catch {
            state = .error(message: "Couldn't save the watch.")
        }
    }

    func remove() async {
        do {
            _ = try await api.request(RecordWatchEndpoints.delete(homeId: homeId)) as EmptyResponse
            state = .none
        } catch {
            await load()
        }
    }
}

struct PlaceMoneyDetailContent: View {
    let intel: PlaceIntelligence
    let vm: PlaceDetailViewModel
    @State private var rateWatch: PlaceRateWatchViewModel

    init(intel: PlaceIntelligence, vm: PlaceDetailViewModel) {
        self.intel = intel
        self.vm = vm
        _rateWatch = State(initialValue: PlaceRateWatchViewModel(homeId: vm.homeId))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let bill = vm.section(.billBenchmark, in: intel) {
                PlaceDetailSectionLabel(text: "Bill benchmark")
                if let data = bill.billBenchmark, bill.status == .ready || bill.status == .stale {
                    BillBenchmarkCard(data: data)
                } else {
                    vm.fallbackCard(bill)
                }
                PlaceSourceNote(name: "Your utility · peer comparison", asOf: nil)
            }

            if let incentives = vm.section(.incentives, in: intel) {
                PlaceDetailSectionLabel(text: "Incentives")
                if let data = incentives.incentives, !data.programs.isEmpty {
                    VStack(spacing: 8) {
                        ForEach(data.programs) { p in IncentiveRow(program: p) }
                    }
                    Text("Eligibility is an estimate — verify with each provider.")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .padding(.top, 8)
                } else {
                    vm.fallbackCard(incentives)
                }
                PlaceSourceNote(name: "DSIRE database", asOf: nil)
            }

            if let rent = vm.section(.rentBand, in: intel) {
                PlaceDetailSectionLabel(text: "Rent band")
                if let data = rent.rentBand, rent.status == .ready || rent.status == .stale {
                    RentBandCard(data: data)
                } else {
                    vm.fallbackCard(rent)
                }
                PlaceSourceNote(name: "HUD Fair Market Rents", asOf: nil)
            }

            if let exemption = vm.section(.exemptionCheck, in: intel) {
                PlaceDetailSectionLabel(text: "Property-tax exemption")
                if let data = exemption.exemptionCheck, exemption.status == .ready || exemption.status == .stale {
                    ExemptionCheckCard(data: data)
                } else {
                    vm.fallbackCard(exemption)
                }
                PlaceSourceNote(name: "County records · ATTOM", asOf: nil)
            }

            PlaceDetailSectionLabel(text: "Rate watch")
            if intel.tier == .t4 {
                RateWatchSection(vm: rateWatch)
                    .task { await rateWatch.load() }
            } else {
                PlaceLockedCard(
                    icon: .trendingDown,
                    title: "Rate watch",
                    reason: "Verify your address to watch the market against the month your loan was recorded — only the proven resident can watch a home.",
                    cta: "Verify address",
                    onTap: nil
                )
            }
            PlaceSourceNote(name: "Freddie Mac Primary Mortgage Market Survey", asOf: "weekly")
            PlaceComingSoonRow(
                icon: .landmark,
                title: "Deed & lien alerts",
                subtitle: "Know within days if anyone records against your home"
            )

            PlaceDetailSectionLabel(text: "Property tax")
            PlaceDetailCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("How appeals work")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(
                        "If your assessment is above market, that's the usual basis for an appeal. Check your county's deadline, gather comparable sales, and file a petition with the assessor."
                    )
                    .font(.system(size: 13.5))
                    .lineSpacing(2)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    Text("Informational only — not legal or tax advice.")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.warning)
                }
            }
        }
    }
}

// MARK: - Bill benchmark track

private struct BillBenchmarkCard: View {
    let data: PlaceBillBenchmarkData

    var body: some View {
        PlaceDetailCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    if let amount = data.yourAmount {
                        Text("\(PlacePresentation.money(amount) ?? "—") / mo")
                            .font(.system(size: 26, weight: .bold))
                            .foregroundStyle(Theme.Color.appText)
                    } else {
                        Text("Typical \(PlacePresentation.money(data.bandLow) ?? "")–\(PlacePresentation.money(data.bandHigh) ?? "")")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(Theme.Color.appText)
                    }
                    Spacer(minLength: 0)
                    PlaceChip(model: comparisonChip)
                }
                track
                Text(data.summary)
                    .font(.system(size: 13.5))
                    .lineSpacing(2)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }

    private var comparisonChip: PlaceChipModel {
        let pct = Int(abs(data.comparisonPct).rounded())
        switch data.comparison {
        case .higher: return PlaceChipModel(tone: .warning, text: "\(pct)% above", icon: .trendingUp)
        case .lower: return PlaceChipModel(tone: .success, text: "\(pct)% below", icon: .trendingDown)
        default: return PlaceChipModel(tone: .neutral, text: "Typical")
        }
    }

    private var track: some View {
        VStack(spacing: 4) {
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.Color.appSurfaceSunken).frame(height: 8)
                    Capsule().fill(Theme.Color.homeBg)
                        .frame(width: proxy.size.width * 0.4, height: 8)
                        .offset(x: proxy.size.width * 0.3)
                    if let pos = position {
                        Circle().fill(Theme.Color.primary600)
                            .frame(width: 14, height: 14)
                            .overlay(Circle().strokeBorder(Theme.Color.appSurface, lineWidth: 2))
                            .offset(x: proxy.size.width * pos - 7)
                    }
                }
                .frame(height: 14)
            }
            .frame(height: 14)
            HStack {
                Text("Lower")
                Spacer()
                Text("Typical")
                Spacer()
                Text("Higher")
            }
            .font(.system(size: 11, weight: .medium))
            .foregroundStyle(Theme.Color.appTextMuted)
        }
    }

    private var position: Double? {
        guard let amount = data.yourAmount else { return nil }
        let span = max(data.bandHigh - data.bandLow, 1)
        let lo = data.bandLow - span * 0.75
        let hi = data.bandHigh + span * 0.75
        return min(max((amount - lo) / (hi - lo), 0.04), 0.96)
    }
}

// MARK: - Rate watch section (Wave 2b)

private struct RateWatchSection: View {
    let vm: PlaceRateWatchViewModel

    var body: some View {
        switch vm.state {
        case .loading:
            PlaceDetailCard {
                Text("Loading your watch…")
                    .font(.system(size: 13.5))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        case let .error(message):
            PlaceDetailCard {
                Text(message)
                    .font(.system(size: 13.5))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        case .none:
            RateWatchForm(vm: vm)
        case let .loaded(watch):
            RateWatchCard(watch: watch, vm: vm)
        }
    }
}

private struct RateWatchForm: View {
    @Bindable var vm: PlaceRateWatchViewModel

    var body: some View {
        PlaceDetailCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Watch rates against your loan")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text("Hear it from your dashboard before the refi mailers find you. Enter the month your loan was recorded (YYYY-MM).")
                    .font(.system(size: 12.5))
                    .lineSpacing(2)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                TextField("2023-03", text: $vm.monthInput)
                    .keyboardType(.numbersAndPunctuation)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(size: 15))
                Button {
                    Task { await vm.save() }
                } label: {
                    Text(vm.isSaving ? "Saving…" : "Start watching")
                        .font(.system(size: 14.5, weight: .semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(vm.isSaving || vm.monthInput.trimmingCharacters(in: .whitespaces).isEmpty)
                Text("We compare Freddie Mac's weekly 30-year survey average with the average for your month — facts about the market, not refinancing advice. Only you can see this.")
                    .font(.system(size: 11.5))
                    .lineSpacing(2)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        }
    }
}

private struct RateWatchCard: View {
    let watch: RecordWatch
    let vm: PlaceRateWatchViewModel

    private var monthLabel: String {
        PlacePresentation.fmtMonthYear(watch.loanRecordedMonth + "-01T00:00:00Z") ?? watch.loanRecordedMonth
    }

    var body: some View {
        PlaceDetailCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Text("Rate watch")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Spacer(minLength: 0)
                    if let ev = watch.evaluation {
                        PlaceChip(model: deltaChip(ev))
                    }
                }
                Text("Watching against \(monthLabel), when your loan was recorded")
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextMuted)
                HStack(spacing: 20) {
                    rateColumn(label: "\(monthLabel) average", rate: watch.baselineRate)
                    rateColumn(label: "This week", rate: watch.evaluation?.currentRate)
                }
                Text(caption)
                    .font(.system(size: 12.5))
                    .lineSpacing(2)
                    .foregroundStyle(Theme.Color.appTextMuted)
                Button(role: .destructive) {
                    Task { await vm.remove() }
                } label: {
                    Text("Remove watch")
                        .font(.system(size: 13.5, weight: .semibold))
                }
            }
        }
    }

    private var caption: String {
        if watch.evaluation?.refiWindow == true {
            return "The market average is meaningfully below your loan month's average — the comparison lenders start from. We'll nudge you when it moves further."
        }
        return "We check the weekly market average against your month and nudge you if it falls meaningfully below — before the mail offers do."
    }

    private func deltaChip(_ ev: RecordWatchEvaluation) -> PlaceChipModel {
        if ev.refiWindow {
            return PlaceChipModel(tone: .success, text: String(format: "%.2fpp below your month", abs(ev.deltaPp)), icon: .trendingDown)
        }
        let sign = ev.deltaPp > 0 ? "+" : ""
        return PlaceChipModel(tone: .neutral, text: String(format: "%@%.2fpp vs your month", sign, ev.deltaPp))
    }

    private func rateColumn(label: String, rate: Double?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextMuted)
            Text(rate.map { String(format: "%.2f%%", $0) } ?? "—")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
        }
    }
}

// MARK: - Exemption check (Wave 2)
// The honesty ladder as a card: on_file (green, nothing to chase) ·
// none_on_file (amber — the "exemptions aren't automatic" hook) ·
// unknown (neutral — the county feed doesn't report it; never dressed
// as either). Plus the Over-Assessment Radar line when the county
// reports both of its own totals.

private struct ExemptionCheckCard: View {
    let data: PlaceExemptionCheckData

    var body: some View {
        PlaceDetailCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Text("Homestead exemption")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Spacer(minLength: 0)
                    PlaceChip(model: statusChip)
                }
                Text(lead)
                    .font(.system(size: 13.5))
                    .lineSpacing(2)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                VStack(alignment: .leading, spacing: 3) {
                    Text(data.stateProgram.label)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(data.stateProgram.note)
                        .font(.system(size: 12.5))
                        .lineSpacing(2)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                .padding(Spacing.s3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.Color.appSurfaceSunken, in: RoundedRectangle(cornerRadius: Radii.sm))
                if let signal = data.assessmentSignal {
                    assessmentBlock(signal)
                }
            }
        }
    }

    private var statusChip: PlaceChipModel {
        switch data.filingStatus {
        case .onFile: PlaceChipModel(tone: .success, text: "On file", icon: .badgeCheck)
        case .noneOnFile: PlaceChipModel(tone: .warning, text: "Nothing on file", icon: .alertCircle)
        case .unknown: PlaceChipModel(tone: .neutral, text: "Not reported")
        }
    }

    private var lead: String {
        switch data.filingStatus {
        case .onFile:
            "The county's record shows \(data.exemptions.joined(separator: ", ")) on this parcel — nothing to chase."
        case .noneOnFile:
            "No exemption appears on the county's record for this parcel. Exemptions usually aren't automatic — if this is your primary residence, it's worth checking whether one applies."
        case .unknown:
            "This county's assessor feed doesn't report exemption status to our data provider. Your tax bill or the assessor's site will say."
        }
    }

    private func assessmentBlock(_ signal: PlaceAssessmentSignal) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 8) {
                Text("Assessment vs county market value")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextMuted)
                Spacer(minLength: 0)
                PlaceChip(model: stanceChip(signal))
            }
            Text(assessmentCopy(signal))
                .font(.system(size: 13))
                .lineSpacing(2)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(.top, Spacing.s2)
    }

    private func stanceChip(_ signal: PlaceAssessmentSignal) -> PlaceChipModel {
        switch signal.stance {
        case .above: PlaceChipModel(tone: .warning, text: "\(Int(signal.ratioPct))% above")
        case .below: PlaceChipModel(tone: .success, text: "\(Int(abs(signal.ratioPct)))% below")
        case .near, .unknown: PlaceChipModel(tone: .neutral, text: "Within 5%")
        }
    }

    private func assessmentCopy(_ signal: PlaceAssessmentSignal) -> String {
        let assessed = PlacePresentation.money(signal.assessedValue) ?? "—"
        let market = PlacePresentation.money(signal.marketValue) ?? "—"
        let base = "Assessed at \(assessed) against the county's own \(market) market value."
        return signal.stance == .above
            ? base + " An assessment meaningfully above the county's market value is the usual basis for an appeal, filed with your county."
            : base + " Nothing here suggests the usual basis for an appeal."
    }
}

private struct IncentiveRow: View {
    let program: PlaceIncentive

    var body: some View {
        PlaceDetailCard(padding: 14) {
            HStack(alignment: .top, spacing: 11) {
                PlaceIconTile(icon: .badgePercent, tone: .home, size: 32)
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(program.name)
                            .font(.system(size: 14.5, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                        Spacer(minLength: 0)
                        PlaceChip(model: PlaceChipModel(tone: .success, text: "You may be eligible"))
                    }
                    Text(program.summary)
                        .font(.system(size: 12.5))
                        .lineSpacing(2)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
        }
    }
}

private struct RentBandCard: View {
    let data: PlaceRentBandData

    var body: some View {
        PlaceDetailCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("\(data.bedrooms)BR fair-market band")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextMuted)
                Text("\(PlacePresentation.money(data.bandLow) ?? "")–\(PlacePresentation.money(data.bandHigh) ?? "")")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                GeometryReader { proxy in
                    let span = max(data.marketHigh - data.marketLow, 1)
                    let start = (data.bandLow - data.marketLow) / span
                    let width = (data.bandHigh - data.bandLow) / span
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.Color.appSurfaceSunken).frame(height: 8)
                        Capsule().fill(Theme.Color.homeBg)
                            .frame(width: max(proxy.size.width * width, 8), height: 8)
                            .offset(x: proxy.size.width * start)
                    }
                }
                .frame(height: 8)
                if !data.summary.isEmpty {
                    Text(data.summary)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
        }
    }
}
