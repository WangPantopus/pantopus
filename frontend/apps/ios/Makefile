# Pantopus iOS — Makefile
# Thin wrapper over xcodegen / xcodebuild / fastlane.

SCHEME     := Pantopus
PROJECT    := Pantopus.xcodeproj
DESTINATION_SIM := platform=iOS Simulator,name=iPhone 15,OS=latest

ENV_FILE        := .env
SECRETS_XCCONFIG := Config/Secrets.xcconfig

.PHONY: help bootstrap open build test lint format clean beta release validate-env env-to-xcconfig

help:
	@echo "Pantopus iOS — available targets:"
	@echo ""
	@echo "  bootstrap      Install XcodeGen deps and generate $(PROJECT)"
	@echo "  validate-env   Check that .env has the keys the build needs"
	@echo "  open           Open $(PROJECT) in Xcode"
	@echo "  build          Build for simulator"
	@echo "  test           Run unit + UI tests"
	@echo "  lint           Run SwiftLint"
	@echo "  format         Run SwiftFormat in-place"
	@echo "  clean          Remove build artifacts and generated project"
	@echo "  beta           Fastlane: upload to TestFlight"
	@echo "  release        Fastlane: submit to App Store"

# Write Config/Secrets.xcconfig from .env so Info.plist variable substitutions
# (StripePublishableKey, SentryDSN, …) resolve at build time.
env-to-xcconfig:
	@mkdir -p Config
	@if [ ! -f $(ENV_FILE) ]; then \
		echo "Creating empty $(SECRETS_XCCONFIG) — copy .env.example to .env to customise."; \
		: > $(SECRETS_XCCONFIG); \
	else \
		echo "// Generated from $(ENV_FILE) — do not edit by hand." > $(SECRETS_XCCONFIG); \
		grep -E '^[A-Z_][A-Z0-9_]*=' $(ENV_FILE) \
			| grep -v '^#' \
			| sed -E 's/^([A-Z_][A-Z0-9_]*)=(.*)$$/\1 = \2/' \
			>> $(SECRETS_XCCONFIG); \
		echo "✓ $(SECRETS_XCCONFIG) written from $(ENV_FILE)"; \
	fi

validate-env:
	@missing=""; \
	if [ ! -f $(ENV_FILE) ]; then \
		echo "✗ $(ENV_FILE) not found. Run: cp .env.example .env"; exit 1; \
	fi; \
	for key in STRIPE_PUBLISHABLE_KEY; do \
		if ! grep -q "^$$key=..*" $(ENV_FILE); then missing="$$missing $$key"; fi; \
	done; \
	if [ -n "$$missing" ]; then \
		echo "✗ Missing or empty in $(ENV_FILE):$$missing"; exit 1; \
	fi; \
	echo "✓ $(ENV_FILE) has the required keys. Optional: SENTRY_DSN, MATCH_*, APP_STORE_CONNECT_*."

bootstrap: env-to-xcconfig
	@command -v xcodegen >/dev/null 2>&1 || { echo >&2 "Install XcodeGen: brew install xcodegen"; exit 1; }
	xcodegen generate
	@echo "✓ $(PROJECT) generated. Open with: make open"

open: bootstrap
	open $(PROJECT)

build: bootstrap
	xcodebuild \
		-project $(PROJECT) \
		-scheme $(SCHEME) \
		-destination "$(DESTINATION_SIM)" \
		-configuration Debug \
		build

test: bootstrap
	xcodebuild \
		-project $(PROJECT) \
		-scheme $(SCHEME) \
		-destination "$(DESTINATION_SIM)" \
		-configuration Debug \
		test

lint:
	@command -v swiftlint >/dev/null 2>&1 || { echo >&2 "Install SwiftLint: brew install swiftlint"; exit 1; }
	swiftlint lint

format:
	@command -v swiftformat >/dev/null 2>&1 || { echo >&2 "Install SwiftFormat: brew install swiftformat"; exit 1; }
	swiftformat .

clean:
	rm -rf build DerivedData $(PROJECT) .swiftpm Package.resolved Config/Secrets.xcconfig

beta:
	bundle exec fastlane beta

release:
	bundle exec fastlane release
