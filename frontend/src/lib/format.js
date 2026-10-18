// Format a decode's time as "YYYY-MM-DD HH:MM:SS" (UTC), from either its ISO
// `time` or its `unix` epoch seconds.
export function fmtTime(d) {
  if (d.time) return d.time.replace('T', ' ').replace('Z', '');
  if (d.unix) return new Date(d.unix * 1000).toISOString().replace('T', ' ').replace(/\..*/, '');
  return '';
}
