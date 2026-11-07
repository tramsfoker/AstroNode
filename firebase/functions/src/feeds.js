// RSS/Atom akışlarını çekip "news" koleksiyonuna yazar.
import { createHash } from 'node:crypto';
import { XMLParser } from 'fast-xml-parser';
import { FieldValue, Timestamp } from 'firebase-admin/firestore';

/** feed_sources koleksiyonu boşsa kullanılan varsayılan kaynaklar (admin panelinden değiştirilebilir) */
export const DEFAULT_SOURCES = [
  { id: 'nasa-apod', name: 'NASA APOD', url: 'https://apod.nasa.gov/apod.rss', lang: 'en', enabled: true },
  { id: 'nasa-news', name: 'NASA', url: 'https://www.nasa.gov/news-release/feed/', lang: 'en', enabled: true },
  { id: 'esa-science', name: 'ESA Space Science', url: 'https://www.esa.int/rssfeed/Our_Activities/Space_Science', lang: 'en', enabled: true },
  { id: 'darksky', name: 'DarkSky International', url: 'https://darksky.org/feed/', lang: 'en', enabled: true },
  { id: 'skytel', name: 'Sky & Telescope', url: 'https://skyandtelescope.org/feed/', lang: 'en', enabled: true },
];

const MAX_ITEMS_PER_SOURCE = 15;
const SUMMARY_MAX = 300;
export const NEWS_RETENTION_DAYS = 60;

const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: '@_',
  textNodeName: '#text',
  cdataPropName: '#cdata',
  processEntities: true,
});

const text = (v) => {
  if (v == null) return '';
  if (typeof v === 'string' || typeof v === 'number') return String(v);
  if (Array.isArray(v)) return text(v[0]);
  return text(v['#cdata'] ?? v['#text'] ?? '');
};

export function stripHtml(s) {
  return s
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;|&#8217;/g, "'")
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&#8230;|&hellip;/g, '…')
    .replace(/\s+/g, ' ')
    .trim();
}

function truncate(s, n) {
  return s.length <= n ? s : s.slice(0, n - 1).replace(/\s+\S*$/, '') + '…';
}

function firstImage(item, html) {
  const enc = Array.isArray(item.enclosure) ? item.enclosure[0] : item.enclosure;
  if (enc?.['@_url'] && /^image\//.test(enc['@_type'] ?? 'image/')) return enc['@_url'];
  const media = item['media:content'] ?? item['media:thumbnail'];
  const m = Array.isArray(media) ? media[0] : media;
  if (m?.['@_url']) return m['@_url'];
  const img = /<img[^>]+src=["']([^"']+)["']/i.exec(html);
  return img ? img[1] : null;
}

const isHttpUrl = (u) => typeof u === 'string' && /^https?:\/\//i.test(u);

/** RSS 2.0 veya Atom metnini ortak biçime çevirir. */
export function parseFeed(xml) {
  const doc = parser.parse(xml);
  const rssItems = doc?.rss?.channel?.item;
  const atomEntries = doc?.feed?.entry;
  const raw = rssItems ?? atomEntries ?? [];
  const items = Array.isArray(raw) ? raw : [raw];
  return items.map((it) => {
    const isAtom = !rssItems;
    let link = isAtom
      ? (Array.isArray(it.link) ? (it.link.find((l) => l['@_rel'] === 'alternate') ?? it.link[0]) : it.link)?.['@_href']
      : text(it.link);
    const html = text(it['content:encoded'] ?? it.description ?? it.summary ?? it.content);
    const dateStr = text(it.pubDate ?? it.published ?? it.updated ?? it['dc:date']);
    const date = dateStr ? new Date(dateStr) : null;
    return {
      title: truncate(stripHtml(text(it.title)), 200),
      summary: truncate(stripHtml(html), SUMMARY_MAX),
      link: (link ?? '').trim(),
      image_url: firstImage(it, html),
      published_at: date && !isNaN(date) ? date : null,
    };
  }).filter((x) => x.title && isHttpUrl(x.link));
}

export const newsIdFor = (link) => 'rss_' + createHash('sha1').update(link).digest('hex').slice(0, 20);

async function fetchText(url, timeoutMs = 15000) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      signal: ctrl.signal,
      headers: { 'User-Agent': 'AstroNodeFeedBot/1.0 (+https://astronode-50a6c.web.app)', Accept: 'application/rss+xml, application/atom+xml, application/xml, text/xml' },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.text();
  } finally {
    clearTimeout(timer);
  }
}

/** Tüm etkin kaynakları çeker; hata veren kaynak diğerlerini durdurmaz. */
export async function fetchAllFeeds(db, { fetcher = fetchText, now = new Date() } = {}) {
  const configured = (await db.collection('feed_sources').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  const sources = (configured.length ? configured : DEFAULT_SOURCES).filter((s) => s.enabled !== false);
  const report = [];
  for (const src of sources) {
    try {
      const items = parseFeed(await fetcher(src.url)).slice(0, MAX_ITEMS_PER_SOURCE);
      const writer = db.bulkWriter();
      for (const it of items) {
        const published = it.published_at && it.published_at <= now ? it.published_at : now;
        writer.set(db.doc(`news/${newsIdFor(it.link)}`), {
          type: 'rss',
          source_id: src.id,
          source_name: src.name,
          lang: src.lang ?? 'en',
          title: it.title,
          summary: it.summary,
          link: it.link,
          image_url: isHttpUrl(it.image_url) ? it.image_url : null,
          published_at: Timestamp.fromDate(published),
          pinned: false,
          fetched_at: FieldValue.serverTimestamp(),
        }, { merge: true });
      }
      await writer.close();
      report.push({ source: src.id, items: items.length });
    } catch (e) {
      report.push({ source: src.id, error: String(e.message ?? e) });
    }
  }
  // Eski RSS haberlerini temizle (duyurular — type "post" — silinmez)
  const cutoff = Timestamp.fromDate(new Date(now.getTime() - NEWS_RETENTION_DAYS * 86400000));
  const old = await db.collection('news').where('type', '==', 'rss').where('published_at', '<', cutoff).limit(400).get();
  if (!old.empty) {
    const b = db.batch();
    old.docs.forEach((d) => b.delete(d.ref));
    await b.commit();
  }
  return report;
}
