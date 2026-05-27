//
//  MailDetailContent+Mutations.swift
//  Pantopus
//
//  Local copy helpers for optimistic mail-detail state changes.
//

extension MailDetailContent {
    /// Return a copy of `content` with `isAcknowledged` flipped to the
    /// supplied value. Used by the optimistic acknowledge mutation.
    static func replacingAck(_ content: MailDetailContent, with value: Bool) -> MailDetailContent {
        rebuild(content, readStatusLabel: value ? "Read" : content.readStatusLabel, isAcknowledged: value)
    }

    /// Return a copy of `content` with the community detail's RSVP
    /// status flipped. Used by the optimistic `setRsvp` mutation.
    static func replacingRsvp(
        _ content: MailDetailContent,
        with status: CommunityRsvpStatus
    ) -> MailDetailContent {
        guard let community = content.communityDetail else { return content }
        let updatedCommunity = CommunityDetailDTO(
            communityItemId: community.communityItemId,
            subtype: community.subtype,
            group: community.group,
            event: community.event,
            poll: community.poll,
            update: community.update,
            attendees: community.attendees,
            attendeeCount: status == .going && community.rsvp != .going
                ? community.attendeeCount + 1
                : (status != .going && community.rsvp == .going
                    ? max(0, community.attendeeCount - 1)
                    : community.attendeeCount),
            attendeesFromBlock: community.attendeesFromBlock,
            pulseThread: community.pulseThread,
            rsvp: status
        )
        return rebuild(content, communityDetail: updatedCommunity)
    }

    /// Return a copy of `content` with the gig detail flipped to its
    /// accepted state. Used by the optimistic `acceptGigBid` mutation.
    static func replacingGigAccepted(
        _ content: MailDetailContent,
        with gig: GigDetailDTO
    ) -> MailDetailContent {
        rebuild(content, gigDetail: gig)
    }

    /// Return a copy of `content` with the memory detail's `isSaved`
    /// flag flipped. Used by the optimistic `saveMemoryToVault` flow.
    static func replacingMemorySaved(
        _ content: MailDetailContent,
        with value: Bool
    ) -> MailDetailContent {
        guard let memory = content.memoryDetail else { return content }
        return rebuild(content, memoryDetail: memory.withSaved(value))
    }

    /// Shared rebuilder so the per-field mutations stay one line each.
    /// Every parameter defaults to "keep the existing field"; pass only
    /// the field(s) you intend to flip.
    private static func rebuild(
        _ content: MailDetailContent,
        readStatusLabel: String? = nil,
        isAcknowledged: Bool? = nil,
        bookletDetail: BookletDetailDTO?? = nil,
        certifiedDetail: CertifiedDetailDTO?? = nil,
        communityDetail: CommunityDetailDTO?? = nil,
        couponDetail: CouponDetailDTO?? = nil,
        gigDetail: GigDetailDTO?? = nil,
        memoryDetail: MemoryDetailDTO?? = nil,
        packageDetail: PackageBodyContent?? = nil
    ) -> MailDetailContent {
        MailDetailContent(
            mailId: content.mailId,
            category: content.category,
            trust: content.trust,
            detailTrust: content.detailTrust,
            senderDisplayName: content.senderDisplayName,
            senderMeta: content.senderMeta,
            senderTypeLabel: content.senderTypeLabel,
            carrierLine: content.carrierLine,
            senderInitials: content.senderInitials,
            senderUserId: content.senderUserId,
            title: content.title,
            excerpt: content.excerpt,
            referenceLabel: content.referenceLabel,
            createdAtLabel: content.createdAtLabel,
            expiresAtLabel: content.expiresAtLabel,
            readStatusLabel: readStatusLabel ?? content.readStatusLabel,
            bodyParagraphs: content.bodyParagraphs,
            attachments: content.attachments,
            aiSummary: content.aiSummary,
            ackRequired: content.ackRequired,
            isAcknowledged: isAcknowledged ?? content.isAcknowledged,
            isArchived: content.isArchived,
            bookletDetail: bookletDetail ?? content.bookletDetail,
            certifiedDetail: certifiedDetail ?? content.certifiedDetail,
            communityDetail: communityDetail ?? content.communityDetail,
            couponDetail: couponDetail ?? content.couponDetail,
            gigDetail: gigDetail ?? content.gigDetail,
            memoryDetail: memoryDetail ?? content.memoryDetail,
            packageDetail: packageDetail ?? content.packageDetail
        )
    }
}
