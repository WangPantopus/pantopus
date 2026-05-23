//
//  StartSupportTrainSampleData.swift
//  Pantopus
//
//  Deterministic A12.11 data for previews, structural snapshots, and
//  step-1 invite/verified-recipient fixtures.
//

import Foundation

public enum StartSupportTrainSampleData {
    public static let verifiedNeighbor = MailRecipientDTO(
        userId: "u_maya_patel",
        name: "Maya Patel",
        username: "maya",
        homeId: "home_elm_418",
        homeAddress: "418 Elm St, Apt 2",
        isVerified: true,
        homeMediaUrl: nil,
        isOnPantopus: true
    )

    public static let verifiedContextNote =
        "Maya is home after knee surgery on the 12th. Meals, dog walks for Pixel, and rides to PT would help."

    public static let inviteQuery = "David Chen"
}
