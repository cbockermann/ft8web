import { useEffect, useRef, useState } from 'react';
import { apiBase } from '../api.js';
import { gridOf } from './maidenhead.js';

const MAX_PER_REQUEST = 100;

function loadBool(key, fallback) {
  try {
    const v = localStorage.getItem(key);
    return v == null ? fallback : v === 'true';
  } catch {
    return fallback;
  }
}
function saveBool(key, value) {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    /* ignore */
  }
}

/** Persisted on/off toggle (shared across the feeds via one storage key). */
export function useMetaToggle(storageKey = 'feed.showMeta') {
  const [enabled, setEnabled] = useState(() => loadBool(storageKey, false));
  const toggle = (v) => {
    setEnabled(v);
    saveBool(storageKey, v);
  };
  return [enabled, toggle];
}

/**
 * Resolve each decode's locator to its country metadata ({ country, cc, flag })
 * via GET /api/locators, cached by grid. Returns `metaFor(msg)` → meta or null.
 * Only fetches while `enabled`.
 */
export function useLocatorMeta(decodes, enabled) {
  const cache = useRef(new Map()); // grid -> meta | null (null = resolved to nothing / pending)
  const [, bump] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    const missing = [];
    for (const d of decodes) {
      const g = gridOf(d.msg);
      if (g && !cache.current.has(g)) missing.push(g);
    }
    if (missing.length === 0) return;
    const batch = missing.slice(0, MAX_PER_REQUEST);
    batch.forEach((g) => cache.current.set(g, null)); // mark pending to avoid refetch
    fetch(`${apiBase}/locators?grids=${encodeURIComponent(batch.join(','))}`)
      .then((r) => (r.ok ? r.json() : {}))
      .then((map) => {
        for (const g of batch) {
          const info = map[g];
          cache.current.set(g, info && info.country ? { country: info.country, cc: info.cc, flag: info.flag } : null);
        }
        bump((x) => x + 1);
      })
      .catch(() => {});
  }, [decodes, enabled]);

  return (msg) => {
    if (!enabled) return null;
    const g = gridOf(msg);
    return g ? cache.current.get(g) ?? null : null;
  };
}
