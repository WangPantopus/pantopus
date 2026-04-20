const express = require('express');
const router = express.Router();
const { createClient } = require('@supabase/supabase-js');
const crypto = require('crypto');
const { randomBytes } = crypto;
const supabase = require('../config/supabase');
const supabaseAdmin = require('../config/supabaseAdmin');
const { signUp, signIn } = require('../config/auth');
const rateLimit = require('express-rate-limit');
const verifyToken = require('../middleware/verifyToken');
const validate = require('../middleware/validate');
const Joi = require('joi');
const logger = require('../utils/logger');
const { getPublicResidencySummary } = require('../utils/publicResidencyProfile');
const { generateCsrfToken } = require('../utils/csrf');
const affinityService = require('../services/gig/affinityService');
const inviteRewardService = require('../services/inviteRewardService');

// ── Auth cookie helpers (AUTH-3.3) ──────────────────────────

const IS_PRODUCTION = process.env.NODE_ENV === 'production';

/**
 * Set httpOnly auth cookies + non-httpOnly CSRF cookie on the response.
 * Also returns tokens in JSON body for mobile backward compat.
 */
function setAuthCookies(res, accessToken, refreshToken, userId) {
  res.cookie('pantopus_access', accessToken, {
    httpOnly: true,
    secure: IS_PRODUCTION,
    sameSite: 'lax',
    path: '/',
    maxAge: 3600 * 1000, // 1 hour
  });

  if (refreshToken) {
    res.cookie('pantopus_refresh', refreshToken, {
      httpOnly: true,
      secure: IS_PRODUCTION,
      sameSite: 'lax',
      path: '/api/users/refresh',
      maxAge: 7 * 24 * 3600 * 1000, // 7 days
    });
  }

  // Session-bound CSRF token: HMAC(secret, userId) so the token
  // cannot be forged or reused across different user sessions.
  const csrfToken = userId ? generateCsrfToken(userId) : crypto.randomBytes(32).toString('hex');
  res.cookie('pantopus_csrf', csrfToken, {
    httpOnly: false,
    secure: IS_PRODUCTION,
    sameSite: 'lax',
    path: '/',
    maxAge: 24 * 3600 * 1000, // 24 hours — longer than access token (1h)
    // because CSRF is HMAC-deterministic (same value per user) and gets
    // refreshed alongside the access token on each refresh cycle.
  });

  // JS-readable session flag for client-side auth guards (survives page
  // refresh). Long-lived — actual auth is gated by httpOnly cookies above.
  res.cookie('pantopus_session', '1', {
    httpOnly: false,
    secure: IS_PRODUCTION,
    sameSite: 'lax',
    path: '/',
    maxAge: 30 * 24 * 3600 * 1000, // 30 days
  });
}

/**
 * Check if the client uses cookie-based token transport (web).
 * If so, tokens should NOT be included in JSON response bodies.
 */
function isCookieTransport(req) {
  // req.get() is Express's header getter; fall back to raw headers object
  const val = typeof req.get === 'function' ? req.get('x-token-transport') : req.headers?.['x-token-transport'];
  return val === 'cookie';
}

function clearAuthCookies(res) {
  res.clearCookie('pantopus_access', { path: '/' });
  res.clearCookie('pantopus_refresh', { path: '/api/users/refresh' });
  res.clearCookie('pantopus_csrf', { path: '/' });
  res.clearCookie('pantopus_session', { path: '/' });
}

// ============ RATE LIMITERS ============

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 10,
  message: { error: 'Too many login attempts. Please wait a few minutes and try again.' },
});

const registerLimiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 hour
  max: 20,
  message: { error: 'Too many registration attempts. Please try again later.' },
});

const forgotPasswordLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 5,
  message: { error: 'Too many password reset requests. Please try again later.' },
});

const resendVerificationLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 5,
  message: { error: 'Too many verification email requests. Please try again later.' },
});

const oauthLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 20,
  message: { error: 'Too many OAuth requests. Please try again later.' },
});

const refreshLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 30,
  message: { error: 'Too many token refresh requests. Please try again later.' },
});

const reauthLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 10,
  message: 'Too many re-authentication attempts. Please try again later.',
});

const logoutLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 10,
  message: { error: 'Too many logout requests. Please try again later.' },
});

const OAUTH_USER_SELECT = 'id, username, name, first_name, last_name, email, verified, role';
const PASSWORD_MIN_LENGTH = 12;
const PASSWORD_MAX_LENGTH = 128;

function isUniqueConstraintError(error) {
  if (!error) return false;
  if (String(error.code) === '23505') return true;
  const message = String(error.message || '').toLowerCase();
  const details = String(error.details || '').toLowerCase();
  return message.includes('duplicate key') || message.includes('unique constraint') || details.includes('duplicate key');
}

function getUniqueConstraintField(error) {
  if (!error) return null;

  const details = String(error.details || '');
  const detailsMatch = details.match(/Key \(([^)]+)\)=/i);
  if (detailsMatch?.[1]) {
    return detailsMatch[1];
  }

  const message = String(error.message || '');
  const messageMatch = message.match(/"User_([^"]+?)_key"/i);
  if (messageMatch?.[1]) {
    return messageMatch[1];
  }

  return null;
}

function getRegistrationConflictResponse(error) {
  const field = getUniqueConstraintField(error);

  switch (field) {
    case 'username':
      return { status: 400, body: { error: 'Username already taken' } };
    case 'email':
      return { status: 400, body: { error: 'Email already registered' } };
    case 'phone_number':
      return { status: 400, body: { error: 'Phone number already in use' } };
    default:
      return { status: 409, body: { error: 'Account information already exists' } };
  }
}

function buildOAuthUsername(email) {
  const emailPrefix = String(email || '')
    .split('@')[0]
    .replace(/[^a-zA-Z0-9_]/g, '')
    .slice(0, 20) || 'user';
  const randomSuffix = randomBytes(3).toString('hex');
  return `${emailPrefix}_${randomSuffix}`;
}

async function getOAuthProfileById(userId) {
  const { data, error } = await supabaseAdmin
    .from('User')
    .select(OAUTH_USER_SELECT)
    .eq('id', userId)
    .single();

  if (error && error.code !== 'PGRST116') {
    logger.error('OAuth user lookup error', { userId, error: error.message });
  }
  return data || null;
}

async function ensureOAuthUserProfile({ userId, email, meta, source }) {
  const existingUser = await getOAuthProfileById(userId);
  if (existingUser) {
    if (!existingUser.verified) {
      await supabaseAdmin
        .from('User')
        .update({ verified: true, updated_at: new Date().toISOString() })
        .eq('id', userId);
      return { ...existingUser, verified: true };
    }
    return existingUser;
  }

  // Apple only sends name on the FIRST sign-in; subsequent logins omit it.
  const rawFirst = (meta.given_name || meta.first_name || meta.name?.split(' ')[0] || '').trim();
  const rawLast = (meta.family_name || meta.last_name || meta.name?.split(' ').slice(1).join(' ') || '').trim();
  // Fallback: use email prefix as first name if OAuth didn't provide one
  const firstName = rawFirst || email.split('@')[0];
  const lastName = rawLast;
  const fullName = [firstName, lastName].filter(Boolean).join(' ').trim() || email.split('@')[0];
  const username = buildOAuthUsername(email);

  const { data: newUser, error: insertError } = await supabaseAdmin
    .from('User')
    .insert({
      id: userId,
      email,
      username,
      first_name: firstName,
      last_name: lastName || null,
      name: fullName,
      account_type: 'individual',
      verified: true,
      role: 'user',
    })
    .select()
    .single();

  if (!insertError && newUser) {
    logger.info('OAuth user created', { source, userId, email, username });
    return newUser;
  }

  if (isUniqueConstraintError(insertError)) {
    // Concurrent OAuth requests can race on first sign-in. Re-read and continue.
    const concurrentUser = await getOAuthProfileById(userId);
    if (concurrentUser) {
      logger.info('OAuth user already created by concurrent request', { source, userId, email });
      return concurrentUser;
    }
  }

  logger.error('OAuth user profile creation error', {
    source,
    userId,
    email,
    error: insertError?.message || 'unknown insert error',
  });
  return null;
}

// ============ VALIDATION SCHEMAS ============

const registerSchema = Joi.object({
  email: Joi.string().email().required(),
  password: Joi.string().min(PASSWORD_MIN_LENGTH).max(PASSWORD_MAX_LENGTH).required(),
  phoneNumber: Joi.string().pattern(/^\+[1-9]\d{1,14}$/), // E.164 format
  username: Joi.string().pattern(/^[a-zA-Z0-9_]+$/).min(3).max(30).required(),
  firstName: Joi.string().min(1).max(255).required(),
  middleName: Joi.string().min(1).max(255).allow('', null),
  lastName: Joi.string().min(1).max(255).required(),
  dateOfBirth: Joi.date().iso().max('now'), // optional
  address: Joi.string().min(5).max(255),
  city: Joi.string().min(2).max(100),
  state: Joi.string().min(2).max(50),
  zipcode: Joi.string().min(3).max(20),
  accountType: Joi.string().valid('individual', 'business').default('individual'),
  invite_code: Joi.string().alphanum().min(6).max(12).optional(),
});

const loginSchema = Joi.object({
  email: Joi.string().email().required(),
  password: Joi.string().required(),
});

const reauthenticateSchema = Joi.object({
  password: Joi.string().min(1).max(PASSWORD_MAX_LENGTH).required(),
});

const updatePasswordSchema = Joi.object({
  currentPassword: Joi.string().min(1).max(PASSWORD_MAX_LENGTH).optional().allow('', null),
  newPassword: Joi.string().min(PASSWORD_MIN_LENGTH).max(PASSWORD_MAX_LENGTH).required(),
});

const forgotPasswordSchema = Joi.object({
  email: Joi.string().email().required(),
});

const resetPasswordSchema = Joi.object({
  token: Joi.string().required(),
  newPassword: Joi.string().min(PASSWORD_MIN_LENGTH).max(PASSWORD_MAX_LENGTH).required(),
  email: Joi.string().email().optional(),
});

const resendVerificationSchema = Joi.object({
  email: Joi.string().email().required(),
});

const verifyEmailSchema = Joi.object({
  tokenHash: Joi.string().optional(),
  token: Joi.string().optional(),
  email: Joi.string().email().optional(),
  type: Joi.string().valid('signup', 'email').default('signup'),
}).or('tokenHash', 'token');

// Optional: loose URL validation (allows empty string / null for clearing)
const urlOrEmpty = Joi.string()
  .uri({ scheme: ['http', 'https'] })
  .allow('', null);

const updateProfileSchema = Joi.object({
  firstName: Joi.string().min(1).max(255),
  middleName: Joi.string().min(1).max(255).allow('', null),
  lastName: Joi.string().min(1).max(255),

  // E.164 only, as you currently enforce
  phoneNumber: Joi.string().pattern(/^\+[1-9]\d{1,14}$/),

  address: Joi.string().min(5).max(255),
  city: Joi.string().min(2).max(100),
  state: Joi.string().min(2).max(50),
  zipcode: Joi.string().min(3).max(20),

  // NEW: already exists in DB screenshot
  bio: Joi.string().max(2000).allow('', null),
  tagline: Joi.string().max(255).allow('', null),
  dateOfBirth: Joi.date().iso().allow(null, ''),

  // NEW: store these into social_links jsonb
  website: urlOrEmpty,
  linkedin: urlOrEmpty,
  twitter: urlOrEmpty,
  instagram: urlOrEmpty,
  facebook: urlOrEmpty,

  // Profile visibility (stealth mode)
  profileVisibility: Joi.string().valid('public', 'registered', 'private'),
}).min(1);

function getAuthProviders(authUser) {
  const providers = new Set();

  const primaryProvider = authUser?.app_metadata?.provider;
  if (typeof primaryProvider === 'string' && primaryProvider) {
    providers.add(primaryProvider);
  }

  const appProviders = authUser?.app_metadata?.providers;
  if (Array.isArray(appProviders)) {
    appProviders.forEach((provider) => {
      if (typeof provider === 'string' && provider) {
        providers.add(provider);
      }
    });
  }

  const identities = authUser?.identities;
  if (Array.isArray(identities)) {
    identities.forEach((identity) => {
      if (typeof identity?.provider === 'string' && identity.provider) {
        providers.add(identity.provider);
      }
    });
  }

  return Array.from(providers);
}

function hasPasswordProvider(authUser) {
  return getAuthProviders(authUser).includes('email');
}

function normalizeHttpOrigin(rawValue) {
  try {
    const parsed = new URL(String(rawValue || ''));
    if (!['http:', 'https:'].includes(parsed.protocol)) return null;
    return `${parsed.protocol}//${parsed.host}`;
  } catch {
    return null;
  }
}

function isLoopbackHost(hostname) {
  const normalized = String(hostname || '').toLowerCase();
  return normalized === 'localhost' || normalized === '127.0.0.1' || normalized === '[::1]' || normalized === '::1';
}

function isLoopbackOrigin(origin) {
  try {
    return isLoopbackHost(new URL(origin).hostname);
  } catch {
    return false;
  }
}

function isTrustedPantopusOrigin(origin) {
  try {
    const parsed = new URL(origin);
    if (parsed.protocol !== 'https:') return false;
    const hostname = String(parsed.hostname || '').toLowerCase();
    return hostname === 'pantopus.com' || hostname.endsWith('.pantopus.com');
  } catch {
    return false;
  }
}

function getAuthRedirectBaseUrl(req) {
  const configuredBase =
    process.env.AUTH_REDIRECT_URL ||
    process.env.APP_URL ||
    '';
  const configuredOrigin = normalizeHttpOrigin(configuredBase);
  const requestOrigin = normalizeHttpOrigin(req?.get?.('origin'));

  if (configuredOrigin && !isLoopbackOrigin(configuredOrigin)) {
    return configuredOrigin;
  }

  if (requestOrigin && isTrustedPantopusOrigin(requestOrigin)) {
    logger.warn('Using request origin for auth redirect base URL', {
      configuredBase: configuredOrigin || configuredBase || null,
      requestOrigin,
    });
    return requestOrigin;
  }

  if (requestOrigin && !isLoopbackOrigin(requestOrigin)) {
    logger.warn('Ignoring untrusted public request origin for auth redirect base URL', {
      configuredBase: configuredOrigin || configuredBase || null,
      requestOrigin,
    });
  }

  if (configuredOrigin) {
    if (process.env.NODE_ENV === 'production' && isLoopbackOrigin(configuredOrigin)) {
      logger.error('Auth redirect base URL is loopback in production', {
        configuredBase: configuredOrigin,
      });
    }
    return configuredOrigin;
  }

  if (requestOrigin && isLoopbackOrigin(requestOrigin)) {
    return requestOrigin;
  }

  if (process.env.NODE_ENV === 'production') {
    logger.error('Auth redirect base URL missing in production; falling back to localhost');
  }

  return 'http://localhost:3000';
}

function getOAuthRedirectBaseUrl(req) {
  const configuredBase = getAuthRedirectBaseUrl(req);
  const configuredOrigin = normalizeHttpOrigin(configuredBase);
  const requestOrigin = normalizeHttpOrigin(req?.get?.('origin'));

  if (!requestOrigin) {
    return configuredBase;
  }

  try {
    const requested = new URL(requestOrigin);
    if (isLoopbackHost(requested.hostname)) {
      return requestOrigin;
    }
    if (configuredOrigin) {
      const configured = new URL(configuredOrigin);
      if (requested.hostname === configured.hostname) {
        return requestOrigin;
      }
    }
  } catch {
    // Fallback below.
  }

  logger.warn('Ignoring untrusted OAuth request origin; using configured redirect base', {
    requestOrigin,
    configuredBase,
  });
  return configuredBase;
}

const REGISTRATION_AUTH_RETRY_ATTEMPTS = Number(
  process.env.AUTH_SIGNUP_RETRY_ATTEMPTS || 2
);
const REGISTRATION_AUTH_RETRY_DELAY_MS = Number(
  process.env.AUTH_SIGNUP_RETRY_DELAY_MS || 400
);

const wait = (ms) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

const isTransientFetchError = (err) => {
  if (!err) return false;

  const message = String(err.message || '').toLowerCase();
  const causeMessage = String(err.cause?.message || '').toLowerCase();
  const code = String(err.code || '').toLowerCase();
  const name = String(err.name || '').toLowerCase();

  return (
    message.includes('fetch failed') ||
    message.includes('network') ||
    message.includes('socket hang up') ||
    causeMessage.includes('fetch failed') ||
    causeMessage.includes('network') ||
    causeMessage.includes('socket hang up') ||
    code.includes('econnreset') ||
    code.includes('enotfound') ||
    code.includes('etimedout') ||
    name.includes('fetch')
  );
};

const signUpWithRetry = async (payload) => {
  let lastThrown;

  for (let attempt = 1; attempt <= REGISTRATION_AUTH_RETRY_ATTEMPTS; attempt++) {
    try {
      const result = await supabase.auth.signUp(payload);

      if (
        result?.error &&
        isTransientFetchError(result.error) &&
        attempt < REGISTRATION_AUTH_RETRY_ATTEMPTS
      ) {
        logger.warn('Auth signup transient error, retrying', {
          attempt,
          maxAttempts: REGISTRATION_AUTH_RETRY_ATTEMPTS,
          error: result.error.message,
        });
        await wait(REGISTRATION_AUTH_RETRY_DELAY_MS * attempt);
        continue;
      }

      return result;
    } catch (err) {
      lastThrown = err;
      if (!isTransientFetchError(err) || attempt >= REGISTRATION_AUTH_RETRY_ATTEMPTS) {
        throw err;
      }

      logger.warn('Auth signup threw transient error, retrying', {
        attempt,
        maxAttempts: REGISTRATION_AUTH_RETRY_ATTEMPTS,
        error: err.message,
      });
      await wait(REGISTRATION_AUTH_RETRY_DELAY_MS * attempt);
    }
  }

  throw lastThrown || new Error('Auth signup failed');
};

// ============ HELPER FUNCTIONS ============

/**
 * Calculate age from date of birth
 */
const calculateAge = (dob) => {
  const birthDate = new Date(dob);
  const today = new Date();
  let age = today.getFullYear() - birthDate.getFullYear();
  const monthDiff = today.getMonth() - birthDate.getMonth();
  if (
    monthDiff < 0 ||
    (monthDiff === 0 && today.getDate() < birthDate.getDate())
  ) {
    age--;
  }
  return age;
};

/**
 * Normalize full name spacing
 */
const buildFullName = (firstName, middleName, lastName) => {
  return `${firstName} ${middleName || ''} ${lastName}`
    .replace(/\s+/g, ' ')
    .trim();
};

/**
 * Check if username is available
 */
const isUsernameAvailable = async (username, excludeUserId = null) => {
  const { data, error } = await supabase
    .from('User')
    .select('id')
    .eq('username', username)
    .limit(1);

  if (error) throw error;

  if (!data || data.length === 0) return true;
  if (excludeUserId && data[0].id === excludeUserId) return true;

  return false;
};

/**
 * Check if phone number is available
 */
const isPhoneAvailable = async (phoneNumber, excludeUserId = null) => {
  if (!phoneNumber) return true; // allow empty/undefined phone
  const { data, error } = await supabase
    .from('User')
    .select('id')
    .eq('phone_number', phoneNumber)
    .limit(1);

  if (error) throw error;

  if (!data || data.length === 0) return true;
  if (excludeUserId && data[0].id === excludeUserId) return true;

  return false;
};

/**
 * Check if email is available
 */
const isEmailAvailable = async (email, excludeUserId = null) => {
  const { data, error } = await supabase
    .from('User')
    .select('id')
    .eq('email', email)
    .limit(1);

  if (error) throw error;

  if (!data || data.length === 0) return true;
  if (excludeUserId && data[0].id === excludeUserId) return true;

  return false;
};

// ============ ROUTES ============

/**
 * POST /api/users/register
 * Register a new user with auto-login
 */
router.post(
  '/register',
  registerLimiter,
  validate(registerSchema),
  async (req, res) => {
    const {
      email,
      password,
      phoneNumber,
      username,
      firstName,
      middleName,
      lastName,
      dateOfBirth,
      address,
      city,
      state,
      zipcode,
      accountType,
      invite_code,
    } = req.body;

    logger.info('Registration attempt', { email, username });

    try {
      // ============ VALIDATION ============

      // If dateOfBirth is provided, enforce 18+
      if (dateOfBirth) {
        const age = calculateAge(dateOfBirth);
        if (Number.isFinite(age) && age < 18) {
          logger.warn('Registration rejected - under 18', { email, age });
          return res.status(400).json({
            error: 'You must be at least 18 years old to register',
          });
        }
        // If invalid date passed, reject
        if (!Number.isFinite(age)) {
          logger.warn('Registration rejected - invalid dateOfBirth', { email, dateOfBirth });
          return res.status(400).json({ error: 'Invalid date of birth' });
        }
      }

      // Check username availability
      logger.info('Checking username availability', { username });
      const usernameAvailable = await isUsernameAvailable(username);
      if (!usernameAvailable) {
        logger.warn('Registration rejected - username taken', { username });
        return res.status(400).json({ error: 'Username already taken' });
      }

      // Check phone number availability
      logger.info('Checking phone availability', { phoneNumber });
      const phoneAvailable = await isPhoneAvailable(phoneNumber);
      if (!phoneAvailable) {
        logger.warn('Registration rejected - phone in use', { phoneNumber });
        return res.status(400).json({ error: 'Phone number already in use' });
      }

      logger.info('Checking email availability', { email });
      const emailAvailable = await isEmailAvailable(email);
      if (!emailAvailable) {
        logger.warn('Registration rejected - email in use', { email });
        return res.status(400).json({ error: 'Email already registered' });
      }

      // ============ CREATE AUTH USER ============

      logger.info('Creating auth user with email verification', { email });

      const { data: authData, error: authError } = await signUpWithRetry({
        email,
        password,
        options: {
          emailRedirectTo: `${getAuthRedirectBaseUrl(req)}/verify-email`,
          data: {
            username,
            firstName,
            middleName,
            lastName,
          },
        },
      });

      if (authError) {
        logger.error('Auth signup error', {
          error: authError.message,
          code: authError.code,
          email,
        });

        if (authError.message?.includes('already registered')) {
          return res.status(400).json({ error: 'Email already registered' });
        }

        if (
          isTransientFetchError(authError) ||
          authError.code === 'unexpected_failure'
        ) {
          return res.status(503).json({
            error: 'Authentication service temporarily unavailable. Please try again.',
            details: process.env.NODE_ENV === 'development' ? authError : undefined,
          });
        }

        return res.status(400).json({
          error: authError.message,
          details: process.env.NODE_ENV === 'development' ? authError : undefined,
        });
      }

      if (!authData || !authData.user || !authData.user.id) {
        logger.error('Invalid auth response structure', {
          email,
          authData: JSON.stringify(authData),
        });
        return res.status(500).json({
          error: 'Failed to create account - invalid response from auth service',
        });
      }

      const userId = authData.user.id;
      const normalizedMiddle = middleName ? String(middleName).trim() : '';
      const fullName = buildFullName(firstName, normalizedMiddle || null, lastName);

      logger.info('Auth user created successfully', {
        userId,
        email,
        emailConfirmed: authData.user.email_confirmed_at ? 'yes' : 'no',
      });

      // ============ CREATE DATABASE PROFILE ============

      logger.info('Creating user profile in database', { userId, email });

      const { data: userData, error: dbError } = await supabaseAdmin
        .from('User')
        .insert({
          id: userId,
          email: email,
          username: username,
          first_name: firstName,
          middle_name: normalizedMiddle || null, // ✅ FIX
          last_name: lastName,
          name: fullName,
          phone_number: phoneNumber,
          date_of_birth: dateOfBirth,
          address: address,
          city: city,
          state: state,
          zipcode: zipcode,
          account_type: accountType || 'individual',
          verified: false,
          role: 'user',
        })
        .select()
        .single();

      if (dbError) {
        logger.error('Database insert error', {
          error: dbError.message,
          code: dbError.code,
          details: dbError.details,
          hint: dbError.hint,
          userId,
          email,
        });

        // Rollback: delete auth user if database insert fails
        logger.info('Rolling back - deleting auth user', { userId });
        try {
          await supabaseAdmin.auth.admin.deleteUser(userId);
          logger.info('Auth user deleted successfully in rollback', { userId });
        } catch (rollbackErr) {
          logger.error('Failed to rollback auth user', {
            error: rollbackErr.message,
            userId,
          });
        }

        if (isUniqueConstraintError(dbError)) {
          const conflictResponse = getRegistrationConflictResponse(dbError);
          return res.status(conflictResponse.status).json(conflictResponse.body);
        }

        return res.status(500).json({
          error: 'Failed to create user profile. Please try again.',
          details:
            process.env.NODE_ENV === 'development'
              ? {
                  message: dbError.message,
                  hint: dbError.hint,
                  code: dbError.code,
                }
              : undefined,
        });
      }

      logger.info('User registered successfully', {
        userId,
        email,
        username,
        verified: userData.verified,
      });

      // ============ REFERRAL CONVERSION ============
      if (invite_code) {
        try {
          const { data: referral } = await supabaseAdmin
            .from('UserReferral')
            .select('id, referrer_id')
            .eq('invite_code', invite_code)
            .is('referred_user_id', null)
            .maybeSingle();

          if (referral && referral.referrer_id !== userId) {
            await supabaseAdmin
              .from('UserReferral')
              .update({
                referred_user_id: userId,
                converted_at: new Date().toISOString(),
              })
              .eq('id', referral.id);
            logger.info('Referral conversion recorded', {
              userId,
              referrerId: referral.referrer_id,
              invite_code,
            });
          }
        } catch (refErr) {
          // Non-critical — don't block registration
          logger.warn('Failed to record referral conversion', {
            error: refErr.message,
            userId,
            invite_code,
          });
        }
      }

      res.status(201).json({
        message: 'Registration successful. Please verify your email before signing in.',
        requiresEmailVerification: true,
        user: {
          id: userId,
          email,
          username,
          name: fullName,
          firstName,
          middleName: normalizedMiddle || null,
          lastName,
          phoneNumber,
          address,
          city,
          state,
          zipcode,
          accountType: accountType || 'individual',
          role: 'user',
          verified: false,
          createdAt: userData.created_at,
        },
      });
    } catch (err) {
      const transient = isTransientFetchError(err);

      logger.error('Registration error (uncaught)', {
        error: err.message,
        code: err.code,
        cause: err.cause?.message,
        stack: err.stack,
        email,
      });

      res.status(transient ? 503 : 500).json({
        error: transient
          ? 'Authentication service temporarily unavailable. Please try again.'
          : 'Registration failed. Please try again.',
        details:
          process.env.NODE_ENV === 'development'
            ? {
                message: err.message,
                code: err.code,
                cause: err.cause?.message,
                stack: err.stack,
              }
            : undefined,
      });
    }
  }
);

/**
 * POST /api/users/login
 * Login user
 */
router.post('/login', loginLimiter, validate(loginSchema), async (req, res) => {
  const { email, password } = req.body;

  logger.info('Login attempt', { email });

  try {
    const { data: authData, error: authError } = await supabase.auth.signInWithPassword({
      email,
      password,
    });

    if (authError) {
      logger.warn('Login failed - invalid credentials', {
        email,
        error: authError.message,
      });
      return res.status(401).json({
        error: 'Invalid email or password',
      });
    }

    if (!authData || !authData.user || !authData.session) {
      logger.error('Login failed - no session data', { email });
      return res.status(401).json({
        error: 'Login failed. Please try again.',
      });
    }

    if (!authData.user.email_confirmed_at) {
      logger.warn('Login blocked - email not verified', { email });
      await supabase.auth.signOut();
      return res.status(403).json({
        error: 'Please verify your email before signing in.',
        needsVerification: true,
      });
    }

    const userId = authData.user.id;

    const { data: userData, error: userError } = await supabase
      .from('User')
      .select(`
        id,
        email,
        username,
        name,
        first_name,
        middle_name,
        last_name,
        phone_number,
        address,
        city,
        state,
        zipcode,
        account_type,
        role,
        verified,
        created_at
      `)
      .eq('id', userId)
      .single();

    if (userError || !userData) {
      logger.error('User profile not found', {
        userId,
        error: userError?.message,
      });
      return res.status(500).json({
        error: 'User profile not found. Please contact support.',
      });
    }

    if (!userData.verified) {
      const { error: verifySyncError } = await supabaseAdmin
        .from('User')
        .update({
          verified: true,
          updated_at: new Date().toISOString(),
        })
        .eq('id', userId);

      if (!verifySyncError) {
        userData.verified = true;
      } else {
        logger.warn('Could not sync verified flag on login', {
          userId,
          error: verifySyncError.message,
        });
      }
    }

    logger.info('Login successful', {
      userId,
      email,
      username: userData.username,
    });

    // Set auth cookies for web clients (AUTH-3.3)
    setAuthCookies(res, authData.session.access_token, authData.session.refresh_token, userId);

    // Web clients get tokens via httpOnly cookies; omit from body to reduce XSS exposure.
    const tokenFields = isCookieTransport(req) ? {} : {
      accessToken: authData.session.access_token,
      refreshToken: authData.session.refresh_token,
      expiresIn: authData.session.expires_in,
      expiresAt: authData.session.expires_at,
    };

    res.status(200).json({
      message: 'Login successful',
      ...tokenFields,
      user: {
        id: userData.id,
        email: userData.email,
        username: userData.username,
        name: userData.name,
        firstName: userData.first_name,
        middleName: userData.middle_name, // ✅ FIX
        lastName: userData.last_name,
        phoneNumber: userData.phone_number,
        address: userData.address,
        city: userData.city,
        state: userData.state,
        zipcode: userData.zipcode,
        accountType: userData.account_type,
        role: userData.role,
        verified: userData.verified,
        createdAt: userData.created_at,
      },
    });
  } catch (err) {
    logger.error('Login error (uncaught)', {
      error: err.message,
      stack: err.stack,
      email,
    });

    res.status(500).json({
      error: 'Login failed. Please try again.',
      details: process.env.NODE_ENV === 'development' ? err.message : undefined,
    });
  }
});

router.post('/reauthenticate', verifyToken, reauthLimiter, validate(reauthenticateSchema), async (req, res) => {
  const userId = req.user?.id;
  const email = req.user?.email;
  const { password } = req.body;

  if (!userId || !email) {
    return res.status(400).json({
      error: 'Authenticated user email is required',
    });
  }

  const scopedClient = createClient(
    process.env.SUPABASE_URL,
    process.env.SUPABASE_ANON_KEY,
    { auth: { persistSession: false, autoRefreshToken: false } }
  );

  try {
    const { data: authData, error: authError } = await scopedClient.auth.signInWithPassword({
      email,
      password,
    });

    if (authError || !authData?.user || !authData?.session) {
      logger.warn('Re-authentication failed - invalid credentials', {
        userId,
        email,
        error: authError?.message,
      });
      return res.status(401).json({
        error: 'Invalid password',
      });
    }

    if (authData.user.id !== userId) {
      logger.warn('Re-authentication failed - user mismatch', {
        userId,
        email,
        authUserId: authData.user.id,
      });
      return res.status(403).json({
        error: 'Re-authentication failed',
      });
    }

    // Invalidate the temporary session created by signInWithPassword.
    // Try the scoped client first (uses the session's own token), then
    // fall back to the admin API which can revoke any session by JWT.
    let sessionRevoked = false;
    try {
      const { error: signOutError } = await scopedClient.auth.signOut({ scope: 'local' });
      sessionRevoked = !signOutError;
      if (signOutError) {
        logger.warn('Scoped re-auth sign-out returned error, falling back to admin', {
          userId,
          error: signOutError.message,
        });
      }
    } catch (signOutError) {
      logger.warn('Scoped re-auth sign-out threw, falling back to admin', {
        userId,
        error: signOutError.message,
      });
    }

    if (!sessionRevoked) {
      try {
        await supabaseAdmin.auth.admin.signOut(authData.session.access_token, 'local');
        sessionRevoked = true;
      } catch (adminSignOutError) {
        logger.error('Admin sign-out of temporary re-auth session also failed — dangling session', {
          userId,
          error: adminSignOutError.message,
        });
      }
    }

    logger.info('Re-authentication successful', { userId, email, sessionRevoked });
    return res.status(200).json({
      verified: true,
      message: 'Password verified',
    });
  } catch (err) {
    logger.error('Re-authentication error', {
      userId,
      email,
      error: err.message,
      stack: err.stack,
    });

    return res.status(500).json({
      error: 'Could not verify password. Please try again.',
    });
  }
});

router.get('/auth-methods', verifyToken, async (req, res) => {
  const userId = req.user?.id;

  try {
    const { data, error } = await supabaseAdmin.auth.admin.getUserById(userId);
    if (error || !data?.user) {
      logger.warn('Auth methods lookup failed', {
        userId,
        error: error?.message,
      });
      return res.status(404).json({
        error: 'Authenticated user not found',
      });
    }

    const providers = getAuthProviders(data.user);
    return res.status(200).json({
      providers,
      hasPassword: hasPasswordProvider(data.user),
    });
  } catch (err) {
    logger.error('Auth methods lookup error', {
      userId,
      error: err.message,
      stack: err.stack,
    });
    return res.status(500).json({
      error: 'Could not load authentication methods',
    });
  }
});

router.post('/password', verifyToken, reauthLimiter, validate(updatePasswordSchema), async (req, res) => {
  const userId = req.user?.id;
  const email = req.user?.email;
  const currentPassword = req.body?.currentPassword || null;
  const { newPassword } = req.body;

  if (!userId || !email) {
    return res.status(400).json({
      error: 'Authenticated user email is required',
    });
  }

  if (currentPassword && currentPassword === newPassword) {
    return res.status(400).json({
      error: 'New password must be different from your current password',
    });
  }

  try {
    const { data, error } = await supabaseAdmin.auth.admin.getUserById(userId);
    if (error || !data?.user) {
      logger.warn('Password update failed - auth user missing', {
        userId,
        error: error?.message,
      });
      return res.status(404).json({
        error: 'Authenticated user not found',
      });
    }

    const authUser = data.user;
    const hasPassword = hasPasswordProvider(authUser);

    if (hasPassword) {
      if (!currentPassword) {
        return res.status(400).json({
          error: 'Current password is required',
        });
      }

      const scopedClient = createClient(
        process.env.SUPABASE_URL,
        process.env.SUPABASE_ANON_KEY,
        { auth: { persistSession: false, autoRefreshToken: false } }
      );

      const { data: authData, error: authError } = await scopedClient.auth.signInWithPassword({
        email,
        password: currentPassword,
      });

      if (authError || !authData?.user || !authData?.session || authData.user.id !== userId) {
        logger.warn('Password update failed - current password invalid', {
          userId,
          email,
          error: authError?.message,
        });
        return res.status(401).json({
          error: 'Current password is incorrect',
        });
      }

      // Invalidate the temporary session created by signInWithPassword.
      let sessionRevoked = false;
      try {
        const { error: signOutError } = await scopedClient.auth.signOut({ scope: 'local' });
        sessionRevoked = !signOutError;
        if (signOutError) {
          logger.warn('Scoped password-update sign-out returned error, falling back to admin', {
            userId,
            error: signOutError.message,
          });
        }
      } catch (signOutError) {
        logger.warn('Scoped password-update sign-out threw, falling back to admin', {
          userId,
          error: signOutError.message,
        });
      }

      if (!sessionRevoked) {
        try {
          await supabaseAdmin.auth.admin.signOut(authData.session.access_token, 'local');
          sessionRevoked = true;
        } catch (adminSignOutError) {
          logger.error('Admin sign-out of temporary password-update session also failed — dangling session', {
            userId,
            error: adminSignOutError.message,
          });
        }
      }

      if (!sessionRevoked) {
        logger.error('Could not revoke temporary session during password update, aborting', { userId });
        return res.status(500).json({
          error: 'Could not verify password securely. Please try again.',
        });
      }
    }

    const { error: updateError } = await supabaseAdmin.auth.admin.updateUserById(userId, {
      password: newPassword,
    });

    if (updateError) {
      logger.warn('Password update failed - admin update error', {
        userId,
        error: updateError.message,
      });
      return res.status(400).json({
        error: 'Unable to update password',
      });
    }

    logger.info('Password updated successfully', {
      userId,
      mode: hasPassword ? 'change' : 'set',
    });

    return res.status(200).json({
      message: hasPassword ? 'Password updated successfully' : 'Password set successfully',
      hasPassword: true,
      providers: Array.from(new Set([...getAuthProviders(authUser), 'email'])),
    });
  } catch (err) {
    logger.error('Password update error', {
      userId,
      email,
      error: err.message,
      stack: err.stack,
    });

    return res.status(500).json({
      error: 'Could not update password. Please try again.',
    });
  }
});

/**
 * POST /api/users/refresh
 * Exchange a refresh token for a new access token (and new refresh token).
 * Enables persistent sessions: clients store refresh_token and call this when access token expires.
 */
router.post('/refresh', refreshLimiter, async (req, res) => {
  const refreshToken = req.cookies?.pantopus_refresh || req.body?.refreshToken || req.body?.refresh_token;
  if (!refreshToken || typeof refreshToken !== 'string') {
    return res.status(400).json({ error: 'refreshToken is required' });
  }

  try {
    const scopedClient = createClient(
      process.env.SUPABASE_URL,
      process.env.SUPABASE_ANON_KEY,
      { auth: { persistSession: false, autoRefreshToken: false } }
    );
    const { data, error } = await scopedClient.auth.refreshSession({ refresh_token: refreshToken });

    if (error) {
      const isReuse = /already used|not found/i.test(error.message);
      if (isReuse) {
        logger.warn('auth.refresh_token_reuse', {
          error: error.message,
          ip: req.ip,
          ua: req.get('user-agent'),
        });
        // Reuse detected — potential token theft. Clear all auth cookies
        // to terminate the session and force re-authentication.
        clearAuthCookies(res);
        return res.status(401).json({ error: 'Session invalidated. Please sign in again.', code: 'TOKEN_REUSE' });
      }
      logger.warn('Token refresh failed', { error: error.message });
      return res.status(401).json({ error: 'Session expired. Please sign in again.' });
    }

    const session = data?.session;
    if (!session?.access_token || !session?.refresh_token) {
      return res.status(401).json({ error: 'Invalid session' });
    }

    // Set refreshed auth cookies for web clients (AUTH-3.3)
    setAuthCookies(res, session.access_token, session.refresh_token, data.user?.id);

    const tokenFields = isCookieTransport(req) ? {} : {
      accessToken: session.access_token,
      refreshToken: session.refresh_token,
      expiresIn: session.expires_in,
      expiresAt: session.expires_at,
    };

    res.status(200).json({ ok: true, ...tokenFields });
  } catch (err) {
    logger.error('Refresh token error', { error: err.message });
    res.status(500).json({ error: 'Failed to refresh session' });
  }
});

/**
 * GET /api/users/profile
 * Get current user's profile
 */
router.get('/profile', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;

    const { data: userData, error } = await supabaseAdmin
      .from('User')
      .select('*')
      .eq('id', userId)
      .single();

    if (error || !userData) {
      logger.warn('Profile not found', { userId });
      return res.status(404).json({ error: 'User profile not found' });
    }

    // Fetch skills and invite progress in parallel
    const [skillsResult, inviteProgress] = await Promise.all([
      supabaseAdmin
        .from('UserSkill')
        .select('skill_name')
        .eq('user_id', userId)
        .order('display_order', { ascending: true }),
      inviteRewardService.getInviteProgress(userId),
    ]);
    const userSkills = skillsResult.data;

    logger.info('Profile fetched', { userId });

    const residency = await getPublicResidencySummary(userId);

    res.json({
      user: {
        id: userData.id,
        email: userData.email,
        username: userData.username,
        firstName: userData.first_name,
        middleName: userData.middle_name, // ✅ FIX
        lastName: userData.last_name,
        name: userData.name,
        phoneNumber: userData.phone_number,
        dateOfBirth: userData.date_of_birth,
        address: userData.address,
        city: userData.city,
        state: userData.state,
        zipcode: userData.zipcode,
        accountType: userData.account_type,
        role: userData.role,
        verified: userData.verified,
        residency,
        avatar_url: userData.avatar_url || null,
        profile_picture_url: userData.profile_picture_url || null,
        profilePicture: userData.profile_picture_url || null,
        bio: userData.bio || null,
        tagline: userData.tagline || null,
        socialLinks: userData.social_links || {},
        skills: (userSkills || []).map(s => s.skill_name),
        followers_count: userData.followers_count || 0,
        average_rating: userData.average_rating || 0,
        gigs_posted: userData.gigs_posted || 0,
        gigs_completed: userData.gigs_completed || 0,
        profileVisibility: userData.profile_visibility || 'public',
        createdAt: userData.created_at,
        updatedAt: userData.updated_at,
      },
      invite_progress: inviteProgress,
    });
  } catch (err) {
    logger.error('Profile fetch error', { error: err.message, userId: req.user.id });
    res.status(500).json({ error: 'Failed to fetch profile' });
  }
});

/**
 * PATCH /api/users/profile
 * Update current user's profile
 */
router.patch('/profile', verifyToken, validate(updateProfileSchema), async (req, res) => {
  try {
    const userId = req.user.id;
    const updates = {};

    // Map request body to DB columns
    if (req.body.firstName !== undefined) updates.first_name = req.body.firstName;
    if (req.body.middleName !== undefined) updates.middle_name = req.body.middleName || null;
    if (req.body.lastName !== undefined) updates.last_name = req.body.lastName;
    if (req.body.dateOfBirth !== undefined) updates.date_of_birth = req.body.dateOfBirth || null;

    if (req.body.phoneNumber !== undefined) updates.phone_number = req.body.phoneNumber;

    if (req.body.address !== undefined) updates.address = req.body.address;
    if (req.body.city !== undefined) updates.city = req.body.city;
    if (req.body.state !== undefined) updates.state = req.body.state;
    if (req.body.zipcode !== undefined) updates.zipcode = req.body.zipcode;

    // NEW: bio/tagline
    if (req.body.bio !== undefined) updates.bio = req.body.bio || null;
    if (req.body.tagline !== undefined) updates.tagline = req.body.tagline || null;

    // Profile visibility (stealth mode)
    if (req.body.profileVisibility !== undefined) updates.profile_visibility = req.body.profileVisibility;

    // If name parts changed, rebuild full name
    if (req.body.firstName !== undefined || req.body.middleName !== undefined || req.body.lastName !== undefined) {
      const { data: currentUser, error: currentErr } = await supabaseAdmin
        .from('User')
        .select('first_name, middle_name, last_name')
        .eq('id', userId)
        .single();

      if (currentErr) {
        return res.status(500).json({ error: 'Failed to read current user for name rebuild' });
      }

      const firstName =
        req.body.firstName !== undefined ? req.body.firstName : currentUser.first_name;

      const middleName =
        req.body.middleName !== undefined ? (req.body.middleName || null) : currentUser.middle_name;

      const lastName =
        req.body.lastName !== undefined ? req.body.lastName : currentUser.last_name;

      updates.name = buildFullName(firstName, middleName, lastName);
    }

    // Phone availability check (only if provided and truthy)
    if (req.body.phoneNumber) {
      const phoneAvailable = await isPhoneAvailable(req.body.phoneNumber, userId);
      if (!phoneAvailable) {
        return res.status(400).json({ error: 'Phone number already in use' });
      }
    }

    // NEW: social_links merge (website/linkedin/twitter/instagram/facebook)
    const socialKeys = ['website', 'linkedin', 'twitter', 'instagram', 'facebook'];
    const anySocialProvided = socialKeys.some((k) => req.body[k] !== undefined);

    if (anySocialProvided) {
      // Fetch current social_links to merge safely
      const { data: currentUser, error: socialErr } = await supabaseAdmin
        .from('User')
        .select('social_links')
        .eq('id', userId)
        .single();

      if (socialErr) {
        return res.status(500).json({ error: 'Failed to read current social links' });
      }

      const current = currentUser?.social_links || {};
      const merged = { ...current };

      for (const k of socialKeys) {
        if (req.body[k] !== undefined) {
          const v = req.body[k];
          // allow clearing with "" or null
          if (v === '' || v === null) {
            delete merged[k];
          } else {
            merged[k] = v;
          }
        }
      }

      updates.social_links = merged;
    }

    updates.updated_at = new Date().toISOString();

    const { data: userData, error } = await supabaseAdmin
      .from('User')
      .update(updates)
      .eq('id', userId)
      .select()
      .single();

    if (error) {
      logger.error('Profile update error', { error: error.message, userId });
      return res.status(500).json({ error: 'Failed to update profile' });
    }

    logger.info('Profile updated', { userId });

    res.json({
      message: 'Profile updated successfully',
      user: {
        id: userData.id,
        email: userData.email,
        username: userData.username,
        firstName: userData.first_name,
        middleName: userData.middle_name,
        lastName: userData.last_name,
        name: userData.name,
        phoneNumber: userData.phone_number,
        address: userData.address,
        city: userData.city,
        state: userData.state,
        zipcode: userData.zipcode,
        dateOfBirth: userData.date_of_birth,

        // NEW
        bio: userData.bio,
        tagline: userData.tagline,
        socialLinks: userData.social_links || {},
        profileVisibility: userData.profile_visibility || 'public',

        updatedAt: userData.updated_at,
      },
    });
  } catch (err) {
    logger.error('Profile update error', { error: err.message, userId: req.user?.id });
    res.status(500).json({ error: 'Failed to update profile' });
  }
});


/**
 * PUT /api/users/skills
 * Bulk-update the authenticated user's skills (sync from edit-profile form).
 * Accepts { skills: string[] } — replaces the full list in UserSkill.
 */
router.put('/skills', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const { skills } = req.body;

    if (!Array.isArray(skills)) {
      return res.status(400).json({ error: 'skills must be an array of strings' });
    }

    // Deduplicate and sanitize
    const cleaned = [...new Set(
      skills
        .map((s) => (typeof s === 'string' ? s.trim() : ''))
        .filter((s) => s.length > 0 && s.length <= 100)
    )];

    if (cleaned.length > 50) {
      return res.status(400).json({ error: 'Maximum 50 skills allowed' });
    }

    // Delete existing skills for this user
    const { error: delError } = await supabaseAdmin
      .from('UserSkill')
      .delete()
      .eq('user_id', userId);

    if (delError) {
      logger.error('Failed to delete existing skills', { error: delError.message, userId });
      return res.status(500).json({ error: 'Failed to update skills' });
    }

    // Insert new skills (if any)
    if (cleaned.length > 0) {
      const rows = cleaned.map((name, idx) => ({
        user_id: userId,
        skill_name: name,
        display_order: idx,
        show_on_profile: true,
      }));

      const { error: insertError } = await supabaseAdmin
        .from('UserSkill')
        .insert(rows);

      if (insertError) {
        logger.error('Failed to insert skills', { error: insertError.message, userId });
        return res.status(500).json({ error: 'Failed to update skills' });
      }
    }

    logger.info('Skills updated', { userId, count: cleaned.length });
    res.json({ skills: cleaned });
  } catch (err) {
    logger.error('Skills update error', { error: err.message, userId: req.user?.id });
    res.status(500).json({ error: 'Failed to update skills' });
  }
});

/**
 * POST /api/users/skills
 * Add a single skill to the authenticated user's profile.
 */
router.post('/skills', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const skill = (req.body.skill || '').trim();

    if (!skill || skill.length > 100) {
      return res.status(400).json({ error: 'Skill must be between 1 and 100 characters' });
    }

    const { error } = await supabaseAdmin
      .from('UserSkill')
      .upsert(
        { user_id: userId, skill_name: skill, show_on_profile: true },
        { onConflict: 'user_id,skill_name' }
      );

    if (error) {
      logger.error('Failed to add skill', { error: error.message, userId });
      return res.status(500).json({ error: 'Failed to add skill' });
    }

    res.json({ message: 'Skill added', skill });
  } catch (err) {
    logger.error('Add skill error', { error: err.message, userId: req.user?.id });
    res.status(500).json({ error: 'Failed to add skill' });
  }
});

/**
 * DELETE /api/users/skills/:skillId
 * Remove a skill by skill name (used as identifier from the frontend).
 */
router.delete('/skills/:skillId', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const skillName = decodeURIComponent(req.params.skillId);

    const { error } = await supabaseAdmin
      .from('UserSkill')
      .delete()
      .eq('user_id', userId)
      .eq('skill_name', skillName);

    if (error) {
      logger.error('Failed to remove skill', { error: error.message, userId });
      return res.status(500).json({ error: 'Failed to remove skill' });
    }

    res.json({ message: 'Skill removed' });
  } catch (err) {
    logger.error('Remove skill error', { error: err.message, userId: req.user?.id });
    res.status(500).json({ error: 'Failed to remove skill' });
  }
});


/**
 * GET /api/users/search?q=<query>&limit=5&type=all|people|business
 * Search users by username/name/email for discovery and invite flows.
 * Returns basic public info only.
 */
router.get('/search', verifyToken, async (req, res) => {
  try {
    const { q, limit: rawLimit, type = 'all' } = req.query;
    const userId = req.user.id;
    const limit = Math.min(parseInt(rawLimit) || 5, 20);
    const normalizedType = String(type || 'all').toLowerCase();
    const queryText = String(q || '').trim();
    const normalizedQuery = queryText.toLowerCase();
    const tokens = normalizedQuery.split(/\s+/).filter(Boolean).slice(0, 6);
    const primaryToken = tokens[0] || normalizedQuery;

    if (!queryText || queryText.length < 2) {
      return res.status(400).json({ error: 'Query must be at least 2 characters' });
    }
    if (!['all', 'people', 'business'].includes(normalizedType)) {
      return res.status(400).json({ error: 'type must be one of: all, people, business' });
    }

    const fullSearchTerm = `%${queryText}%`;
    const broadSearchTerm = `%${primaryToken}%`;
    const candidateLimit = Math.min(Math.max(limit * 8, 40), 200);

    let query = supabaseAdmin
      .from('User')
      .select('id, username, name, first_name, last_name, profile_picture_url, city, state, email, account_type, followers_count')
      .or(
        `username.ilike.${fullSearchTerm},name.ilike.${fullSearchTerm},first_name.ilike.${fullSearchTerm},last_name.ilike.${fullSearchTerm},email.ilike.${fullSearchTerm},username.ilike.${broadSearchTerm},name.ilike.${broadSearchTerm},first_name.ilike.${broadSearchTerm},last_name.ilike.${broadSearchTerm},email.ilike.${broadSearchTerm}`
      )
      .neq('id', userId) // Exclude self
      .neq('account_type', 'curator') // Exclude curator accounts — platform-owned, not a real neighbor
      .limit(candidateLimit);

    if (normalizedType === 'people') {
      query = query.neq('account_type', 'business');
    } else if (normalizedType === 'business') {
      query = query.eq('account_type', 'business');
    }

    const { data, error } = await query;

    if (error) {
      logger.error('User search error', { error: error.message });
      return res.status(500).json({ error: 'Search failed' });
    }

    const scored = (data || [])
      .filter((u) => {
        const searchable = [
          u.username,
          u.name,
          u.first_name,
          u.last_name,
          u.email,
          [u.first_name, u.last_name].filter(Boolean).join(' '),
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();

        return tokens.every((token) => searchable.includes(token));
      })
      .map((u) => {
        const username = String(u.username || '').toLowerCase();
        const fullName = String(u.name || [u.first_name, u.last_name].filter(Boolean).join(' ') || '').trim().toLowerCase();
        const email = String(u.email || '').toLowerCase();
        const searchable = [username, fullName, email].filter(Boolean).join(' ');

        let score = 4;
        if (username === normalizedQuery || fullName === normalizedQuery || email === normalizedQuery) {
          score = 0;
        } else if (username.startsWith(normalizedQuery) || fullName.startsWith(normalizedQuery)) {
          score = 1;
        } else if (searchable.includes(normalizedQuery)) {
          score = 2;
        } else if (tokens.every((token) => searchable.includes(token))) {
          score = 3;
        }

        return { u, score };
      })
      .sort((a, b) => {
        if (a.score !== b.score) return a.score - b.score;
        const followersA = Number(a.u.followers_count) || 0;
        const followersB = Number(b.u.followers_count) || 0;
        if (followersA !== followersB) return followersB - followersA;
        return String(a.u.username || '').localeCompare(String(b.u.username || ''));
      })
      .slice(0, limit)
      .map(({ u }) => u);

    // Map to safe public shape
    const users = scored.map(u => ({
      id: u.id,
      username: u.username,
      name: u.name || [u.first_name, u.last_name].filter(Boolean).join(' ') || u.username,
      profilePicture: u.profile_picture_url || null,
      city: u.city || null,
      state: u.state || null,
      accountType: u.account_type || 'individual',
      followersCount: u.followers_count || 0,
    }));

    res.json({ users });
  } catch (err) {
    logger.error('User search error', { error: err.message });
    res.status(500).json({ error: 'Search failed' });
  }
});


/**
 * GET /api/users/me/activity
 * Recent activity feed derived from Gig + GigBid tables.
 * Returns a chronological list of user actions (gig posted, bid placed, bid accepted, gig completed).
 */
router.get('/me/activity', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const limit = Math.min(parseInt(req.query.limit) || 10, 50);

    // 1. Gigs posted by user
    const { data: gigsPosted } = await supabaseAdmin
      .from('Gig')
      .select('id, title, status, created_at')
      .eq('user_id', userId)
      .order('created_at', { ascending: false })
      .limit(limit);

    // 2. Bids placed by user
    const { data: bidsPlaced } = await supabaseAdmin
      .from('GigBid')
      .select('id, gig_id, bid_amount, status, created_at')
      .eq('user_id', userId)
      .order('created_at', { ascending: false })
      .limit(limit);

    // 3. Gigs assigned to user (accepted bids)
    const { data: gigsAccepted } = await supabaseAdmin
      .from('Gig')
      .select('id, title, accepted_at')
      .eq('accepted_by', userId)
      .not('accepted_at', 'is', null)
      .order('accepted_at', { ascending: false })
      .limit(limit);

    // 4. Gigs completed (posted by user)
    const { data: gigsCompleted } = await supabaseAdmin
      .from('Gig')
      .select('id, title, owner_confirmed_at, updated_at')
      .eq('user_id', userId)
      .eq('status', 'completed')
      .order('updated_at', { ascending: false })
      .limit(limit);

    // Build unified activity list
    const activities = [];

    for (const g of (gigsPosted || [])) {
      activities.push({
        id: `gig-posted-${g.id}`,
        type: 'gig_posted',
        icon: '📝',
        text: `You posted a new gig${g.title ? `: ${g.title}` : ''}`,
        timestamp: g.created_at,
        gig_id: g.id,
      });
    }

    for (const b of (bidsPlaced || [])) {
      activities.push({
        id: `bid-placed-${b.id}`,
        type: 'bid_placed',
        icon: '💰',
        text: `You placed a bid${b.bid_amount ? ` of $${b.bid_amount}` : ''} on a gig`,
        timestamp: b.created_at,
        gig_id: b.gig_id,
      });
    }

    for (const g of (gigsAccepted || [])) {
      activities.push({
        id: `bid-accepted-${g.id}`,
        type: 'bid_accepted',
        icon: '🎉',
        text: `Your bid was accepted${g.title ? ` for: ${g.title}` : ''}`,
        timestamp: g.accepted_at,
        gig_id: g.id,
      });
    }

    for (const g of (gigsCompleted || [])) {
      activities.push({
        id: `gig-completed-${g.id}`,
        type: 'gig_completed',
        icon: '✅',
        text: `Your gig was completed${g.title ? `: ${g.title}` : ''}`,
        timestamp: g.owner_confirmed_at || g.updated_at,
        gig_id: g.id,
      });
    }

    // Sort by timestamp descending, add human-readable time_ago
    activities.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
    const trimmed = activities.slice(0, limit);

    const now = new Date();
    for (const a of trimmed) {
      const diff = now - new Date(a.timestamp);
      const mins = Math.floor(diff / 60000);
      const hours = Math.floor(diff / 3600000);
      const days = Math.floor(diff / 86400000);
      if (mins < 1) a.time_ago = 'just now';
      else if (mins < 60) a.time_ago = `${mins} minute${mins > 1 ? 's' : ''} ago`;
      else if (hours < 24) a.time_ago = `${hours} hour${hours > 1 ? 's' : ''} ago`;
      else if (days < 7) a.time_ago = `${days} day${days > 1 ? 's' : ''} ago`;
      else a.time_ago = new Date(a.timestamp).toLocaleDateString();
    }

    res.json({ activities: trimmed });
  } catch (err) {
    logger.error('Activity fetch error:', err);
    res.status(500).json({ error: 'Failed to load activity' });
  }
});

/**
 * POST /api/users/me/signals
 * Receive batched implicit view signals for affinity scoring.
 * Body: { signals: [{ gig_id, category, dwell_ms, timestamp }] }
 */
router.post('/me/signals', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const { signals } = req.body;

    if (!Array.isArray(signals) || signals.length === 0) {
      return res.status(400).json({ error: 'signals array is required' });
    }

    // Cap batch size to prevent abuse
    const batch = signals.slice(0, 50);
    let processed = 0;

    for (const signal of batch) {
      const { category, dwell_ms } = signal;
      if (!category || typeof category !== 'string') continue;
      const dwell = Number(dwell_ms);
      if (!Number.isFinite(dwell)) continue;

      if (dwell > 10000) {
        // Positive signal: user spent >10s reading this task
        affinityService.recordInteraction(userId, category, 'view').catch(() => {});
        processed++;
      } else if (dwell < 3000) {
        // Quick-back: log but don't count as a view
        logger.debug('Quick-back signal', { userId, category, dwell_ms: dwell });
      }
    }

    return res.json({ processed });
  } catch (err) {
    logger.error('Signals endpoint error', { error: err.message });
    return res.status(500).json({ error: 'Failed to process signals' });
  }
});

/**
 * GET /api/users/id/:id
 * Get full public profile by user UUID
 */
router.get('/id/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const { data: userData, error } = await supabaseAdmin
      .from('User')
      .select('*')
      .eq('id', id)
      .single();

    if (error || !userData) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Compute live gig counts + fetch skills in parallel
    const [postedRes, completedRes, skillsRes] = await Promise.allSettled([
      supabaseAdmin.from('Gig').select('id', { count: 'exact', head: true }).eq('user_id', userData.id),
      supabaseAdmin.from('Gig').select('id', { count: 'exact', head: true }).eq('user_id', userData.id).eq('status', 'completed'),
      supabaseAdmin.from('UserSkill').select('skill_name').eq('user_id', userData.id).order('display_order', { ascending: true }),
    ]);

    const gigsPosted = postedRes.status === 'fulfilled' ? (postedRes.value.count || 0) : 0;
    const gigsCompleted = completedRes.status === 'fulfilled' ? (completedRes.value.count || 0) : 0;
    const profileSkills = skillsRes.status === 'fulfilled' ? (skillsRes.value.data || []) : [];

    // Fetch reviews for this user
    let reviews = [];
    let averageRating = 0;
    let reviewCount = 0;

    try {
      const { data: reviewData, count: revCount } = await supabaseAdmin
        .from('Review')
        .select('*', { count: 'exact' })
        .eq('reviewee_id', userData.id)
        .order('created_at', { ascending: false })
        .limit(20);

      if (reviewData && reviewData.length > 0) {
        const reviewerIds = [...new Set(reviewData.map(r => r.reviewer_id))];
        const { data: reviewers } = await supabaseAdmin
          .from('User')
          .select('id, username, name, first_name, last_name, profile_picture_url')
          .in('id', reviewerIds);

        const reviewerMap = {};
        (reviewers || []).forEach(u => { reviewerMap[u.id] = u; });

        reviews = reviewData.map(r => ({
          ...r,
          reviewer_name: reviewerMap[r.reviewer_id]?.name ||
                         reviewerMap[r.reviewer_id]?.first_name ||
                         reviewerMap[r.reviewer_id]?.username || 'Anonymous',
          reviewer_avatar: reviewerMap[r.reviewer_id]?.profile_picture_url || null,
          reviewer_username: reviewerMap[r.reviewer_id]?.username || null,
        }));

        const totalRating = reviews.reduce((sum, r) => sum + r.rating, 0);
        averageRating = Math.round((totalRating / reviews.length) * 100) / 100;
        reviewCount = revCount || reviews.length;
      }
    } catch (reviewErr) {
      logger.warn('Could not fetch reviews', { error: reviewErr.message });
    }

    const residency = await getPublicResidencySummary(userData.id);

    res.json({
      id: userData.id,
      username: userData.username,
      firstName: userData.first_name,
      lastName: userData.last_name,
      name: userData.name,
      bio: userData.bio,
      tagline: userData.tagline,
      avatar_url: userData.avatar_url,
      profile_picture_url: userData.profile_picture_url,
      profilePicture: userData.profile_picture_url,
      city: userData.city,
      state: userData.state,
      accountType: userData.account_type,
      verified: userData.verified,
      residency,
      created_at: userData.created_at,
      gigs_posted: gigsPosted,
      gigs_completed: gigsCompleted,
      average_rating: averageRating || userData.average_rating || 0,
      review_count: reviewCount,
      followers_count: userData.followers_count || 0,
      reviews: reviews,
      socialLinks: userData.social_links || {},
      skills: (profileSkills || []).map(s => s.skill_name),
    });
  } catch (err) {
    logger.error('Public profile fetch by ID error', { error: err.message, id: req.params.id });
    res.status(500).json({ error: 'Failed to fetch user profile' });
  }
});

/**
 * GET /api/users/:id/stats
 * Get user stats (gigs, earnings, rating) for profile. Authenticated user may only fetch own stats.
 */
router.get('/:id/stats', verifyToken, async (req, res) => {
  try {
    const { id } = req.params;
    if (req.user.id !== id) {
      return res.status(403).json({ error: 'Can only fetch your own stats' });
    }
    const userId = id;
    const earningsService = require('../services/earningsService');

    const [postedRes, userRow, earningsResult, walletRes] = await Promise.allSettled([
      supabaseAdmin.from('Gig').select('id', { count: 'exact', head: true }).eq('user_id', userId),
      supabaseAdmin.from('User').select('average_rating, review_count, gigs_completed').eq('id', userId).single(),
      earningsService.getEarningsForUser(userId, null, null),
      supabaseAdmin.from('Wallet').select('lifetime_received').eq('user_id', userId).maybeSingle(),
    ]);

    const gigsPosted = postedRes.status === 'fulfilled' ? (postedRes.value?.count ?? 0) : 0;
    const userData = userRow.status === 'fulfilled' ? userRow.value?.data : null;
    const gigsCompleted = (userData?.gigs_completed != null) ? Number(userData.gigs_completed) : 0;
    const averageRating = userData?.average_rating ?? 0;
    const totalRatings = userData?.review_count ?? 0;

    const earningsCents = (earningsResult.status === 'fulfilled' && earningsResult.value)
      ? Number(earningsResult.value.total_earned ?? 0) || 0
      : 0;
    const lifetimeReceivedCents = (walletRes.status === 'fulfilled' && walletRes.value?.data)
      ? Number(walletRes.value.data.lifetime_received ?? 0) || 0
      : 0;
    const totalEarningsDollars = Math.round((Math.max(earningsCents, lifetimeReceivedCents) / 100) * 100) / 100;

    return res.json({
      total_gigs_posted: gigsPosted,
      total_gigs_completed: gigsCompleted,
      total_earnings: totalEarningsDollars,
      average_rating: averageRating,
      total_ratings: totalRatings,
    });
  } catch (err) {
    logger.error('User stats fetch error', { error: err.message, id: req.params.id });
    res.status(500).json({ error: 'Failed to fetch stats' });
  }
});

// ============================================================
// GET /api/users/me/invite-progress
// Returns invite referral counts, unlocked features, and next unlock
// MUST be registered before the catch-all /:username route
// ============================================================
router.get('/me/invite-progress', verifyToken, async (req, res) => {
  try {
    const progress = await inviteRewardService.getInviteProgress(req.user.id);
    res.json(progress);
  } catch (err) {
    logger.error('Invite progress fetch error', { error: err.message, userId: req.user.id });
    res.status(500).json({ error: 'Failed to fetch invite progress' });
  }
});

// ============================================================
// GET /api/users/me/invite-code
// Returns the user's stable invite code (creates one if needed)
// MUST be registered before the catch-all /:username route
// ============================================================
router.get('/me/invite-code', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;

    // Check for existing referral row
    const { data: existing } = await supabaseAdmin
      .from('UserReferral')
      .select('invite_code')
      .eq('referrer_id', userId)
      .is('referred_user_id', null)
      .order('created_at', { ascending: true })
      .limit(1)
      .maybeSingle();

    if (existing) {
      return res.json({
        invite_code: existing.invite_code,
        invite_url: `https://pantopus.com/join/${existing.invite_code}`,
      });
    }

    // Generate a new 8-char alphanumeric code
    const invite_code = crypto.randomBytes(6).toString('base64url').slice(0, 8);

    const { error: insertErr } = await supabaseAdmin
      .from('UserReferral')
      .insert({
        referrer_id: userId,
        invite_code,
        source: 'post_transaction',
      });

    if (insertErr) {
      // Rare collision — retry once
      if (insertErr.code === '23505') {
        const retry_code = crypto.randomBytes(6).toString('base64url').slice(0, 8);
        const { error: retryErr } = await supabaseAdmin
          .from('UserReferral')
          .insert({
            referrer_id: userId,
            invite_code: retry_code,
            source: 'post_transaction',
          });
        if (retryErr) {
          logger.error('Failed to create invite code after retry', { error: retryErr.message, userId });
          return res.status(500).json({ error: 'Failed to generate invite code' });
        }
        return res.json({
          invite_code: retry_code,
          invite_url: `https://pantopus.com/join/${retry_code}`,
        });
      }
      logger.error('Failed to create invite code', { error: insertErr.message, userId });
      return res.status(500).json({ error: 'Failed to generate invite code' });
    }

    res.json({
      invite_code,
      invite_url: `https://pantopus.com/join/${invite_code}`,
    });
  } catch (err) {
    logger.error('invite-code error', { error: err.message, userId: req.user?.id });
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ============================================================
// GET /api/users/me/monthly-receipt
// Returns the stored receipt, or computes on-demand mid-month
// MUST be registered before the catch-all /:username route
// ============================================================
router.get('/me/monthly-receipt', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const year = parseInt(req.query.year, 10);
    const month = parseInt(req.query.month, 10);

    if (!year || !month || month < 1 || month > 12) {
      return res.status(400).json({ error: 'Valid year and month (1-12) query params required' });
    }

    // Try stored receipt first
    const { data: stored } = await supabaseAdmin
      .from('MonthlyReceipt')
      .select('receipt, created_at')
      .eq('user_id', userId)
      .eq('year', year)
      .eq('month', month)
      .maybeSingle();

    if (stored) {
      return res.json(stored.receipt);
    }

    // Not stored yet — compute on-demand (e.g. mid-month or job hasn't run)
    const { computeMonthlyReceipt } = require('../services/monthlyReceiptService');
    const receipt = await computeMonthlyReceipt(userId, year, month);
    res.json(receipt);
  } catch (err) {
    logger.error('monthly-receipt error', { error: err.message, userId: req.user?.id });
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ============================================================
// GET /api/users/public/join/:code
// Public endpoint — look up a referral invite code (no auth)
// MUST be registered before the catch-all /:username route
// ============================================================
router.get('/public/join/:code', async (req, res) => {
  try {
    const { code } = req.params;

    if (!code || code.length < 6 || code.length > 12) {
      return res.json({ valid: false });
    }

    const { data: referral } = await supabaseAdmin
      .from('UserReferral')
      .select('id, referrer_id, invite_code')
      .eq('invite_code', code)
      .maybeSingle();

    if (!referral) {
      return res.json({ valid: false });
    }

    // Fetch referrer info
    const { data: referrer } = await supabaseAdmin
      .from('User')
      .select('id, name, first_name, username, profile_picture_url')
      .eq('id', referral.referrer_id)
      .single();

    const referrerName = referrer?.name || referrer?.first_name || referrer?.username || 'A neighbor';

    res.json({
      valid: true,
      referrer_name: referrerName,
      referrer_avatar: referrer?.profile_picture_url || null,
      code: referral.invite_code,
    });
  } catch (err) {
    logger.error('public join lookup error', { error: err.message, code: req.params.code });
    res.status(500).json({ valid: false });
  }
});

/**
 * GET /api/users/:username
 * Get public profile by username
 */
router.get('/:username', async (req, res) => {
  try {
    const { username } = req.params;

    const { data: userData, error } = await supabaseAdmin
      .from('User')
      .select('id, username, name, first_name, middle_name, last_name, bio, city, state, profile_picture_url, created_at')
      .eq('username', username)
      .single();

    if (error || !userData) {
      return res.status(404).json({ error: 'User not found' });
    }

    res.json({
      user: {
        id: userData.id,
        username: userData.username,
        name: userData.name,
        firstName: userData.first_name,
        middleName: userData.middle_name,
        lastName: userData.last_name,
        bio: userData.bio,
        city: userData.city,
        state: userData.state,
        profile_picture_url: userData.profile_picture_url,
        memberSince: userData.created_at,
      },
    });
  } catch (err) {
    logger.error('Public profile fetch error', { error: err.message, username: req.params.username });
    res.status(500).json({ error: 'Failed to fetch user profile' });
  }
});

/**
 * POST /api/users/resend-verification
 * Resend email verification
 */
router.post('/resend-verification', resendVerificationLimiter, validate(resendVerificationSchema), async (req, res) => {
  try {
    const { email } = req.body;

    const { error } = await supabase.auth.resend({
      type: 'signup',
      email,
      options: {
        emailRedirectTo: `${getAuthRedirectBaseUrl(req)}/verify-email`,
      },
    });

    if (error) {
      logger.error('Resend verification error', { error: error.message, email });
      // Don't reveal if email exists
    }

    logger.info('Verification email resent', { email });

    res.json({ message: 'If that email exists, a verification email has been sent.' });
  } catch (err) {
    logger.error('Resend verification error', { error: err.message, email: req.body?.email });
    res.json({ message: 'If that email exists, a verification email has been sent.' });
  }
});

/**
 * POST /api/users/verify-email
 * Verify email using Supabase OTP token hash (link-based) or OTP code.
 */
router.post('/verify-email', validate(verifyEmailSchema), async (req, res) => {
  try {
    const { tokenHash, token, email, type = 'signup' } = req.body;
    logger.info('Email verification attempt', {
      type,
      email: email || null,
      hasTokenHash: Boolean(tokenHash),
      hasToken: Boolean(token),
    });

    if (!tokenHash && (!token || !email)) {
      return res.status(400).json({
        error: 'Email and code are required when tokenHash is not provided',
      });
    }

    let verifyPayload;
    if (tokenHash) {
      verifyPayload = {
        type,
        token_hash: tokenHash,
      };
    } else {
      verifyPayload = {
        type,
        token,
        email,
      };
    }

    const { data, error } = await supabase.auth.verifyOtp(verifyPayload);
    if (error) {
      logger.warn('Email verification failed', {
        error: error.message,
        type,
        hasTokenHash: Boolean(tokenHash),
        hasToken: Boolean(token),
      });
      return res.status(400).json({ error: 'Invalid or expired verification link/code' });
    }

    const verifiedUserId = data?.user?.id || null;
    if (verifiedUserId) {
      const { error: syncErr } = await supabaseAdmin
        .from('User')
        .update({
          verified: true,
          updated_at: new Date().toISOString(),
        })
        .eq('id', verifiedUserId);

      if (syncErr) {
        logger.warn('Email verified but user.verified sync failed', {
          userId: verifiedUserId,
          error: syncErr.message,
        });
      }
    }

    return res.json({
      message: 'Email verified successfully. You can now sign in.',
      verified: true,
    });
  } catch (err) {
    logger.error('Email verification error', { error: err.message });
    return res.status(500).json({ error: 'Failed to verify email' });
  }
});

/**
 * POST /api/users/forgot-password
 * Send a password reset email via Supabase's built-in email delivery
 * (same transport that handles signup verification emails).
 */
router.post('/forgot-password', forgotPasswordLimiter, validate(forgotPasswordSchema), async (req, res) => {
  try {
    const { email } = req.body;

    if (!email) {
      return res.status(400).json({ error: 'Email is required' });
    }

    const redirectTo = `${getAuthRedirectBaseUrl(req)}/reset-password`;
    const { error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo,
    });

    if (error) {
      logger.error('Forgot password: Supabase error', { email, error: error.message });
      // Don't reveal if email exists
    } else {
      logger.info('Password reset email sent via Supabase', { email });
    }

    // Always return same message to prevent email enumeration
    res.json({
      message: 'If that email exists, a password reset link has been sent.',
    });
  } catch (err) {
    logger.error('Password reset error', { error: err.message });
    res.json({
      message: 'If that email exists, a password reset link has been sent.',
    });
  }
});

/**
 * POST /api/users/reset-password
 * Reset password using recovery token (token_hash) or access token
 */
router.post('/reset-password', validate(resetPasswordSchema), async (req, res) => {
  try {
    const { token, newPassword, email } = req.body;
    const isJwtAccessToken = token.split('.').length === 3;

    if (isJwtAccessToken) {
      const { data: userData, error: userError } = await supabase.auth.getUser(token);
      if (userError || !userData?.user?.id) {
        logger.warn('Reset password failed - invalid access token', { error: userError?.message });
        return res.status(400).json({ error: 'Invalid or expired reset token' });
      }

      const { error: updateError } = await supabaseAdmin.auth.admin.updateUserById(
        userData.user.id,
        { password: newPassword }
      );

      if (updateError) {
        logger.error('Reset password failed - admin update failed', { error: updateError.message });
        return res.status(400).json({ error: 'Unable to reset password' });
      }
    } else {
      const verifyPayload = {
        type: 'recovery',
        token_hash: token,
      };

      if (email) {
        verifyPayload.email = email;
      }

      const { data: verifyData, error: verifyError } = await supabase.auth.verifyOtp(verifyPayload);
      const session = verifyData?.session;
      if (verifyError || !session?.access_token || !session?.refresh_token) {
        logger.warn('Reset password failed - verify otp failed', { error: verifyError?.message });
        return res.status(400).json({ error: 'Invalid or expired reset token' });
      }

      const scopedClient = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_ANON_KEY);
      const { error: sessionError } = await scopedClient.auth.setSession({
        access_token: session.access_token,
        refresh_token: session.refresh_token,
      });
      if (sessionError) {
        logger.error('Reset password failed - set session failed', { error: sessionError.message });
        return res.status(400).json({ error: 'Invalid reset session' });
      }

      const { error: updateError } = await scopedClient.auth.updateUser({ password: newPassword });
      if (updateError) {
        logger.error('Reset password failed - update user failed', { error: updateError.message });
        return res.status(400).json({ error: 'Unable to reset password' });
      }
    }

    return res.json({ message: 'Password reset successful. You can now sign in.' });
  } catch (err) {
    logger.error('Reset password error', { error: err.message });
    return res.status(500).json({ error: 'Failed to reset password' });
  }
});

/**
 * DELETE /api/users/cleanup/:email
 * Clean up orphaned auth users (development only)
 */
router.delete('/cleanup/:email', verifyToken, verifyToken.requireAdmin, async (req, res) => {
  if (process.env.NODE_ENV !== 'development') {
    return res.status(403).json({ error: 'Only available in development' });
  }

  try {
    const { email } = req.params;

    logger.info('Cleaning up user', { email });

    // Find user in auth
    const { data: existingUsers } = await supabaseAdmin.auth.admin.listUsers();
    const authUser = existingUsers?.users?.find((u) => u.email === email);

    if (authUser) {
      await supabaseAdmin.auth.admin.deleteUser(authUser.id);
      logger.info('Deleted from auth', { email, userId: authUser.id });
    }

    // Delete from database
    const { error: dbError } = await supabaseAdmin.from('User').delete().eq('email', email);

    if (dbError) {
      logger.warn('Error deleting from database', { error: dbError.message });
    }

    res.json({
      message: 'User cleaned up successfully',
      deletedFromAuth: !!authUser,
      deletedFromDb: !dbError,
    });
  } catch (err) {
    logger.error('Cleanup error', { error: err.message });
    res.status(500).json({ error: err.message });
  }
});

/**
 * GET /api/users/username/:username
 * Get user profile by username (for public profiles)
 */
router.get('/username/:username', async (req, res) => {
  try {
    const { username } = req.params;

    const { data: userData, error } = await supabaseAdmin
      .from('User')
      .select('*')
      .eq('username', username)
      .single();

    if (error || !userData) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Compute live gig counts + fetch skills in parallel
    const [postedRes, completedRes, skillsRes2] = await Promise.allSettled([
      supabaseAdmin.from('Gig').select('id', { count: 'exact', head: true }).eq('user_id', userData.id),
      supabaseAdmin.from('Gig').select('id', { count: 'exact', head: true }).eq('user_id', userData.id).eq('status', 'completed'),
      supabaseAdmin.from('UserSkill').select('skill_name').eq('user_id', userData.id).order('display_order', { ascending: true }),
    ]);

    const gigsPosted = postedRes.status === 'fulfilled' ? (postedRes.value.count || 0) : 0;
    const gigsCompleted = completedRes.status === 'fulfilled' ? (completedRes.value.count || 0) : 0;
    const profileSkills2 = skillsRes2.status === 'fulfilled' ? (skillsRes2.value.data || []) : [];

    // Fetch reviews for this user
    let reviews = [];
    let averageRating = 0;
    let reviewCount = 0;

    try {
      const { data: reviewData, count: revCount } = await supabaseAdmin
        .from('Review')
        .select('*', { count: 'exact' })
        .eq('reviewee_id', userData.id)
        .order('created_at', { ascending: false })
        .limit(20);

      if (reviewData && reviewData.length > 0) {
        // Hydrate reviewer info
        const reviewerIds = [...new Set(reviewData.map(r => r.reviewer_id))];
        const { data: reviewers } = await supabaseAdmin
          .from('User')
          .select('id, username, name, first_name, last_name, profile_picture_url')
          .in('id', reviewerIds);

        const reviewerMap = {};
        (reviewers || []).forEach(u => { reviewerMap[u.id] = u; });

        reviews = reviewData.map(r => ({
          ...r,
          reviewer_name: reviewerMap[r.reviewer_id]?.name ||
                         reviewerMap[r.reviewer_id]?.first_name ||
                         reviewerMap[r.reviewer_id]?.username || 'Anonymous',
          reviewer_avatar: reviewerMap[r.reviewer_id]?.profile_picture_url || null,
          reviewer_username: reviewerMap[r.reviewer_id]?.username || null,
        }));

        const totalRating = reviews.reduce((sum, r) => sum + r.rating, 0);
        averageRating = Math.round((totalRating / reviews.length) * 100) / 100;
        reviewCount = revCount || reviews.length;
      }
    } catch (reviewErr) {
      // If Review table doesn't exist yet, gracefully skip
      logger.warn('Could not fetch reviews', { error: reviewErr.message });
    }

    const residency = await getPublicResidencySummary(userData.id);

    res.json({
      id: userData.id,
      username: userData.username,
      firstName: userData.first_name,
      lastName: userData.last_name,
      name: userData.name,
      bio: userData.bio,
      tagline: userData.tagline,
      avatar_url: userData.avatar_url,
      profile_picture_url: userData.profile_picture_url,
      profilePicture: userData.profile_picture_url,
      city: userData.city,
      state: userData.state,
      accountType: userData.account_type,
      verified: userData.verified,
      residency,
      created_at: userData.created_at,
      gigs_posted: gigsPosted,
      gigs_completed: gigsCompleted,
      average_rating: averageRating || userData.average_rating || 0,
      review_count: reviewCount,
      followers_count: userData.followers_count || 0,
      reviews: reviews,
      socialLinks: userData.social_links || {},
      skills: (profileSkills2 || []).map(s => s.skill_name),
    });
  } catch (err) {
    logger.error('Public profile fetch error', { error: err.message });
    res.status(500).json({ error: 'Failed to fetch profile' });
  }
});


// ============ FOLLOW ENDPOINTS (canonical location) ============

const FOLLOW_USER_SELECT = 'id, username, name, first_name, last_name, profile_picture_url, city, state, account_type';

/**
 * POST /:id/follow - Follow a user
 */
router.post('/:id/follow', verifyToken, async (req, res) => {
  try {
    const followingId = req.params.id;
    const followerId = req.user.id;

    if (followerId === followingId) {
      return res.status(400).json({ error: 'You cannot follow yourself' });
    }

    // Verify target user exists
    const { data: user } = await supabaseAdmin
      .from('User')
      .select('id, username, account_type')
      .eq('id', followingId)
      .single();

    if (!user) return res.status(404).json({ error: 'User not found' });

    // Exclude curator accounts — platform-owned, not a real neighbor
    if (user.account_type === 'curator') {
      return res.status(403).json({ error: 'This account cannot be followed' });
    }

    // Check for existing follow
    const { data: existing } = await supabaseAdmin
      .from('UserFollow')
      .select('id')
      .eq('follower_id', followerId)
      .eq('following_id', followingId)
      .single();

    if (existing) {
      return res.status(400).json({ error: 'You are already following this user' });
    }

    // Check block status
    const { isBlocked } = require('../utils/visibilityPolicy');
    if (await isBlocked(followerId, followingId)) {
      return res.status(403).json({ error: 'Cannot follow this user' });
    }

    const { error } = await supabaseAdmin
      .from('UserFollow')
      .insert({ follower_id: followerId, following_id: followingId });

    if (error) {
      logger.error('Error following user', { error: error.message, followerId, followingId });
      return res.status(500).json({ error: 'Failed to follow user' });
    }

    // Fire notification (non-blocking)
    const notificationService = require('../services/notificationService');
    const { data: followerUser } = await supabaseAdmin
      .from('User')
      .select('username, name, first_name')
      .eq('id', followerId)
      .single();

    const followerName = followerUser?.name || followerUser?.first_name || followerUser?.username || 'Someone';
    notificationService.createNotification({
      userId: followingId,
      type: 'new_follower',
      title: `${followerName} started following you`,
      body: null,
      icon: '👤',
      link: `/${followerUser?.username || followerId}`,
      metadata: { follower_id: followerId },
    });

    res.status(200).json({ message: `You are now following ${user.username}`, following: true });
  } catch (err) {
    logger.error('Follow error', { error: err.message });
    res.status(500).json({ error: 'Failed to follow user' });
  }
});

/**
 * DELETE /:id/follow - Unfollow a user
 */
router.delete('/:id/follow', verifyToken, async (req, res) => {
  try {
    const followingId = req.params.id;
    const followerId = req.user.id;

    const { error } = await supabaseAdmin
      .from('UserFollow')
      .delete()
      .eq('follower_id', followerId)
      .eq('following_id', followingId);

    if (error) {
      logger.error('Error unfollowing user', { error: error.message, followerId, followingId });
      return res.status(500).json({ error: 'Failed to unfollow user' });
    }

    res.json({ message: 'Unfollowed successfully', following: false });
  } catch (err) {
    logger.error('Unfollow error', { error: err.message });
    res.status(500).json({ error: 'Failed to unfollow user' });
  }
});

/**
 * GET /:id/followers - Get a user's followers
 */
router.get('/:id/followers', verifyToken, async (req, res) => {
  try {
    const userId = req.params.id;
    const { data: followers, error } = await supabaseAdmin
      .from('UserFollow')
      .select(`follower:follower_id (${FOLLOW_USER_SELECT}), created_at`)
      .eq('following_id', userId)
      .order('created_at', { ascending: false });

    if (error) {
      logger.error('Error fetching followers', { error: error.message, userId });
      return res.status(500).json({ error: 'Failed to fetch followers' });
    }

    // Exclude curator accounts — platform-owned, not a real neighbor
    const filtered = (followers || []).filter(f => f.follower?.account_type !== 'curator');
    res.json({ followers: filtered });
  } catch (err) {
    logger.error('Followers fetch error', { error: err.message });
    res.status(500).json({ error: 'Failed to fetch followers' });
  }
});

/**
 * GET /:id/following - Get who a user is following
 */
router.get('/:id/following', verifyToken, async (req, res) => {
  try {
    const userId = req.params.id;
    const { data: following, error } = await supabaseAdmin
      .from('UserFollow')
      .select(`following:following_id (${FOLLOW_USER_SELECT}), created_at`)
      .eq('follower_id', userId)
      .order('created_at', { ascending: false });

    if (error) {
      logger.error('Error fetching following', { error: error.message, userId });
      return res.status(500).json({ error: 'Failed to fetch following list' });
    }

    // Exclude curator accounts — platform-owned, not a real neighbor
    const filtered = (following || []).filter(f => f.following?.account_type !== 'curator');
    res.json({ following: filtered });
  } catch (err) {
    logger.error('Following fetch error', { error: err.message });
    res.status(500).json({ error: 'Failed to fetch following list' });
  }
});

/**
 * GET /:id/follow/status - Check if current user follows a target user
 */
router.get('/:id/follow/status', verifyToken, async (req, res) => {
  try {
    const targetId = req.params.id;
    const viewerId = req.user.id;

    const { data } = await supabaseAdmin
      .from('UserFollow')
      .select('id')
      .eq('follower_id', viewerId)
      .eq('following_id', targetId)
      .single();

    res.json({ following: !!data });
  } catch (err) {
    logger.error('Follow status error', { error: err.message });
    res.status(500).json({ error: 'Failed to check follow status' });
  }
});

/**
 * GET /:id/relationship - Get relationship status with another user
 * Returns combined follow + connection status for profile display.
 */
router.get('/:id/relationship', verifyToken, async (req, res) => {
  try {
    const targetId = req.params.id;
    const viewerId = req.user.id;

    const visibility = require('../utils/visibilityPolicy');

    const [relationshipStatus, followingThem, theyFollowMe] = await Promise.all([
      visibility.getRelationshipStatus(viewerId, targetId),
      visibility.isFollowing(viewerId, targetId),
      visibility.isFollowing(targetId, viewerId),
    ]);

    res.json({
      relationship: relationshipStatus,
      following: followingThem,
      followed_by: theyFollowMe,
    });
  } catch (err) {
    logger.error('Relationship status error', { error: err.message });
    res.status(500).json({ error: 'Failed to fetch relationship status' });
  }
});


// ============ OAUTH ENDPOINTS ============

/**
 * GET /api/users/oauth/:provider
 * Generate OAuth redirect URL for a given provider (google, apple)
 */
router.get('/oauth/:provider', oauthLimiter, async (req, res) => {
  const provider = req.params.provider;
  const validProviders = ['google', 'apple'];
  const requestedFlow = String(req.query.flow || 'code').toLowerCase();
  const validFlows = ['code', 'token'];

  if (!validProviders.includes(provider)) {
    return res.status(400).json({ error: `Invalid provider. Must be one of: ${validProviders.join(', ')}` });
  }
  if (!validFlows.includes(requestedFlow)) {
    return res.status(400).json({ error: `Invalid flow. Must be one of: ${validFlows.join(', ')}` });
  }

  try {
    // Allow mobile clients to pass their own deep-link redirect URI.
    // Validate against allowed origins to prevent open-redirect attacks.
    const oauthRedirectBase = getOAuthRedirectBaseUrl(req);
    const defaultRedirect = `${oauthRedirectBase}/auth/callback`;
    let redirectTo = defaultRedirect;
    if (req.query.redirectTo) {
      try {
        const requested = new URL(req.query.redirectTo);
        const appBase = new URL(oauthRedirectBase);
        // Allow same-origin redirects and common mobile deep-link schemes
        const isSameOrigin = requested.origin === appBase.origin;
        const isMobileScheme = ['pantopus:', 'exp:', 'exps:'].includes(requested.protocol);
        if (isSameOrigin || isMobileScheme) {
          redirectTo = req.query.redirectTo;
        } else {
          logger.warn('OAuth redirectTo rejected - not allowed origin', { requested: req.query.redirectTo });
        }
      } catch {
        logger.warn('OAuth redirectTo rejected - invalid URL', { requested: req.query.redirectTo });
      }
    }

    // Supabase handles provider callbacks at /auth/v1/callback and expects
    // `state` in query/body. Forcing `response_type=token` causes some
    // providers to return state in URL fragments, which never reaches server.
    // Keep `flow=token` accepted for backward compatibility, but always
    // initiate provider OAuth in authorization-code mode.
    if (requestedFlow === 'token') {
      logger.warn('OAuth token flow requested; using code flow to preserve callback state', {
        provider,
        redirectTo,
      });
    }
    const oauthOptions = {
      redirectTo,
      skipBrowserRedirect: true,
    };

    const { data, error } = await supabase.auth.signInWithOAuth({
      provider,
      options: oauthOptions,
    });

    if (error) {
      logger.error('OAuth URL generation error', { provider, error: error.message });
      return res.status(500).json({ error: 'Failed to initiate OAuth login' });
    }

    res.json({ url: data.url });
  } catch (err) {
    logger.error('OAuth error', { provider, error: err.message });
    res.status(500).json({ error: 'Failed to initiate OAuth login' });
  }
});

/**
 * POST /api/users/oauth/callback
 * Exchange OAuth code for session and ensure user profile exists
 */
/**
 * POST /api/users/oauth/token
 * Handle implicit flow — verify Supabase access token, create profile if needed
 */
router.post('/oauth/token', oauthLimiter, async (req, res) => {
  const { accessToken, refreshToken } = req.body;

  if (!accessToken) {
    return res.status(400).json({ error: 'Access token is required' });
  }

  try {
    // Verify the token and get the user
    const { data: { user }, error: userError } = await supabaseAdmin.auth.getUser(accessToken);

    if (userError || !user) {
      logger.error('OAuth token verification error', { error: userError?.message });
      return res.status(401).json({ error: 'Invalid or expired access token' });
    }

    const userId = user.id;
    const email = user.email || user.user_metadata?.email || null;
    const meta = user.user_metadata || {};

    // Apple may not provide email if user chose "Hide My Email".
    // In that case Supabase derives an @privaterelay.appleid.com address.
    if (!email) {
      logger.error('OAuth token login failed — no email available', { userId, provider: meta.iss || 'unknown' });
      return res.status(400).json({ error: 'Email address is required. Please allow email access during sign-in.' });
    }

    const userData = await ensureOAuthUserProfile({
      userId,
      email,
      meta,
      source: 'token',
    });

    logger.info('OAuth token login successful', { userId, email });

    // Set auth cookies for web clients (AUTH-3.3)
    setAuthCookies(res, accessToken, refreshToken || null, userId);

    const tokenFields = isCookieTransport(req) ? {} : {
      accessToken,
      ...(refreshToken ? { refreshToken } : {}),
    };

    res.json({
      message: 'Login successful',
      ...tokenFields,
      user: userData
        ? {
            id: userData.id,
            email: userData.email,
            username: userData.username,
            name: userData.name,
            firstName: userData.first_name,
            lastName: userData.last_name,
            role: userData.role,
            verified: true,
          }
        : { id: userId, email },
    });
  } catch (err) {
    logger.error('OAuth token callback error', { error: err.message, stack: err.stack });
    res.status(500).json({ error: 'OAuth login failed. Please try again.' });
  }
});

// Retained for backwards compatibility with clients using authorization-code flow.
router.post('/oauth/callback', oauthLimiter, async (req, res) => {
  const { code } = req.body;

  if (!code) {
    return res.status(400).json({ error: 'Authorization code is required' });
  }

  try {
    // Exchange code for session
    const { data: sessionData, error: sessionError } = await supabase.auth.exchangeCodeForSession(code);

    if (sessionError || !sessionData?.session) {
      logger.error('OAuth code exchange error', { error: sessionError?.message });
      return res.status(400).json({ error: 'Invalid or expired authorization code' });
    }

    const { session, user } = sessionData;
    const userId = user.id;
    const email = user.email || user.user_metadata?.email || null;
    const meta = user.user_metadata || {};

    // Apple may not provide email if user chose "Hide My Email".
    // Supabase usually assigns a relay address, but guard against null.
    if (!email) {
      logger.error('OAuth callback failed — no email available', { userId, provider: meta.iss || 'unknown' });
      return res.status(400).json({ error: 'Email address is required. Please allow email access during sign-in.' });
    }

    const userData = await ensureOAuthUserProfile({
      userId,
      email,
      meta,
      source: 'callback',
    });

    logger.info('OAuth login successful', { userId, email });

    // Set auth cookies for web clients (AUTH-3.3)
    setAuthCookies(res, session.access_token, session.refresh_token, userId);

    const tokenFields = isCookieTransport(req) ? {} : {
      accessToken: session.access_token,
      refreshToken: session.refresh_token,
      expiresIn: session.expires_in,
      expiresAt: session.expires_at,
    };

    res.json({
      message: 'Login successful',
      ...tokenFields,
      user: userData
        ? {
            id: userData.id,
            email: userData.email,
            username: userData.username,
            name: userData.name,
            firstName: userData.first_name,
            lastName: userData.last_name,
            role: userData.role,
            verified: true,
          }
        : { id: userId, email },
    });
  } catch (err) {
    logger.error('OAuth callback error', { error: err.message, stack: err.stack });
    res.status(500).json({ error: 'OAuth login failed. Please try again.' });
  }
});

// ============ DELETE ACCOUNT ============

/**
 * DELETE /api/users/account
 * Permanently delete the authenticated user's account and all associated data.
 *
 * Deletion order:
 *   1. Pre-checks — block if user has in-progress gigs or pending escrow payments
 *   2. Nullify bare FK columns (tables with NO ON DELETE clause → default NO ACTION)
 *   2c. Delete ChatMessage rows (user_id NOT NULL; DB had SET NULL until migration 20260406000001)
 *   3. Delete rows in RESTRICT-constrained tables (incl. seeder_config for curators)
 *   4. Delete the User row (CASCADE handles ~70+ related tables automatically)
 *   5. Delete the Supabase Auth user
 */
router.delete('/account', verifyToken, async (req, res) => {
  const userId = req.user.id;
  logger.info('Account deletion requested', { userId });

  try {
    // ── 1. Pre-checks ────────────────────────────────────────────
    // Block deletion if user has gigs currently in progress
    const { data: activeGigs } = await supabaseAdmin
      .from('Gig')
      .select('id, status')
      .eq('user_id', userId)
      .in('status', ['in_progress', 'assigned', 'pending_completion']);

    if (activeGigs && activeGigs.length > 0) {
      return res.status(409).json({
        error: 'Cannot delete account while you have gigs in progress. Please complete or cancel them first.',
        activeGigCount: activeGigs.length,
      });
    }

    // Block if user is assigned to someone else's gig that is in progress
    const { data: assignedGigs } = await supabaseAdmin
      .from('Gig')
      .select('id, status')
      .eq('accepted_by', userId)
      .in('status', ['in_progress', 'assigned', 'pending_completion']);

    if (assignedGigs && assignedGigs.length > 0) {
      return res.status(409).json({
        error: 'Cannot delete account while you are assigned to active gigs. Please complete them first.',
        assignedGigCount: assignedGigs.length,
      });
    }

    // Block if user has payments held in escrow
    const { data: escrowPayments } = await supabaseAdmin
      .from('Payment')
      .select('id')
      .or(`payer_id.eq.${userId},payee_id.eq.${userId}`)
      .in('status', ['escrow', 'pending', 'processing']);

    if (escrowPayments && escrowPayments.length > 0) {
      return res.status(409).json({
        error: 'Cannot delete account while you have pending or escrowed payments. Please resolve them first.',
        pendingPaymentCount: escrowPayments.length,
      });
    }

    // ── 2. Nullify bare FK columns (NO ON DELETE clause) ─────────
    // These columns reference User(id) without an ON DELETE rule,
    // meaning PostgreSQL defaults to NO ACTION which blocks deletion.
    // We SET NULL so the User row can be deleted afterwards.
    const nullifyOps = [
      // Business audit / admin columns
      { table: 'BusinessAuditLog', column: 'actor_user_id' },
      { table: 'BusinessPage', column: 'published_by' },
      { table: 'BusinessPageRevision', column: 'published_by' },
      { table: 'BusinessPermissionOverride', column: 'created_by' },
      { table: 'BusinessTeam', column: 'invited_by' },
      { table: 'BusinessSeat', column: 'invited_by_seat_id' },
      // Gig audit columns
      { table: 'Gig', column: 'cancelled_by' },
      { table: 'GigBid', column: 'countered_by' },
      { table: 'GigChangeOrder', column: 'requested_by' },
      { table: 'GigChangeOrder', column: 'reviewed_by' },
      { table: 'GigIncident', column: 'reported_by' },
      { table: 'GigIncident', column: 'reported_against' },
      { table: 'GigIncident', column: 'resolved_by' },
      { table: 'GigQuestion', column: 'asked_by' },
      { table: 'GigQuestion', column: 'answered_by' },
      // Home audit columns
      { table: 'HomeAccessSecret', column: 'created_by' },
      { table: 'HomeAsset', column: 'created_by' },
      { table: 'HomeBill', column: 'created_by' },
      { table: 'HomeBusinessLink', column: 'created_by' },
      { table: 'HomeCalendarEvent', column: 'created_by' },
      { table: 'HomeDevice', column: 'created_by' },
      { table: 'HomeDocument', column: 'created_by' },
      { table: 'HomeEmergency', column: 'created_by' },
      { table: 'HomeIssue', column: 'reported_by' },
      { table: 'HomeMaintenanceTemplate', column: 'created_by' },
      { table: 'HomePackage', column: 'created_by' },
      { table: 'HomeResidencyClaim', column: 'reviewed_by' },
      { table: 'HomeSubscription', column: 'created_by' },
      { table: 'HomeTask', column: 'created_by' },
      { table: 'HomeVendor', column: 'created_by' },
      // Listing
      { table: 'ListingQuestion', column: 'answered_by' },
      // Payment / refund audit columns
      { table: 'Payment', column: 'escrow_released_by' },
      { table: 'Refund', column: 'approved_by' },
      { table: 'Refund', column: 'initiated_by' },
      // Relationship
      { table: 'Relationship', column: 'blocked_by' },
    ];

    // Run all nullify operations in parallel
    const nullifyResults = await Promise.allSettled(
      nullifyOps.map(({ table, column }) =>
        supabaseAdmin.from(table).update({ [column]: null }).eq(column, userId)
      )
    );

    // Log any failures but continue — these are non-critical audit columns
    nullifyResults.forEach((result, i) => {
      if (result.status === 'rejected') {
        logger.warn('Nullify failed (continuing)', {
          table: nullifyOps[i].table,
          column: nullifyOps[i].column,
          error: result.reason?.message,
        });
      }
    });

    // ── 2b. Delete rows from tables with bare FK where column is NOT nullable ──
    await Promise.allSettled([
      supabaseAdmin.from('GigQuestionUpvote').delete().eq('user_id', userId),
      supabaseAdmin.from('NeighborEndorsement').delete().eq('endorser_user_id', userId),
      supabaseAdmin.from('NeighborEndorsement').delete().eq('business_user_id', userId),
    ]);

    // ── 2c. ChatMessage: user_id is NOT NULL; legacy FK used ON DELETE SET NULL ──
    await supabaseAdmin.from('ChatMessage').delete().eq('user_id', userId);

    // ── 3. Delete rows from RESTRICT-constrained tables ──────────
    // These have ON DELETE RESTRICT and would block User deletion.
    // Order matters: Refund → Payment → Payout, WalletTransaction, Subscription

    // Delete refunds linked to user's payments first
    const { data: userPayments } = await supabaseAdmin
      .from('Payment')
      .select('id')
      .or(`payer_id.eq.${userId},payee_id.eq.${userId}`);
    const paymentIds = (userPayments || []).map(p => p.id);

    if (paymentIds.length > 0) {
      // Delete refunds that reference these payments
      await supabaseAdmin.from('Refund').delete().in('payment_id', paymentIds);
    }

    // Now delete payments themselves
    await supabaseAdmin.from('Payment').delete().or(`payer_id.eq.${userId},payee_id.eq.${userId}`);

    // Delete wallet transactions (RESTRICT on user_id AND wallet_id)
    await supabaseAdmin.from('WalletTransaction').delete().eq('user_id', userId);

    // Delete payouts (RESTRICT on user_id)
    await supabaseAdmin.from('Payout').delete().eq('user_id', userId);

    // Delete subscriptions (RESTRICT on user_id)
    await supabaseAdmin.from('Subscription').delete().eq('user_id', userId);

    // Delete regional seeder config where this user is the curator (RESTRICT on curator_user_id)
    await supabaseAdmin.from('seeder_config').delete().eq('curator_user_id', userId);

    // ── 4. Delete User row ───────────────────────────────────────
    // This triggers ON DELETE CASCADE for ~70+ tables (Posts, Gigs,
    // Wallet, Listings, Notifications, Chat participants, etc.)
    const { error: deleteError } = await supabaseAdmin
      .from('User')
      .delete()
      .eq('id', userId);

    if (deleteError) {
      logger.error('User row deletion failed', { userId, error: deleteError.message });
      return res.status(500).json({ error: 'Failed to delete account. Please contact support.' });
    }

    // ── 5. Delete Supabase Auth user ─────────────────────────────
    try {
      await supabaseAdmin.auth.admin.deleteUser(userId);
      logger.info('Supabase auth user deleted', { userId });
    } catch (authErr) {
      // User row is already gone — log but don't fail the request
      logger.warn('Auth user deletion failed (DB user already removed)', {
        userId,
        error: authErr.message,
      });
    }

    logger.info('Account deleted successfully', { userId });
    res.json({ message: 'Account deleted successfully' });
  } catch (err) {
    logger.error('Account deletion error', { userId, error: err.message, stack: err.stack });
    res.status(500).json({ error: 'Failed to delete account. Please contact support.' });
  }
});

// ============================================================
// POST /api/users/:userId/report — Report an abusive user
// ============================================================

const reportUserSchema = Joi.object({
  reason: Joi.string()
    .valid('spam', 'harassment', 'inappropriate', 'misinformation', 'safety', 'other')
    .required(),
  details: Joi.string().max(1000).optional(),
});

function isUserReportTableMissing(error) {
  const message = (error?.message || '').toLowerCase();
  return (
    error?.code === 'PGRST205' ||
    message.includes('relation "userreport" does not exist') ||
    message.includes('could not find the table')
  );
}

router.post('/:userId/report', verifyToken, validate(reportUserSchema), async (req, res) => {
  try {
    const { userId } = req.params;
    const { reason, details } = req.body;
    const reporterId = req.user.id;

    if (userId === reporterId) {
      return res.status(400).json({ error: 'You cannot report yourself' });
    }

    // Verify target user exists
    const { data: targetUser, error: userErr } = await supabaseAdmin
      .from('User')
      .select('id')
      .eq('id', userId)
      .maybeSingle();

    if (userErr) {
      logger.error('User report lookup error', { userId, reporterId, error: userErr.message });
      return res.status(500).json({ error: 'Failed to report user' });
    }

    if (!targetUser) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Check for existing report
    const { data: existingReport, error: existingErr } = await supabaseAdmin
      .from('UserReport')
      .select('id')
      .eq('reported_user_id', userId)
      .eq('reported_by', reporterId)
      .maybeSingle();

    if (existingErr) {
      if (isUserReportTableMissing(existingErr)) {
        logger.warn('UserReport table not yet migrated', { userId, reporterId });
        return res.status(503).json({
          error: 'Report feature is unavailable until database migrations are applied',
        });
      }
      logger.error('User report lookup error', { userId, reporterId, error: existingErr.message });
      return res.status(500).json({ error: 'Failed to report user' });
    }

    if (!existingReport) {
      const { error: insertErr } = await supabaseAdmin.from('UserReport').insert({
        reported_user_id: userId,
        reported_by: reporterId,
        reason,
        details: details || null,
      });

      if (insertErr) {
        if (isUserReportTableMissing(insertErr)) {
          logger.warn('UserReport table not yet migrated', { userId, reporterId });
          return res.status(503).json({
            error: 'Report feature is unavailable until database migrations are applied',
          });
        }
        logger.error('User report insert error', { userId, reporterId, error: insertErr.message });
        return res.status(500).json({ error: 'Failed to report user' });
      }
    }

    res.json({
      message: existingReport
        ? 'User already reported. We will review it shortly.'
        : 'User reported successfully. We will review it shortly.',
      already_reported: Boolean(existingReport),
    });
  } catch (err) {
    logger.error('User report error', { error: err.message, userId: req.params.userId });
    res.status(500).json({ error: 'Failed to report user' });
  }
});

// ============================================================
// POST /api/users/logout — Clear auth cookies (AUTH-3.3 / P1-7)
// ============================================================

// Logout does NOT require verifyToken + CSRF — it must work even when the
// access token has expired or the CSRF cookie is stale. Clearing cookies is
// safe regardless of auth state; the worst an attacker can do via CSRF is
// log the user out, which is low-severity.
router.post('/logout', logoutLimiter, async (req, res) => {
  clearAuthCookies(res);

  try {
    await supabase.auth.signOut();
  } catch {
    // Non-fatal — cookies are already cleared
  }

  res.json({ success: true });
});

module.exports = router;
