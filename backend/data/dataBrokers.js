/**
 * Unlisted (Wave 4) — the data-broker registry.
 *
 * WHAT THIS IS: the sites that republish US county property records, and
 * the exact, verified path to remove yourself from each. It is the
 * actionable half of Unlisted — the value was never confirming that
 * you are listed, it is the removal path.
 *
 * WHY WE DO NOT SCAN: the obvious build is querying ~30 people-search
 * sites with the user's address and reporting what comes back. We
 * deliberately do not, for a reason that outranks the others: querying
 * a people-search site with someone's address DISCLOSES that address to
 * that broker. A scan meant to reduce exposure would create it. (It is
 * also legally grey, brittle against blocking, slow enough to kill the
 * conversion it exists to drive, and needs the permanent scraper
 * staffing that is the stated reason the erase tier waits.)
 *
 * THEREFORE THE COPY RULE, enforced by the shape of this file: nothing
 * here asserts that a given person IS listed anywhere. There is no
 * `found` field and there never should be. Every entry describes what a
 * site publishes and how to leave it — both true without querying
 * anyone.
 *
 * EVERY FIELD MUST BE VERIFIED. A wrong opt-out URL is worse than no
 * entry: it sends someone frightened enough to be doing this to a dead
 * end, or to a form that harvests more data than it removes. Each entry
 * carries `source_url` (the page that was actually fetched) and
 * `verified_at`. An entry that cannot be verified is omitted, not
 * guessed — a short accurate list beats a long invented one.
 *
 * MAINTENANCE: brokers move their opt-out pages. `verified_at` is the
 * honesty marker; the UI should say when the list was last checked, and
 * entries should be re-verified on a schedule rather than assumed good
 * forever.
 */

/**
 * @typedef {object} DataBroker
 * @property {string}   id          kebab-case slug, stable — UnlistedRemoval rows key on it
 * @property {string}   name
 * @property {'people_search'|'background_check'|'property_records'|'marketing'} category
 * @property {string[]} exposes     what the site publishes
 * @property {string}   opt_out_url the exact page a person should start at
 * @property {'web_form'|'email'|'phone'|'mail'|'account_required'} method
 * @property {boolean}  requires_id does it demand a government ID or photo?
 * @property {boolean}  requires_email
 * @property {number}   typical_days stated processing time; 0 when unstated
 * @property {string}   note        one honest sentence the person needs
 * @property {string}   source_url  the page this was verified against
 * @property {string}   verified_at ISO date
 */

/** @type {DataBroker[]} */
const DATA_BROKERS = [
  // Populated exclusively from verified research — see the header. Each
  // entry must carry a source_url that was actually fetched.
];

/** Sites grouped for the UI: the order a person should work through them. */
const CATEGORY_ORDER = ['people_search', 'background_check', 'property_records', 'marketing'];

const CATEGORY_LABELS = {
  people_search: 'People-search sites',
  background_check: 'Background-check sites',
  property_records: 'Property-record aggregators',
  marketing: 'Marketing data brokers',
};

/** What each `exposes` token means, in the person's own terms. */
const EXPOSURE_LABELS = {
  home_address: 'Home address',
  phone: 'Phone number',
  email: 'Email address',
  relatives: 'Relatives and household members',
  age: 'Age or date of birth',
  prior_addresses: 'Previous addresses',
  property_value: 'What your home is worth',
};

module.exports = {
  DATA_BROKERS,
  CATEGORY_ORDER,
  CATEGORY_LABELS,
  EXPOSURE_LABELS,
};
