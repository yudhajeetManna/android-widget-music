import { fetch } from "@tauri-apps/plugin-http";

export type Line = { t: number; text: string };
export type Result = { lines: Line[]; source: string };
type Found = { source: string; text: string; synced: boolean };

/* Chain: LRCLIB (synced) -> NetEase (synced, unofficial) -> LRCLIB plain text with estimated timing */

const noise =
  /[(\[][^)\]]*(official|video|audio|lyric|lyrics|hd|4k|remaster|remastered|live|from|feat|ft\.|with)[^)\]]*[)\]]/gi;
const dashTail = /\s+-\s+(remaster(ed)?|\d{4}|from\s|live|official|lyric).*$/i;
const topic = /\s*-\s*topic$/i;
const artistSplit = /\s*(,|&|;|\sfeat\.?\s|\sft\.?\s|\sx\s)\s*/i;

export const cleanTitle = (s: string) =>
  s.replace(noise, "").replace(dashTail, "").replace(topic, "").trim();
export const firstArtist = (s: string) =>
  s.replace(topic, "").split(artistSplit)[0].trim();

const enc = encodeURIComponent;

async function getJson(url: string): Promise<any | null> {
  try {
    const r = await fetch(url);
    return r.ok ? await r.json() : null;
  } catch {
    return null;
  }
}

export function parseLrc(lrc: string): Line[] {
  const out: Line[] = [];
  for (const row of lrc.split("\n")) {
    const m = row.trim().match(/^\[(\d+):(\d+(?:\.\d+)?)\](.*)$/);
    if (m) out.push({ t: (+m[1] * 60 + +m[2]) * 1000, text: m[3].trim() });
  }
  return out.sort((a, b) => a.t - b.t);
}

/** Plain lyrics have no timestamps: spread the lines evenly across the song. */
function estimate(plain: string, durMs: number): Line[] {
  const rows = plain
    .split("\n")
    .map((r) => r.trim())
    .filter((r) => r && !r.startsWith("["));
  if (!rows.length) return [];
  const total = durMs > 0 ? durMs : 200_000;
  const start = total * 0.06;
  const span = total * 0.88;
  return rows.map((text, i) => ({ t: start + (span * i) / rows.length, text }));
}

function toResult(f: Found, durMs: number): Result {
  return f.synced
    ? { lines: parseLrc(f.text), source: f.source }
    : { lines: estimate(f.text, durMs), source: f.source + " est." };
}

const toFound = (o: any): Found | null => {
  if (!o) return null;
  if (o.syncedLyrics) return { source: "LRCLIB", text: o.syncedLyrics, synced: true };
  if (o.plainLyrics) return { source: "LRCLIB", text: o.plainLyrics, synced: false };
  return null;
};

const dist = (o: any, durSec: number) =>
  durSec > 0 && o.duration > 0 ? Math.abs(o.duration - durSec) : 5;

function pickBest(arr: any, durSec: number): Found | null {
  if (!Array.isArray(arr)) return null;
  let best: any = null;
  let bestScore = Infinity;
  for (const o of arr) {
    if (!o.syncedLyrics) continue;
    const s = dist(o, durSec);
    if (s <= 12 && s < bestScore) { best = o; bestScore = s; }
  }
  if (best) return { source: "LRCLIB", text: best.syncedLyrics, synced: true };
  for (const o of arr) {
    if (o.plainLyrics && dist(o, durSec) <= 15)
      return { source: "LRCLIB", text: o.plainLyrics, synced: false };
  }
  return null;
}

async function lrclib(title: string, artist: string, album: string, durMs: number) {
  const dur = Math.round(durMs / 1000);
  const base = `track_name=${enc(title)}&artist_name=${enc(artist)}`;
  const exact =
    base + (album ? `&album_name=${enc(album)}` : "") + (dur > 0 ? `&duration=${dur}` : "");
  const attempts: (() => Promise<Found | null>)[] = [
    async () => toFound(await getJson(`https://lrclib.net/api/get?${exact}`)),
    async () => toFound(await getJson(`https://lrclib.net/api/get?${base}`)),
    async () => pickBest(await getJson(`https://lrclib.net/api/search?${base}`), dur),
    async () => pickBest(await getJson(`https://lrclib.net/api/search?q=${enc(title)}`), dur),
    async () =>
      pickBest(await getJson(`https://lrclib.net/api/search?q=${enc(artist + " " + title)}`), dur),
  ];
  let plain: Found | null = null;
  for (const a of attempts) {
    const f = await a();
    if (!f) continue;
    if (f.synced) return f;
    plain ??= f;
  }
  return plain;
}

async function netease(title: string, artist: string, durMs: number): Promise<Found | null> {
  const s = await getJson(
    `https://music.163.com/api/search/get/web?csrf_token=&type=1&offset=0&limit=6&s=${enc(artist + " " + title)}`
  );
  const songs = s?.result?.songs;
  if (!Array.isArray(songs)) return null;
  let id = -1;
  let best = Infinity;
  songs.forEach((o: any, i: number) => {
    const d = o.duration ?? o.dt ?? 0;
    const score = durMs > 0 && d > 0 ? Math.abs(d - durMs) : 5000 + i;
    if (score < best) { best = score; id = o.id; }
  });
  if (id < 0 || best > 12000) return null;
  const l = await getJson(`https://music.163.com/api/song/lyric?id=${id}&lv=1&kv=1&tv=-1`);
  const txt = l?.lrc?.lyric;
  return txt ? { source: "NetEase", text: txt, synced: true } : null;
}

export async function getLyrics(
  rawTitle: string, rawArtist: string, album: string, durMs: number, force = false
): Promise<Result> {
  let title = cleanTitle(rawTitle);
  let artist = firstArtist(rawArtist);
  if (!artist && title.includes(" - ")) {
    artist = title.split(" - ")[0].trim();
    title = title.split(" - ").slice(1).join(" - ").trim();
  }
  const key = `lyr2:${artist}|${title}`;
  if (force) localStorage.removeItem(key);
  const cached = localStorage.getItem(key);
  if (cached) {
    try { return toResult(JSON.parse(cached), durMs); } catch { /* refetch */ }
  }
  let found: Found | null = null;
  try {
    const lrc = await lrclib(title, artist, album, durMs);
    found = lrc && lrc.synced ? lrc : (await netease(title, artist, durMs)) ?? lrc;
  } catch { /* offline */ }
  if (!found) return { lines: [], source: "none" };
  localStorage.setItem(key, JSON.stringify(found));
  return toResult(found, durMs);
}
