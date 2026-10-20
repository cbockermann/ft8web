/**
 * Callsign filter box: space-separated terms, each a callsign or a `*`/`?` glob.
 */
export default function FilterInput({ value, onChange }) {
  return (
    <div className="filter">
      <input
        className="filter-input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Filter calls — e.g. DL1ABC HB9* G?ABC"
        spellCheck={false}
        autoCapitalize="characters"
        aria-label="Filter by callsign"
      />
      {value && (
        <button type="button" className="filter-clear" onClick={() => onChange('')} title="Clear filter">
          ×
        </button>
      )}
    </div>
  );
}
