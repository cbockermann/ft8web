/**
 * Locator meta-info appended to a decode — country flag + name, in parentheses
 * so it reads as metadata, not part of the message. Renders nothing without meta.
 */
export default function MetaSpan({ meta }) {
  if (!meta) return null;
  return (
    <span className="meta">
      {' '}
      ({meta.flag} {meta.country})
    </span>
  );
}
