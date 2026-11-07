// Ölçüm konumundan il/ilçe bulur (şimdilik Türkiye; diğer ülkeler için sadece geohash hücresi).
import { readFileSync } from 'node:fs';
import { pointInArea } from './geo.js';

let cache = null;
function data() {
  if (!cache) cache = JSON.parse(readFileSync(new URL('../data/tr-regions.json', import.meta.url), 'utf8'));
  return cache;
}

/** Türkiye'nin kaba sınır kutusu — dışındaki noktalar için poligon taraması yapılmaz */
const TR_BBOX = [25.5, 35.7, 45.0, 42.3];

/**
 * @returns {{country:string, admin1:string, admin2:string|null} | null}
 */
export function findRegion(lat, lng) {
  if (lng < TR_BBOX[0] || lng > TR_BBOX[2] || lat < TR_BBOX[1] || lat > TR_BBOX[3]) return null;
  const d = data();
  const district = d.districts.find((x) => pointInArea(lng, lat, x));
  if (district) return { country: 'TR', admin1: district.province, admin2: district.name };
  const province = d.provinces.find((x) => pointInArea(lng, lat, x));
  return province ? { country: 'TR', admin1: province.name, admin2: null } : null;
}

/** Firestore belge kimliği için güvenli anahtar: "TR__Bursa__Nilüfer" */
export function regionKey(country, admin1, admin2) {
  const clean = (s) => String(s).replace(/[\/.#$\[\]]/g, '-');
  return [country, admin1, admin2].filter(Boolean).map(clean).join('__');
}
