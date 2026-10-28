// Small predicate language for the Control-view decode filter.
//
// Grammar (low→high precedence): or, and, not, comparison, primary.
// Examples:
//   snr > -18 and not worked
//   cq and (new or last_worked > 30days) and distance < 3000
//   country in ("DL","F","G") and db >= -15
//   call matches "HB9*"
//
// Fields/funcs resolve against a decode's backend facts (see ctxFor). The
// predicate *values* are computed on the backend; this only does the boolean
// logic, so it stays a thin, testable tree-walk (mirrored later in the Java
// auto-responder engine).

const UNITS = {
  d: 86400, day: 86400, days: 86400,
  h: 3600, hr: 3600, hour: 3600, hours: 3600,
  m: 60, min: 60, mins: 60, minute: 60, minutes: 60,
  s: 1, sec: 1, secs: 1, second: 1, seconds: 1,
};
const KEYWORDS = new Set(['and', 'or', 'not', 'in', 'matches', 'true', 'false']);

function tokenize(src) {
  const toks = [];
  let i = 0;
  const prevType = () => (toks.length ? toks[toks.length - 1].t : null);
  while (i < src.length) {
    const c = src[i];
    if (/\s/.test(c)) {
      i += 1;
      continue;
    }
    // negative number: '-' before a digit, in value position
    if (c === '-' && /[0-9.]/.test(src[i + 1] || '') && !['num', 'str', 'ident', ')'].includes(prevType())) {
      let j = i + 1;
      while (j < src.length && /[0-9.]/.test(src[j])) j += 1;
      toks.push({ t: 'num', v: parseFloat(src.slice(i, j)) });
      i = j;
      continue;
    }
    if (/[0-9.]/.test(c)) {
      let j = i;
      while (j < src.length && /[0-9.]/.test(src[j])) j += 1;
      const num = parseFloat(src.slice(i, j));
      // optional duration unit directly after the number
      let k = j;
      while (k < src.length && /[a-zA-Z]/.test(src[k])) k += 1;
      const unit = src.slice(j, k).toLowerCase();
      if (k > j && UNITS[unit]) {
        toks.push({ t: 'num', v: num * UNITS[unit] });
        i = k;
      } else {
        toks.push({ t: 'num', v: num });
        i = j;
      }
      continue;
    }
    if (c === '"' || c === "'") {
      let j = i + 1;
      while (j < src.length && src[j] !== c) j += 1;
      toks.push({ t: 'str', v: src.slice(i + 1, j) });
      i = j + 1;
      continue;
    }
    if (/[a-zA-Z_]/.test(c)) {
      let j = i;
      while (j < src.length && /[a-zA-Z0-9_]/.test(src[j])) j += 1;
      const word = src.slice(i, j);
      const lw = word.toLowerCase();
      toks.push(KEYWORDS.has(lw) ? { t: lw } : { t: 'ident', v: word.toLowerCase() });
      i = j;
      continue;
    }
    const two = src.slice(i, i + 2);
    if (['>=', '<=', '==', '!='].includes(two)) {
      toks.push({ t: two });
      i += 2;
      continue;
    }
    if ('><=(),'.includes(c)) {
      toks.push({ t: c === '=' ? '==' : c });
      i += 1;
      continue;
    }
    throw new Error(`Unexpected character "${c}"`);
  }
  return toks;
}

function parse(toks) {
  let p = 0;
  const peek = () => toks[p];
  const next = () => toks[p++];
  const expect = (t) => {
    if (!peek() || peek().t !== t) throw new Error(`Expected "${t}"`);
    return next();
  };

  function parseOr() {
    let left = parseAnd();
    while (peek() && peek().t === 'or') {
      next();
      left = { t: 'or', l: left, r: parseAnd() };
    }
    return left;
  }
  function parseAnd() {
    let left = parseNot();
    while (peek() && peek().t === 'and') {
      next();
      left = { t: 'and', l: left, r: parseNot() };
    }
    return left;
  }
  function parseNot() {
    if (peek() && peek().t === 'not') {
      next();
      return { t: 'not', e: parseNot() };
    }
    return parseCmp();
  }
  function parseCmp() {
    const left = parsePrimary();
    const op = peek() && peek().t;
    if (['>', '<', '>=', '<=', '==', '!='].includes(op)) {
      next();
      return { t: 'cmp', op, l: left, r: parsePrimary() };
    }
    if (op === 'in') {
      next();
      expect('(');
      const items = [];
      if (peek() && peek().t !== ')') {
        items.push(parsePrimary());
        while (peek() && peek().t === ',') {
          next();
          items.push(parsePrimary());
        }
      }
      expect(')');
      return { t: 'in', l: left, items };
    }
    if (op === 'matches') {
      next();
      return { t: 'matches', l: left, r: parsePrimary() };
    }
    return left;
  }
  function parsePrimary() {
    const tk = peek();
    if (!tk) throw new Error('Unexpected end of expression');
    if (tk.t === '(') {
      next();
      const e = parseOr();
      expect(')');
      return e;
    }
    if (tk.t === 'num') return next() && { t: 'num', v: tk.v };
    if (tk.t === 'str') return next() && { t: 'str', v: tk.v };
    if (tk.t === 'true') return next() && { t: 'bool', v: true };
    if (tk.t === 'false') return next() && { t: 'bool', v: false };
    if (tk.t === 'ident') {
      next();
      if (peek() && peek().t === '(') {
        // function form worked(call) etc. — args are ignored (subject call)
        next();
        let depth = 1;
        while (peek() && depth > 0) {
          const t = next().t;
          if (t === '(') depth += 1;
          else if (t === ')') depth -= 1;
        }
      }
      return { t: 'field', name: tk.v };
    }
    throw new Error(`Unexpected token "${tk.t}"`);
  }

  const ast = parseOr();
  if (p < toks.length) throw new Error(`Unexpected token "${peek().t}"`);
  return ast;
}

function truthy(v) {
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') return !Number.isNaN(v) && v !== 0;
  if (typeof v === 'string') return v.length > 0;
  return !!v;
}

function evalNode(n, ctx) {
  switch (n.t) {
    case 'or':
      return truthy(evalNode(n.l, ctx)) || truthy(evalNode(n.r, ctx));
    case 'and':
      return truthy(evalNode(n.l, ctx)) && truthy(evalNode(n.r, ctx));
    case 'not':
      return !truthy(evalNode(n.e, ctx));
    case 'num':
    case 'str':
    case 'bool':
      return n.v;
    case 'field':
      return ctx(n.name);
    case 'cmp': {
      const a = evalNode(n.l, ctx);
      const b = evalNode(n.r, ctx);
      if (n.op === '==' || n.op === '!=') {
        const eq = typeof a === 'string' || typeof b === 'string'
          ? String(a).toLowerCase() === String(b).toLowerCase()
          : a === b;
        return n.op === '==' ? eq : !eq;
      }
      const x = Number(a);
      const y = Number(b);
      if (Number.isNaN(x) || Number.isNaN(y)) return false;
      if (n.op === '>') return x > y;
      if (n.op === '<') return x < y;
      if (n.op === '>=') return x >= y;
      return x <= y;
    }
    case 'in': {
      const a = String(evalNode(n.l, ctx)).toLowerCase();
      return n.items.some((it) => String(evalNode(it, ctx)).toLowerCase() === a);
    }
    case 'matches': {
      const a = String(evalNode(n.l, ctx));
      const glob = String(evalNode(n.r, ctx));
      const re = new RegExp(
        '^' + glob.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.') + '$',
        'i',
      );
      return re.test(a);
    }
    default:
      return false;
  }
}

/** Compile an expression once; returns { ok, test, error }. */
export function compileFilter(src) {
  if (!src || !src.trim()) return { ok: true, empty: true, test: () => true };
  try {
    const ast = parse(tokenize(src));
    return { ok: true, test: (ctx) => truthy(evalNode(ast, ctx)) };
  } catch (e) {
    return { ok: false, error: e.message, test: () => true };
  }
}

/** Field resolver for a decode (base fields + backend facts). */
export function ctxFor(d) {
  const f = d.facts || {};
  const table = {
    snr: d.snr,
    db: d.snr,
    dt: d.dt,
    df: d.df,
    band: d.band || '',
    mode: d.mode || '',
    message: d.message || '',
    cq: !!d.cq,
    is_cq: !!d.cq,
    call: f.call || '',
    grid: f.grid || '',
    cc: f.cc || '',
    country: f.country || '',
    worked: !!f.worked,
    new: !f.worked,
    last_worked: f.lastWorkedSec == null ? Infinity : f.lastWorkedSec,
    worked_count: f.workedCount || 0,
    distance: f.distanceKm == null ? NaN : f.distanceKm,
    directed_at_me: !!f.directedAtMe,
    age: f.ageSec || 0,
  };
  return (name) => (name in table ? table[name] : undefined);
}
