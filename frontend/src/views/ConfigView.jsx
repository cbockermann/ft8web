import { useEffect, useState } from 'react';
import { settingsUrl } from '../api.js';

export default function ConfigView() {
  const [form, setForm] = useState({ callsign: '', locator: '' });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState(null); // { ok, msg }

  useEffect(() => {
    fetch(settingsUrl)
      .then((r) => r.json())
      .then((s) => setForm({ callsign: s.callsign || '', locator: s.locator || '' }))
      .catch(() => setStatus({ ok: false, msg: 'Could not load settings.' }))
      .finally(() => setLoading(false));
  }, []);

  const field = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));

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
        setForm({ callsign: body.callsign || '', locator: body.locator || '' });
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
        <div className="config-actions">
          <button type="submit" disabled={loading || saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
          {status && <span className={status.ok ? 'ok' : 'error'}>{status.msg}</span>}
        </div>
      </form>
      <p className="hint">
        Stored on the server in a local properties file. Your locator marks your
        station on the overview map.
      </p>
    </main>
  );
}
