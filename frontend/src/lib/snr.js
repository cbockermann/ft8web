// Sequential color scale for FT8 reception level (SNR, dB). One warm ramp,
// dim → bright, monotonic in lightness so a stronger signal reads as "hotter"
// and stays legible on the dark map surface. Magnitude → sequential (not a
// rainbow, not diverging).

export const SNR_MIN = -24; // typical FT8 decode floor
export const SNR_MAX = 3; // strong local signal

// Warm ramp stops [t, [r, g, b]] from weak to strong.
const STOPS = [
  [0.0, [58, 35, 18]], // #3a2312 dark ember
  [0.25, [138, 51, 20]], // #8a3314
  [0.5, [207, 90, 28]], // #cf5a1c
  [0.75, [240, 147, 43]], // #f0932b
  [1.0, [255, 216, 90]], // #ffd85a bright gold
];

const NO_DATA = '#6b7280';

function lerp(a, b, t) {
  return Math.round(a + (b - a) * t);
}

/** Color for a mean SNR value (dB), clamped to the scale's domain. */
export function snrColor(db) {
  if (db == null || Number.isNaN(db)) return NO_DATA;
  let t = (db - SNR_MIN) / (SNR_MAX - SNR_MIN);
  t = Math.max(0, Math.min(1, t));
  for (let i = 1; i < STOPS.length; i++) {
    if (t <= STOPS[i][0]) {
      const [t0, c0] = STOPS[i - 1];
      const [t1, c1] = STOPS[i];
      const u = (t - t0) / (t1 - t0);
      return `rgb(${lerp(c0[0], c1[0], u)}, ${lerp(c0[1], c1[1], u)}, ${lerp(c0[2], c1[2], u)})`;
    }
  }
  const last = STOPS[STOPS.length - 1][1];
  return `rgb(${last[0]}, ${last[1]}, ${last[2]})`;
}

/** CSS gradient (weak → strong) for a legend bar. */
export const SNR_GRADIENT = `linear-gradient(to right, ${STOPS.map(
  ([, c]) => `rgb(${c[0]}, ${c[1]}, ${c[2]})`,
).join(', ')})`;

/** Format a dB value with an explicit sign. */
export function fmtDb(v, decimals = 0) {
  if (v == null || Number.isNaN(v)) return '—';
  const s = v.toFixed(decimals);
  return v > 0 ? `+${s}` : s;
}
