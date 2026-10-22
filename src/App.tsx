import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { useMedia } from "./hooks/useMedia";
import { useVoice, VoiceAction } from "./hooks/useVoice";
import { getLyrics, Line } from "./lib/lyrics";
import Search from "./Search";
import "./island.css";

const THEMES = ["glass", "neon", "minimal", "sunset", "mono"];

function accentFrom(src: string): Promise<string> {
  return new Promise((res) => {
    const img = new Image();
    img.onload = () => {
      const c = document.createElement("canvas");
      c.width = c.height = 8;
      const x = c.getContext("2d")!;
      x.drawImage(img, 0, 0, 8, 8);
      const d = x.getImageData(0, 0, 8, 8).data;
      let r = 0, g = 0, b = 0;
      for (let i = 0; i < d.length; i += 4) { r += d[i]; g += d[i + 1]; b += d[i + 2]; }
      const n = d.length / 4;
      const lift = (v: number) => Math.min(255, Math.round(v / n) + 60);
      res(`rgb(${lift(r)},${lift(g)},${lift(b)})`);
    };
    img.onerror = () => res("#7c5cff");
    img.src = src;
  });
}

export default function App() {
  const { track, pos } = useMedia();
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [theme, setTheme] = useState<string>(localStorage.getItem("theme") ?? "glass");
  const [lines, setLines] = useState<Line[]>([]);
  const [source, setSource] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [accent, setAccent] = useState("#7c5cff");
  const hover = useRef(false);

  const trackKey = track ? `${track.title}|${track.artist}` : "";
  const playing = !!track?.playing;

  const load = (force: boolean) => {
    if (!track) return;
    setLines([]); setSource(""); setLoaded(false);
    getLyrics(track.title, track.artist, track.album, track.duration_ms, force).then((r) => {
      setLines(r.lines); setSource(r.source); setLoaded(true);
    });
  };

  const nextTheme = useCallback(() => {
    setTheme((prev) => THEMES[(THEMES.indexOf(prev) + 1) % THEMES.length]);
  }, []);

  const handleVoiceAction = useCallback((action: VoiceAction) => {
    switch (action.type) {
      case "media":
        invoke("media_cmd", { action: action.action });
        break;
      case "theme":
        setTheme(action.theme);
        break;
      case "theme_next":
        nextTheme();
        break;
      case "lyrics_reload":
        load(true);
        break;
      case "expand":
        setOpen(true);
        break;
      case "collapse":
        setOpen(false);
        break;
      case "search":
        setSearchQuery(action.query);
        setSearching(true);
        setOpen(true);
        break;
    }
  }, [track, nextTheme]);

  const {
    listening,
    feedback,
    sensitivity,
    audioLevel,
    thresholdPct,
    cycleSensitivity,
    start,
    stop,
  } = useVoice({ onAction: handleVoiceAction });

  // window size follows open/collapsed
  useEffect(() => {
    if (searching) invoke("set_search", { on: true });
    else invoke("set_open", { open });
  }, [open, searching]);
  useEffect(() => { localStorage.setItem("theme", theme); }, [theme]);

  // pop up when music plays or when voice control is active, hide when nothing plays (or paused for 30s)
  useEffect(() => {
    if (listening) {
      invoke("set_visible", { visible: true });
      return;
    }
    if (!track) { invoke("set_visible", { visible: false }); return; }
    if (playing) { invoke("set_visible", { visible: true }); return; }
    const id = setTimeout(() => invoke("set_visible", { visible: false }), 30_000);
    return () => clearTimeout(id);
  }, [trackKey, playing, listening]);

  // Global hotkey: Alt+V or V to toggle voice control (unless typing in an input)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const isInput = target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA");
      if (
        (e.altKey && (e.key === "v" || e.key === "V")) ||
        (!isInput && (e.key === "v" || e.key === "V") && !e.ctrlKey && !e.metaKey)
      ) {
        e.preventDefault();
        start();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [start]);

  // on a new song (or resume) show expanded for 5s, then collapse to the pill
  useEffect(() => {
    if (!playing || !trackKey) return;
    setOpen(true);
    const id = setTimeout(() => { if (!hover.current && !listening) setOpen(false); }, 5000);
    return () => clearTimeout(id);
  }, [trackKey, playing, listening]);

  useEffect(() => {
    if (!track) return;
    if (track.art) accentFrom(track.art).then(setAccent);
    load(false);
  }, [trackKey]);

  const idx = useMemo(() => {
    let i = -1;
    for (let k = 0; k < lines.length; k++) {
      if (lines[k].t <= pos + 150) i = k; else break;
    }
    return i;
  }, [lines, pos]);

  if (!track && !listening && !searching) {
    return (
      <div
        className="island idle"
        data-theme={theme}
        onClick={() => { setSearching(true); setOpen(true); }}
        title="Lizmo idle · Click to search or press Alt+V for voice control"
      >
        ♪
      </div>
    );
  }

  const cur = lines[idx]?.text ?? "";
  const next = lines[idx + 1]?.text ?? "";
  const status = !loaded ? "searching lyrics…" : lines.length ? source : "no lyrics found";
  const shown =
    cur || (lines.length ? "♪" : loaded ? "No lyrics found · tap to retry" : "Searching lyrics…");
  const pct = track?.duration_ms ? Math.min(100, (pos / track.duration_ms) * 100) : 0;

  return (
    <div
      className={`island ${open ? "open" : ""}`}
      data-theme={theme}
      style={{ ["--accent" as any]: accent }}
      onMouseEnter={() => { hover.current = true; setOpen(true); }}
      onMouseLeave={() => { hover.current = false; if (!searching && !listening) setOpen(false); }}
    >
      {feedback && <div className="voice-toast">{feedback}</div>}

      {searching ? (
        <Search
          initialQuery={searchQuery}
          onClose={() => { setSearching(false); setOpen(false); setSearchQuery(""); }}
          onVoiceSearch={start}
        />
      ) : (<>
      <div className="row">
        {track?.art && <img className="art" src={track.art} />}
        <div className="meta">
          {listening && !open ? (
            <div className="voice-pill">
              <span className="voice-dot" /> Listening… ({audioLevel}%) [Sens: {sensitivity.toUpperCase()}]
            </div>
          ) : (
            <>
              <b>{open ? (track?.title ?? "Lizmo") : cur || (track?.title ?? "Lizmo")}</b>
              <span>{open ? `${track?.artist ?? ""} · ${status}` : cur ? "" : (track?.artist ?? "")}</span>
            </>
          )}
        </div>
        {!open && track && (
          <div className={`bars ${track.playing ? "on" : ""}`}><i /><i /><i /><i /></div>
        )}
      </div>

      {open && (
        <>
          <div className="lyric" onClick={() => load(true)}>
            <p className="cur" key={idx}>{shown}</p>
            <p className="next">{next}</p>
          </div>
          <div className="bar"><div style={{ width: `${pct}%` }} /></div>
          {listening && (
            <div
              className="audio-meter-bar"
              title={`Live Mic Level: ${audioLevel}% (Threshold: ${thresholdPct}%)`}
            >
              <div
                className="audio-meter-fill"
                style={{ width: `${Math.min(100, audioLevel)}%` }}
              />
              <div className="audio-meter-marker" style={{ left: `${thresholdPct}%` }} />
            </div>
          )}
          <div className="ctrl">
            <button title="Previous track (or say 'previous')" onClick={() => invoke("media_cmd", { action: "prev" })}>⏮</button>
            <button title="Play/Pause (or say 'pause'/'play')" onClick={() => invoke("media_cmd", { action: "toggle" })}>
              {track?.playing ? "⏸" : "▶"}
            </button>
            <button title="Next track (or say 'skip')" onClick={() => invoke("media_cmd", { action: "next" })}>⏭</button>
            <button
              className={`mic-btn ${listening ? "listening" : ""}`}
              title={listening ? "Listening… Click to cancel" : "Voice control (Click or Alt+V to speak)"}
              onClick={listening ? stop : start}
            >
              🎙️
            </button>
            <button
              className="chip sens-btn"
              title={`Mic Sensitivity: ${sensitivity.toUpperCase()} (${thresholdPct}% threshold) · Click to switch`}
              onClick={cycleSensitivity}
            >
              Sens: {sensitivity.toUpperCase()}
            </button>
            <button title="Search catalog" onClick={() => { setSearching(true); setOpen(true); }}>🔍</button>
            <button className="chip" onClick={nextTheme}>◐ {theme}</button>
          </div>
        </>
      )}
      </>)}
    </div>
  );
}
