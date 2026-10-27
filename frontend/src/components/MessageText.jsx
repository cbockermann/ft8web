import { tokenizeMessage } from '../lib/callsign.js';
import { useWorkedCalls } from '../qsolog/WorkedCallsProvider.jsx';

/**
 * Render a decode's message. Callsigns we've worked before are underlined
 * (`.worked`). With `flagFor` (callsign flags enabled) a small country flag is
 * shown after each callsign. Falls back to the plain string when there is nothing
 * to mark.
 */
export default function MessageText({ msg, flagFor }) {
  const { isWorked } = useWorkedCalls();
  const tokens = tokenizeMessage(msg);
  const anyWorked = tokens.some((t) => t.call && isWorked(t.call));
  if (!flagFor && !anyWorked) return msg;

  return tokens.map((tk, i) => {
    const info = tk.call && flagFor ? flagFor(tk.call) : null;
    const worked = tk.call && isWorked(tk.call);
    return (
      <span key={i}>
        {i > 0 ? ' ' : ''}
        {worked ? (
          <span className="worked" title="Worked before">
            {tk.text}
          </span>
        ) : (
          tk.text
        )}
        {info && info.flag ? (
          <span className="flag" title={info.country}>
            {' '}
            {info.flag}
          </span>
        ) : null}
      </span>
    );
  });
}
