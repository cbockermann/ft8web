// Single source of truth for the Control-view filter documentation.
//
// Kept in step with `ctxFor` in filterExpr.js (the fields a decode exposes) and
// with the grammar in that file. Both the inline "instant help" panel and the
// standalone help page render from these tables, so the docs can't drift from
// one place to the other.

/** Testable properties — one row per field the evaluator resolves. */
export const FILTER_FIELDS = [
  { name: 'snr', alias: 'db', type: 'number (dB)', desc: 'Signal-to-noise ratio of the decode.', example: 'snr > -15' },
  { name: 'dt', type: 'number (s)', desc: 'Time offset of the decode.', example: 'dt < 1' },
  { name: 'df', type: 'number (Hz)', desc: 'Audio offset within the passband.', example: 'df > 1500' },
  { name: 'band', type: 'string', desc: 'Band label tagged at decode time, e.g. 20m.', example: 'band == "20m"' },
  { name: 'mode', type: 'string', desc: 'Decode mode (FT8 shows as ~).', example: 'mode == "~"' },
  { name: 'message', type: 'string', desc: 'The full decoded message text.', example: 'message matches "*73*"' },
  { name: 'cq', alias: 'is_cq', type: 'boolean', desc: 'True when the message is a CQ call.', example: 'cq' },
  { name: 'call', type: 'string', desc: "The subject callsign — the station you'd work.", example: 'call matches "HB9*"' },
  { name: 'grid', type: 'string', desc: 'Their Maidenhead grid from the message, or empty.', example: 'grid matches "JO*"' },
  { name: 'cc', type: 'string', desc: 'ISO country code of the subject (DE, FR, GB, …).', example: 'cc in ("DE","FR","GB")' },
  { name: 'country', type: 'string', desc: 'Country name of the subject (e.g. Germany).', example: 'country == "Germany"' },
  { name: 'worked', type: 'boolean', desc: 'You have logged a QSO with this call before.', example: 'not worked' },
  { name: 'new', type: 'boolean', desc: 'Never worked (the opposite of worked).', example: 'new' },
  { name: 'last_worked', type: 'duration', desc: 'Time since you last worked this call (never → very large).', example: 'last_worked > 30days' },
  { name: 'worked_count', type: 'number', desc: "How many QSOs you've logged with this call.", example: 'worked_count == 0' },
  { name: 'distance', type: 'number (km)', desc: 'Great-circle distance to their grid (needs your locator).', example: 'distance < 2000' },
  { name: 'directed_at_me', type: 'boolean', desc: 'The message is addressed to your station.', example: 'directed_at_me' },
  { name: 'age', type: 'duration', desc: 'Time since this call was first seen this session.', example: 'age < 5min' },
];

/** Operators and forms the grammar accepts. */
export const FILTER_OPERATORS = [
  { sym: 'and  or  not', desc: 'Boolean logic. not binds tighter than and, and tighter than or.' },
  { sym: '( … )', desc: 'Grouping.' },
  { sym: '>  <  >=  <=', desc: 'Numeric comparison.' },
  { sym: '==  !=', desc: 'Equality. For strings it is case-insensitive.' },
  { sym: 'in ( a, b, … )', desc: 'Membership — true if the field equals any listed value (case-insensitive).' },
  { sym: 'matches "glob"', desc: 'Glob match: * = any run, ? = one char. Case-insensitive.' },
];

/** Things that trip people up, stated once. */
export const FILTER_NOTES = [
  'Boolean fields (worked, new, cq, directed_at_me) can be used on their own, e.g. cq and new.',
  'Durations: 30s · 5min · 2h · 10days (units: s, min/m, h, days/d). Compare against last_worked and age.',
  'Strings go in "double" or \'single\' quotes; text comparisons ignore case.',
  'Negative numbers are fine: snr > -20.',
  'An empty filter shows everything.',
];

/** Ready-to-use expressions. */
export const FILTER_EXAMPLES = [
  { expr: 'snr > -15 and not worked', desc: 'Readable stations you have never worked.' },
  { expr: 'cq and new', desc: 'CQ calls from new stations.' },
  { expr: 'cq and (new or last_worked > 30days)', desc: 'CQs worth answering again.' },
  { expr: 'cc in ("DE","FR","GB") and db >= -18', desc: 'Strong signals from DE / FR / GB.' },
  { expr: 'call matches "HB9*"', desc: 'Any Swiss HB9 station.' },
  { expr: 'directed_at_me', desc: 'Messages addressed to your own station.' },
  { expr: 'distance < 2000 and new', desc: 'Nearby stations you have not worked.' },
];
