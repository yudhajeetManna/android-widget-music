import { useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";

export type Track = {
  title: string; artist: string; album: string; app: string;
  playing: boolean; position_ms: number; duration_ms: number; art: string | null;
};

export function useMedia() {
  const [track, setTrack] = useState<Track | null>(null);
  const [pos, setPos] = useState(0);
  const base = useRef({ pos: 0, at: performance.now(), playing: false });

  useEffect(() => {
    const un = listen<Track>("media://update", (e) => {
      const t = e.payload;
      setTrack(t.title ? t : null);
      base.current = { pos: t.position_ms, at: performance.now(), playing: t.playing };
    });
    let raf = 0, lastPaint = 0;
    const tick = (now: number) => {
      if (now - lastPaint > 100) {
        const b = base.current;
        setPos(b.pos + (b.playing ? now - b.at : 0));
        lastPaint = now;
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => { un.then((f) => f()); cancelAnimationFrame(raf); };
  }, []);

  return { track, pos };
}
