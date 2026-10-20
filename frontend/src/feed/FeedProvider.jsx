import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { streamUrl, recentUrl } from '../api.js';

// How many recent decodes to keep in memory for the live feed, and to seed on
// (re)load so the feed is not empty after a reload.
const MAX_FEED = 300;

const FeedContext = createContext(null);

/**
 * Owns the single app-wide SSE connection to /api/stream and the rolling list
 * of recent decodes, so the feed keeps running while the user switches views.
 */
export function FeedProvider({ children }) {
  const [decodes, setDecodes] = useState([]);
  const [connected, setConnected] = useState(false);
  const seen = useRef(new Set());

  useEffect(() => {
    let active = true;

    // Seed the feed with a backlog so a reload isn't empty; merge with any live
    // decodes that already arrived (de-duped), newest first.
    fetch(recentUrl(MAX_FEED))
      .then((r) => (r.ok ? r.json() : []))
      .then((list) => {
        if (!active || !Array.isArray(list)) return;
        setDecodes((prev) => {
          const merged = [...prev];
          for (const d of list) {
            if (!seen.current.has(d.id)) {
              seen.current.add(d.id);
              merged.push(d);
            }
          }
          merged.sort((a, b) => (b.unix || 0) - (a.unix || 0));
          return merged.slice(0, MAX_FEED);
        });
      })
      .catch(() => {});

    const es = new EventSource(streamUrl);
    es.onopen = () => setConnected(true);
    es.onerror = () => setConnected(false);
    es.addEventListener('decode', (ev) => {
      const d = JSON.parse(ev.data);
      if (seen.current.has(d.id)) return;
      seen.current.add(d.id);
      // Keep the de-dupe set from growing without bound over long sessions.
      if (seen.current.size > MAX_FEED * 4) {
        seen.current = new Set([d.id]);
      }
      setDecodes((prev) => [d, ...prev].slice(0, MAX_FEED));
    });
    return () => {
      active = false;
      es.close();
    };
  }, []);

  return (
    <FeedContext.Provider value={{ decodes, connected }}>
      {children}
    </FeedContext.Provider>
  );
}

/** Access the live feed: `{ decodes, connected }`. */
export function useFeed() {
  const ctx = useContext(FeedContext);
  if (!ctx) {
    throw new Error('useFeed must be used within a FeedProvider');
  }
  return ctx;
}
