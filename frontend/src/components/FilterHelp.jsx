import { FILTER_FIELDS, FILTER_OPERATORS, FILTER_NOTES, FILTER_EXAMPLES } from '../lib/filterDocs.js';

/**
 * Renders the Control-view filter documentation from the shared tables in
 * filterDocs.js. Used in two places:
 *   - `compact` (the inline "instant help" panel under the filter input),
 *   - full (the standalone /help/filters page).
 *
 * When `onPick` is given, example expressions become clickable chips that insert
 * the expression into the filter. They suppress mousedown so the filter input
 * keeps focus (and the inline panel stays open) when clicked.
 */
export default function FilterHelp({ compact = false, onPick = null }) {
  const Example = ({ expr, desc }) =>
    onPick ? (
      <button
        type="button"
        className="fh-example"
        onMouseDown={(e) => e.preventDefault()}
        onClick={() => onPick(expr)}
        title={desc ? `${desc} — click to use` : 'Click to use'}
      >
        <code>{expr}</code>
      </button>
    ) : (
      <div className="fh-example static">
        <code>{expr}</code>
        {desc && <span className="fh-example-desc">{desc}</span>}
      </div>
    );

  return (
    <div className={`filter-help ${compact ? 'compact' : 'full'}`}>
      <section className="fh-section">
        <h4>Properties you can test</h4>
        <div className="fh-fields">
          {FILTER_FIELDS.map((f) => (
            <div className="fh-field" key={f.name}>
              <code className="fh-name">
                {f.name}
                {f.alias && <span className="fh-alias"> / {f.alias}</span>}
              </code>
              <span className="fh-type">{f.type}</span>
              <span className="fh-desc">{f.desc}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="fh-section">
        <h4>Operators</h4>
        <div className="fh-ops">
          {FILTER_OPERATORS.map((o) => (
            <div className="fh-op" key={o.sym}>
              <code className="fh-sym">{o.sym}</code>
              <span className="fh-desc">{o.desc}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="fh-section">
        <h4>Examples</h4>
        <div className={`fh-examples ${onPick ? 'pickable' : ''}`}>
          {FILTER_EXAMPLES.map((e) => (
            <Example key={e.expr} expr={e.expr} desc={e.desc} />
          ))}
        </div>
      </section>

      {!compact && (
        <section className="fh-section">
          <h4>Good to know</h4>
          <ul className="fh-notes">
            {FILTER_NOTES.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
