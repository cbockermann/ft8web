import { useEffect, useState } from 'react';
import { apiBase } from '../api.js';

// Poll interval for the activity snapshot. The FT8 cycle is 15 s, so a few
// seconds keeps the heatmap effectively live while the backend stays the source
// of truth (and activity fades as it ages out of the window).
const POLL_MS = 5000;

/**
 * Live per-cell message counts for the last `minutes` at the given resolution
 * ('field' or 'square'), polled from GET /api/fields. Returns a { cell: count }
 * map keyed by 2-char field or 4-char square.
 */
export function useCellHeat(minutes, level) {
  const [counts, setCounts] = useState({});

  useEffect(() => {
    let active = true;
    const load = () =>
      fetch(`${apiBase}/fields?minutes=${minutes}&level=${level}`)
        .then((r) => (r.ok ? r.json() : []))
        .then((list) => {
          if (active) setCounts(Object.fromEntries(list.map((c) => [c.cell, c.count])));
        })
        .catch(() => {
          /* transient fetch error — keep the last snapshot */
        });

    load();
    const timer = setInterval(load, POLL_MS);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [minutes, level]);

  return counts;
}
