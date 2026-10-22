import { useEffect, useRef, useState } from "react";
import { fetch } from "@tauri-apps/plugin-http";
import { invoke } from "@tauri-apps/api/core";

type Song = { id: number; title: string; artist: string; album: string; art: string; preview: string };

const country = (navigator.language.split("-")[1] || "US").toUpperCase();

async function searchSongs(q: string): Promise<Song[] | null> {
  try {
    const r = await fetch(
      `https://itunes.apple.com/search?media=music&entity=song&limit=30&country=${country}&term=${encodeURIComponent(q)}`
    );
    if (!r.ok) return null;
    const j = await r.json();
    return (j.results as any[]).map((o) => ({
      id: o.trackId,
      title: o.trackName,
      artist: o.artistName,
      album: o.collectionName ?? "",
      art: o.artworkUrl60 ?? o.artworkUrl100 ?? "",
      preview: o.previewUrl ?? "",
    }));
  } catch {
    return null;
  }
}

export default function Search({
  onClose,
  initialQuery = "",
  onVoiceSearch,
}: {
  onClose: () => void;
  initialQuery?: string;
  onVoiceSearch?: () => void;
}) {
  const [q, setQ] = useState(initialQuery);
  const [songs, setSongs] = useState<Song[]>([]);
  const [status, setStatus] = useState("Type a song or artist · Win+H or 🎙️ to speak");
  const [playing, setPlaying] = useState<number | null>(null);
  const audio = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    if (initialQuery) {
      setQ(initialQuery);
    }
  }, [initialQuery]);

  useEffect(() => {
    if (q.trim().length < 2) return;
    const id = window.setTimeout(async () => {
      setStatus("Searching…");
      const r = await searchSongs(q.trim());
      if (!r) { setStatus("Couldn't reach the music catalog"); return; }
      setSongs(r);
      setStatus(r.length ? `${r.length} results` : "No results");
    }, 450);
    return () => window.clearTimeout(id);
  }, [q]);

  useEffect(() => () => { audio.current?.pause(); }, []);

  const preview = (s: Song) => {
    audio.current?.pause();
    if (playing === s.id || !s.preview) { setPlaying(null); return; }
    const a = new Audio(s.preview);
    audio.current = a;
    a.onended = () => setPlaying(null);
    a.play().catch(() => setPlaying(null));
    setPlaying(s.id);
  };

  const full = (s: Song, where: "spotify" | "ytm") => {
    const t = encodeURIComponent(`${s.title} ${s.artist}`);
    invoke("open_uri", {
      uri: where === "spotify" ? `spotify:search:${t}` : `https://music.youtube.com/search?q=${t}`,
    });
  };

  return (
    <div className="search">
      <div className="sbar">
        <input
          autoFocus
          value={q}
          placeholder="Search any song…"
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => e.key === "Escape" && onClose()}
        />
        {onVoiceSearch && (
          <button type="button" title="Speak to search" onClick={onVoiceSearch}>
            🎙️
          </button>
        )}
        <button onClick={onClose}>✕</button>
      </div>
      <div className="status">{status}</div>
      <div className="results">
        {songs.map((s) => (
          <div className="item" key={s.id}>
            <img src={s.art} />
            <div className="t"><b>{s.title}</b><span>{s.artist}</span></div>
            <button title="30s preview" onClick={() => preview(s)}>{playing === s.id ? "⏸" : "▶"}</button>
            <button title="Open in Spotify" onClick={() => full(s, "spotify")}>S</button>
            <button title="Open in YouTube Music" onClick={() => full(s, "ytm")}>Y</button>
          </div>
        ))}
      </div>
    </div>
  );
}
