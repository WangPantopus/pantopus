// ============================================================
// API PACKAGE - MAIN EXPORT
// Central export for all API endpoints and client utilities
// ============================================================

// Export client utilities
export {
  default as apiClient,
  getAuthToken,
  getApiBaseUrl,
  hasActiveSession,
  setAuthToken,
  setRefreshToken,
  getRefreshToken,
  clearAuthToken,
  applyAuthSession,
  clearAuthSession,
  refreshAuthSession,
  setTokenCache,
  onTokenChange,
  configureApiClient,
  apiRequest,
  get,
  post,
  put,
  del,
  uploadFile,
} from './client';

// Export types
export type { TokenStorage, AuthSessionUpdate, AuthRefreshResult, AuthClientEvent } from './client';

// Export all endpoints as namespaces
export * as auth from './endpoints/auth';
export * as users from './endpoints/users';
export * as gigs from './endpoints/gigs';
export * as homes from './endpoints/homes';
export * as chat from './endpoints/chat';
export * as files from './endpoints/files';
export * as mailbox from './endpoints/mailbox';
export * as mailboxV2 from './endpoints/mailboxV2';
export * as mailboxV2P2 from './endpoints/mailboxV2Phase2';
export * as mailboxV2P3 from './endpoints/mailboxV2Phase3';
export * as payments from './endpoints/payments';
export * as geo from './endpoints/geo';
export * as bids from './endpoints/bids';
export * as homeProfile from './endpoints/homeProfile';
export * as notifications from './endpoints/notifications';
export * as homeIam from './endpoints/homeIam';
export * as upload from './endpoints/upload';       // NEW
export * as reviews from './endpoints/reviews';     // NEW
export * as posts from './endpoints/posts';                 // Scoped feeds & posts
export * as businesses from './endpoints/businesses';       // Business profiles
export * as businessIam from './endpoints/businessIam';     // Business IAM
export * as wallet from './endpoints/wallet';               // Wallet/balance
export * as relationships from './endpoints/relationships'; // Trust graph (connections)
export * as professional from './endpoints/professional';   // Professional mode
export * as hub from './endpoints/hub';                     // Hub (Mission Control)
export * as location from './endpoints/location';           // Viewing Location
export * as listings from './endpoints/listings';           // Marketplace Listings
export * as savedPlaces from './endpoints/savedPlaces';    // Saved Places
export * as homeOwnership from './endpoints/homeOwnership'; // Home Ownership (claims, owners, quorum, disputes)
export * as homeGuest from './endpoints/homeGuest';         // Public guest pass & shared resource views
export * as admin from './endpoints/admin';                 // Platform admin
export * as addressValidation from './endpoints/addressValidation'; // Address validation pipeline
export * as landlord from './endpoints/landlord';                  // Landlord portal
export * as tenant from './endpoints/tenant';                      // Tenant landlord verification flow
export * as businessSeats from './endpoints/businessSeats';        // Identity Firewall — Seat management
export * as privacy from './endpoints/privacy';                    // Identity Firewall — Privacy & blocks
export * as magicTask from './endpoints/magicTask';                // Magic Task — AI-powered task posting
export * as ai from './endpoints/ai';                              // AI Agent — chat, drafts, place brief
export * as mailCompose from './endpoints/mailCompose';            // Mail Compose — four-moment flow
export * as linkPreview from './endpoints/linkPreview';            // Link preview (OG metadata)
export * as supportTrains from './endpoints/supportTrains';        // Support Train (activities)

// Also export individual endpoint functions for convenience
export { login, register, logout, getAuthMethods, updatePassword, reauthenticate } from './endpoints/auth';
export { getProfile, getProfileByUsername, getMyProfile, updateProfile, followUser, sendSignals, getInviteCode, getMonthlyReceipt, getInviteProgress } from './endpoints/users';
export { getGigs, getGig, getGigById, createGig, createGigV2, getGigsInBounds, placeBid, getBrowseSections, dismissGig, undismissGig, getHiddenCategories, hideCategory, unhideCategory, getGigPriceBenchmark, getRebookableGigs } from './endpoints/gigs';
export { getHomes, attachToHome, detachFromHome } from './endpoints/homes';
export { getChatRooms, getBusinessChatRooms, sendMessage, markMessagesAsRead, markMessagesAsReadForIdentity, getConversationMessages, markConversationAsRead } from './endpoints/chat';
export { uploadProfilePicture, uploadPortfolio, getPortfolio } from './endpoints/files';
export { getMailbox, markMailAsRead, createAdCampaign } from './endpoints/mailbox';
export { getBalance, createPaymentIntent, requestPayout } from './endpoints/payments';
export {
  uploadGigMedia,
  uploadGigQuestionMedia,
  uploadGigCompletionMedia,
  getGigMedia,
  deleteGigMedia,
  uploadHomeTaskMedia,
  uploadChatMedia,
  uploadOwnershipEvidence,
  uploadPostMedia,
  uploadLivePhoto,
  uploadCommentMedia,
  uploadListingMedia,
  deleteListingMedia,
  uploadMailAttachments,
} from './endpoints/upload';  // NEW
export { createReview, getUserReviews, getGigReviews, getPendingReviews } from './endpoints/reviews';   // NEW
export { createBusiness, getMyBusinesses, getBusiness, getBusinessDashboard, updateBusiness, getVerificationStatus, selfAttest, uploadVerificationEvidence, reviewVerificationEvidence, getFoundingOfferStatus, claimFoundingOffer } from './endpoints/businesses';
export { getMyBusinessAccess, getTeamMembers, addTeamMember } from './endpoints/businessIam';
export { getHomeBusinessLinks, searchBusinesses, linkBusiness, removeBusinessLink, getHomePets, createHomePet, updateHomePet, deleteHomePet, getHomePolls, createHomePoll, voteOnPoll, updateHomePoll, getHomeActivity, getHomeSettings, updateHomeSettings, enableLockdown, disableLockdown, transferAdmin, getHomeHealthScore, getSeasonalChecklist, updateChecklistItem, getBillTrends, getHomeTimeline, getPropertyValue } from './endpoints/homeProfile';
export { sendRequest, acceptRequest, rejectRequest, getConnections, getPendingRequests, blockUser, disconnect } from './endpoints/relationships';
export { createProfile as createProfessionalProfile, getMyProfile as getMyProfessionalProfile, discoverProfessionals, startVerification } from './endpoints/professional';
export {
  submitResidencyClaim,
  getHomeClaims,
  approveResidencyClaim,
  rejectResidencyClaim,
  getMyClaims,
  getHouseholdAccessRequests,
  approveHouseholdAccessRequest,
  rejectHouseholdAccessRequest,
} from './endpoints/homes';
export { getHub, getHubToday, updateHubContext, getDiscovery, getHubPreferences, updateHubPreferences, dismissDensityMilestone } from './endpoints/hub';
export { getLocation, setLocation, resolveLocation, setPinned, setRadius } from './endpoints/location';
export { getListings, getNearbyListings, searchListings, createListing, getListing, toggleSave as toggleListingSave, getSavedListings, getMyListings, getUserListings, getCarouselListings, refreshListing, getListingsInBounds, browseListings, discoverListings, autocompleteListings } from './endpoints/listings';
export {
  submitOwnershipClaim,
  getMyOwnershipClaims,
  deleteMyOwnershipClaim,
  getHomeOwners,
  getSecuritySettings,
  getDisputeDetails,
  getHomeOwnershipClaims,
  getOwnershipClaimDetail,
  getOwnershipClaimComparison,
  reviewOwnershipClaim,
  uploadClaimEvidence,
  resolveOwnershipClaimRelationship,
  acceptOwnershipClaimMerge,
  challengeOwnershipClaim,
} from './endpoints/homeOwnership';
export { getLinkPreview } from './endpoints/linkPreview';
export type { LinkPreviewData } from './endpoints/linkPreview';

// Re-export commonly used types from endpoints for convenient importing
export type { Post, PostType, PostVisibility, PostFormat, FeedSurface, PostComment, PostCreator, PostingIdentity, MapMarker, MatchedBusiness, FeedResponseV2, CursorPagination, SafetyAlertKind, LocationPrecision, VisibilityScope, PostAs, Audience, DistributionTarget, FeedScope, PrecheckResult, FeedPreferences } from './endpoints/posts';
export type { ProfessionalCategory } from './endpoints/professional';
export type { GigBid, GigCluster, GigStack, BrowseSections, BrowseResponse } from '@pantopus/types';
export type { PriceBenchmark, RebookableGig } from './endpoints/gigs';

// Types from listings (marketplace redesign)
export type { Listing, ListingLayer, ListingType, ListingCategory, ListingCondition, ListingStatus, ListingCreator, MarketplaceBrowseParams, MarketplaceBrowseResponse, MarketplaceDiscoverResponse, MarketplaceAutocompleteResponse, ListingCategoryCluster, ListingOffer, ListingOfferStatus, ReputationScore, PriceSuggestion, TransactionReview } from './endpoints/listings';

// Types from businesses
export type { MapBusinessMarker, BusinessInsights, DiscoverySearchResult, DiscoverySearchResponse, CatalogPreviewItem, DiscoverySort, EndorsementInfo, OnboardingChecklistItem, OnboardingStatus, VerificationEvidence, VerificationStatus, FoundingOfferStatus, FoundingSlotClaim, BusinessDashboardResponse, BusinessInvoice, InvoiceLineItem, BusinessPage, BusinessMembership, BusinessReview, BusinessHours, BusinessSpecialHours } from './endpoints/businesses';

// Types from homeOwnership
export type {
  DisputeInfo,
  HomeOwner,
  OwnershipClaim,
  OwnershipClaimDetail,
  OwnershipClaimComparison,
  OwnershipClaimSubmissionResponse,
  SecuritySettings,
  SecurityState,
  ClaimPhaseV2,
  ClaimRoutingClassification,
  HouseholdResolutionState,
} from './endpoints/homeOwnership';

// Types from homes
export type {
  AddressCheckResult,
  AttomPropertyDetailPayload,
  HomePropertyDetailResponse,
  HouseholdAccessRequestRow,
  PropertySuggestionsResponse,
  PropertySuggestionTier,
} from './endpoints/homes';

// Types from hub
export type { ActionItem, ActivityItem, DiscoveryItem, DiscoveryFilter, JumpBackInItem, HubHome, HubPersonalCard, HubHomeCard, HubBusinessCard, SetupStep, HubPayload, NeighborDensity } from './endpoints/hub';

// Types from businessIam
export type { BusinessAuditEntry } from './endpoints/businessIam';

// Types from homeIam
export type { GuestPass, ScopedGrant } from './endpoints/homeIam';

// Types from homeGuest
export type { GuestPassView, SharedResourceView, PasscodeRequired } from './endpoints/homeGuest';

// Types from mailboxV2
export type { MailItemV2, Drawer, Tab, PendingRouting, EarnOffer, EarnBalance, MailPackage, PackageEvent, SenderTrust, MailDaySummary as MailDaySummaryV2 } from './endpoints/mailboxV2';

// Types from mailboxV2Phase2
export type { BookletPage, BookletMail, CertifiedMail, AuditEvent, VaultFolder, VaultSearchResult } from './endpoints/mailboxV2Phase2';

// Types from mailboxV2Phase3
export type { TranslationResult, CommunityMailItem, CommunityType, HomeAssetSummary, HomeMapPin, MapPinType, MailMemoryItem, YearInMail, MailDaySummary, MailDaySettings, AssetDetection, Stamp, SeasonalTheme, StampRarity, MailTask, VacationHold, HoldAction, PackageHoldAction, EarnWallet, WalletTransaction, TopSender, MailAssetLink } from './endpoints/mailboxV2Phase3';

// Types from tenant
export type { TenantHomeStatus, TenantLease, TenantLeaseState, LandlordInfo } from './endpoints/tenant';

// Types from addressValidation
export type { AddressVerdictStatus, AddressVerdict, NormalizedAddress, ValidateAddressResponse, AddressCandidate, ExistingHousehold, AddressClaim, MailVerificationStatus, MailVerifyStartResponse, MailVerifyConfirmResponse, MailVerifyStatusResponse } from './endpoints/addressValidation';
export { validateAddress, validateUnit, claimAddress, startMailVerification, confirmMailVerification, resendMailVerification, getMailVerificationStatus } from './endpoints/addressValidation';

// Convenience exports from businessSeats (Identity Firewall)
export { getMySeats, getBusinessSeats as getSeats, getSeatDetail, createSeatInvite, getInviteDetails, acceptInvite, declineInvite, updateSeat, removeSeat } from './endpoints/businessSeats';

// Convenience exports from privacy (Identity Firewall)
export { getPrivacySettings, updatePrivacySettings, getBlocks, createBlock, removeBlock } from './endpoints/privacy';

// Convenience exports from magicTask
export { getMagicDraft, getBasicDraft, magicPost, undoTask, getTemplateLibrary, getSavedTemplates, saveTemplate, deleteSavedTemplate, useTemplate, getMagicSettings, updateMagicSettings } from './endpoints/magicTask';

// Convenience exports from AI Agent
export { streamChat, draftListing, draftListingFromImages, draftPost, summarizeMail, getPlaceBrief, getConversations as getAIConversations, deleteConversation as deleteAIConversation, transcribeAudio } from './endpoints/ai';
export type { TranscriptionResult } from './endpoints/ai';

// Convenience exports from Mail Compose
export { sendComposedMail, searchRecipients, getRecipientHomeContext, requestAISuggestion, uploadVoicePostscript, getEscrowedMailPublic, claimEscrowedMail, withdrawEscrowedMail } from './endpoints/mailCompose';

// Types from Mail Compose
export type { SendMailResponse, RecipientSearchResult, HomeContext, EscrowedMailView } from './endpoints/mailCompose';

// Types from Magic Task
export type { MagicDraftRequest, MagicDraftResponse, MagicTaskDraft, MagicPostRequest, MagicPostResponse, SmartTemplate, SavedTaskTemplate, MagicSettings, ClarifyingQuestion, TaskItem, ScheduleType, PayType, TaskSourceFlow, PrivacyLevel, LocationMode } from '@pantopus/types';

// Types from AI Agent
export type { GigDraft, ListingDraft, PostDraft, ClarifyingQuestionAI, MailKeyFact, MailRecommendedAction, MailSummary, PlaceBriefHeadline, PlaceBriefSource, PlaceBrief, AIDraftType, AIChatDraft, AIChatMessage, AIConversation, AIStreamEvent, AIChatRequest, AIDraftListingRequest, AIDraftListingResponse, AIDraftPostRequest, AIDraftPostResponse, AISummarizeMailRequest } from '@pantopus/types';

// Types from Home Intelligence
export type { DimensionScore, HomeHealthScore, SeasonalChecklistItem, SeasonalChecklist, BillTrendData, PropertyValueData, HomeTimelineItem } from '@pantopus/types';

// Types from identity (Identity Firewall)
export type { BusinessSeat, SeatListItem, SeatDetail, MySeat, InviteDetails, UserPrivacySettings, UpdatePrivacySettingsPayload, UserProfileBlock, CreateBlockPayload, CreateSeatInvitePayload, AcceptInvitePayload, DeclineInvitePayload, UpdateSeatPayload, NotificationWithContext, SeatInviteStatus, SeatBindingMethod, SearchVisibilityLevel, ProfileVisibilityLevel, BlockScopeType, NotificationContextType, BusinessRoleBase } from '@pantopus/types';
