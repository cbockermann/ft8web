import { useEffect, useMemo, useRef, useState } from 'react';
import { apiBase } from '../api.js';
import { fmtTime } from '../lib/format.js';
import { useCallsignFlags, useMetaToggle } from '../lib/locatorMeta.js';

const MAX_ROWS = 300;

// Which side of the report the entered callsign sits on.
const ROLES = [
  { key: 'both', label: 'Both', title: 'Reports where the call is sender or receiver' },
  { key: 'sender', label: 'Heard', title: 'Reports where the call was heard by others (it is the sender)' },
  { key: 'receiver', label: 'Hearing', title: 'Reports where the call heard others (it is the receiver)' },
];

/**
 * Diagnostics: subscribe to the PSKReporter public MQTT gateway for one callsign
 * and show its reports live. Streams from the backend relay at
 * GET /api/mqtt/reports (SSE); payload keys: sc/rc (calls), sl/rl (grids),
 * rp (SNR), f (Hz), b (band), md (mode), t (unix). Reports arrive in bursts
 * (stations upload every few minutes), so the list may sit idle a while.
 */
export default function MqttReports() {
  const [call, setCall] = useState('');
  const [role, setRole] = useState('both');
  const [active, setActive] = useState(false);
  const [status, setStatus] = useState('idle'); // idle | connecting | live | error
  const [reports, setReports] = useState([]);
  const [showFlags, setShowFlags] = useMetaToggle('feed.showMeta');
  const esRef = useRef(null);

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
    status === 'live' ? `live · ${reports.length}` : status === 'connecting' ? 'connecting…' : status === 'error' ? 'error' : '';

  return (
    <section className="stats-panel mqtt-diag">
      <div className="feed-head">
        <h3>Live reports · PSKReporter MQTT</h3>
        <label className="meta-toggle">
          <input type="checkbox" checked={showFlags} onChange={(e) => setShowFlags(e.target.checked)} />
          Flags
        </label>
      </div>
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

      <ul className="mqtt-list">
        {active && reports.length === 0 && (
          <li className="mqtt-empty">Waiting for reports — PSKReporter delivers them in bursts, so this can take a few minutes.</li>
        )}
        {!active && reports.length === 0 && (
          <li className="mqtt-empty">Enter a callsign and press Watch to stream its live PSKReporter spots.</li>
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
  );
}
