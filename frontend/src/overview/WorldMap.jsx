import { useEffect, useMemo, useRef, useState } from 'react';
import { feature } from 'topojson-client';
import countriesTopo from 'world-atlas/countries-50m.json';
import { cellRect } from '../lib/maidenhead.js';

// Equirectangular canvas: 360° × 180° mapped 2:1. Each Maidenhead field (20°×10°)
// is exactly one cell of an even 18×18 grid.
const W = 720;
const H = 360;
const COLS = 18;
const ROWS = 18;
const CELL_W = W / COLS; // 40
const CELL_H = H / ROWS; // 20

const WORLD_BOX = [0, 0, W, H];
// Europe + North Africa + the Levant: lon [-20, 45], lat [18, 72].
const EUROPE_BOX = [320, 36, 130, 108];

// Preset viewports (reset buttons); free zoom/pan works on top of them.
export const REGIONS = {
  world: { label: 'World', box: WORLD_BOX },
  europe: { label: 'Europe', box: EUROPE_BOX },
};

const MIN_W = 10; // strongest zoom-in (map units of width)
const HOME_R_WORLD = 2.5; // marker radius at world scale (world units)

const project = (lon, lat) => [((lon + 180) / 360) * W, ((90 - lat) / 180) * H];

function pathFromGeometry(geom) {
  const polys =
    geom.type === 'Polygon' ? [geom.coordinates] : geom.type === 'MultiPolygon' ? geom.coordinates : [];
  let d = '';
  for (const poly of polys) {
    for (const ring of poly) {
      ring.forEach(([lon, lat], i) => {
        const [x, y] = project(lon, lat);
        d += (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1);
      });
      d += 'Z';
    }
  }
  return d;
}

// Country polygons once: filled as land, stroked as borders/coastlines.
const COUNTRIES_PATH = (() => {
  const fc = feature(countriesTopo, countriesTopo.objects.countries);
  const features = fc.features ?? [fc];
  return features.map((f) => pathFromGeometry(f.geometry)).join('');
})();

// Static 18×18 Maidenhead field grid.
const GRID_LINES = (() => {
  const lines = [];
  for (let c = 0; c <= COLS; c++) lines.push(`M${c * CELL_W} 0V${H}`);
  for (let r = 0; r <= ROWS; r++) lines.push(`M0 ${r * CELL_H}H${W}`);
  return lines.join('');
})();

function clampView([x, y, w, h]) {
  if (w > W) {
    h = (h * W) / w;
    w = W;
  }
  if (h > H) {
    w = (w * H) / h;
    h = H;
  }
  x = Math.min(Math.max(x, 0), W - w);
  y = Math.min(Math.max(y, 0), H - h);
  return [x, y, w, h];
}

function clientToUser(svg, cx, cy) {
  const pt = svg.createSVGPoint();
  pt.x = cx;
  pt.y = cy;
  const ctm = svg.getScreenCTM();
  if (!ctm) return { x: 0, y: 0 };
  const u = pt.matrixTransform(ctm.inverse());
  return { x: u.x, y: u.y };
}

/**
 * Equirectangular activity map with zoomable/pannable viewport: country land +
 * borders, the Maidenhead field grid, a heat cell per active cell, and the
 * own-station marker. Wheel to zoom, drag to pan; the region buttons reset the
 * view. Everything shares the 720×360 projection, so it all zooms together.
 */
export default function WorldMap({
  counts,
  region = 'world',
  home = null,
  colorFor = null, // optional (cell) => CSS color; overrides the count-based heat fill
  onCellEnter = null, // optional (cell, event) => void; enables a custom hover tooltip
  onCellMove = null, // optional (event) => void
  onCellLeave = null, // optional () => void
}) {
  const [view, setView] = useState(() => REGIONS[region]?.box ?? WORLD_BOX);
  const viewRef = useRef(view);
  const svgRef = useRef(null);

  useEffect(() => {
    viewRef.current = view;
  }, [view]);

  // Reset to a preset when the region buttons change it.
  useEffect(() => {
    setView(REGIONS[region]?.box ?? WORLD_BOX);
  }, [region]);

  // Wheel zoom around the cursor (native listener so we can preventDefault).
  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return undefined;
    const onWheel = (e) => {
      e.preventDefault();
      const [x, y, w, h] = viewRef.current;
      const p = clientToUser(svg, e.clientX, e.clientY);
      const factor = e.deltaY < 0 ? 1 / 1.15 : 1.15;
      const nw = Math.min(Math.max(w * factor, MIN_W), W);
      const nh = nw * (h / w);
      const nx = p.x - (p.x - x) * (nw / w);
      const ny = p.y - (p.y - y) * (nh / h);
      setView(clampView([nx, ny, nw, nh]));
    };
    svg.addEventListener('wheel', onWheel, { passive: false });
    return () => svg.removeEventListener('wheel', onWheel);
  }, []);

  const onPointerDown = (e) => {
    const svg = svgRef.current;
    const ctm = svg && svg.getScreenCTM();
    if (!ctm) return;
    const sx = ctm.a;
    const sy = ctm.d;
    const startX = e.clientX;
    const startY = e.clientY;
    const [vx0, vy0, vw0, vh0] = viewRef.current;
    svg.classList.add('panning');
    const move = (ev) => {
      setView(clampView([vx0 + (startX - ev.clientX) / sx, vy0 + (startY - ev.clientY) / sy, vw0, vh0]));
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      svg.classList.remove('panning');
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  const homeXY = home ? project(home.lon, home.lat) : null;
  const homeR = HOME_R_WORLD * (view[2] / W); // constant on-screen size at any zoom

  const cells = useMemo(() => {
    const max = Math.max(1, ...Object.values(counts));
    return Object.entries(counts)
      .map(([cell, count]) => {
        const rect = cellRect(cell, W, H);
        if (!rect) return null;
        const intensity = Math.sqrt(count / max);
        return { cell, count, ...rect, opacity: 0.15 + 0.75 * intensity };
      })
      .filter(Boolean);
  }, [counts]);

  return (
    <svg
      ref={svgRef}
      className="worldmap"
      viewBox={view.join(' ')}
      preserveAspectRatio="xMidYMid meet"
      onPointerDown={onPointerDown}
      xmlns="http://www.w3.org/2000/svg"
    >
      <rect x="0" y="0" width={W} height={H} className="map-ocean" />
      <path d={COUNTRIES_PATH} className="map-land" fillRule="evenodd" vectorEffect="non-scaling-stroke" />

      {cells.map((c) => {
        const color = colorFor ? colorFor(c.cell) : null;
        return (
          <rect
            key={c.cell}
            x={c.x}
            y={c.y}
            width={c.w}
            height={c.h}
            className="map-heat"
            style={color ? { fill: color, opacity: 0.85 } : { opacity: c.opacity }}
            onMouseEnter={onCellEnter ? (e) => onCellEnter(c.cell, e) : undefined}
            onMouseMove={onCellMove ? (e) => onCellMove(e) : undefined}
            onMouseLeave={onCellLeave || undefined}
          >
            {!onCellEnter && <title>{`${c.cell}: ${c.count}`}</title>}
          </rect>
        );
      })}

      <path d={GRID_LINES} className="map-grid" vectorEffect="non-scaling-stroke" />

      {homeXY && (
        <circle
          cx={homeXY[0]}
          cy={homeXY[1]}
          r={homeR}
          className="map-home"
          vectorEffect="non-scaling-stroke"
        >
          <title>Your station</title>
        </circle>
      )}
    </svg>
  );
}
