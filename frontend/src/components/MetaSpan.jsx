/**
 * Locator meta-info appended to a decode — flag, country and finest region, in
 * parentheses so it reads as metadata, not part of the message. Nothing without
 * meta.
 */
export default function MetaSpan({ meta }) {
  if (!meta) return null;
  const region = meta.region && meta.region !== meta.country ? ` · ${meta.region}` : '';
  return (
    <span className="meta">
      {' '}
      ({meta.flag} {meta.country}
      {region})
    </span>
  );
}
