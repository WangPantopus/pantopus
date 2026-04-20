/**
 * Shared auth utilities for extracting error messages from API responses
 * and normalizing form inputs.
 */

/**
 * Validate a redirectTo path is safe for internal navigation.
 * Rejects protocol-relative URLs, external schemes, and path traversal.
 */
export function safeRedirectPath(redirectTo: string | null | undefined, fallback = '/app/hub'): string {
  if (!redirectTo) return fallback;
  // Must start with /app/ and not contain protocol markers or traversal
  if (
    !redirectTo.startsWith('/app/') ||
    redirectTo.includes('//') ||
    redirectTo.includes('..') ||
    redirectTo.includes('@') ||
    redirectTo.includes('\\')
  ) {
    return fallback;
  }
  return redirectTo;
}

/**
 * Extract a user-friendly error message from an API error.
 * The API client rejects with { message, statusCode, data, validationErrors, validationDetails }.
 */
export function extractApiError(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (!err) return fallback;

  const errObj = err as Record<string, unknown> | null;

  // Validation details → field-level errors
  const validationErrors = Array.isArray(errObj?.validationErrors) ? errObj.validationErrors : [];
  if (validationErrors.length > 0) {
    return validationErrors.map((m: unknown) => `- ${m}`).join('\n');
  }

  // Direct message from API client
  if (typeof errObj?.message === 'string' && errObj.message) {
    return errObj.message;
  }

  // Nested error in data
  const data = errObj?.data as Record<string, unknown> | undefined;
  if (typeof data?.error === 'string' && data.error) {
    return data.error;
  }

  // Standard Error instance
  if (err instanceof Error && err.message) {
    return err.message;
  }

  return fallback;
}

/**
 * Extract field-level errors from an API error response.
 */
export function extractFieldErrors(err: unknown): Record<string, string> {
  const errObj = err as Record<string, unknown> | null;
  const details = Array.isArray(errObj?.validationDetails) ? errObj.validationDetails : [];
  const fieldErrors: Record<string, string> = {};
  for (const d of details) {
    const detail = d as Record<string, unknown>;
    if (typeof detail?.field === 'string' && typeof detail?.message === 'string' && !fieldErrors[detail.field]) {
      fieldErrors[detail.field] = detail.message;
    }
  }
  return fieldErrors;
}

/**
 * Normalize an email address for consistent comparison.
 */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}
