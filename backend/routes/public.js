const express = require('express');
const router = express.Router();
const supabaseAdmin = require('../config/supabaseAdmin');

// ============================================================
// Public Preview Endpoints
// No authentication required. Returns sanitized data only —
// no user IDs, no exact addresses, no PII.
// ============================================================

// ── GET /api/public/gigs/:id ────────────────────────────────
router.get('/gigs/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const { data: gig, error } = await supabaseAdmin
      .from('Gig')
      .select('id, title, description, category, price, price_max, city, state, status, created_at')
      .eq('id', id)
      .single();

    if (error || !gig) {
      return res.status(404).json({ error: 'Not found' });
    }

    const isExpired = gig.status === 'completed' || gig.status === 'cancelled' || gig.status === 'expired';

    res.json({
      id: gig.id,
      title: gig.title,
      description: isExpired ? null : (gig.description || '').slice(0, 300),
      category: gig.category,
      price_min: gig.price || null,
      price_max: gig.price_max || null,
      city: gig.city,
      state: gig.state,
      status: gig.status,
      is_expired: isExpired,
      created_at: gig.created_at,
    });
  } catch (err) {
    console.error('[public/gigs] Error:', err.message);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ── GET /api/public/listings/:id ────────────────────────────
router.get('/listings/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const { data: listing, error } = await supabaseAdmin
      .from('Listing')
      .select('id, title, description, price, currency, condition, city, state, status, photos, created_at')
      .eq('id', id)
      .single();

    if (error || !listing) {
      return res.status(404).json({ error: 'Not found' });
    }

    const isSold = listing.status === 'sold' || listing.status === 'removed' || listing.status === 'expired';

    // Only expose the first photo
    let photoUrl = null;
    if (listing.photos && Array.isArray(listing.photos) && listing.photos.length > 0) {
      const first = listing.photos[0];
      photoUrl = typeof first === 'string' ? first : first?.url || first?.uri || null;
    }

    res.json({
      id: listing.id,
      title: listing.title,
      description: isSold ? null : (listing.description || '').slice(0, 300),
      price: listing.price,
      currency: listing.currency || 'USD',
      condition: listing.condition,
      city: listing.city,
      state: listing.state,
      status: listing.status,
      is_sold: isSold,
      photo_url: photoUrl,
      created_at: listing.created_at,
    });
  } catch (err) {
    console.error('[public/listings] Error:', err.message);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ── GET /api/public/posts/:id ───────────────────────────────
router.get('/posts/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const { data: post, error } = await supabaseAdmin
      .from('Post')
      .select('id, title, content, post_type, city, state, visibility, created_at')
      .eq('id', id)
      .single();

    if (error || !post) {
      return res.status(404).json({ error: 'Not found' });
    }

    // Only expose publicly visible posts
    const publicVisibilities = ['public', 'neighborhood', 'city', 'global'];
    if (!publicVisibilities.includes(post.visibility)) {
      return res.status(404).json({ error: 'Not found' });
    }

    res.json({
      id: post.id,
      title: post.title,
      body: (post.content || '').slice(0, 200),
      post_type: post.post_type,
      city: post.city,
      state: post.state,
      created_at: post.created_at,
    });
  } catch (err) {
    console.error('[public/posts] Error:', err.message);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
