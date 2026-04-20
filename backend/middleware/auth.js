const { createClient } = require('@supabase/supabase-js');
const supabaseAdmin = require('./supabaseAdmin');
require('dotenv').config();

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_ANON_KEY;

if (!supabaseUrl || !supabaseKey) {
  throw new Error('Missing SUPABASE_URL or SUPABASE_ANON_KEY');
}

const supabase = createClient(supabaseUrl, supabaseKey);

// ============ AUTHENTICATION HELPERS ============

/**
 * Sign up a new user (uses admin API to avoid email rate limits)
 */
async function signUp(email, password) {
  // Use admin API - creates user without sending email
  const { data, error } = await supabaseAdmin.auth.admin.createUser({
    email,
    password,
    email_confirm: true // Auto-confirm for development
  });
  
  if (error) throw error;
  
  // Admin API returns { user: {...} } directly, not { data: { user: {...} } }
  // Normalize the response to match regular signUp format
  if (!data || !data.user) {
    throw new Error('No user data returned from Supabase');
  }
  
  return {
    user: data.user,
    session: null // No session since we used admin API
  };
}

/**
 * Sign in user with email/password
 */
async function signIn(email, password) {
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password
  });
  
  if (error) throw error;
  return data;
}

/**
 * Get current session from token
 */
async function getSession(token) {
  const { data, error } = await supabase.auth.getUser(token);
  
  if (error) throw error;
  return data;
}

/**
 * Sign out user
 */
async function signOut(token) {
  const { error } = await supabase.auth.signOut({ scope: 'global' });
  
  if (error) throw error;
}

/**
 * Send password reset email
 */
async function resetPassword(email) {
  const { data, error } = await supabase.auth.resetPasswordForEmail(email, {
    redirectTo: `${process.env.APP_URL}/reset-password`
  });
  
  if (error) throw error;
  return data;
}

/**
 * Update user password
 */
async function updatePassword(accessToken, newPassword) {
  const { data, error } = await supabase.auth.updateUser(
    { password: newPassword },
    { accessToken }
  );
  
  if (error) throw error;
  return data;
}

/**
 * Resend confirmation email
 */
async function resendConfirmationEmail(email) {
  const { data, error } = await supabase.auth.resend({
    type: 'signup',
    email
  });
  
  if (error) throw error;
  return data;
}

module.exports = {
  signUp,
  signIn,
  getSession,
  signOut,
  resetPassword,
  updatePassword,
  resendConfirmationEmail
};
