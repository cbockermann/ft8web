import { useEffect, useState } from 'react';
import { settingsUrl } from '../api.js';

const DEFAULT_PSK = {
  enabled: false,
  antenna: '',
  host: 'report.pskreporter.info',
  port: 4739,
  intervalSeconds: 300,
};

export default function ConfigView() {
  const [form, setForm] = useState({
    callsign: '',
    locator: '',
    gridResolution: 'square',
    psk: { ...DEFAULT_PSK },
  });
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
          psk: { ...DEFAULT_PSK, ...(s.psk || {}) },
        }),
      )
      .catch(() => setStatus({ ok: false, msg: 'Could not load settings.' }))
      .finally(() => setLoading(false));
  }, []);

  const field = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));
  const pskField = (name, parse = (v) => v) => (e) => {
    const value = e.target.type === 'checkbox' ? e.target.checked : parse(e.target.value);
    setForm((f) => ({ ...f, psk: { ...f.psk, [name]: value } }));
  };

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
          psk: { ...DEFAULT_PSK, ...(body.psk || {}) },
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
        <label>
          Callsign
          <input
            value={form.callsign}
            onChange={field('callsign')}
            placeholder="e.g. DL0ABC"
            autoCapitalize="characters"
            spellCheck={false}
          />
        </label>
        <label>
          Locator
          <input
            value={form.locator}
            onChange={field('locator')}
            placeholder="e.g. JO31 or JO31HK"
            autoCapitalize="characters"
            spellCheck={false}
          />
        </label>
        <label>
          Default map resolution
          <select value={form.gridResolution} onChange={field('gridResolution')}>
            <option value="field">Field (2 chars, 20°×10°)</option>
            <option value="square">Square (4 chars, 2°×1°)</option>
          </select>
        </label>

        <fieldset className="config-section">
          <legend>PSKReporter</legend>
          <label className="checkbox">
            <input type="checkbox" checked={form.psk.enabled} onChange={pskField('enabled')} />
            Report observed stations to PSKReporter
          </label>
          <p className="hint">Uses the callsign and locator above as your reporting identity.</p>
          <label>
            Antenna (optional)
            <input
              value={form.psk.antenna}
              onChange={pskField('antenna')}
              placeholder="e.g. Longwire"
              spellCheck={false}
            />
          </label>
          <label>
            Host
            <input value={form.psk.host} onChange={pskField('host')} spellCheck={false} />
          </label>
          <div className="config-row">
            <label>
              Port
              <input
                type="number"
                value={form.psk.port}
                onChange={pskField('port', (v) => Number(v))}
              />
            </label>
            <label>
              Interval (s)
              <input
                type="number"
                min="120"
                value={form.psk.intervalSeconds}
                onChange={pskField('intervalSeconds', (v) => Number(v))}
              />
            </label>
          </div>
          <p className="hint">Test port 14739, live 4739. Minimum interval 120 s.</p>
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
