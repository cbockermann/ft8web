// Identify callsign tokens in an FT8 message (mirror of the backend Callsigns),
// to flag them and to batch-resolve their country.

const CALL_CHARS = /^[A-Z0-9/]+$/;
const GRID = /^[A-R]{2}[0-9]{2}([A-X]{2})?$/;
const REPORT = /^R?[-+]\d{1,2}$/;
const SIGNOFFS = new Set(['RRR', 'RR73', '73', 'R73', 'TU', 'TU73', 'QSL', 'DE', 'CQ', 'DX', 'QRZ']);

function unhash(t) {
  return t.length >= 2 && t[0] === '<' && t[t.length - 1] === '>' ? t.slice(1, -1) : t;
}

/** Is the token a callsign (letter+digit, not a grid/report/sign-off)? */
export function isCallsign(token) {
  const t = unhash(token);
  if (!CALL_CHARS.test(t)) return false;
  if (!/[A-Z]/.test(t) || !/[0-9]/.test(t)) return false;
  return !(SIGNOFFS.has(t) || GRID.test(t) || REPORT.test(t));
}

/** Split a message into tokens, marking which are callsigns. */
export function tokenizeMessage(msg) {
  if (!msg) return [];
  return msg.trim().split(/\s+/).map((text) => ({ text, call: isCallsign(text) ? unhash(text) : null }));
}

/** Distinct callsign tokens in a message. */
export function callTokens(msg) {
  const out = [];
  for (const t of tokenizeMessage(msg)) {
    if (t.call && !out.includes(t.call)) out.push(t.call);
  }
  return out;
}
