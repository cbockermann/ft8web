import { useState, useCallback } from 'react';
import { useFeed } from '../feed/FeedProvider.jsx';
import { qslUrl } from '../api.js';
import { fmtTime } from '../lib/format.js';
import { useCallsignFilter } from '../lib/callsignFilter.js';
import { useLocatorMeta, useMetaToggle } from '../lib/locatorMeta.js';
import FilterInput from '../components/FilterInput.jsx';
import MetaSpan from '../components/MetaSpan.jsx';

function DecodeRow({ d, selected, meta, onClick }) {
  const band = d.tags && d.tags.band;
  return (
    <li className={selected ? 'row selected' : 'row'} onClick={onClick}>
      <span className="col time">{fmtTime(d)}</span>
      <span className="col snr">{d.snr ?? ''}</span>
      {band && <span className="col band">{band}</span>}
      <span className="col msg">
        {d.msg}
        <MetaSpan meta={meta} />
      </span>
    </li>
  );
}

export default function FeedView() {
  const { decodes } = useFeed();
  const filter = useCallsignFilter('feed.filter');
  const [showMeta, setShowMeta] = useMetaToggle();
  const metaFor = useLocatorMeta(decodes, showMeta);
  const [selectedId, setSelectedId] = useState(null);
  const [qsl, setQsl] = useState(null);
  const [qslError, setQslError] = useState(null);

  const shown = decodes.filter((d) => filter.matches(d.msg));

  const selectDecode = useCallback((d) => {
    setSelectedId(d.id);
    setQsl(null);
    setQslError(null);
    fetch(qslUrl(d.id))
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then(setQsl)
      .catch((e) => setQslError(e.message));
  }, []);

  return (
    <main className="feed-view">
      <section className="feed">
        <div className="feed-head">
          <h2>Live decodes</h2>
          <div className="feed-tools">
            <label className="meta-toggle" title="Show country from the locator (meta)">
              <input type="checkbox" checked={showMeta} onChange={(e) => setShowMeta(e.target.checked)} />
              Meta
            </label>
            <FilterInput value={filter.value} onChange={filter.setValue} />
          </div>
        </div>
        <ul className="rows">
          {decodes.length === 0 && <li className="empty">Waiting for decodes…</li>}
          {decodes.length > 0 && shown.length === 0 && (
            <li className="empty">No decodes match the filter.</li>
          )}
          {shown.map((d) => (
            <DecodeRow
              key={d.id}
              d={d}
              selected={d.id === selectedId}
              meta={metaFor(d.msg)}
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
  );
}
