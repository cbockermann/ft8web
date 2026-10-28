// API base, derived from the servlet context-path the backend injects as
// window.__CONTEXT_PATH__. Empty at root (and in the dev server, where /api is
// proxied), "/ft8" under a context-path. Absolute paths are used so calls work
// regardless of the current client-side route.
const CTX = (typeof window !== 'undefined' && window.__CONTEXT_PATH__) || '';

export const contextPath = CTX;
export const apiBase = `${CTX}/api`;
export const streamUrl = `${apiBase}/stream`;
export const settingsUrl = `${apiBase}/settings`;

// Standalone help pages (opened in a new window). Absolute so they resolve
// correctly under a servlet context-path when opened as a fresh document.
export const filterHelpUrl = `${CTX}/help/filters`;

export function qslUrl(decodeId) {
  return `${apiBase}/qsl/${encodeURIComponent(decodeId)}`;
}

export function recentUrl(limit) {
  return `${apiBase}/recent?limit=${limit}`;
}

// WSJT-X control (MVP TX backend)
export const controlStreamUrl = `${apiBase}/control/stream`;
export const controlStatusUrl = `${apiBase}/control/status`;

export function controlReplyUrl(id) {
  return `${apiBase}/control/reply?id=${encodeURIComponent(id)}`;
}

export function controlHaltUrl(auto = false) {
  return `${apiBase}/control/halt?auto=${auto ? 'true' : 'false'}`;
}

export function controlFrequencyUrl(hz, mode) {
  const m = mode ? `&mode=${encodeURIComponent(mode)}` : '';
  return `${apiBase}/control/frequency?hz=${hz}${m}`;
}

// Saved Control-view filters
export const filtersUrl = `${apiBase}/filters`;
export function filterUrl(name) {
  return `${apiBase}/filters/${encodeURIComponent(name)}`;
}

// QSO log
export const qsosCallsUrl = `${apiBase}/qsos/calls`;
export const qsosImportUrl = `${apiBase}/qsos/import`;
export function qsosUrl(limit = 1000) {
  return `${apiBase}/qsos?limit=${limit}`;
}
