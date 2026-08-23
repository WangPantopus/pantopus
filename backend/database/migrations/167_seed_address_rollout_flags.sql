-- Migration 167: Register the address rollout flags with the feature-flag service
--
-- CRIT-06: the address-verification enforcement flags were a frozen process.env
-- snapshot, invisible to the admin-flippable, cache-invalidating, audit-logged
-- FeatureFlag service that ships in the same codebase. Changing enforcement
-- posture meant an environment change plus a full redeploy — no gradual ramp, no
-- instant rollback, and no IdentityAuditLog record of who changed it.
--
-- These rows give each flag a runtime home. They are seeded DISABLED, which
-- matches the current production defaults in config/addressVerification.js, so
-- applying this migration changes no behaviour. utils/addressRolloutFlags.js
-- treats a missing row as "use the environment value", so an operator can also
-- delete a row to hand control back to the environment.

INSERT INTO "public"."FeatureFlag" ("flag_name", "description")
VALUES
  ('address.enable_place_provider',
   'Address: call the Google Places classification provider.'),
  ('address.enforce_place_provider_business',
   'Address: let Places classification produce a BUSINESS verdict (enforcing, not shadow).'),
  ('address.enable_secondary_provider',
   'Address: call the secondary unit-intelligence provider.'),
  ('address.enable_parcel_provider',
   'Address: call the parcel/property intelligence provider.'),
  ('address.enforce_parcel_provider_classification',
   'Address: let parcel land use produce a classification verdict (enforcing, not shadow).'),
  ('address.require_address_id_for_home_create',
   'Address: require a validated address_id on POST /api/homes.'),
  ('address.enforce_mixed_use_step_up',
   'Address: require step-up verification for MIXED_USE verdicts.'),
  ('address.enforce_low_confidence_step_up',
   'Address: require step-up verification for LOW_CONFIDENCE verdicts.')
ON CONFLICT ("flag_name") DO NOTHING;
