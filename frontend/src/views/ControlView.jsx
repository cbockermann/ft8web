import { useEffect, useMemo, useState } from 'react';
import { controlReplyUrl, controlHaltUrl, controlFrequencyUrl, settingsUrl } from '../api.js';
import MessageText from '../components/MessageText.jsx';
import { useControl } from '../control/ControlProvider.jsx';
import { useCallsignFlags, useMetaToggle } from '../lib/locatorMeta.js';
import { callTokens } from '../lib/callsign.js';
import { compileFilter, ctxFor } from '../lib/filterExpr.js';
import { filtersUrl, filterUrl, filterHelpUrl } from '../api.js';
import FilterHelp from '../components/FilterHelp.jsx';

function fmtFreq(hz) {
  return hz ? `${(hz / 1e6).toFixed(3)} MHz` : '—';
}

/**
 * FT8 Control (MVP): drives a locally running WSJT-X over its UDP protocol via
 * the backend. Shows live WSJT-X status + decodes and lets the operator answer a
 * CQ (WSJT-X then auto-sequences the QSO) or halt transmit. Read + reply only —
 * no auto-CQ yet (that comes with the own ft8_lib daemon). Status + decodes come
 * from the app-wide ControlProvider, so they persist across view switches.
 */
export default function ControlView() {
  const { status, decodes } = useControl();
  const [onlyCq, setOnlyCq] = useState(false);
  const [showFlags, setShowFlags] = useMetaToggle('feed.showMeta');
  const [note, setNote] = useState('');
  const [pendingId, setPendingId] = useState('');
  const [bands, setBands] = useState([]);
  const [filterText, setFilterText] = useState('');
  const [filterMode, setFilterMode] = useState('hide'); // hide | dim
  const [saved, setSaved] = useState([]);
  const [saveName, setSaveName] = useState('');
  const [filterFocused, setFilterFocused] = useState(false);
  const [helpEnabled] = useMetaToggle('control.filterHelp', true);

  useEffect(() => {
    let active = true;
    fetch(settingsUrl)
      .then((r) => (r.ok ? r.json() : null))
      .then((s) => {
        if (active && s && Array.isArray(s.bands)) setBands(s.bands.filter((b) => b.enabled));
      })
      .catch(() => {});
    return () => {
      active = false;
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
      const r = await fetch(controlFrequencyUrl(b.freqHz, b.mode), { method: 'POST' });
      const body = await r.json().catch(() => ({}));
      const label = `${b.name} · ${(b.freqHz / 1e6).toFixed(3)} MHz${b.mode ? ` · ${b.mode}` : ''}`;
      flash(body.ok ? `Tuned ${label}` : body.message || 'Retune failed');
    } catch {
      flash('Retune failed — backend unreachable');
    }
  };

  const activeBand = bands.find((b) => status.dialHz && Math.abs(status.dialHz - b.freqHz) < 3000);

  // Saved filters.
  const loadSaved = () =>
    fetch(filtersUrl)
      .then((r) => (r.ok ? r.json() : []))
      .then((l) => Array.isArray(l) && setSaved(l))
      .catch(() => {});
  useEffect(() => {
    loadSaved();
  }, []);
  const saveFilter = async () => {
    const name = saveName.trim();
    if (!name || !filterText.trim()) return;
    await fetch(filtersUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, expression: filterText }),
    }).catch(() => {});
    setSaveName('');
    loadSaved();
  };
  const deleteFilter = async (name) => {
    await fetch(filterUrl(name), { method: 'DELETE' }).catch(() => {});
    loadSaved();
  };

  // Compile the filter once; apply live to the in-memory buffer.
  const compiled = useMemo(() => compileFilter(filterText), [filterText]);
  const matches = (d) => {
    if (onlyCq && !d.cq) return false;
    return compiled.test(ctxFor(d));
  };
  const nonTx = decodes.filter((d) => !d.tx);
  const matchCount = nonTx.filter(matches).length;
  // Hide mode drops non-matching (own Tx always shown); dim mode shows all, dimmed.
  const shown = filterMode === 'hide' ? decodes.filter((d) => d.tx || matches(d)) : decodes;
  const isDim = (d) => filterMode === 'dim' && !d.tx && !matches(d);

  // Resolve flags over all decodes so the QSO thread below gets them too.
  const pseudo = useMemo(() => decodes.map((d) => ({ msg: d.message })), [decodes]);
  const flagFor = useCallsignFlags(pseudo, showFlags);

  // Current QSO = the pair {our call, WSJT-X dxCall}. The thread is every related
  // message (our Tx + the DX's messages + the CQ we answered), oldest first.
  const me = (status.deCall || '').toUpperCase();
  const dx = (status.dxCall || '').toUpperCase();
  const thread = useMemo(() => {
    if (!dx) return [];
    return decodes
      .filter((d) => {
        const toks = callTokens(d.message || '');
        const hasDx = toks.includes(dx);
        const hasMe = me && toks.includes(me);
        return hasDx && (hasMe || d.tx || d.cq);
      })
      .slice()
      .reverse();
  }, [decodes, dx, me]);

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

      <div className="ctl-filter">
        <input
          className="filter-input ctl-filter-input"
          value={filterText}
          onChange={(e) => setFilterText(e.target.value)}
          onFocus={() => setFilterFocused(true)}
          onBlur={() => setFilterFocused(false)}
          placeholder="Filter — e.g. snr > -18 and not worked"
          spellCheck={false}
        />
        <div className="seg">
          <button type="button" className={filterMode === 'hide' ? 'active' : ''} onClick={() => setFilterMode('hide')}>
            Hide
          </button>
          <button type="button" className={filterMode === 'dim' ? 'active' : ''} onClick={() => setFilterMode('dim')}>
            Dim
          </button>
        </div>
        {filterText.trim() &&
          (compiled.ok ? (
            <span className="ctl-filter-count">
              {matchCount}/{nonTx.length}
            </span>
          ) : (
            <span className="ctl-filter-err" title={compiled.error}>
              ⚠ {compiled.error}
            </span>
          ))}
        <a
          className="filter-help-link"
          href={filterHelpUrl}
          target="_blank"
          rel="noopener noreferrer"
          title="Open the filter syntax help in a new window"
        >
          Filter help ↗
        </a>
        <span className="ctl-filter-spacer" />
        <select
          className="band-select"
          value=""
          onChange={(e) => {
            const s = saved.find((x) => x.name === e.target.value);
            if (s) {
              setFilterText(s.expression);
              setSaveName(s.name);
            }
          }}
          title="Load a saved filter"
        >
          <option value="">Saved…</option>
          {saved.map((s) => (
            <option key={s.name} value={s.name}>
              {s.name}
            </option>
          ))}
        </select>
        <input
          className="filter-input ctl-save-name"
          value={saveName}
          onChange={(e) => setSaveName(e.target.value)}
          placeholder="name"
          spellCheck={false}
        />
        <button type="button" className="ctl-save-btn" onClick={saveFilter} disabled={!saveName.trim() || !filterText.trim()}>
          Save
        </button>
        {saveName.trim() && saved.some((s) => s.name === saveName.trim()) && (
          <button type="button" className="ctl-del-btn" onClick={() => deleteFilter(saveName.trim())} title="Delete saved filter">
            ×
          </button>
        )}
      </div>

      {helpEnabled && filterFocused && (
        <FilterHelp compact onPick={(expr) => setFilterText(expr)} />
      )}

      <ul className="ctl-decodes">
        {shown.length === 0 && (
          <li className="ctl-empty">
            {status.up
              ? 'Waiting for decodes from WSJT-X…'
              : 'No WSJT-X datagrams. In WSJT-X: Settings → Reporting → UDP Server = this host:2237, “Accept UDP requests” on.'}
          </li>
        )}
        {shown.map((d, i) => (
          <li className={`ctl-row ${d.cq ? 'cq' : ''} ${d.tx ? 'tx' : ''} ${isDim(d) ? 'dim' : ''}`} key={`${d.id}-${i}`}>
            <span className="ctl-time">{d.at}</span>
            <span className="ctl-band">{d.band || ''}</span>
            <span className="ctl-snr">{d.tx ? 'Tx' : d.snr > 0 ? `+${d.snr}` : d.snr}</span>
            <span className="ctl-dt">{d.tx ? '' : d.dt?.toFixed(1)}</span>
            <span className="ctl-df">{d.df}</span>
            <span className="ctl-msg">
              <MessageText msg={d.message} flagFor={showFlags ? flagFor : null} />
            </span>
            {d.tx ? (
              <span />
            ) : (
              <button
                type="button"
                className="ctl-reply"
                disabled={pendingId === d.id}
                onClick={() => reply(d)}
                title="Answer this station (WSJT-X auto-sequences the QSO)"
              >
                {d.cq ? 'Answer' : 'Reply'}
              </button>
            )}
          </li>
        ))}
      </ul>

      <section className="ctl-qso">
        <h3>{dx ? `QSO with ${dx}` : 'QSO'}</h3>
        {!dx && (
          <p className="ctl-qso-empty">
            The CQ / response sequence will appear here — pick a station above to start.
          </p>
        )}
        {dx && thread.length === 0 && <p className="ctl-qso-empty">Waiting for messages with {dx}…</p>}
        {dx && thread.length > 0 && (
          <ul className="qso-thread">
            {thread.map((d, i) => (
              <li className={`qso-line ${d.tx ? 'tx' : 'rx'}`} key={`${d.id}-${i}`}>
                <span className="qso-dir">{d.tx ? '▲ Tx' : '▼ Rx'}</span>
                <span className="qso-time">{d.at}</span>
                <span className="qso-msg">
                  <MessageText msg={d.message} flagFor={showFlags ? flagFor : null} />
                </span>
                <span className="qso-snr">{d.tx ? '' : d.snr > 0 ? `+${d.snr}` : d.snr}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
