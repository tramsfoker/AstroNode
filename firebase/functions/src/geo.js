// Nokta-poligon testi (ray casting) ve bölge bulma. Harici bağımlılık yok.

/** ring: [[lng,lat],...] */
function pointInRing(lng, lat, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i];
    const [xj, yj] = ring[j];
    if ((yi > lat) !== (yj > lat) && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

/** polygon: [outerRing, ...holes] */
function pointInPolygon(lng, lat, polygon) {
  if (!pointInRing(lng, lat, polygon[0])) return false;
  for (let k = 1; k < polygon.length; k++) if (pointInRing(lng, lat, polygon[k])) return false;
  return true;
}

/** area: { bbox:[minLng,minLat,maxLng,maxLat], polygons:[polygon,...] } */
export function pointInArea(lng, lat, area) {
  const [a, b, c, d] = area.bbox;
  if (lng < a || lng > c || lat < b || lat > d) return false;
  return area.polygons.some((p) => pointInPolygon(lng, lat, p));
}

/** GeoJSON geometry → polygons listesi */
export function toPolygons(geometry) {
  if (geometry.type === 'Polygon') return [geometry.coordinates];
  if (geometry.type === 'MultiPolygon') return geometry.coordinates;
  return [];
}

export function bboxOf(polygons) {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const p of polygons) for (const [x, y] of p[0]) {
    if (x < minX) minX = x; if (y < minY) minY = y; if (x > maxX) maxX = x; if (y > maxY) maxY = y;
  }
  return [minX, minY, maxX, maxY];
}

/** Halkanın ağırlık merkezi (yaklaşık) */
export function ringCentroid(ring) {
  let x = 0, y = 0;
  for (const [px, py] of ring) { x += px; y += py; }
  return [x / ring.length, y / ring.length];
}
