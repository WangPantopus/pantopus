// ============================================================
// UPLOAD ENDPOINTS — S3-based file uploads
// Profile pictures, gig media, home task media
// ============================================================

import apiClient, { get, del } from '../client';

function appendMultipartFiles(formData: FormData, files: any[], prefix: string) {
  files.forEach((file: any, idx: number) => {
    // React Native FormData requires uri-based file objects.
    if (file && typeof file === 'object' && file.uri) {
      const rawType = file.type || file.mimeType;
      const normalizedType =
        typeof rawType === 'string' && rawType.includes('/')
          ? rawType
          : (file.mimeType || 'application/octet-stream');
      formData.append('files', {
        uri: file.uri,
        name: file.name || file.fileName || `${prefix}-${Date.now()}-${idx}`,
        type: normalizedType,
      } as any);
      return;
    }
    formData.append('files', file as any);
  });
}

/**
 * Upload profile picture
 */
export async function uploadProfilePicture(file: File): Promise<{
  message: string;
  url: string;
  key: string;
  user: { id: string; profile_picture_url: string };
}> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await apiClient.post('/api/upload/profile-picture', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload media files for a gig (up to 10)
 */
export async function uploadGigMedia(
  gigId: string,
  files: File[]
): Promise<{
  message: string;
  media: Array<{
    id: string;
    file_url: string;
    file_key: string;
    file_name: string;
    file_type: string;
    mime_type: string;
    file_size: number;
    thumbnail_url: string | null;
  }>;
}> {
  const formData = new FormData();
  appendMultipartFiles(formData, files as any[], 'gig-media');

  const response = await apiClient.post(`/api/upload/gig-media/${gigId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload media files for gig Q&A (questions/answers)
 */
export async function uploadGigQuestionMedia(
  gigId: string,
  files: File[]
): Promise<{
  message: string;
  media: Array<{
    file_url: string;
    file_key: string;
    file_name: string;
    file_type: string;
    mime_type: string;
    file_size: number;
    thumbnail_url: string | null;
  }>;
}> {
  const formData = new FormData();
  appendMultipartFiles(formData, files as any[], 'gig-question-media');

  const response = await apiClient.post(`/api/upload/gig-question-media/${gigId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload completion proof files for a gig
 */
export async function uploadGigCompletionMedia(
  gigId: string,
  files: File[]
): Promise<{
  message: string;
  media: Array<{
    file_url: string;
    file_key: string;
    file_name: string;
    file_type: string;
    mime_type: string;
    file_size: number;
    thumbnail_url: string | null;
  }>;
}> {
  const formData = new FormData();
  appendMultipartFiles(formData, files as any[], 'gig-completion-media');

  const response = await apiClient.post(`/api/upload/gig-completion-media/${gigId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Get media for a gig
 */
export async function getGigMedia(gigId: string): Promise<{
  media: Array<{
    id: string;
    file_url: string;
    file_name: string;
    file_type: string;
    mime_type: string;
    thumbnail_url: string | null;
  }>;
}> {
  return get(`/api/upload/gig-media/${gigId}`);
}

/**
 * Delete a gig media file
 */
export async function deleteGigMedia(gigId: string, mediaId: string): Promise<{ message: string }> {
  return del(`/api/upload/gig-media/${gigId}/${mediaId}`);
}

/**
 * Upload media for a home task (up to 10)
 */
export async function uploadHomeTaskMedia(
  homeId: string,
  taskId: string,
  files: File[]
): Promise<{
  message: string;
  media: Array<{
    id: string;
    file_url: string;
    file_key: string;
    file_name: string;
    file_type: string;
    mime_type: string;
    file_size: number;
    thumbnail_url: string | null;
  }>;
}> {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));

  const response = await apiClient.post(
    `/api/upload/home-task-media/${homeId}/${taskId}`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  );
  return response.data;
}

/**
 * Upload attachments for a chat room (documents/images).
 */
export async function uploadChatMedia(
  roomId: string,
  files: File[]
): Promise<{
  message: string;
  media: Array<{
    id: string;
    file_url: string;
    original_filename: string;
    mime_type: string;
    file_size: number;
    file_type: string;
  }>;
}> {
  const formData = new FormData();
  files.forEach((file: any, idx: number) => {
    // React Native files are uri-based objects, not browser File objects.
    if (file && typeof file === 'object' && file.uri) {
      formData.append('files', {
        uri: file.uri,
        name: file.name || `chat-file-${Date.now()}-${idx}`,
        type: file.type || file.mimeType || 'application/octet-stream',
      } as any);
      return;
    }
    formData.append('files', file as any);
  });

  // Must explicitly set multipart/form-data so axios doesn't use the default
  // application/json content-type which causes multer to skip file parsing.
  const response = await apiClient.post(`/api/upload/chat-media/${roomId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload ownership evidence document for a claim.
 * Used on mobile with React Native FormData (uri-based file objects).
 */
export async function uploadOwnershipEvidence(
  homeId: string,
  claimId: string,
  file: any, // File on web, { uri, name, type } on mobile
  evidenceType: string
): Promise<{
  message: string;
  evidence: {
    id: string;
    evidence_type: string;
    status: string;
    file_url: string;
    file_name: string;
  };
}> {
  const formData = new FormData();

  // React Native FormData requires uri-based file objects with explicit shape.
  if (file && typeof file === 'object' && file.uri) {
    formData.append('file', {
      uri: file.uri,
      name: file.name || `evidence-${Date.now()}`,
      type: file.type || file.mimeType || 'application/octet-stream',
    } as any);
  } else {
    formData.append('file', file);
  }

  formData.append('evidence_type', evidenceType);

  const response = await apiClient.post(
    `/api/upload/ownership-evidence/${homeId}/${claimId}`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  );
  return response.data;
}

/**
 * Get media for a home task
 */
export async function getHomeTaskMedia(
  homeId: string,
  taskId: string
): Promise<{
  media: Array<{
    id: string;
    file_url: string;
    file_name: string;
    file_type: string;
    mime_type: string;
    thumbnail_url: string | null;
  }>;
}> {
  return get(`/api/upload/home-task-media/${homeId}/${taskId}`);
}

/**
 * Upload a Live Photo pair (still image + companion video clip).
 * Returns URLs for both files plus an optional thumbnail.
 */
export async function uploadLivePhoto(
  imageFile: { uri: string; name?: string; type?: string },
  videoFile: { uri: string; name?: string; type?: string },
): Promise<{
  imageUrl: string;
  liveVideoUrl: string;
  thumbnailUrl: string | null;
}> {
  const formData = new FormData();
  formData.append('image', {
    uri: imageFile.uri,
    name: imageFile.name || `live-still-${Date.now()}.jpg`,
    type: imageFile.type || 'image/jpeg',
  } as any);
  formData.append('video', {
    uri: videoFile.uri,
    name: videoFile.name || `live-video-${Date.now()}.mov`,
    type: videoFile.type || 'video/quicktime',
  } as any);

  const response = await apiClient.post('/api/upload/live-photo', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload media files for a post (images/videos only).
 */
export async function uploadPostMedia(
  postId: string,
  files: any[]
): Promise<{
  message: string;
  media_urls: string[];
  media_types: string[];
}> {
  const formData = new FormData();
  appendMultipartFiles(formData, files, 'post-media');

  const response = await apiClient.post(`/api/upload/post-media/${postId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload image files for a post comment.
 */
export async function uploadCommentMedia(
  commentId: string,
  files: any[]
): Promise<{
  message: string;
  attachments: Array<{
    id: string;
    comment_id?: string | null;
    file_url: string;
    original_filename: string;
    mime_type: string;
    file_size: number;
    file_type?: string;
    created_at?: string;
  }>;
}> {
  const formData = new FormData();
  appendMultipartFiles(formData, files, 'comment-media');

  const response = await apiClient.post(`/api/upload/comment-media/${commentId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload media files for a listing.
 */
export async function uploadListingMedia(
  listingId: string,
  files: any[]
): Promise<{
  message: string;
  media_urls: string[];
  media_types: string[];
}> {
  const formData = new FormData();
  appendMultipartFiles(formData, files, 'listing');

  const response = await apiClient.post(`/api/upload/listing-media/${listingId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Remove one listing media item by index.
 */
export async function deleteListingMedia(
  listingId: string,
  index: number
): Promise<{ message: string; media_urls: string[]; media_types: string[] }> {
  const response = await apiClient.delete(`/api/upload/listing-media/${listingId}`, { data: { index } });
  return response.data;
}

/**
 * Upload files to use as mailbox attachments.
 */
export async function uploadMailAttachments(
  files: any[]
): Promise<{
  message: string;
  attachments: Array<{
    url: string;
    key: string;
    name: string;
    mime_type: string;
    size: number;
  }>;
}> {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));

  const response = await apiClient.post('/api/upload/mail-attachments', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload images for AI assistant chat.
 * Returns public URLs to pass to the AI chat endpoint.
 */
export async function uploadAIMedia(
  files: any[]
): Promise<{
  message: string;
  images: Array<{
    url: string;
    key: string;
    name: string;
    mime_type: string;
    size: number;
  }>;
}> {
  const formData = new FormData();
  appendMultipartFiles(formData, files, 'ai-chat');

  const response = await apiClient.post('/api/upload/ai-media', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

/**
 * Upload business logo or banner image.
 */
export async function uploadBusinessMedia(
  businessId: string,
  file: any,
  type: 'logo' | 'banner'
): Promise<{
  message: string;
  url: string;
  key: string;
}> {
  const formData = new FormData();
  if (file && typeof file === 'object' && file.uri) {
    formData.append('file', {
      uri: file.uri,
      name: file.name || `business-${type}-${Date.now()}`,
      type: file.type || file.mimeType || 'image/jpeg',
    } as any);
  } else {
    formData.append('file', file);
  }

  const response = await apiClient.post(
    `/api/upload/business-media/${businessId}?type=${type}`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  );
  return response.data;
}

/**
 * Upload media (photos/files) to attach to a review.
 * Max 5 files. Only the review author can upload.
 */
export async function uploadReviewMedia(
  reviewId: string,
  files: any[]
): Promise<{
  message: string;
  media_urls: string[];
}> {
  const formData = new FormData();
  appendMultipartFiles(formData, files, 'review');

  const response = await apiClient.post(`/api/upload/review-media/${reviewId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}
