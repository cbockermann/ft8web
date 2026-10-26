import { useEffect, useState } from 'react';
import { settingsUrl } from '../api.js';

const DEFAULT_PSK = {
  enabled: false,
  antenna: '',
  host: 'report.pskreporter.info',
  port: 4739,
  intervalSeconds: 300,
};

const DEFAULT_BANDS = [
  { name: '80m', freqHz: 3573000, enabled: true },
  { name: '40m', freqHz: 7074000, enabled: true },
  { name: '20m', freqHz: 14074000, enabled: true },
  { name: '10m', freqHz: 28074000, enabled: true },
];

const DEFAULTS = {
  callsign: '',
  locator: '',
  gridResolution: 'square',
  backfillMinutes: 15,
  psk: { ...DEFAULT_PSK },
  bands: DEFAULT_BANDS.map((b) => ({ ...b })),
};

const bandsOf = (s) => (Array.isArray(s.bands) && s.bands.length ? s.bands : DEFAULT_BANDS).map((b) => ({ ...b }));

function Field({ label, desc, children }) {
  return (
    <div className="config-field">
      <label>
        <span className="field-label">{label}</span>
        {children}
      </label>
      <p className="field-desc">{desc}</p>
    </div>
  );
}

export default function ConfigView() {
  const [form, setForm] = useState(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState(null); // { ok, msg }

  useEffect(() => {
    fetch(settingsUrl)
      .then((r) => r.json())
      .then((s) =>
        setForm({
          callsign: s.callsign || '',
          locator: s.locator || '',
          gridResolution: s.gridResolution || 'square',
          backfillMinutes: s.backfillMinutes ?? 15,
          psk: { ...DEFAULT_PSK, ...(s.psk || {}) },
          bands: bandsOf(s),
        }),
      )
      .catch(() => setStatus({ ok: false, msg: 'Could not load settings.' }))
      .finally(() => setLoading(false));
  }, []);

  const field = (name, parse = (v) => v) => (e) =>
    setForm((f) => ({ ...f, [name]: parse(e.target.value) }));
  const pskField = (name, parse = (v) => v) => (e) => {
    const value = e.target.type === 'checkbox' ? e.target.checked : parse(e.target.value);
    setForm((f) => ({ ...f, psk: { ...f.psk, [name]: value } }));
  };

  const setBand = (i, key, value) =>
    setForm((f) => ({ ...f, bands: f.bands.map((b, j) => (j === i ? { ...b, [key]: value } : b)) }));
  const addBand = () =>
    setForm((f) => ({ ...f, bands: [...f.bands, { name: '', freqHz: 0, enabled: true }] }));
  const removeBand = (i) =>
    setForm((f) => ({ ...f, bands: f.bands.filter((_, j) => j !== i) }));

  const save = (e) => {
    e.preventDefault();
    setSaving(true);
    setStatus(null);
    fetch(settingsUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form),
    })
      .then(async (r) => {
        const body = await r.json().catch(() => ({}));
        if (!r.ok) throw new Error(body.error || `HTTP ${r.status}`);
        setForm({
          callsign: body.callsign || '',
          locator: body.locator || '',
          gridResolution: body.gridResolution || 'square',
          backfillMinutes: body.backfillMinutes ?? 15,
          psk: { ...DEFAULT_PSK, ...(body.psk || {}) },
          bands: bandsOf(body),
        });
        setStatus({ ok: true, msg: 'Saved.' });
      })
      .catch((err) => setStatus({ ok: false, msg: err.message }))
      .finally(() => setSaving(false));
  };

  return (
    <main className="view config">
      <h2>Station configuration</h2>
      <form className="config-form" onSubmit={save}>
        <Field label="Callsign" desc="Your station callsign. Also used as your PSKReporter identity.">
          <input
            className="upper"
            value={form.callsign}
            onChange={field('callsign')}
            placeholder="e.g. DL0ABC"
            autoCapitalize="characters"
            spellCheck={false}
          />
        </Field>

        <Field label="Locator" desc="Your Maidenhead locator (4 or 6 chars). Marks your station on the map.">
          <input
            className="upper"
            value={form.locator}
            onChange={field('locator')}
            placeholder="e.g. JO31 or JO31HK"
            autoCapitalize="characters"
            spellCheck={false}
          />
        </Field>

        <Field
          label="Default map resolution"
          desc="Initial activity-map granularity; viewers can switch Field/Square live."
        >
          <select value={form.gridResolution} onChange={field('gridResolution')}>
            <option value="field">Field (2 chars, 20°×10°)</option>
            <option value="square">Square (4 chars, 2°×1°)</option>
          </select>
        </Field>

        <Field
          label="Startup backfill (min)"
          desc="On restart, replay this many minutes of history to refill the views (0 = off). Applies on next restart."
        >
          <input
            type="number"
            min="0"
            value={form.backfillMinutes}
            onChange={field('backfillMinutes', (v) => Number(v))}
          />
        </Field>

        <fieldset className="config-section">
          <legend>PSKReporter</legend>
          <div className="config-field">
            <label className="checkbox">
              <input type="checkbox" checked={form.psk.enabled} onChange={pskField('enabled')} />
              <span>Enable</span>
            </label>
            <p className="field-desc">Report the stations you hear to PSKReporter.</p>
          </div>

          <Field label="Antenna" desc="Optional antenna description, sent with your reports.">
            <input
              value={form.psk.antenna}
              onChange={pskField('antenna')}
              placeholder="e.g. Longwire"
              spellCheck={false}
            />
          </Field>

          <Field label="Host" desc="PSKReporter collector hostname.">
            <input value={form.psk.host} onChange={pskField('host')} spellCheck={false} />
          </Field>

          <Field label="Port" desc="Collector UDP port — 4739 live, 14739 for testing.">
            <input type="number" value={form.psk.port} onChange={pskField('port', (v) => Number(v))} />
          </Field>

          <Field label="Interval (s)" desc="Seconds between batches of reports (minimum 120).">
            <input
              type="number"
              min="120"
              value={form.psk.intervalSeconds}
              onChange={pskField('intervalSeconds', (v) => Number(v))}
            />
          </Field>
        </fieldset>

        <fieldset className="config-section">
          <legend>Bands</legend>
          <p className="field-desc">
            Selectable bands in the Control view, with their FT8 dial frequency. Uncheck “Shown” to
            hide a band from the picker. Retuning the rig needs rigctld (see README).
          </p>
          <div className="band-editor">
            <div className="band-row band-head">
              <span>Name</span>
              <span>Dial freq (MHz)</span>
              <span>Shown</span>
              <span />
            </div>
            {form.bands.map((b, i) => (
              <div className="band-row" key={i}>
                <input
                  value={b.name}
                  onChange={(e) => setBand(i, 'name', e.target.value)}
                  placeholder="e.g. 20m"
                  spellCheck={false}
                />
                <input
                  type="number"
                  step="0.001"
                  min="0"
                  value={b.freqHz ? (b.freqHz / 1e6).toFixed(3) : ''}
                  onChange={(e) => setBand(i, 'freqHz', Math.round(Number(e.target.value) * 1e6))}
                  placeholder="e.g. 14.074"
                />
                <input
                  type="checkbox"
                  checked={b.enabled}
                  onChange={(e) => setBand(i, 'enabled', e.target.checked)}
                />
                <button type="button" className="band-remove" onClick={() => removeBand(i)} title="Remove band">
                  ×
                </button>
              </div>
            ))}
          </div>
          <button type="button" className="band-add" onClick={addBand}>
            + Add band
          </button>
        </fieldset>

        <div className="config-actions">
          <button type="submit" disabled={loading || saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
          {status && <span className={status.ok ? 'ok' : 'error'}>{status.msg}</span>}
        </div>
      </form>
      <p className="hint">Stored on the server in a local properties file.</p>
    </main>
  );
}
