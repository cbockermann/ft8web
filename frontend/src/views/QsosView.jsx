import { useEffect, useMemo, useRef, useState } from 'react';
import { qsosUrl, qsosImportUrl } from '../api.js';
import { useCallsignFlags, useMetaToggle } from '../lib/locatorMeta.js';
import { useWorkedCalls } from '../qsolog/WorkedCallsProvider.jsx';

function fmtDateTime(unix) {
  if (!unix) return '';
  return new Date(unix * 1000).toISOString().replace('T', ' ').replace(/:\d\d\..*$/, ' UTC');
}

/**
 * Logged QSOs: the list of worked stations (from WSJT-X auto-logging and ADIF
 * imports), with an ADIF file import (duplicates are ignored server-side).
 */
export default function QsosView() {
  const [qsos, setQsos] = useState([]);
  const [status, setStatus] = useState(null);
  const [showFlags, setShowFlags] = useMetaToggle('feed.showMeta');
  const { refresh: refreshWorked } = useWorkedCalls();
  const fileRef = useRef(null);

  const load = () =>
    fetch(qsosUrl(5000))
      .then((r) => (r.ok ? r.json() : []))
      .then((d) => Array.isArray(d) && setQsos(d))
      .catch(() => {});

  useEffect(() => {
    load();
  }, []);

  const pseudo = useMemo(() => qsos.map((q) => ({ msg: q.call })), [qsos]);
  const flagFor = useCallsignFlags(pseudo, showFlags);

  const onFile = async (e) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setStatus({ msg: 'Importing…' });
    try {
      const fd = new FormData();
      fd.append('file', f);
      const r = await fetch(qsosImportUrl, { method: 'POST', body: fd });
      const body = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(body.error || `HTTP ${r.status}`);
      setStatus({ ok: true, msg: `Imported ${body.added} new, ${body.skipped} duplicate(s) skipped.` });
      load();
      refreshWorked();
    } catch (err) {
      setStatus({ ok: false, msg: `Import failed: ${err.message}` });
    } finally {
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const flag = (c) => (showFlags ? flagFor(c) : null);

  return (
    <main className="qsos">
      <div className="qsos-head">
        <h2>
          Logged QSOs <span className="qsos-count">{qsos.length}</span>
        </h2>
        <div className="qsos-tools">
          <label className="meta-toggle">
            <input type="checkbox" checked={showFlags} onChange={(e) => setShowFlags(e.target.checked)} />
            Flags
          </label>
          <label className="import-btn">
            Import ADIF…
            <input ref={fileRef} type="file" accept=".adi,.adif,.txt" onChange={onFile} hidden />
          </label>
          {status && <span className={status.ok === false ? 'error' : 'ok'}>{status.msg}</span>}
        </div>
      </div>

      <div className="qsos-table-wrap">
        <table className="qsos-table">
          <thead>
            <tr>
              <th>Date (UTC)</th>
              <th>Call</th>
              <th>Grid</th>
              <th>Band</th>
              <th>Mode</th>
              <th>Sent</th>
              <th>Rcvd</th>
              <th>Freq (MHz)</th>
            </tr>
          </thead>
          <tbody>
            {qsos.length === 0 && (
              <tr>
                <td colSpan={8} className="qsos-empty">
                  No QSOs yet. They appear here when WSJT-X logs one, or import an ADIF file.
                </td>
              </tr>
            )}
            {qsos.map((q, i) => (
              <tr key={i}>
                <td className="mono">{fmtDateTime(q.t)}</td>
                <td className="call">
                  {flag(q.call)?.flag ? <span title={flag(q.call).country}>{flag(q.call).flag} </span> : null}
                  {q.call}
                </td>
                <td className="mono">{q.grid}</td>
                <td>{q.band}</td>
                <td>{q.mode}</td>
                <td className="mono">{q.rstSent}</td>
                <td className="mono">{q.rstRcvd}</td>
                <td className="mono">{q.freqHz ? (q.freqHz / 1e6).toFixed(3) : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </main>
  );
}
