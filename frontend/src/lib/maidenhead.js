// Maidenhead helpers (mirror of the backend Maidenhead util), plus grid-cell
// geometry for the 18×18 field lattice on an equirectangular map.

const LOCATOR = /^[A-R]{2}[0-9]{2}([A-X]{2})?$/;
const SIGNOFFS = new Set(['RRR', 'RR73', '73', 'R73', 'TU', 'TU73', 'QSL', 'DE', 'CQ', 'DX']);

/** The 2-char field (e.g. "JO") of the first locator in a message, or null. */
export function fieldOf(msg) {
  if (!msg) return null;
  for (const token of msg.trim().split(/\s+/)) {
    if (!SIGNOFFS.has(token) && LOCATOR.test(token)) return token.slice(0, 2);
  }
  return null;
}

/**
 * Rectangle of a Maidenhead cell on an equirectangular canvas `width`×`height`.
 * Accepts a 2-char field (20°×10°) or a 4-char square (2°×1°) and returns
 * { x, y, w, h }, or null if the cell is malformed.
 */
export function cellRect(cell, width, height) {
  if (!cell || cell.length < 2) return null;
  const f0 = cell.charCodeAt(0) - 65; // lon field 0..17
  const f1 = cell.charCodeAt(1) - 65; // lat field 0..17 (from the south)
  if (f0 < 0 || f0 > 17 || f1 < 0 || f1 > 17) return null;

  if (cell.length >= 4) {
    const s0 = cell.charCodeAt(2) - 48; // lon square 0..9
    const s1 = cell.charCodeAt(3) - 48; // lat square 0..9
    if (s0 < 0 || s0 > 9 || s1 < 0 || s1 > 9) return null;
    const w = width / 180;
    const h = height / 180;
    const col = f0 * 10 + s0; // 0..179
    const latIdx = f1 * 10 + s1; // 0..179 from the south
    return { x: col * w, y: (179 - latIdx) * h, w, h };
  }

  const w = width / 18;
  const h = height / 18;
  return { x: f0 * w, y: (17 - f1) * h, w, h };
}

/** Center {lat, lon} of a 4- or 6-char Maidenhead locator, or null if invalid. */
export function locatorCenter(loc) {
  if (!loc) return null;
  const s = loc.trim().toUpperCase();
  if (!LOCATOR.test(s)) return null;
  let lon = (s.charCodeAt(0) - 65) * 20 - 180;
  let lat = (s.charCodeAt(1) - 65) * 10 - 90;
  lon += (s.charCodeAt(2) - 48) * 2;
  lat += (s.charCodeAt(3) - 48) * 1;
  if (s.length === 6) {
    lon += (s.charCodeAt(4) - 65) * (5 / 60) + (5 / 60) / 2;   // subsquare: 5' lon
    lat += (s.charCodeAt(5) - 65) * (2.5 / 60) + (2.5 / 60) / 2; // 2.5' lat
  } else {
    lon += 1; // center of the 2° square
    lat += 0.5; // center of the 1° square
  }
  return { lat, lon };
}
