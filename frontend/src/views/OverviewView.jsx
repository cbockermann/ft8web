import { useState } from 'react';
import WorldMap, { REGIONS } from '../overview/WorldMap.jsx';
import { useFieldHeat } from '../overview/useFieldHeat.js';
import { useFeed } from '../feed/FeedProvider.jsx';
import { fmtTime } from '../lib/format.js';

const WINDOW_OPTIONS = [5, 10, 15, 30, 60]; // minutes; capped server-side
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

export default function OverviewView() {
  const [minutes, setMinutes] = useState(() => Number(loadPref('ov.minutes', '15')));
  const [region, setRegion] = useState(() => loadPref('ov.region', 'world'));
  const counts = useFieldHeat(minutes);
  const { decodes } = useFeed();

  const total = Object.values(counts).reduce((a, b) => a + b, 0);

  const changeMinutes = (m) => {
    setMinutes(m);
    savePref('ov.minutes', String(m));
  };
  const changeRegion = (r) => {
    setRegion(r);
    savePref('ov.region', r);
  };

  return (
    <main className="overview">
      <section className="map-panel">
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
          <span className="map-caption">
            {total} msg · {Object.keys(counts).length} fields · last {minutes} min
          </span>
        </div>
        <WorldMap counts={counts} region={region} />
      </section>

      <section className="mini-feed">
        <h2>Recent decodes</h2>
        <ul className="rows">
          {decodes.length === 0 && <li className="empty">Waiting for decodes…</li>}
          {decodes.slice(0, MINI_FEED).map((d) => (
            <li key={d.id} className="row">
              <span className="col time">{fmtTime(d)}</span>
              <span className="col snr">{d.snr ?? ''}</span>
              {d.tags && d.tags.band && <span className="col band">{d.tags.band}</span>}
              <span className="col msg">{d.msg}</span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
