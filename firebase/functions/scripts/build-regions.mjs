// geoBoundaries (OSM, ODbL) GeoJSON'larından Cloud Functions'ın kullandığı kompakt bölge dosyasını üretir.
// Kullanım: node scripts/build-regions.mjs <ADM1.geojson> <ADM2.geojson>
// Kaynak: https://github.com/wmgeolab/geoBoundaries (gbOpen/TUR, simplified) — © OpenStreetMap katkıcıları
import { readFileSync, writeFileSync } from 'node:fs';
import { toPolygons, bboxOf, pointInArea, ringCentroid } from '../src/geo.js';

const [adm1Path, adm2Path] = process.argv.slice(2);
const round = (polys) => polys.map((p) => p.map((r) => r.map(([x, y]) => [+x.toFixed(4), +y.toFixed(4)])));
const load = (p) => JSON.parse(readFileSync(p, 'utf8')).features;

const provinces = load(adm1Path).map((f) => {
  const polygons = round(toPolygons(f.geometry));
  return { name: f.properties.shapeName, iso: f.properties.shapeISO || null, bbox: bboxOf(polygons), polygons };
});

let unmatched = 0;
const districts = load(adm2Path).map((f) => {
  const polygons = round(toPolygons(f.geometry));
  // En büyük halkanın merkezi hangi ilin içindeyse ilçe o ile aittir
  const biggest = polygons.reduce((a, b) => (b[0].length > a[0].length ? b : a));
  let [cx, cy] = ringCentroid(biggest[0]);
  let prov = provinces.find((p) => pointInArea(cx, cy, p));
  if (!prov) {
    // Merkez dışarıda kaldıysa (hilal biçimli ilçe) köşe noktalarından oy çokluğu
    const votes = new Map();
    for (const [x, y] of biggest[0]) {
      const p = provinces.find((pp) => pointInArea(x, y, pp));
      if (p) votes.set(p.name, (votes.get(p.name) || 0) + 1);
    }
    const best = [...votes.entries()].sort((a, b) => b[1] - a[1])[0];
    prov = best && provinces.find((p) => p.name === best[0]);
  }
  if (!prov) unmatched++;
  return { name: f.properties.shapeName, province: prov ? prov.name : null, bbox: bboxOf(polygons), polygons };
});

const out = {
  country: 'TR',
  source: 'geoBoundaries gbOpen TUR ADM1/ADM2 (simplified) — © OpenStreetMap contributors, ODbL 1.0',
  provinces,
  districts,
};
writeFileSync(new URL('../data/tr-regions.json', import.meta.url), JSON.stringify(out));
console.log(`il: ${provinces.length}, ilçe: ${districts.length}, eşleşmeyen ilçe: ${unmatched}`);
