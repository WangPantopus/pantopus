# Stage E — adversarial security review of the persistent-login backend

Status: IN PROGRESS (2026-08-19)

Role: threat-model the *implemented* code (not the design) on the auth paths:
`backend/routes/users.js`, `backend/routes/authDevices.js`,
`backend/middleware/{dpop,stepUp,verifyToken}.js`, `backend/services/auth*.js`,
`backend/socket/chatSocketio.js`, `backend/config/authPolicy.js`,
`backend/database/migrations/160_auth_devices.sql`. Fix every real issue, add a
regression test, run `pnpm test`.

## Findings (severity → status)

| # | Severity | Title | Status |
|---|---|---|---|
| S1 | HIGH | `/api/users/refresh` — unauthenticated session kill + forged security alerts via the `sessionId` body hint | FIXING |
| S2 | MEDIUM-HIGH | `/api/users/refresh` legacy adoption can **rotate an existing device binding** (revokes the victim's bound sessions, wipes the enrolled step-up key) | FIXING |
| S3 | MEDIUM | `/api/users/logout` scope=local resolves the caller *after* revoking the access token ⇒ registry side effects never run in production | FIXING |
| S4 | LOW-MED | TOKEN_REUSE detection depends only on a GoTrue error-message regex | FIXING |

Detail is written up below as each fix lands.

## Log (append-only)

- 2026-08-19: report created.
- 2026-08-19: read CONTRACT.md, WORKLOG IMPLEMENTATION section, conformance.md,
  and the whole backend surface: `config/authPolicy.js`,
  `database/migrations/160_auth_devices.sql`, `middleware/dpop.js`,
  `middleware/stepUp.js`, `middleware/verifyToken.js`,
  `middleware/optionalAuth.js`, `middleware/csrfProtection.js`,
  `services/authSessionService.js`, `services/authDeviceService.js` (all 1521
  lines), `services/authNotifyService.js`, `routes/authDevices.js`,
  the persistent-login hooks in `routes/users.js` (/login, /reauthenticate,
  /password, /refresh, /oauth/token, /oauth/callback, /oauth/native,
  DELETE /account, /logout), `socket/chatSocketio.js`, `app.js` wiring.
  Four real defects found (S1–S4); fixes in progress.
- 2026-08-19: S1 + S2 + S3 + S4 fixed; `npx jest tests/authDpop tests/authStepUp
  tests/authDeviceService tests/authDevicesRoutes tests/authUsersHooks` →
  **5 suites / 214 tests / 0 failures**. Each fix has a regression test; S3's was
  verified to FAIL against the pre-fix code before being kept.
  Files changed so far:
  * `backend/services/authDeviceService.js` — `checkRefresh` gains
    `tokenResolved`; `upsertDeviceForKey` gains `allowRebind`; the adoption call
    site is now `interactive:false, allowRebind:false`.
  * `backend/routes/users.js` — `isRefreshReuseError()`; reuse/foreign-token
    punishment gated on `tokenResolved`; `/logout` local resolves proof (a)
    before revoking the JWT.
  * `backend/tests/authUsersHooks.test.js` — 8 new/updated tests (S1 ×5, S2 ×2, S3 ×1).
- 2026-08-19: S5 fixed (credential values echoed + logged by `middleware/validate.js`)
  and DPoP key-confusion coverage widened.
  * `backend/middleware/validate.js` — `rejectedValue` (and any Joi message that
    quotes the value) is `[redacted]` for credential-bearing leaf field names.
  * `backend/routes/users.js` — new `reauthAccountLimiter` (per-account 10/15 m)
    mounted on `/reauthenticate` and `/password` next to the existing per-IP one.
  * `backend/tests/unit/validateRedaction.test.js` — NEW, 13 tests.
  * `backend/tests/authDpop.test.js` — oct/HS256, alg:none, P-384, RSA jwk and a
    cross-path replay that must not burn the legitimate jti (38 tests total).
