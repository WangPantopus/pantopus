export const meta = {
  name: 'calendarly-parity-fix',
  description: 'Apply confirmed iOS/Android Calendarly parity findings per-cluster with token-safe surgical edits',
  phases: [
    { title: 'Fix', detail: 'per-cluster: re-verify each finding then apply token-safe edits' },
    { title: 'Report', detail: 'consolidate applied / skipped / deferred / shared-file TODOs' },
  ],
}

const PLAN = '/tmp/calendarly_fix_plan_full.json'
const IOS_ROOT = '/Users/yingpengwang/pantopus/native/pantopus/frontend/apps/ios/Pantopus'
const AND_ROOT = '/Users/yingpengwang/pantopus/native/pantopus/frontend/apps/android/app/src/main/java/app/pantopus/android'

const CLUSTERS = [
  'setup-hub', 'event-types', 'availability', 'booking-page', 'invitee-discovery',
  'invitee-confirm', 'invitee-edge', 'bookings-core', 'bookings-extras', 'home-calendar',
  'find-a-time', 'home-resources', 'business-config', 'payments', 'packages-invoices',
  'automations', 'insights-polish', 'misc',
]

const RULES = [
  'TOKEN / GUARD RULES (CI rejects violations — non-negotiable):',
  '  iOS (Features/**): colors via Theme.Color.<token> (NO raw hex); spacing via Spacing.s1..s16; radii via Radii.*; icons via Icon(.case, size:, strokeWidth:, color:) (NEVER Image(systemName:)); type via .font(.system(size:weight:)).',
  '  Android (ui/screens/**): colors via PantopusColors.* / PantopusTheme.tokens.* (NO Color(0xFF..)); spacing via Spacing.*; radii via Radii.*; icons via PantopusIconImage(PantopusIcon.X,...) (NEVER Icons.Filled.* / painterResource(R.drawable.ic_lucide_*)); NO bare .dp literals in ui/screens/** (use Spacing/Radii or a named private val).',
  '  Keep accessibilityIdentifier(iOS) <-> Modifier.testTag(Android) names mirrored; keep the same backend endpoint + query params.',
  '  PILLAR ACCENT must be OWNER-DERIVED — never hardcode Personal/sky / Home-green / Business-violet. Operational/host CTAs (Approve, FAB, dock primary on bookings inbox/detail) use the FIXED primary blue token (#0284c7 == Theme.Color.primary600 / PantopusColors primary), not the pillar accent.',
].join('\n')

const POLICY = [
  'FILE-OWNERSHIP (prevents parallel-edit collisions — STRICT):',
  '  - Edit ONLY the screen/component/viewmodel/helper files implicated by YOUR findings (follow each finding\'s evidence file:line).',
  '  - DO NOT edit any navigation router or route file: SchedulingRouter.swift, SchedulingRoute.swift, RootTabScreen.kt, *Routes.kt, or app-wide DI/theme files. If a fix needs one, add it to sharedTodos[] (exact file + precise change + why) and implement only the non-router part. The main loop applies router/shared edits serially.',
  '  - A helper used ONLY by your cluster (a *Kit.swift / *Support.kt / *Components.kt local to your feature) IS yours to edit.',
].join('\n')

const METHOD = [
  'METHOD — for EACH finding in your cluster:',
  '  1. RE-VERIFY: open the cited file at the cited line; confirm the defect still exists in the CURRENT code. If it does not (mis-read, already fixed, or a snapshot-scope artifact like a body-only render), put it in skipped[] with the reason — do NOT edit.',
  '  2. DEFER (never fabricate): if it needs net-new design, backend, or invented data/members/slots (defer==true, or titles like "not implemented on either", group-event frames, offer-slots engine, lifecycle states) put it in deferred[]. Do NOT invent data or whole new screens.',
  '  3. DIVERGENCE (platform=="both"): change the side that diverges from the DESIGN frame (the evidence/recommendation usually names the design-correct side). If the fix is a pure presentation-modality swap (sheet<->full-screen) that would require router changes, prefer sharedTodos[] for the router part; if too risky to do surgically, defer with a clear reason.',
  '  4. APPLY otherwise: smallest correct edit honoring the token/guard rules. Surgical only — do not rewrite whole screens. After editing, sanity-check braces/imports/symbol names so the file still compiles.',
  '  Record each handled finding in applied[] / skipped[] / deferred[] with the files touched and a one-line note.',
].join('\n')

function fixPrompt(clusterId) {
  return [
    'You are fixing confirmed Calendarly design-parity findings for cluster "' + clusterId + '".',
    'iOS root: ' + IOS_ROOT + '  |  Android root: ' + AND_ROOT,
    '',
    'STEP 0: Read the plan file ' + PLAN + ' (Bash: `python3 -c "import json;d=json.load(open(\'' + PLAN + '\'));[print(json.dumps(x)) for x in d[\'clusters\'].get(\'' + clusterId + '\',[])]"`). Each line is one finding with: platform, screen, designId, severity, axis, title, evidence (file:line + what), recommendation, defer.',
    '',
    RULES, '', POLICY, '', METHOD,
    '',
    'Be thorough: handle EVERY finding in your cluster (fix, skip, or defer — none left unaddressed). Prefer many small correct edits over a few big risky ones. Return the structured report.',
  ].join('\n')
}

const FIX_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['cluster', 'applied', 'skipped', 'deferred', 'sharedTodos'],
  properties: {
    cluster: { type: 'string' },
    applied: { type: 'array', items: {
      type: 'object', additionalProperties: false, required: ['title', 'platform', 'files', 'note'],
      properties: { title: { type: 'string' }, platform: { type: 'string' }, files: { type: 'array', items: { type: 'string' } }, note: { type: 'string' } } } },
    skipped: { type: 'array', items: {
      type: 'object', additionalProperties: false, required: ['title', 'reason'],
      properties: { title: { type: 'string' }, reason: { type: 'string' } } } },
    deferred: { type: 'array', items: {
      type: 'object', additionalProperties: false, required: ['title', 'reason'],
      properties: { title: { type: 'string' }, reason: { type: 'string' } } } },
    sharedTodos: { type: 'array', items: {
      type: 'object', additionalProperties: false, required: ['file', 'change', 'why'],
      properties: { file: { type: 'string' }, change: { type: 'string' }, why: { type: 'string' } } } },
  },
}

phase('Fix')
const WAVE = 3
const out = []
for (let i = 0; i < CLUSTERS.length; i += WAVE) {
  const batch = CLUSTERS.slice(i, i + WAVE)
  const r = await parallel(batch.map(id => () =>
    agent(fixPrompt(id), { label: 'fix:' + id, phase: 'Fix', schema: FIX_SCHEMA })
      .catch(() => ({ cluster: id, applied: [], skipped: [], deferred: [], sharedTodos: [], _error: true }))
  ))
  out.push(...r.filter(Boolean))
  const ap = out.flatMap(o => o.applied || []).length
  log('fix wave ' + (Math.floor(i / WAVE) + 1) + '/' + Math.ceil(CLUSTERS.length / WAVE) + ' done — ' + ap + ' edits so far')
}

phase('Report')
const applied = out.flatMap(o => o.applied || [])
const skipped = out.flatMap(o => o.skipped || [])
const deferred = out.flatMap(o => o.deferred || [])
const sharedTodos = out.flatMap(o => o.sharedTodos || [])
log('TOTAL applied ' + applied.length + ' | skipped ' + skipped.length + ' | deferred ' + deferred.length + ' | sharedTodos ' + sharedTodos.length)

return { perCluster: out, applied, skipped, deferred, sharedTodos }
