import { useMemo, useState } from 'react';

// Callsign filter: several space-separated terms; each term is a literal
// callsign or a glob with `*` (any run) and `?` (single char). A decode matches
// when any token of its message matches any term (case-insensitive).

function globToRegExp(glob) {
  const escaped = glob.replace(/[.+^${}()|[\]\\]/g, '\\$&'); // escape regex, keep * ?
  const body = escaped.replace(/\*/g, '.*').replace(/\?/g, '.');
  return new RegExp(`^${body}$`, 'i');
}

/** Parse the filter string into a list of compiled patterns (empty = match all). */
export function parseFilter(input) {
  return (input || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map(globToRegExp);
}

/** Does a message contain a token matching any pattern? Empty patterns = yes. */
export function matchesFilter(msg, patterns) {
  if (!patterns || patterns.length === 0) return true;
  if (!msg) return false;
  const tokens = msg.trim().split(/\s+/);
  return patterns.some((re) => tokens.some((t) => re.test(t)));
}

function loadPref(key) {
  try {
    return localStorage.getItem(key) ?? '';
  } catch {
    return '';
  }
}
function savePref(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}

/**
 * Stateful callsign filter persisted under `storageKey`. Returns the current
 * value, a setter, and `matches(msg)` predicate.
 */
export function useCallsignFilter(storageKey) {
  const [value, setValue] = useState(() => loadPref(storageKey));
  const patterns = useMemo(() => parseFilter(value), [value]);
  const matches = useMemo(() => (msg) => matchesFilter(msg, patterns), [patterns]);
  const update = (v) => {
    setValue(v);
    savePref(storageKey, v);
  };
  return { value, setValue: update, matches, active: patterns.length > 0 };
}
