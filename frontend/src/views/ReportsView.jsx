import { useEffect, useMemo, useRef, useState } from 'react';
import WorldMap, { REGIONS } from '../overview/WorldMap.jsx';
import { apiBase, settingsUrl } from '../api.js';
import { fmtTime } from '../lib/format.js';
import { locatorCenter } from '../lib/maidenhead.js';
import { useCallsignFlags, useMetaToggle } from '../lib/locatorMeta.js';

const MAX_ROWS = 500;

// Which side of the report the entered callsign sits on.
const ROLES = [
  { key: 'both', label: 'Both', title: 'Reports where the call is sender or receiver' },
  { key: 'sender', label: 'Heard', title: 'Reports where the call was heard by others (it is the sender)' },
  { key: 'receiver', label: 'Hearing', title: 'Reports where the call heard others (it is the receiver)' },
];
const LEVELS = [
  { key: 'field', label: 'Field' },
  { key: 'square', label: 'Square' },
];

const FIELD_RE = /^[A-R]{2}/;
const SQUARE_RE = /^[A-R]{2}[0-9]{2}/;

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

/** The locator of the *other* station in a report, relative to the watched call. */
function counterpartGrid(d, watched) {
  const sc = (d.sc || '').toUpperCase();
  const rc = (d.rc || '').toUpperCase();
  if (watched && watched === sc) return d.rl; // call is sender → where it was heard
  if (watched && watched === rc) return d.sl; // call is receiver → where it heard from
  return d.rl || d.sl;
}

/** Field (2 char) or square (4 char) bucket key for a raw locator, or null. */
function cellKey(grid, level) {
  if (!grid) return null;
  const g = grid.toUpperCase();
  if (level === 'square') return SQUARE_RE.test(g) ? g.slice(0, 4) : null;
  return FIELD_RE.test(g) ? g.slice(0, 2) : null;
}

/**
 * Diagnostics view: subscribe to the PSKReporter public MQTT gateway for one
 * callsign and show its reports live — both as a scrolling list and as a world
 * map of the Maidenhead cells where the call was received (or heard from).
 *
 * Streams from the backend relay at GET /api/mqtt/reports (SSE); payload keys:
 * sc/rc (calls), sl/rl (grids), rp (SNR), f (Hz), b (band), md (mode), t (unix).
 */
export default function ReportsView() {
  const [call, setCall] = useState('');
  const [role, setRole] = useState('both');
  const [active, setActive] = useState(false);
  const [status, setStatus] = useState('idle'); // idle | connecting | live | error
  const [reports, setReports] = useState([]);
  const [watching, setWatching] = useState(''); // the call actually subscribed to
  const [level, setLevel] = useState(() => loadPref('rp.level', 'field'));
  const [region, setRegion] = useState(() => loadPref('rp.region', 'world'));
  const [showFlags, setShowFlags] = useMetaToggle('feed.showMeta');
  const [home, setHome] = useState(null);
  const esRef = useRef(null);

  // Own-station marker, from the configured locator.
  useEffect(() => {
    fetch(settingsUrl)
      .then((r) => r.json())
      .then((s) => setHome(locatorCenter(s.locator)))
      .catch(() => {});
  }, []);

  const stop = () => {
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }
    setActive(false);
    setStatus('idle');
  };

  const start = () => {
    const c = call.trim().toUpperCase();
    if (!c) return;
    stop();
    setReports([]);
    setWatching(c);
    setActive(true);
    setStatus('connecting');
    const url = `${apiBase}/mqtt/reports?call=${encodeURIComponent(c)}&role=${role}`;
    const es = new EventSource(url);
    esRef.current = es;
    es.addEventListener('subscribed', () => setStatus('live'));
    es.addEventListener('report', (ev) => {
      try {
        const d = JSON.parse(ev.data);
        setReports((prev) => [d, ...prev].slice(0, MAX_ROWS));
      } catch {
        /* ignore malformed */
      }
    });
    es.onerror = () => setStatus('error');
  };

  // Close the stream on unmount so the backend unsubscribes the topic.
  useEffect(() => () => stop(), []);

  const submit = (e) => {
    e.preventDefault();
    if (active) stop();
    else start();
  };

  const pick = (setter, key) => (value) => {
    setter(value);
    savePref(key, value);
  };
  const changeLevel = pick(setLevel, 'rp.level');
  const changeRegion = pick(setRegion, 'rp.region');

  // Heat cells: the counterpart locator of each report, bucketed by field/square.
  const counts = useMemo(() => {
    const out = {};
    for (const d of reports) {
      const key = cellKey(counterpartGrid(d, watching), level);
      if (key) out[key] = (out[key] || 0) + 1;
    }
    return out;
  }, [reports, watching, level]);

  const cellCount = Object.keys(counts).length;
  const mapTitle =
    watching && role === 'sender'
      ? `Where ${watching} was received`
      : watching && role === 'receiver'
        ? `Where ${watching} is hearing from`
        : watching
          ? `Stations exchanging with ${watching}`
          : 'Reception map';

  // Reuse the shared flag resolver by feeding it decode-shaped rows.
  const pseudoDecodes = useMemo(
    () => reports.map((d) => ({ msg: `${d.sc || ''} ${d.rc || ''}` })),
    [reports],
  );
  const flagFor = useCallsignFlags(pseudoDecodes, showFlags);

  const callName = (c) => {
    const f = showFlags ? flagFor(c) : null;
    return (
      <span className="flag">
        {f && <span title={f.country}>{f.flag} </span>}
        {c}
      </span>
    );
  };

  const statusText =
    status === 'live'
      ? `live · ${reports.length}`
      : status === 'connecting'
        ? 'connecting…'
        : status === 'error'
          ? 'error'
          : '';

  return (
    <main className="reports-view">
      <form className="mqtt-controls" onSubmit={submit}>
        <input
          className="filter-input"
          value={call}
          onChange={(e) => setCall(e.target.value)}
          placeholder="Callsign — e.g. DL1ABC"
          spellCheck={false}
          autoCapitalize="characters"
          aria-label="Callsign to watch"
        />
        <div className="seg">
          {ROLES.map((r) => (
            <button type="button" key={r.key} title={r.title} className={role === r.key ? 'active' : ''} onClick={() => setRole(r.key)}>
              {r.label}
            </button>
          ))}
        </div>
        <button type="submit" className={`mqtt-start ${active ? 'on' : ''}`} disabled={!active && !call.trim()}>
          {active ? 'Stop' : 'Watch'}
        </button>
        {statusText && <span className={`mqtt-status ${status}`}>{statusText}</span>}
      </form>

      <section className="reports-map">
        <div className="map-controls">
          <h3>{mapTitle}</h3>
          <div className="reports-map-tools">
            <span className="map-caption">
              {reports.length} reports · {cellCount} {level === 'square' ? 'squares' : 'fields'}
            </span>
            <div className="seg">
              {LEVELS.map((l) => (
                <button key={l.key} className={level === l.key ? 'active' : ''} onClick={() => changeLevel(l.key)}>
                  {l.label}
                </button>
              ))}
            </div>
            <div className="seg">
              {Object.entries(REGIONS).map(([key, r]) => (
                <button key={key} className={region === key ? 'active' : ''} onClick={() => changeRegion(key)}>
                  {r.label}
                </button>
              ))}
            </div>
          </div>
        </div>
        <WorldMap counts={counts} region={region} home={home} />
      </section>

      <section className="stats-panel reports-list-panel">
        <div className="feed-head">
          <h3>Live reports · PSKReporter MQTT</h3>
          <label className="meta-toggle">
            <input type="checkbox" checked={showFlags} onChange={(e) => setShowFlags(e.target.checked)} />
            Flags
          </label>
        </div>
        <ul className="mqtt-list">
          {active && reports.length === 0 && (
            <li className="mqtt-empty">Waiting for reports — PSKReporter delivers them in bursts, so this can take a few minutes.</li>
          )}
          {!active && reports.length === 0 && (
            <li className="mqtt-empty">Enter a callsign and press Watch to stream its live PSKReporter spots and map.</li>
          )}
          {reports.map((d, i) => (
            <li className="mqtt-row" key={`${d.t || ''}-${d.sc || ''}-${d.rc || ''}-${i}`}>
              <span className="mqtt-time">{fmtTime({ unix: d.t })}</span>
              <span className="mqtt-path">
                {callName(d.sc)} <span className="mqtt-arrow">→</span> {callName(d.rc)}
              </span>
              <span className="mqtt-tags">
                {d.b && <span className="mqtt-band">{d.b}</span>}
                {d.md && <span className="mqtt-mode">{d.md}</span>}
                {d.rp != null && <span className="mqtt-snr">{d.rp > 0 ? `+${d.rp}` : d.rp} dB</span>}
                {(d.sl || d.rl) && (
                  <span className="mqtt-grid">
                    {d.sl || '·'}→{d.rl || '·'}
                  </span>
                )}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
