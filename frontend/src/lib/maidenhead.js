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
 * Field "XY" → cell indices on the 18×18 lattice:
 *   col 0..17  (longitude band, A at 180°W)
 *   rowFromTop 0..17  (latitude band from the top, i.e. 90°N down)
 * Returns null for an out-of-range field.
 */
export function fieldCell(field) {
  if (!field || field.length < 2) return null;
  const lon = field.charCodeAt(0) - 65; // 'A' = 0
  const lat = field.charCodeAt(1) - 65; // 'A' = 0 (from the south pole)
  if (lon < 0 || lon > 17 || lat < 0 || lat > 17) return null;
  return { col: lon, rowFromTop: 17 - lat };
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
