import { createContext, useContext, useEffect, useState } from 'react';
import { controlStreamUrl, controlStatusUrl } from '../api.js';

// Keep a decent history so decodes survive band changes and view switches.
const MAX_DECODES = 500;

const ControlContext = createContext(null);

/**
 * Owns the single app-wide SSE connection to /api/control/stream and the rolling
 * list of WSJT-X decodes + latest status, so they keep accumulating and are not
 * lost when the user switches away from and back to the Control view.
 */
export function ControlProvider({ children }) {
  const [status, setStatus] = useState({ up: false });
  const [decodes, setDecodes] = useState([]);

  useEffect(() => {
    let active = true;
    fetch(controlStatusUrl)
      .then((r) => (r.ok ? r.json() : null))
      .then((s) => active && s && setStatus(s))
      .catch(() => {});

    const es = new EventSource(controlStreamUrl);
    es.addEventListener('status', (ev) => {
      try {
        setStatus(JSON.parse(ev.data));
      } catch {
        /* ignore */
      }
    });
    es.addEventListener('decode', (ev) => {
      try {
        const d = JSON.parse(ev.data);
        setDecodes((prev) => [d, ...prev].slice(0, MAX_DECODES));
      } catch {
        /* ignore */
      }
    });
    return () => {
      active = false;
      es.close();
    };
  }, []);

  return <ControlContext.Provider value={{ status, decodes }}>{children}</ControlContext.Provider>;
}

/** Access the live WSJT-X control feed: `{ status, decodes }`. */
export function useControl() {
  const ctx = useContext(ControlContext);
  if (!ctx) {
    throw new Error('useControl must be used within a ControlProvider');
  }
  return ctx;
}
