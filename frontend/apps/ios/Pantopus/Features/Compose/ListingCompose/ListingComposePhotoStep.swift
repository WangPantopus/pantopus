//
//  ListingComposePhotoStep.swift
//  Pantopus
//
//  Photo picker step components for the Snap & Sell listing wizard.
//

import AVFoundation
import SwiftUI

struct ListingComposePhotosStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel
    let onRequestRemove: (ListingComposePhoto) -> Void

    var body: some View {
        if viewModel.isCameraCaptureStep {
            ListingComposeCameraStep(viewModel: viewModel)
        } else {
            ListingComposePhotoGridEditor(viewModel: viewModel, onRequestRemove: onRequestRemove)
        }
    }
}

private struct ListingComposeCameraStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        ZStack {
            ListingCameraPreview()
            CameraSceneOverlay()
            RuleOfThirdsGrid()
                .padding(.horizontal, 28)
                .padding(.top, 92)
                .padding(.bottom, 190)
            FramingBrackets()
                .padding(.horizontal, 28)
                .padding(.top, 92)
                .padding(.bottom, 190)
            VStack(spacing: 0) {
                topRail
                CapturedAnglesTray(
                    photos: viewModel.form.photos,
                    progressText: viewModel.snapCaptureProgressText
                )
                .padding(.top, Spacing.s4)
                Spacer()
                bottomRail
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s4)
            .padding(.bottom, Spacing.s5)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 612)
        .background(Color(red: 0.04, green: 0.04, blue: 0.05))
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .padding(.horizontal, -Spacing.s4)
        .accessibilityIdentifier("listingComposeCameraStep")
    }

    private var topRail: some View {
        VStack(spacing: Spacing.s3) {
            HStack {
                Button {
                    viewModel.skipToManualPhotoEditor()
                } label: {
                    HStack(spacing: Spacing.s1) {
                        Text("Skip to manual")
                            .font(.system(size: 12, weight: .semibold))
                        Icon(.arrowRight, size: 13, color: .white.opacity(0.78))
                    }
                    .foregroundStyle(.white.opacity(0.78))
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s2)
                    .background(.black.opacity(0.34))
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("listingComposeSkipManual")
                Spacer()
            }
            HStack {
                Spacer()
                HStack(spacing: Spacing.s2) {
                    Icon(.sparkles, size: 12, color: .white)
                    Text(viewModel.snapCoachingText)
                        .font(.system(size: 11.5, weight: .semibold))
                        .lineLimit(1)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, Spacing.s2)
                .background(Color(red: 0.49, green: 0.23, blue: 0.93).opacity(0.92))
                .clipShape(Capsule())
                .shadow(color: .black.opacity(0.28), radius: 14, x: 0, y: 8)
                Spacer()
            }
        }
    }

    private var bottomRail: some View {
        VStack(spacing: Spacing.s4) {
            HStack(spacing: Spacing.s2) {
                Icon(.lightbulb, size: 12, color: .white.opacity(0.9))
                Text("Daylight · clutter-free background = better price")
                    .font(.system(size: 11, weight: .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)
            }
            .foregroundStyle(.white.opacity(0.92))
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .background(.black.opacity(0.52))
            .clipShape(Capsule())

            HStack {
                CameraRailButton(icon: .image, label: "Library") {
                    viewModel.addLibraryPhoto()
                }
                .accessibilityIdentifier("listingComposeLibraryPhoto")
                Spacer()
                Button {
                    viewModel.captureSnapPhoto()
                } label: {
                    Circle()
                        .stroke(.white.opacity(0.95), lineWidth: 4)
                        .frame(width: 72, height: 72)
                        .overlay {
                            Circle()
                                .fill(.white)
                                .padding(7)
                                .shadow(color: .white.opacity(0.28), radius: 12)
                        }
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("listingComposeShutter")
                Spacer()
                CameraRailButton(icon: .zap, label: "Auto") {}
                    .accessibilityIdentifier("listingComposeFlash")
            }
            .padding(.horizontal, Spacing.s4)
        }
    }
}

private struct ListingComposePhotoGridEditor: View {
    @Bindable var viewModel: ListingComposeWizardViewModel
    let onRequestRemove: (ListingComposePhoto) -> Void

    private let columns: [GridItem] = [
        GridItem(.flexible(), spacing: Spacing.s3),
        GridItem(.flexible(), spacing: Spacing.s3)
    ]

    var body: some View {
        HeadlineBlock("Add photos")
        SubcopyBlock(
            "Show your item in good light. The first photo becomes the hero — long-press a tile to reorder, tap to remove."
        )
        LazyVGrid(columns: columns, spacing: Spacing.s3) {
            ForEach(Array(viewModel.form.photos.enumerated()), id: \.element.id) { index, photo in
                PhotoTile(
                    index: index,
                    photo: photo,
                    onTap: { onRequestRemove(photo) },
                    onMoveUp: index > 0 ? { viewModel.movePhoto(from: index, to: index - 1) } : nil,
                    onMoveDown: index < viewModel.form.photos.count - 1
                        ? { viewModel.movePhoto(from: index, to: index + 1) }
                        : nil,
                    onMakeHero: index > 0 ? { viewModel.makeHero(id: photo.id) } : nil
                )
            }
            if viewModel.form.photos.count < ListingComposeFormState.maxPhotos {
                AddPhotoTile { viewModel.addPhoto() }
            }
        }
        PhotoCountLabel(count: viewModel.form.photos.count)
    }
}

private struct ListingCameraPreview: View {
    var body: some View {
        #if targetEnvironment(simulator)
        CameraSceneOverlay()
        #else
        ListingCameraPreviewRepresentable()
            .overlay(CameraSceneOverlay().opacity(0.62))
        #endif
    }
}

#if !targetEnvironment(simulator)
private struct ListingCameraPreviewRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> ListingCameraPreviewController {
        ListingCameraPreviewController()
    }

    func updateUIViewController(_ uiViewController: ListingCameraPreviewController, context: Context) {}
}

private final class ListingCameraPreviewController: UIViewController {
    private let session = AVCaptureSession()
    private lazy var previewLayer = AVCaptureVideoPreviewLayer(session: session)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        configureIfAllowed()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer.frame = view.bounds
    }

    deinit {
        if session.isRunning {
            session.stopRunning()
        }
    }

    private func configureIfAllowed() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                guard granted else { return }
                DispatchQueue.main.async { self?.configureSession() }
            }
        default:
            break
        }
    }

    private func configureSession() {
        guard !session.isRunning else { return }
        session.beginConfiguration()
        session.sessionPreset = .photo
        defer { session.commitConfiguration() }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input)
        else { return }
        session.addInput(input)
        session.startRunning()
    }
}
#endif

private struct CameraSceneOverlay: View {
    var body: some View {
        ZStack {
            RadialGradient(
                colors: [
                    Color(red: 0.54, green: 0.64, blue: 0.58).opacity(0.34),
                    Color(red: 0.04, green: 0.04, blue: 0.05).opacity(0)
                ],
                center: .center,
                startRadius: 60,
                endRadius: 310
            )
            VStack {
                Spacer()
                SofaSilhouette()
                    .frame(height: 150)
                    .padding(.horizontal, 42)
                    .padding(.bottom, 130)
            }
        }
        .background(Color(red: 0.04, green: 0.04, blue: 0.05))
    }
}

private struct SofaSilhouette: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Color(red: 0.42, green: 0.53, blue: 0.45).opacity(0.56))
                .frame(height: 86)
                .offset(y: 20)
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(red: 0.48, green: 0.60, blue: 0.52).opacity(0.58))
                .frame(height: 48)
                .offset(y: -18)
            HStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(red: 0.36, green: 0.47, blue: 0.40).opacity(0.7))
                    .frame(width: 42, height: 118)
                Spacer()
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(red: 0.36, green: 0.47, blue: 0.40).opacity(0.7))
                    .frame(width: 42, height: 118)
            }
            .padding(.horizontal, 4)
            Ellipse()
                .fill(.black.opacity(0.28))
                .frame(height: 20)
                .offset(y: 86)
        }
    }
}

private struct RuleOfThirdsGrid: View {
    var body: some View {
        GeometryReader { proxy in
            Path { path in
                let width = proxy.size.width
                let height = proxy.size.height
                path.move(to: CGPoint(x: width / 3, y: 0))
                path.addLine(to: CGPoint(x: width / 3, y: height))
                path.move(to: CGPoint(x: width * 2 / 3, y: 0))
                path.addLine(to: CGPoint(x: width * 2 / 3, y: height))
                path.move(to: CGPoint(x: 0, y: height / 3))
                path.addLine(to: CGPoint(x: width, y: height / 3))
                path.move(to: CGPoint(x: 0, y: height * 2 / 3))
                path.addLine(to: CGPoint(x: width, y: height * 2 / 3))
            }
            .stroke(.white.opacity(0.18), lineWidth: 1)
        }
        .accessibilityHidden(true)
    }
}

private struct FramingBrackets: View {
    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let height = proxy.size.height
            ZStack {
                BracketCorner()
                    .position(x: 13, y: 13)
                BracketCorner()
                    .rotationEffect(.degrees(90))
                    .position(x: width - 13, y: 13)
                BracketCorner()
                    .rotationEffect(.degrees(-90))
                    .position(x: 13, y: height - 13)
                BracketCorner()
                    .rotationEffect(.degrees(180))
                    .position(x: width - 13, y: height - 13)
            }
        }
        .accessibilityHidden(true)
    }
}

private struct BracketCorner: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            Capsule().fill(.white).frame(width: 18, height: 2.5)
            Capsule().fill(.white).frame(width: 2.5, height: 18)
        }
        .frame(width: 26, height: 26, alignment: .topLeading)
    }
}

private struct CapturedAnglesTray: View {
    let photos: [ListingComposePhoto]
    let progressText: String

    private let labels = ["Wide", "Detail", "Tag", "Back"]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(progressText)
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(.white.opacity(0.7))
                .textCase(.uppercase)
            HStack(spacing: Spacing.s2) {
                ForEach(0..<ListingComposeFormState.targetCaptureAngles, id: \.self) { index in
                    AngleSlot(
                        isFilled: index < photos.count,
                        label: labels[index]
                    )
                }
            }
        }
    }
}

private struct AngleSlot: View {
    let isFilled: Bool
    let label: String

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .fill(isFilled ? Color(red: 0.36, green: 0.48, blue: 0.40).opacity(0.82) : .white.opacity(0.06))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(
                            isFilled ? .white : .white.opacity(0.55),
                            style: StrokeStyle(lineWidth: 1.5, dash: isFilled ? [] : [4, 4])
                        )
                )
            if isFilled {
                Circle()
                    .fill(Theme.Color.success)
                    .frame(width: 16, height: 16)
                    .overlay(Icon(.check, size: 9, color: .white))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .padding(4)
            } else {
                Text(label)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.white.opacity(0.85))
                    .textCase(.uppercase)
            }
        }
        .frame(height: 56)
    }
}

private struct CameraRailButton: View {
    let icon: PantopusIcon
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 1) {
                Icon(icon, size: 18, color: .white)
                Text(label)
                    .font(.system(size: 8, weight: .bold))
                    .textCase(.uppercase)
            }
            .foregroundStyle(.white)
            .frame(width: 48, height: 48)
            .background(.white.opacity(0.14))
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(.white.opacity(0.18), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct PhotoTile: View {
    let index: Int
    let photo: ListingComposePhoto
    let onTap: () -> Void
    let onMoveUp: (() -> Void)?
    let onMoveDown: (() -> Void)?
    let onMakeHero: (() -> Void)?

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .fill(Theme.Color.appSurfaceMuted)
                    .aspectRatio(1, contentMode: .fit)
                    .overlay(
                        Icon(.image, size: 32, color: Theme.Color.appTextSecondary)
                    )
                if index == 0 {
                    HeroChip()
                        .padding(Spacing.s2)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("listingCompose_photo_\(index)")
        .accessibilityLabel(
            index == 0
                ? "Photo \(index + 1) of grid. Hero photo. Tap to remove."
                : "Photo \(index + 1) of grid. Tap to remove."
        )
        .accessibilityAddTraits(.isButton)
        .contextMenu {
            if let onMakeHero {
                Button("Make hero", action: onMakeHero)
                    .accessibilityIdentifier("listingCompose_makeHero_\(index)")
            }
            if let onMoveUp {
                Button("Move up", action: onMoveUp)
                    .accessibilityIdentifier("listingCompose_moveUp_\(index)")
            }
            if let onMoveDown {
                Button("Move down", action: onMoveDown)
                    .accessibilityIdentifier("listingCompose_moveDown_\(index)")
            }
        }
    }
}

private struct HeroChip: View {
    var body: some View {
        Text("HERO")
            .pantopusTextStyle(.overline)
            .foregroundStyle(Theme.Color.appTextInverse)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, Spacing.s1)
            .background(Theme.Color.primary600)
            .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
    }
}

private struct AddPhotoTile: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(
                    Theme.Color.appBorder,
                    style: StrokeStyle(lineWidth: 1, dash: [4, 4])
                )
                .aspectRatio(1, contentMode: .fit)
                .overlay(
                    VStack(spacing: Spacing.s1) {
                        Icon(.camera, size: 28, color: Theme.Color.appTextSecondary)
                        Text("Add photo")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("listingCompose_addPhoto")
        .accessibilityLabel("Add photo")
        .accessibilityAddTraits(.isButton)
    }
}

private struct PhotoCountLabel: View {
    let count: Int

    var body: some View {
        Text("\(count) of \(ListingComposeFormState.maxPhotos) photos")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("listingCompose_photoCount")
    }
}
