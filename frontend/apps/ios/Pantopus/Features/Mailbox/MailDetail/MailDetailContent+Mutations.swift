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
            readStatusLabel: value ? "Read" : content.readStatusLabel,
            bodyParagraphs: content.bodyParagraphs,
            attachments: content.attachments,
            aiSummary: content.aiSummary,
            ackRequired: content.ackRequired,
            isAcknowledged: value,
            isArchived: content.isArchived,
            bookletDetail: content.bookletDetail,
            certifiedDetail: content.certifiedDetail,
            communityDetail: content.communityDetail
        )
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
            group: community.group,
            event: community.event,
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
        return MailDetailContent(
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
            readStatusLabel: content.readStatusLabel,
            bodyParagraphs: content.bodyParagraphs,
            attachments: content.attachments,
            aiSummary: content.aiSummary,
            ackRequired: content.ackRequired,
            isAcknowledged: content.isAcknowledged,
            isArchived: content.isArchived,
            bookletDetail: content.bookletDetail,
            certifiedDetail: content.certifiedDetail,
            communityDetail: updatedCommunity
        )
    }
}
