import { useEffect, useMemo, useRef, useState } from 'react';
import {
  controlStreamUrl,
  controlStatusUrl,
  controlReplyUrl,
  controlHaltUrl,
  controlFrequencyUrl,
  settingsUrl,
} from '../api.js';
import MessageText from '../components/MessageText.jsx';
import { useCallsignFlags, useMetaToggle } from '../lib/locatorMeta.js';

const MAX_DECODES = 300;

function fmtFreq(hz) {
  return hz ? `${(hz / 1e6).toFixed(3)} MHz` : '—';
}

/**
 * FT8 Control (MVP): drives a locally running WSJT-X over its UDP protocol via
 * the backend. Shows live WSJT-X status + decodes and lets the operator answer a
 * CQ (WSJT-X then auto-sequences the QSO) or halt transmit. Read + reply only —
 * no auto-CQ yet (that comes with the own ft8_lib daemon).
 */
export default function ControlView() {
  const [status, setStatus] = useState({ up: false });
  const [decodes, setDecodes] = useState([]);
  const [onlyCq, setOnlyCq] = useState(false);
  const [showFlags, setShowFlags] = useMetaToggle('feed.showMeta');
  const [note, setNote] = useState('');
  const [pendingId, setPendingId] = useState('');
  const [bands, setBands] = useState([]);
  const esRef = useRef(null);

  useEffect(() => {
    let active = true;
    fetch(controlStatusUrl)
      .then((r) => (r.ok ? r.json() : null))
      .then((s) => active && s && setStatus(s))
      .catch(() => {});

    fetch(settingsUrl)
      .then((r) => (r.ok ? r.json() : null))
      .then((s) => {
        if (active && s && Array.isArray(s.bands)) setBands(s.bands.filter((b) => b.enabled));
      })
      .catch(() => {});

    const es = new EventSource(controlStreamUrl);
    esRef.current = es;
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

  const flash = (msg) => {
    setNote(msg);
    setTimeout(() => setNote(''), 3000);
  };

  const reply = async (d) => {
    setPendingId(d.id);
    try {
      const r = await fetch(controlReplyUrl(d.id), { method: 'POST' });
      const ok = r.ok && (await r.json()).ok;
      flash(ok ? `Replying to ${d.message}` : 'Reply failed — is WSJT-X connected & Tx enabled?');
    } catch {
      flash('Reply failed — backend unreachable');
    } finally {
      setPendingId('');
    }
  };

  const halt = async () => {
    try {
      const r = await fetch(controlHaltUrl(false), { method: 'POST' });
      const ok = r.ok && (await r.json()).ok;
      flash(ok ? 'Halt sent' : 'Halt failed — is WSJT-X connected?');
    } catch {
      flash('Halt failed — backend unreachable');
    }
  };

  const tune = async (b) => {
    try {
      const r = await fetch(controlFrequencyUrl(b.freqHz), { method: 'POST' });
      const body = await r.json().catch(() => ({}));
      flash(body.ok ? `Tuned ${b.name} · ${(b.freqHz / 1e6).toFixed(3)} MHz` : body.message || 'Retune failed');
    } catch {
      flash('Retune failed — backend unreachable');
    }
  };

  const activeBand = bands.find((b) => status.dialHz && Math.abs(status.dialHz - b.freqHz) < 3000);

  const shown = onlyCq ? decodes.filter((d) => d.cq) : decodes;
  const pseudo = useMemo(() => shown.map((d) => ({ msg: d.message })), [shown]);
  const flagFor = useCallsignFlags(pseudo, showFlags);

  return (
    <main className="control">
      <section className={`ctl-status ${status.up ? 'up' : 'down'}`}>
        <span className={`ctl-dot ${status.up ? 'ok' : 'bad'}`} />
        <span className="ctl-conn">{status.up ? 'WSJT-X connected' : 'WSJT-X not connected'}</span>
        {status.up && (
          <>
            <span className="ctl-field">{fmtFreq(status.dialHz)}</span>
            <span className="ctl-field">{status.mode || '—'}</span>
            {status.deCall && (
              <span className="ctl-field">
                {status.deCall}
                {status.deGrid ? ` · ${status.deGrid}` : ''}
              </span>
            )}
            {status.transmitting ? (
              <span className="ctl-tx on">● ON AIR</span>
            ) : (
              <span className={`ctl-tx ${status.txEnabled ? 'armed' : ''}`}>
                {status.txEnabled ? 'Tx enabled' : 'Tx off'}
              </span>
            )}
            {status.txWatchdog && <span className="ctl-warn">watchdog</span>}
            {status.txMessage && <span className="ctl-txmsg">“{status.txMessage.trim()}”</span>}
          </>
        )}
        {bands.length > 0 && (
          <div className="seg ctl-bands">
            {bands.map((b) => (
              <button
                type="button"
                key={b.name}
                className={activeBand && activeBand.name === b.name ? 'active' : ''}
                onClick={() => tune(b)}
                title={`${(b.freqHz / 1e6).toFixed(3)} MHz`}
              >
                {b.name}
              </button>
            ))}
          </div>
        )}
        <button type="button" className="ctl-halt" onClick={halt} title="Stop transmitting now">
          ■ Halt Tx
        </button>
      </section>

      <div className="ctl-tools">
        <label className="meta-toggle">
          <input type="checkbox" checked={onlyCq} onChange={(e) => setOnlyCq(e.target.checked)} />
          Only CQ
        </label>
        <label className="meta-toggle">
          <input type="checkbox" checked={showFlags} onChange={(e) => setShowFlags(e.target.checked)} />
          Flags
        </label>
        {note && <span className="ctl-note">{note}</span>}
      </div>

      <ul className="ctl-decodes">
        {shown.length === 0 && (
          <li className="ctl-empty">
            {status.up
              ? 'Waiting for decodes from WSJT-X…'
              : 'No WSJT-X datagrams. In WSJT-X: Settings → Reporting → UDP Server = this host:2237, “Accept UDP requests” on.'}
          </li>
        )}
        {shown.map((d, i) => (
          <li className={`ctl-row ${d.cq ? 'cq' : ''}`} key={`${d.id}-${i}`}>
            <span className="ctl-time">{d.at}</span>
            <span className="ctl-snr">{d.snr > 0 ? `+${d.snr}` : d.snr}</span>
            <span className="ctl-dt">{d.dt?.toFixed(1)}</span>
            <span className="ctl-df">{d.df}</span>
            <span className="ctl-msg">
              <MessageText msg={d.message} flagFor={showFlags ? flagFor : null} />
            </span>
            <button
              type="button"
              className="ctl-reply"
              disabled={pendingId === d.id}
              onClick={() => reply(d)}
              title="Answer this station (WSJT-X auto-sequences the QSO)"
            >
              {d.cq ? 'Answer' : 'Reply'}
            </button>
          </li>
        ))}
      </ul>
    </main>
  );
}
