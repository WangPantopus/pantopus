# Pantopus iOS — Makefile
# Thin wrapper over xcodegen / xcodebuild / fastlane.

SCHEME     := Pantopus
PROJECT    := Pantopus.xcodeproj
DESTINATION_SIM := platform=iOS Simulator,name=iPhone 15,OS=latest

.PHONY: help bootstrap open build test lint format clean beta release

help:
	@echo "Pantopus iOS — available targets:"
	@echo ""
	@echo "  bootstrap   Install XcodeGen deps and generate $(PROJECT)"
	@echo "  open        Open $(PROJECT) in Xcode"
	@echo "  build       Build for simulator"
	@echo "  test        Run unit + UI tests"
	@echo "  lint        Run SwiftLint"
	@echo "  format      Run SwiftFormat in-place"
	@echo "  clean       Remove build artifacts and generated project"
	@echo "  beta        Fastlane: upload to TestFlight"
	@echo "  release     Fastlane: submit to App Store"

bootstrap:
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
	rm -rf build DerivedData $(PROJECT) .swiftpm Package.resolved

beta:
	bundle exec fastlane beta

release:
	bundle exec fastlane release
