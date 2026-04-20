/**
 * Email Service
 * 
 * Uses nodemailer for sending emails.
 * In development (no SMTP config), logs emails via logger.
 * In production, uses configured SMTP transport.
 * 
 * Required env vars for production:
 *   SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS
 * Optional:
 *   SMTP_FROM (defaults to "Pantopus <noreply@pantopus.com>")
 *   APP_URL (defaults to "http://localhost:3000")
 */

const nodemailer = require('nodemailer');
const logger = require('../utils/logger');

const APP_URL = process.env.APP_URL || 'http://localhost:3000';
const SMTP_FROM = process.env.SMTP_FROM || 'Pantopus <noreply@pantopus.com>';

let transporter = null;
let devMode = true;

// Initialize transport
if (process.env.SMTP_HOST && process.env.SMTP_USER) {
  try {
    transporter = nodemailer.createTransport({
      host: process.env.SMTP_HOST,
      port: parseInt(process.env.SMTP_PORT || '587'),
      secure: process.env.SMTP_SECURE === 'true',
      auth: {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS,
      },
    });
    devMode = false;
    logger.info('Email service initialized with SMTP transport');
  } catch (err) {
    logger.warn('Failed to init SMTP transport, falling back to dev mode', { error: err.message });
  }
} else {
  logger.info('Email service running in DEV mode (no SMTP config). Emails will be logged to console.');
}

/**
 * Send an email. In dev mode, logs to console instead.
 * @param {Object} opts - { to, subject, html, text }
 * @returns {Promise<{ success: boolean, messageId?: string }>}
 */
async function sendEmail({ to, subject, html, text }) {
  const mailOptions = {
    from: SMTP_FROM,
    to,
    subject,
    html,
    text: text || stripHtml(html),
  };

  if (devMode) {
    logger.info('📧 [DEV EMAIL] Would send email:', {
      to: mailOptions.to,
      subject: mailOptions.subject,
    });
    logger.debug('DEV EMAIL body', {
      to: mailOptions.to,
      subject: mailOptions.subject,
      body: mailOptions.text || '(html only)',
    });
    return { success: true, messageId: `dev-${Date.now()}` };
  }

  try {
    const info = await transporter.sendMail(mailOptions);
    logger.info('Email sent', { to, subject, messageId: info.messageId });
    return { success: true, messageId: info.messageId };
  } catch (err) {
    logger.error('Failed to send email', { to, subject, error: err.message });
    return { success: false, error: err.message };
  }
}

// ============================================================
//  HOME INVITE EMAIL
// ============================================================

/**
 * Send a home invitation email.
 * @param {Object} opts
 * @param {string} opts.toEmail - recipient email
 * @param {string} opts.inviterName - name of person who invited
 * @param {string} opts.homeName - home name/address
 * @param {string} opts.homeCity - city, state
 * @param {string} opts.role - proposed role
 * @param {string} opts.token - invite token
 * @param {string} [opts.message] - optional personal message
 * @param {boolean} [opts.isExistingUser] - whether recipient has a Pantopus account
 */
async function sendHomeInviteEmail({
  toEmail,
  inviterName,
  homeName,
  homeCity,
  role,
  token,
  message,
  isExistingUser = false,
}) {
  const acceptUrl = `${APP_URL}/invite/${token}`;
  const roleLabel = role.replace('_', ' ');

  const subject = `${inviterName} invited you to join their home on Pantopus`;

  const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0; padding:0; background-color:#f9fafb; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <div style="max-width:480px; margin:40px auto; background:#ffffff; border-radius:16px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,0.1);">
    
    <!-- Header -->
    <div style="background:#111827; padding:32px 32px 24px; text-align:center;">
      <div style="font-size:32px; margin-bottom:8px;">🏠</div>
      <h1 style="color:#ffffff; font-size:20px; font-weight:600; margin:0;">You're Invited!</h1>
    </div>
    
    <!-- Body -->
    <div style="padding:32px;">
      <p style="color:#374151; font-size:15px; line-height:1.6; margin:0 0 16px;">
        <strong>${escapeHtml(inviterName)}</strong> has invited you to join their home on Pantopus as a <strong>${escapeHtml(roleLabel)}</strong>.
      </p>
      
      <!-- Home card -->
      <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:12px; padding:16px; margin:0 0 20px;">
        <div style="font-size:15px; font-weight:600; color:#111827;">🏡 ${escapeHtml(homeName)}</div>
        ${homeCity ? `<div style="font-size:13px; color:#6b7280; margin-top:4px;">${escapeHtml(homeCity)}</div>` : ''}
      </div>
      
      ${message ? `
      <!-- Personal message -->
      <div style="background:#eff6ff; border-left:3px solid #3b82f6; padding:12px 16px; border-radius:0 8px 8px 0; margin:0 0 20px;">
        <div style="font-size:11px; text-transform:uppercase; color:#6b7280; font-weight:600; margin-bottom:4px;">Message from ${escapeHtml(inviterName)}</div>
        <p style="color:#374151; font-size:14px; line-height:1.5; margin:0; font-style:italic;">"${escapeHtml(message)}"</p>
      </div>
      ` : ''}
      
      <!-- CTA -->
      <div style="text-align:center; margin:28px 0;">
        <a href="${acceptUrl}" style="display:inline-block; background:#111827; color:#ffffff; font-size:15px; font-weight:600; padding:14px 32px; border-radius:10px; text-decoration:none;">
          ${isExistingUser ? 'View Invitation' : 'Join Pantopus & Accept'}
        </a>
      </div>
      
      <p style="color:#9ca3af; font-size:12px; text-align:center; margin:20px 0 0;">
        This invitation expires in 7 days.${!isExistingUser ? ' You\'ll create a free account to get started.' : ''}
      </p>
    </div>
    
    <!-- Footer -->
    <div style="background:#f9fafb; border-top:1px solid #e5e7eb; padding:16px 32px; text-align:center;">
      <p style="color:#9ca3af; font-size:11px; margin:0;">
        Pantopus — Your household, organized.
      </p>
      <p style="color:#d1d5db; font-size:10px; margin:8px 0 0;">
        If you didn't expect this email, you can safely ignore it.
      </p>
    </div>
  </div>
</body>
</html>`;

  const text = `
${inviterName} invited you to join their home on Pantopus!

Home: ${homeName}${homeCity ? ` (${homeCity})` : ''}
Role: ${roleLabel}
${message ? `\nMessage: "${message}"\n` : ''}
Accept the invitation: ${acceptUrl}

This invitation expires in 7 days.
${!isExistingUser ? 'You\'ll create a free account to get started.' : ''}
`;

  return sendEmail({ to: toEmail, subject, html, text });
}

// ============================================================
//  PASSWORD RESET EMAIL
// ============================================================

/**
 * Send a password reset email with the reset link.
 * @param {Object} opts
 * @param {string} opts.toEmail - recipient email
 * @param {string} opts.resetLink - full URL to open to reset password (from Supabase generateLink)
 */
async function sendPasswordResetEmail({ toEmail, resetLink }) {
  const subject = 'Reset your Pantopus password';

  const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0; padding:0; background-color:#f9fafb; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <div style="max-width:480px; margin:40px auto; background:#ffffff; border-radius:16px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,0.1);">
    
    <div style="background:#111827; padding:32px 32px 24px; text-align:center;">
      <div style="font-size:32px; margin-bottom:8px;">🔐</div>
      <h1 style="color:#ffffff; font-size:20px; font-weight:600; margin:0;">Reset your password</h1>
    </div>
    
    <div style="padding:32px;">
      <p style="color:#374151; font-size:15px; line-height:1.6; margin:0 0 16px;">
        You requested a password reset for your Pantopus account. Click the button below to set a new password.
      </p>
      
      <div style="text-align:center; margin:28px 0;">
        <a href="${escapeHtml(resetLink)}" style="display:inline-block; background:#111827; color:#ffffff; font-size:15px; font-weight:600; padding:14px 32px; border-radius:10px; text-decoration:none;">
          Reset password
        </a>
      </div>
      
      <p style="color:#9ca3af; font-size:12px; margin:20px 0 0;">
        This link expires in 1 hour. If you didn't request a reset, you can safely ignore this email.
      </p>
    </div>
    
    <div style="background:#f9fafb; border-top:1px solid #e5e7eb; padding:16px 32px; text-align:center;">
      <p style="color:#9ca3af; font-size:11px; margin:0;">Pantopus — Your household, organized.</p>
    </div>
  </div>
</body>
</html>`;

  const text = `
Reset your Pantopus password

You requested a password reset. Open this link to set a new password:

${resetLink}

This link expires in 1 hour. If you didn't request a reset, you can safely ignore this email.
`;

  return sendEmail({ to: toEmail, subject, html, text });
}

// ============================================================
//  MONTHLY RECEIPT EMAIL
// ============================================================

/**
 * Send a monthly receipt email.
 * @param {string} toEmail - recipient email
 * @param {Object} receipt - computed receipt from monthlyReceiptService
 */
async function sendMonthlyReceipt(toEmail, receipt) {
  const { period, earnings, spending, marketplace, community, reputation, highlight } = receipt;
  const earningsDollars = (earnings.total_cents / 100).toFixed(2);
  const spendingDollars = (spending.total_cents / 100).toFixed(2);
  const shareUrl = `${APP_URL}/profile?tab=receipt&year=${period.year}&month=${period.month}`;
  const unsubUrl = `${APP_URL}/settings/notifications`;

  const subject = `Your ${period.label} Pantopus Summary`;

  const section = (icon, title, lines) => {
    const lineHtml = lines
      .filter(([, v]) => v !== null && v !== undefined)
      .map(([label, value]) => `
        <tr>
          <td style="color:#6b7280; font-size:14px; padding:4px 0;">${escapeHtml(label)}</td>
          <td style="color:#111827; font-size:14px; font-weight:600; padding:4px 0; text-align:right;">${escapeHtml(String(value))}</td>
        </tr>`)
      .join('');
    return `
      <div style="margin:0 0 20px;">
        <div style="font-size:14px; font-weight:600; color:#111827; margin-bottom:8px;">${icon} ${escapeHtml(title)}</div>
        <table style="width:100%; border-collapse:collapse;">${lineHtml}</table>
      </div>`;
  };

  const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0; padding:0; background-color:#f9fafb; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <div style="max-width:480px; margin:40px auto; background:#ffffff; border-radius:16px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,0.1);">

    <!-- Header -->
    <div style="background:#111827; padding:32px 32px 24px; text-align:center;">
      <div style="font-size:32px; margin-bottom:8px;">📊</div>
      <h1 style="color:#ffffff; font-size:20px; font-weight:600; margin:0;">Your ${escapeHtml(period.label)} Summary</h1>
    </div>

    <!-- Highlight -->
    <div style="background:#eff6ff; padding:20px 32px; text-align:center;">
      <p style="color:#1d4ed8; font-size:16px; font-weight:600; margin:0;">${escapeHtml(highlight)}</p>
    </div>

    <!-- Body -->
    <div style="padding:32px;">
      ${section('💰', 'Earnings', [
        ['Gigs completed', earnings.gig_count],
        ['Total earned', `$${earningsDollars}`],
        ['Top category', earnings.top_category || 'N/A'],
      ])}

      ${section('🛒', 'Spending', [
        ['Gigs posted (completed)', spending.gig_count],
        ['Total spent', `$${spendingDollars}`],
      ])}

      ${section('🏪', 'Marketplace', [
        ['Listings sold', marketplace.listings_sold],
        ['Listings bought', marketplace.listings_bought],
        ['Free items claimed', marketplace.free_items_claimed],
      ])}

      ${section('🤝', 'Community', [
        ['Posts created', community.posts_created],
        ['Connections made', community.connections_made],
        ['Neighbors helped', community.neighbors_helped],
      ])}

      ${section('⭐', 'Reputation', [
        ['Current rating', reputation.current_rating ? reputation.current_rating.toFixed(1) : 'N/A'],
        ['Reviews received', reputation.reviews_received],
        ['Rating change', reputation.rating_change !== null ? (reputation.rating_change >= 0 ? '+' : '') + reputation.rating_change.toFixed(2) : 'N/A'],
      ])}

      <!-- Share CTA -->
      <div style="text-align:center; margin:28px 0 0;">
        <a href="${shareUrl}" style="display:inline-block; background:#111827; color:#ffffff; font-size:15px; font-weight:600; padding:14px 32px; border-radius:10px; text-decoration:none;">
          Share your Pantopus month
        </a>
      </div>
    </div>

    <!-- Footer -->
    <div style="background:#f9fafb; border-top:1px solid #e5e7eb; padding:16px 32px; text-align:center;">
      <p style="color:#9ca3af; font-size:11px; margin:0;">
        Pantopus — Your neighborhood, connected.
      </p>
      <p style="color:#d1d5db; font-size:10px; margin:8px 0 0;">
        <a href="${unsubUrl}" style="color:#9ca3af; text-decoration:underline;">Unsubscribe from monthly receipts</a>
      </p>
    </div>
  </div>
</body>
</html>`;

  const text = `
Your ${period.label} Pantopus Summary
${highlight}

EARNINGS
  Gigs completed: ${earnings.gig_count}
  Total earned: $${earningsDollars}
  Top category: ${earnings.top_category || 'N/A'}

SPENDING
  Gigs posted (completed): ${spending.gig_count}
  Total spent: $${spendingDollars}

MARKETPLACE
  Listings sold: ${marketplace.listings_sold}
  Listings bought: ${marketplace.listings_bought}
  Free items claimed: ${marketplace.free_items_claimed}

COMMUNITY
  Posts created: ${community.posts_created}
  Connections made: ${community.connections_made}
  Neighbors helped: ${community.neighbors_helped}

REPUTATION
  Current rating: ${reputation.current_rating ? reputation.current_rating.toFixed(1) : 'N/A'}
  Reviews received: ${reputation.reviews_received}
  Rating change: ${reputation.rating_change !== null ? (reputation.rating_change >= 0 ? '+' : '') + reputation.rating_change.toFixed(2) : 'N/A'}

Share your month: ${shareUrl}
Unsubscribe: ${unsubUrl}
`;

  return sendEmail({ to: toEmail, subject, html, text });
}

// ============================================================
//  GUEST RESERVATION CONFIRMATION EMAIL
// ============================================================

/**
 * Send a confirmation email to a guest who signed up for a support train slot.
 * @param {Object} opts
 * @param {string} opts.toEmail
 * @param {string} opts.guestName
 * @param {string} opts.trainTitle
 * @param {string} opts.slotLabel
 * @param {string} opts.slotDate
 * @param {string|null} opts.slotTime
 * @param {string} opts.contributionMode - cook | takeout | groceries
 * @param {string} opts.supportTrainId
 */
async function sendGuestReservationConfirmationEmail({
  toEmail,
  guestName,
  trainTitle,
  slotLabel,
  slotDate,
  slotTime,
  contributionMode,
  supportTrainId,
}) {
  const modeLabels = { cook: 'Home-cooked meal', takeout: 'Takeout', groceries: 'Groceries' };
  const modeLabel = modeLabels[contributionMode] || contributionMode;
  const appUrl = `${APP_URL}/support-trains/${supportTrainId}`;

  const subject = `You're signed up to help: ${trainTitle}`;

  const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0; padding:0; background-color:#f9fafb; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <div style="max-width:480px; margin:40px auto; background:#ffffff; border-radius:16px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,0.1);">

    <div style="background:#111827; padding:32px 32px 24px; text-align:center;">
      <div style="font-size:32px; margin-bottom:8px;">🚂</div>
      <h1 style="color:#ffffff; font-size:20px; font-weight:600; margin:0;">You're Signed Up!</h1>
    </div>

    <div style="padding:32px;">
      <p style="color:#374151; font-size:15px; line-height:1.6; margin:0 0 16px;">
        Hi <strong>${escapeHtml(guestName)}</strong>, thank you for signing up to help with <strong>${escapeHtml(trainTitle)}</strong>.
      </p>

      <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:12px; padding:16px; margin:0 0 20px;">
        <div style="font-size:15px; font-weight:600; color:#111827;">📅 ${escapeHtml(slotLabel)} · ${escapeHtml(slotDate)}</div>
        ${slotTime ? `<div style="font-size:13px; color:#6b7280; margin-top:4px;">⏰ ${escapeHtml(slotTime)}</div>` : ''}
        <div style="font-size:13px; color:#6b7280; margin-top:4px;">🍽 ${escapeHtml(modeLabel)}</div>
      </div>

      <p style="color:#374151; font-size:14px; line-height:1.6; margin:0 0 16px;">
        The organizer will share exact delivery details with you closer to the date. Keep an eye on your inbox for updates.
      </p>

      <div style="background:#eff6ff; border:1px solid #bfdbfe; border-radius:12px; padding:16px; margin:0 0 20px;">
        <div style="font-size:13px; font-weight:600; color:#1e40af; margin-bottom:6px;">Want a better experience?</div>
        <p style="color:#374151; font-size:13px; line-height:1.5; margin:0;">
          Download the Pantopus app for one-tap signups, real-time updates, delivery coordination, and direct chat with the organizer.
        </p>
      </div>

      <div style="text-align:center; margin:28px 0;">
        <a href="${appUrl}" style="display:inline-block; background:#111827; color:#ffffff; font-size:15px; font-weight:600; padding:14px 32px; border-radius:10px; text-decoration:none;">
          View Support Train
        </a>
      </div>
    </div>

    <div style="background:#f9fafb; border-top:1px solid #e5e7eb; padding:16px 32px; text-align:center;">
      <p style="color:#9ca3af; font-size:11px; margin:0;">Pantopus — Your household, organized.</p>
      <p style="color:#d1d5db; font-size:10px; margin:8px 0 0;">
        If you didn't sign up for this, you can safely ignore this email.
      </p>
    </div>
  </div>
</body>
</html>`;

  const text = `
You're signed up to help: ${trainTitle}

Hi ${guestName}, thank you for signing up!

${slotLabel} · ${slotDate}${slotTime ? `\nTime: ${slotTime}` : ''}
Type: ${modeLabel}

The organizer will share exact delivery details with you closer to the date.

Want a better experience? Download the Pantopus app for one-tap signups, real-time updates, and direct chat with the organizer.

View Support Train: ${appUrl}
`;

  return sendEmail({ to: toEmail, subject, html, text });
}

// ============================================================
//  HELPERS
// ============================================================

function escapeHtml(str) {
  if (!str) return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function stripHtml(html) {
  if (!html) return '';
  return html
    .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

module.exports = {
  sendEmail,
  sendHomeInviteEmail,
  sendPasswordResetEmail,
  sendMonthlyReceipt,
  sendGuestReservationConfirmationEmail,
};
