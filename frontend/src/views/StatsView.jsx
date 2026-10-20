import { useEffect, useMemo, useState } from 'react';
import WorldMap, { REGIONS } from '../overview/WorldMap.jsx';
import MqttReports from '../components/MqttReports.jsx';
import { apiBase } from '../api.js';

const RANGES = [
  { key: 'hour', label: 'Hour' },
  { key: 'day', label: 'Day' },
  { key: 'week', label: 'Week' },
  { key: 'month', label: 'Month' },
];
const LEVELS = [
  { key: 'field', label: 'Field' },
  { key: 'square', label: 'Square' },
];
const POLL_MS = 20000;

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

function fmtBucket(tSec, bucketSeconds) {
  const d = new Date(tSec * 1000);
  const iso = d.toISOString();
  return bucketSeconds >= 86400 ? iso.slice(5, 10) : iso.slice(11, 16); // MM-DD or HH:MM (UTC)
}

export default function StatsView() {
  const [range, setRange] = useState(() => loadPref('st.range', 'day'));
  const [level, setLevel] = useState(() => loadPref('st.level', 'field'));
  const [region, setRegion] = useState(() => loadPref('st.region', 'world'));
  const [band, setBand] = useState('');
  const [data, setData] = useState(null);

  useEffect(() => {
    let active = true;
    const url =
      `${apiBase}/stats?range=${range}&level=${level}` + (band ? `&band=${encodeURIComponent(band)}` : '');
    const load = () =>
      fetch(url)
        .then((r) => (r.ok ? r.json() : null))
        .then((d) => {
          if (active && d) setData(d);
        })
        .catch(() => {});
    load();
    const t = setInterval(load, POLL_MS);
    return () => {
      active = false;
      clearInterval(t);
    };
  }, [range, level, band]);

  const heat = useMemo(
    () => (data ? Object.fromEntries(data.heatmap.map((c) => [c.cell, c.count])) : {}),
    [data],
  );
  const rate = data?.rate ?? [];
  const bands = (data?.bands ?? []).filter((b) => b.band);
  const maxRate = Math.max(1, ...rate.map((p) => p.count));
  const maxBand = Math.max(1, ...bands.map((b) => b.count));

  const pick = (setter, key) => (value) => {
    setter(value);
    savePref(key, value);
  };
  const changeRange = pick(setRange, 'st.range');
  const changeLevel = pick(setLevel, 'st.level');
  const changeRegion = pick(setRegion, 'st.region');

  return (
    <main className="stats">
      <div className="stats-controls">
        <div className="seg">
          {RANGES.map((r) => (
            <button key={r.key} className={range === r.key ? 'active' : ''} onClick={() => changeRange(r.key)}>
              {r.label}
            </button>
          ))}
        </div>
        <div className="seg">
          {LEVELS.map((l) => (
            <button key={l.key} className={level === l.key ? 'active' : ''} onClick={() => changeLevel(l.key)}>
              {l.label}
            </button>
          ))}
        </div>
        <select className="band-select" value={band} onChange={(e) => setBand(e.target.value)}>
          <option value="">All bands</option>
          {bands.map((b) => (
            <option key={b.band} value={b.band}>
              {b.band}
            </option>
          ))}
        </select>
        <span className="map-caption">
          {data?.total ?? 0} reports sent · last {range}
        </span>
      </div>

      <div className="stats-body">
        <section className="stats-panel">
          <h3>Reports over time</h3>
          {rate.length === 0 ? (
            <p className="hint">No reports sent yet (enable PSKReporter in Config).</p>
          ) : (
            <div className="bars">
              {rate.map((p) => (
                <div
                  key={p.t}
                  className="bar"
                  style={{ height: `${(p.count / maxRate) * 100}%` }}
                  title={`${fmtBucket(p.t, data.bucketSeconds)} — ${p.count}`}
                />
              ))}
            </div>
          )}
        </section>

        <section className="stats-panel">
          <h3>By band</h3>
          {bands.length === 0 ? (
            <p className="hint">—</p>
          ) : (
            <ul className="band-bars">
              {bands.map((b) => (
                <li key={b.band}>
                  <span className="band-name">{b.band}</span>
                  <span className="band-bar" style={{ width: `${(b.count / maxBand) * 100}%` }} />
                  <span className="band-count">{b.count}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="stats-map">
        <div className="map-controls">
          <h3>Where you reported from</h3>
          <div className="seg">
            {Object.entries(REGIONS).map(([key, r]) => (
              <button key={key} className={region === key ? 'active' : ''} onClick={() => changeRegion(key)}>
                {r.label}
              </button>
            ))}
          </div>
        </div>
        <WorldMap counts={heat} region={region} />
      </section>

      <MqttReports />
    </main>
  );
}
