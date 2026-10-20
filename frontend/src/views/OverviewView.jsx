import { useEffect, useRef, useState } from 'react';
import WorldMap, { REGIONS } from '../overview/WorldMap.jsx';
import { useCellHeat } from '../overview/useCellHeat.js';
import { useFeed } from '../feed/FeedProvider.jsx';
import { fmtTime } from '../lib/format.js';
import { locatorCenter } from '../lib/maidenhead.js';
import { useCallsignFilter } from '../lib/callsignFilter.js';
import { useLocatorMeta, useCallsignFlags, useMetaToggle } from '../lib/locatorMeta.js';
import FilterInput from '../components/FilterInput.jsx';
import MetaSpan from '../components/MetaSpan.jsx';
import MessageText from '../components/MessageText.jsx';
import { settingsUrl } from '../api.js';

const WINDOW_OPTIONS = [5, 10, 15, 30, 60]; // minutes; capped server-side
const RESOLUTIONS = [
  { key: 'field', label: 'Field' },
  { key: 'square', label: 'Square' },
];
const MINI_FEED = 15;

// Small per-viewer preferences; safe to lose.
function loadPref(key, fallback) {
  try {
    const v = localStorage.getItem(key);
    return v == null ? fallback : v;
  } catch {
    return fallback;
  }
}
function savePref(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}

// Min height for the map and the feed panels when dragging the divider.
const MIN_MAP = 160;
const MIN_FEED = 120;
const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), hi);

export default function OverviewView() {
  const [minutes, setMinutes] = useState(() => Number(loadPref('ov.minutes', '15')));
  const [region, setRegion] = useState(() => loadPref('ov.region', 'world'));
  const [home, setHome] = useState(null);
  const overviewRef = useRef(null);
  const [mapHeight, setMapHeight] = useState(() => {
    const v = loadPref('ov.mapHeight', '');
    return v ? Number(v) : null;
  });
  // Resolution: a per-viewer override (if set) wins over the server default.
  const [resOverride, setResOverride] = useState(() => loadPref('ov.resolution', ''));
  const [resDefault, setResDefault] = useState('square');
  const resolution = resOverride || resDefault;

  const counts = useCellHeat(minutes, resolution);
  const { decodes } = useFeed();
  const filter = useCallsignFilter('ov.feedFilter');
  const [showMeta, setShowMeta] = useMetaToggle('feed.showMeta');
  const [showFlags, setShowFlags] = useMetaToggle('feed.showFlags');
  const shownDecodes = decodes.filter((d) => filter.matches(d.msg)).slice(0, MINI_FEED);
  const metaFor = useLocatorMeta(shownDecodes, showMeta);
  const flagFor = useCallsignFlags(shownDecodes, showFlags);

  // Own station marker + default grid resolution from the server settings.
  useEffect(() => {
    fetch(settingsUrl)
      .then((r) => r.json())
      .then((s) => {
        setHome(locatorCenter(s.locator));
        if (s.gridResolution) setResDefault(s.gridResolution);
      })
      .catch(() => {});
  }, []);

  const total = Object.values(counts).reduce((a, b) => a + b, 0);

  const changeMinutes = (m) => {
    setMinutes(m);
    savePref('ov.minutes', String(m));
  };
  const changeRegion = (r) => {
    setRegion(r);
    savePref('ov.region', r);
  };
  const changeResolution = (r) => {
    setResOverride(r);
    savePref('ov.resolution', r);
  };

  // Default the split to ~62% of the available height, and keep it in range.
  useEffect(() => {
    const el = overviewRef.current;
    if (!el) return undefined;
    const fit = () => {
      const h = el.clientHeight;
      if (h <= 0) return;
      setMapHeight((prev) => {
        const target = prev == null ? Math.round(h * 0.62) : prev;
        return clamp(target, MIN_MAP, Math.max(MIN_MAP, h - MIN_FEED));
      });
    };
    fit();
    window.addEventListener('resize', fit);
    return () => window.removeEventListener('resize', fit);
  }, []);

  const startDrag = (e) => {
    e.preventDefault();
    const el = overviewRef.current;
    const startY = e.clientY;
    const startH = mapHeight ?? el.firstChild.getBoundingClientRect().height;
    let lastH = startH;
    const move = (ev) => {
      const maxH = Math.max(MIN_MAP, el.clientHeight - MIN_FEED);
      lastH = clamp(startH + (ev.clientY - startY), MIN_MAP, maxH);
      setMapHeight(lastH);
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      savePref('ov.mapHeight', String(Math.round(lastH)));
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  const mapStyle = mapHeight == null ? undefined : { flex: '0 0 auto', height: `${mapHeight}px` };

  return (
    <main className="overview" ref={overviewRef}>
      <section className="map-panel" style={mapStyle}>
        <div className="map-controls">
          <div className="seg">
            {Object.entries(REGIONS).map(([key, r]) => (
              <button
                key={key}
                className={region === key ? 'active' : ''}
                onClick={() => changeRegion(key)}
              >
                {r.label}
              </button>
            ))}
          </div>
          <div className="seg">
            {WINDOW_OPTIONS.map((m) => (
              <button
                key={m}
                className={minutes === m ? 'active' : ''}
                onClick={() => changeMinutes(m)}
              >
                {m}m
              </button>
            ))}
          </div>
          <div className="seg">
            {RESOLUTIONS.map((r) => (
              <button
                key={r.key}
                className={resolution === r.key ? 'active' : ''}
                onClick={() => changeResolution(r.key)}
              >
                {r.label}
              </button>
            ))}
          </div>
          <span className="map-caption">
            {total} msg · {Object.keys(counts).length} cells · last {minutes} min
          </span>
        </div>
        <WorldMap counts={counts} region={region} home={home} />
      </section>

      <div
        className="v-divider"
        onPointerDown={startDrag}
        role="separator"
        aria-orientation="horizontal"
        title="Drag to resize"
      />

      <section className="mini-feed">
        <div className="feed-head">
          <h2>Recent decodes</h2>
          <div className="feed-tools">
            <label className="meta-toggle" title="Country flag next to each callsign (from the prefix)">
              <input type="checkbox" checked={showFlags} onChange={(e) => setShowFlags(e.target.checked)} />
              Flags
            </label>
            <label className="meta-toggle" title="Country/region from the locator, in parentheses">
              <input type="checkbox" checked={showMeta} onChange={(e) => setShowMeta(e.target.checked)} />
              Meta
            </label>
            <FilterInput value={filter.value} onChange={filter.setValue} />
          </div>
        </div>
        <ul className="rows">
          {decodes.length === 0 && <li className="empty">Waiting for decodes…</li>}
          {decodes.length > 0 && shownDecodes.length === 0 && (
            <li className="empty">No decodes match the filter.</li>
          )}
          {shownDecodes.map((d) => (
            <li key={d.id} className="row">
              <span className="col time">{fmtTime(d)}</span>
              <span className="col snr">{d.snr ?? ''}</span>
              {d.tags && d.tags.band && <span className="col band">{d.tags.band}</span>}
              <span className="col msg">
                <MessageText msg={d.msg} flagFor={flagFor} />
                <MetaSpan meta={metaFor(d.msg)} />
              </span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
