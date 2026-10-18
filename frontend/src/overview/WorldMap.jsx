import { useMemo } from 'react';
import { feature } from 'topojson-client';
import landTopo from 'world-atlas/land-110m.json';
import { fieldCell } from '../lib/maidenhead.js';

// Equirectangular canvas: 360° × 180° mapped 2:1. Each Maidenhead field (20°×10°)
// is exactly one cell of an even 18×18 grid.
const W = 720;
const H = 360;
const COLS = 18;
const ROWS = 18;
const CELL_W = W / COLS; // 40
const CELL_H = H / ROWS; // 20

// Viewports: the whole world, or a crop over Europe (same projection).
export const REGIONS = {
  world: { label: 'World', viewBox: `0 0 ${W} ${H}` },
  // lon [-15, 40], lat [34, 72]
  europe: { label: 'Europe', viewBox: '330 36 110 76' },
};

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

// Build the land outline path once (static coastline data). The land object is
// a GeometryCollection, so feature() yields a FeatureCollection.
const LAND_PATH = (() => {
  const fc = feature(landTopo, landTopo.objects.land);
  const features = fc.features ?? [fc];
  return features.map((f) => pathFromGeometry(f.geometry)).join('');
})();

// Static 18×18 grid lines.
const GRID_LINES = (() => {
  const lines = [];
  for (let c = 0; c <= COLS; c++) lines.push(`M${c * CELL_W} 0V${H}`);
  for (let r = 0; r <= ROWS; r++) lines.push(`M0 ${r * CELL_H}H${W}`);
  return lines.join('');
})();

/**
 * Equirectangular activity map: land background, Maidenhead field grid, and a
 * heat cell per active field (opacity scales with its message count).
 */
export default function WorldMap({ counts, region = 'world' }) {
  const view = REGIONS[region] ?? REGIONS.world;

  const cells = useMemo(() => {
    const max = Math.max(1, ...Object.values(counts));
    return Object.entries(counts)
      .map(([field, count]) => {
        const cell = fieldCell(field);
        if (!cell) return null;
        const intensity = Math.sqrt(count / max); // compress the range a little
        return {
          field,
          count,
          x: cell.col * CELL_W,
          y: cell.rowFromTop * CELL_H,
          opacity: 0.15 + 0.75 * intensity,
        };
      })
      .filter(Boolean);
  }, [counts]);

  return (
    <svg
      className="worldmap"
      viewBox={view.viewBox}
      preserveAspectRatio="xMidYMid meet"
      xmlns="http://www.w3.org/2000/svg"
    >
      <rect x="0" y="0" width={W} height={H} className="map-ocean" />
      <path d={LAND_PATH} className="map-land" vectorEffect="non-scaling-stroke" />

      {cells.map((c) => (
        <rect
          key={c.field}
          x={c.x}
          y={c.y}
          width={CELL_W}
          height={CELL_H}
          className="map-heat"
          style={{ opacity: c.opacity }}
        >
          <title>{`${c.field}: ${c.count}`}</title>
        </rect>
      ))}

      <path d={GRID_LINES} className="map-grid" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}
