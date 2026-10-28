import FilterHelp from '../components/FilterHelp.jsx';

/**
 * Standalone help page for the Control-view filter syntax, reachable at
 * /help/filters (opened in a new window from the filter bar). Content comes from
 * the shared FilterHelp component / filterDocs.js.
 */
export default function FilterHelpView() {
  return (
    <main className="view help-page">
      <h2>Control filter syntax</h2>
      <p className="help-intro">
        The filter in the Control view hides (or dims) decodes that don&apos;t match a
        predicate. It&apos;s a small boolean language over each decode&apos;s properties —
        combine comparisons with <code>and</code>, <code>or</code>, <code>not</code> and
        parentheses. The values (worked? distance? country?) are resolved on the backend;
        the expression is evaluated live over the decodes in view.
      </p>

      <FilterHelp />

      <p className="hint">
        Saved filters are stored on the server and shared across browsers; the inline
        &ldquo;instant help&rdquo; can be turned off under Config → Interface.
      </p>
    </main>
  );
}
