// API base, derived from the servlet context-path the backend injects as
// window.__CONTEXT_PATH__. Empty at root (and in the dev server, where /api is
// proxied), "/ft8" under a context-path. Absolute paths are used so calls work
// regardless of the current client-side route.
const CTX = (typeof window !== 'undefined' && window.__CONTEXT_PATH__) || '';

export const contextPath = CTX;
export const apiBase = `${CTX}/api`;
export const streamUrl = `${apiBase}/stream`;
export const settingsUrl = `${apiBase}/settings`;

export function qslUrl(decodeId) {
  return `${apiBase}/qsl/${encodeURIComponent(decodeId)}`;
}
