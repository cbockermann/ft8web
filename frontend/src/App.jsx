import { useEffect, useRef, useState, useCallback } from 'react';

const MAX_FEED = 300;

function fmtTime(d) {
  if (d.time) return d.time.replace('T', ' ').replace('Z', '');
  if (d.unix) return new Date(d.unix * 1000).toISOString().replace('T', ' ').replace(/\..*/, '');
  return '';
}

function DecodeRow({ d, selected, onClick }) {
  const band = d.tags && d.tags.band;
  return (
    <li className={selected ? 'row selected' : 'row'} onClick={onClick}>
      <span className="col time">{fmtTime(d)}</span>
      <span className="col snr">{d.snr ?? ''}</span>
      {band && <span className="col band">{band}</span>}
      <span className="col msg">{d.msg}</span>
    </li>
  );
}

export default function App() {
  const [feed, setFeed] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [qsl, setQsl] = useState(null);
  const [connected, setConnected] = useState(false);
  const [qslError, setQslError] = useState(null);
  const seen = useRef(new Set());

  // Live feed via SSE.
  useEffect(() => {
    const es = new EventSource('/api/stream');
    es.onopen = () => setConnected(true);
    es.onerror = () => setConnected(false);
    es.addEventListener('decode', (ev) => {
      const d = JSON.parse(ev.data);
      if (seen.current.has(d.id)) return;
      seen.current.add(d.id);
      setFeed((prev) => [d, ...prev].slice(0, MAX_FEED));
    });
    return () => es.close();
  }, []);

  const selectDecode = useCallback((d) => {
    setSelectedId(d.id);
    setQsl(null);
    setQslError(null);
    fetch(`/api/qsl/${encodeURIComponent(d.id)}`)
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then(setQsl)
      .catch((e) => setQslError(e.message));
  }, []);

  return (
    <div className="app">
      <header>
        <h1>FT8 Monitor</h1>
        <span className={connected ? 'status ok' : 'status down'}>
          {connected ? 'live' : 'disconnected'}
        </span>
      </header>

      <main>
        <section className="feed">
          <h2>Live decodes</h2>
          <ul className="rows">
            {feed.length === 0 && <li className="empty">Waiting for decodes…</li>}
            {feed.map((d) => (
              <DecodeRow
                key={d.id}
                d={d}
                selected={d.id === selectedId}
                onClick={() => selectDecode(d)}
              />
            ))}
          </ul>
        </section>

        <section className="detail">
          <h2>QSL detail</h2>
          {!selectedId && <p className="hint">Click a decode to see its QSO.</p>}
          {qslError && <p className="error">Could not load QSL: {qslError}</p>}
          {qsl && (
            <div>
              <div className="pair">
                {qsl.callA}
                {qsl.callB ? ` ↔ ${qsl.callB}` : ' (CQ)'}
              </div>
              <ul className="rows">
                {qsl.messages.map((d) => (
                  <li key={d.id} className={d.id === selectedId ? 'row selected' : 'row'}>
                    <span className="col time">{fmtTime(d)}</span>
                    <span className="col snr">{d.snr ?? ''}</span>
                    <span className="col msg">{d.msg}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
