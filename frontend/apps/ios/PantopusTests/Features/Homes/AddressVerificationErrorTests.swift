import Foundation
import Testing
@testable import Pantopus

/// UX-06 — neither native client had any address layer. Both wizards let the
/// user complete every step, then rendered the 422 from `POST /api/homes`
/// through the generic networking path: a string like "Request failed", with no
/// indication of what was wrong or what to change. The `code` the server sends
/// was referenced nowhere in either app.
@Suite("AddressVerificationError")
struct AddressVerificationErrorTests {
    private func clientError(code: String) -> APIError {
        .clientError(status: 422, message: #"{"error":"nope","code":"\#(code)"}"#)
    }

    @Test("every code the backend can return is mapped")
    func allBackendCodesMapped() {
        // Kept in step with getHomeValidationError in backend/routes/home.js.
        let backendCodes = [
            "ADDRESS_MISSING_UNIT",
            "ADDRESS_NOT_HOME",
            "ADDRESS_UNDELIVERABLE",
            "ADDRESS_CONFLICT",
            "ADDRESS_LOW_CONFIDENCE",
            "ADDRESS_AMBIGUOUS",
            "ADDRESS_PO_BOX",
            "ADDRESS_MISSING_STREET_NUMBER",
            "ADDRESS_UNVERIFIED_STREET_NUMBER",
            "ADDRESS_STEP_UP_REQUIRED",
            "ADDRESS_VALIDATION_UNAVAILABLE"
        ]

        for code in backendCodes {
            #expect(
                AddressVerificationError(rawValue: code) != nil,
                "backend can return \(code) and the app does not recognise it"
            )
        }
    }

    @Test("extracts the code from a 422 body")
    func extractsCode() {
        let error = AddressVerificationError.from(clientError(code: "ADDRESS_MISSING_UNIT"))
        #expect(error == .missingUnit)
    }

    @Test("ignores errors that are not address refusals")
    func ignoresOtherErrors() {
        #expect(AddressVerificationError.from(APIError.unauthorized) == nil)
        #expect(AddressVerificationError.from(APIError.notFound) == nil)
        #expect(AddressVerificationError.from(clientError(code: "SOMETHING_ELSE")) == nil)
    }

    @Test("survives a malformed body without crashing")
    func malformedBody() {
        #expect(AddressVerificationError.from(APIError.clientError(status: 422, message: nil)) == nil)
        #expect(AddressVerificationError.from(APIError.clientError(status: 422, message: "not json")) == nil)
    }

    @Test("every case says what is wrong and what to do")
    func everyCaseIsActionable() {
        for error in AddressVerificationError.allCases {
            #expect(!error.message.isEmpty)
            #expect(!error.recoverySuggestion.isEmpty)
        }
    }

    @Test("only a genuine outage suggests retrying")
    func onlyOutageIsRetryable() {
        // Telling someone to retry a PO Box forever is the defect this replaces.
        for error in AddressVerificationError.allCases where error != .unavailable {
            #expect(!error.isRetryable, "\(error.rawValue) should not be retryable")
        }
        #expect(AddressVerificationError.unavailable.isRetryable)
    }

    @Test("refusals the address step can fix route back to it")
    func fixableRouting() {
        #expect(AddressVerificationError.missingUnit.isFixableInAddressStep)
        #expect(AddressVerificationError.poBox.isFixableInAddressStep)
        // A conflict is not a typo — sending the user back to edit would loop them.
        #expect(!AddressVerificationError.conflict.isFixableInAddressStep)
        #expect(!AddressVerificationError.unavailable.isFixableInAddressStep)
    }
}
