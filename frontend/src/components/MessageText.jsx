import { tokenizeMessage } from '../lib/callsign.js';

/**
 * Render a decode's message. With `flagFor` (callsign flags enabled), a small
 * country flag is shown right after each callsign token; otherwise the plain
 * message text.
 */
export default function MessageText({ msg, flagFor }) {
  if (!flagFor) return msg;
  const tokens = tokenizeMessage(msg);
  return tokens.map((tk, i) => {
    const info = tk.call ? flagFor(tk.call) : null;
    return (
      <span key={i}>
        {i > 0 ? ' ' : ''}
        {tk.text}
        {info && info.flag ? (
          <span className="flag" title={info.country}>
            {' '}
            {info.flag}
          </span>
        ) : null}
      </span>
    );
  });
}
