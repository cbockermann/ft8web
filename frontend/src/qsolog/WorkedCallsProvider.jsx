import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { qsosCallsUrl } from '../api.js';

// Refresh the worked-callsign set periodically so live-logged QSOs start marking
// up without a reload.
const REFRESH_MS = 60000;

const WorkedCallsContext = createContext({ isWorked: () => false, refresh: () => {} });

/**
 * Holds the set of callsigns we've already had a QSO with (from the QSO log) and
 * provides `isWorked(call)`, so every view that renders FT8 messages can mark
 * worked stations. Fetched app-wide once, refreshed periodically and on demand
 * (e.g. after an ADIF import).
 */
export function WorkedCallsProvider({ children }) {
  const setRef = useRef(new Set());
  const [, bump] = useState(0);

  const refresh = useCallback(() => {
    fetch(qsosCallsUrl)
      .then((r) => (r.ok ? r.json() : []))
      .then((calls) => {
        if (Array.isArray(calls)) {
          setRef.current = new Set(calls.map((c) => c.toUpperCase()));
          bump((x) => x + 1);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, REFRESH_MS);
    return () => clearInterval(t);
  }, [refresh]);

  const isWorked = useCallback((call) => !!call && setRef.current.has(call.toUpperCase()), []);

  return (
    <WorkedCallsContext.Provider value={{ isWorked, refresh }}>{children}</WorkedCallsContext.Provider>
  );
}

export function useWorkedCalls() {
  return useContext(WorkedCallsContext);
}
