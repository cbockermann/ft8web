import { useEffect, useMemo, useRef, useState } from 'react';
import WorldMap, { REGIONS } from '../overview/WorldMap.jsx';
import { apiBase, settingsUrl } from '../api.js';
import { fmtTime } from '../lib/format.js';
import { locatorCenter } from '../lib/maidenhead.js';
import { useCallsignFlags, useMetaToggle } from '../lib/locatorMeta.js';
import { useWorkedCalls } from '../qsolog/WorkedCallsProvider.jsx';
import { snrColor, SNR_MIN, SNR_MAX, SNR_GRADIENT, fmtDb } from '../lib/snr.js';

// Bar-chart geometry for the per-cell hover tooltip.
const BAR_MIN_H = 3;
const BAR_MAX_H = 34;
const MAX_BARS = 120; // cap the tooltip chart; show the most recent this many
const barHeight = (db) => {
  const t = Math.max(0, Math.min(1, (db - SNR_MIN) / (SNR_MAX - SNR_MIN)));
  return BAR_MIN_H + (BAR_MAX_H - BAR_MIN_H) * t;
};

const MAX_ROWS = 500;
const OWN_SEED_MINUTES = 720; // how far back to seed the own-station history (12 h)

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
  const { isWorked } = useWorkedCalls();
  const [home, setHome] = useState(null);
  const [ownCall, setOwnCall] = useState(''); // configured station callsign, if any
  const [hover, setHover] = useState(null); // { cell, x, y } for the tooltip
  const esRef = useRef(null);
  const autoStartedRef = useRef(false);

  const stop = () => {
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }
    setActive(false);
    setStatus('idle');
  };

  // Open an SSE stream for a call/role, optionally seeded with stored history.
  const startWatch = (c, roleKey, seed = []) => {
    if (!c) return;
    stop();
    setReports(seed);
    setWatching(c);
    setActive(true);
    setStatus('connecting');
    const url = `${apiBase}/mqtt/reports?call=${encodeURIComponent(c)}&role=${roleKey}`;
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

  const start = () => {
    const c = call.trim().toUpperCase();
    if (!c) return;
    setCall(c);
    startWatch(c, role, []);
  };

  // Load the own station's receivability: own call as sender, seeded with the
  // stored history so the map/list are populated immediately.
  const loadOwnFor = async (c) => {
    if (!c) return;
    setCall(c);
    setRole('sender');
    let seed = [];
    try {
      const r = await fetch(`${apiBase}/mqtt/stored?call=${encodeURIComponent(c)}&minutes=${OWN_SEED_MINUTES}`);
      if (r.ok) {
        const data = await r.json();
        if (Array.isArray(data)) seed = data;
      }
    } catch {
      /* ignore; fall back to live-only */
    }
    startWatch(c, 'sender', seed);
  };

  // Settings: own-station marker (locator) + callsign; auto-load own receivability.
  useEffect(() => {
    fetch(settingsUrl)
      .then((r) => r.json())
      .then((s) => {
        setHome(locatorCenter(s.locator));
        const oc = (s.callsign || '').trim().toUpperCase();
        setOwnCall(oc);
        if (oc && !autoStartedRef.current) {
          autoStartedRef.current = true;
          loadOwnFor(oc);
        }
      })
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  // Per-cell reception stats: the counterpart locator of each report, bucketed
  // by field/square, with min/max/avg SNR and the dB series in arrival order.
  const cellStats = useMemo(() => {
    const m = {};
    // reports is newest-first; walk backwards so each series is oldest-first.
    for (let i = reports.length - 1; i >= 0; i--) {
      const d = reports[i];
      const key = cellKey(counterpartGrid(d, watching), level);
      if (!key) continue;
      const db = typeof d.rp === 'number' ? d.rp : d.rp != null ? Number(d.rp) : null;
      if (db == null || Number.isNaN(db)) continue;
      let s = m[key];
      if (!s) s = m[key] = { count: 0, min: Infinity, max: -Infinity, sum: 0, series: [] };
      s.count += 1;
      s.sum += db;
      if (db < s.min) s.min = db;
      if (db > s.max) s.max = db;
      s.series.push(db);
    }
    for (const k in m) m[k].avg = m[k].sum / m[k].count;
    return m;
  }, [reports, watching, level]);

  const counts = useMemo(
    () => Object.fromEntries(Object.entries(cellStats).map(([k, s]) => [k, s.count])),
    [cellStats],
  );
  const colorFor = (cell) => {
    const s = cellStats[cell];
    return s ? snrColor(s.avg) : null;
  };

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
        {isWorked(c) ? (
          <span className="worked" title="Worked before">
            {c}
          </span>
        ) : (
          c
        )}
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

  // Hover tooltip over a map cell: track the cell + cursor, clamp to viewport.
  const onCellEnter = (cell, e) => setHover({ cell, x: e.clientX, y: e.clientY });
  const onCellMove = (e) => setHover((h) => (h ? { ...h, x: e.clientX, y: e.clientY } : h));
  const onCellLeave = () => setHover(null);

  const hoverStats = hover ? cellStats[hover.cell] : null;
  const TIP_W = 250;
  const TIP_H = 118;
  const tipLeft = hover ? (hover.x + TIP_W + 20 > window.innerWidth ? hover.x - TIP_W - 14 : hover.x + 14) : 0;
  const tipTop = hover ? (hover.y + TIP_H + 20 > window.innerHeight ? hover.y - TIP_H - 14 : hover.y + 14) : 0;
  const tipBars = hoverStats ? hoverStats.series.slice(-MAX_BARS) : [];

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
        {ownCall && (
          <button
            type="button"
            className={`mqtt-mine ${watching === ownCall ? 'on' : ''}`}
            title={`Your station's receivability — where ${ownCall} is being heard`}
            onClick={() => loadOwnFor(ownCall)}
          >
            ★ {ownCall}
          </button>
        )}
        {statusText && <span className={`mqtt-status ${status}`}>{statusText}</span>}
      </form>

      <section className="reports-map">
        <div className="map-controls">
          <h3>{mapTitle}</h3>
          <div className="reports-map-tools">
            <span className="map-caption">
              {reports.length} reports · {cellCount} {level === 'square' ? 'squares' : 'fields'}
            </span>
            <div className="snr-legend" title="Cell colour = mean reception level (SNR)">
              <span className="snr-legend-label">{SNR_MIN}</span>
              <span className="snr-legend-bar" style={{ background: SNR_GRADIENT }} />
              <span className="snr-legend-label">{fmtDb(SNR_MAX)} dB</span>
            </div>
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
        <WorldMap
          counts={counts}
          region={region}
          home={home}
          colorFor={colorFor}
          onCellEnter={onCellEnter}
          onCellMove={onCellMove}
          onCellLeave={onCellLeave}
        />
      </section>

      {hoverStats && (
        <div className="cell-tip" style={{ left: tipLeft, top: tipTop, width: TIP_W }}>
          <div className="cell-tip-head">
            <strong>{hover.cell}</strong> · {hoverStats.count} report{hoverStats.count === 1 ? '' : 's'}
          </div>
          <div className="cell-tip-stats">
            <span>min {fmtDb(hoverStats.min)}</span>
            <span className="cell-tip-avg">avg {fmtDb(hoverStats.avg, 1)}</span>
            <span>max {fmtDb(hoverStats.max)}</span>
          </div>
          <div className="cell-tip-bars" aria-hidden="true">
            {tipBars.map((db, i) => (
              <span
                key={i}
                className="cell-tip-bar"
                style={{ height: `${barHeight(db)}px`, background: snrColor(db) }}
                title={`${fmtDb(db)} dB`}
              />
            ))}
          </div>
          <div className="cell-tip-foot">dB per report, oldest → newest</div>
        </div>
      )}

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
