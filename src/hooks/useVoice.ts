import { useState, useRef, useCallback, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";

export type VoiceAction =
  | { type: "media"; action: "toggle" | "next" | "prev"; feedback: string }
  | { type: "theme"; theme: string; feedback: string }
  | { type: "theme_next"; feedback: string }
  | { type: "lyrics_reload"; feedback: string }
  | { type: "expand"; feedback: string }
  | { type: "collapse"; feedback: string }
  | { type: "search"; query: string; feedback: string }
  | { type: "unknown"; text: string; feedback: string };

export type SensitivityLevel = "low" | "med" | "high";

export const SENSITIVITY_CONFIG: Record<
  SensitivityLevel,
  { label: string; threshold: number; desc: string }
> = {
  low: { label: "Low", threshold: 0.28, desc: "Noise filter (Speak loud)" },
  med: { label: "Med", threshold: 0.12, desc: "Standard conversational" },
  high: { label: "High", threshold: 0.04, desc: "Sensitive (Quiet speech)" },
};

export function parseVoiceCommand(raw: string): VoiceAction | null {
  const text = raw.trim().toLowerCase().replace(/[.,!?;:]/g, "");
  if (!text) return null;

  // Media Playback Controls
  if (/^(play|resume|unpause|start music|play music|start)$/.test(text)) {
    return { type: "media", action: "toggle", feedback: "▶️ Resumed" };
  }
  if (/^(pause|stop|halt|pause music|stop music)$/.test(text)) {
    return { type: "media", action: "toggle", feedback: "⏸️ Paused" };
  }
  if (/^(toggle|play pause)$/.test(text)) {
    return { type: "media", action: "toggle", feedback: "⏯️ Toggled playback" };
  }
  if (/^(next|skip|next song|next track|skip song|forward)$/.test(text)) {
    return { type: "media", action: "next", feedback: "⏭️ Next track" };
  }
  if (/^(previous|prev|back|last song|last track|go back|replay)$/.test(text)) {
    return { type: "media", action: "prev", feedback: "⏮️ Previous track" };
  }

  // Theme Switching
  if (/^(theme\s+)?glass(\s+theme)?$/.test(text)) {
    return { type: "theme", theme: "glass", feedback: "🎨 Glass theme" };
  }
  if (/^(theme\s+)?neon(\s+theme)?$/.test(text)) {
    return { type: "theme", theme: "neon", feedback: "⚡ Neon theme" };
  }
  if (/^((theme\s+)?(minimal|dark)(\s+theme)?)$/.test(text)) {
    return { type: "theme", theme: "minimal", feedback: "🌙 Minimal theme" };
  }
  if (/^(theme\s+)?sunset(\s+theme)?$/.test(text)) {
    return { type: "theme", theme: "sunset", feedback: "🌅 Sunset theme" };
  }
  if (/^((theme\s+)?(mono|light)(\s+theme)?)$/.test(text)) {
    return { type: "theme", theme: "mono", feedback: "☀️ Mono theme" };
  }
  if (/^(next theme|change theme|switch theme|cycle theme)$/.test(text)) {
    return { type: "theme_next", feedback: "🎨 Next theme" };
  }

  // Lyrics
  if (/^(reload lyrics|refresh lyrics|fetch lyrics|get lyrics)$/.test(text)) {
    return { type: "lyrics_reload", feedback: "🔄 Reloading lyrics…" };
  }
  if (/^(lyrics|show lyrics)$/.test(text)) {
    return { type: "expand", feedback: "📜 Showing lyrics" };
  }

  // Island Dimensions
  if (/^(expand|open|maximize|show)$/.test(text)) {
    return { type: "expand", feedback: "Expanded" };
  }
  if (/^(collapse|close|minimize|hide)$/.test(text)) {
    return { type: "collapse", feedback: "Collapsed" };
  }

  // Search Commands (e.g. "search Bohemian Rhapsody", "find Taylor Swift", "play Starboy")
  const searchMatch = text.match(/^(?:search for|search|find|lookup|play song|play)\s+(.+)$/i);
  if (searchMatch && searchMatch[1]?.trim()) {
    const q = searchMatch[1].trim();
    return { type: "search", query: q, feedback: `🔍 Searching: ${q}` };
  }

  // If user says any name/phrase that wasn't a control command, default to searching it
  return { type: "search", query: text, feedback: `🔍 Searching: ${text}` };
}

export function useVoice({
  onAction,
}: {
  onAction: (action: VoiceAction) => void;
}) {
  const [listening, setListening] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [transcript, setTranscript] = useState("");
  const [sensitivity, setSensitivityState] = useState<SensitivityLevel>(() => {
    const saved = localStorage.getItem("voice_sensitivity");
    return saved === "low" || saved === "med" || saved === "high" ? saved : "med";
  });
  const [audioLevel, setAudioLevel] = useState(0); // 0 - 100%

  const timerRef = useRef<number | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const audioStreamRef = useRef<MediaStream | null>(null);
  const maxVolumeRef = useRef<number>(0);
  const animFrameRef = useRef<number | null>(null);

  const showFeedback = useCallback((msg: string, dur = 3500) => {
    setFeedback(msg);
    if (timerRef.current) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => setFeedback(null), dur);
  }, []);

  const setSensitivity = useCallback((level: SensitivityLevel) => {
    setSensitivityState(level);
    localStorage.setItem("voice_sensitivity", level);
    showFeedback(`Sensitivity: ${SENSITIVITY_CONFIG[level].label} (${SENSITIVITY_CONFIG[level].desc})`, 2500);
  }, [showFeedback]);

  const cycleSensitivity = useCallback(() => {
    const order: SensitivityLevel[] = ["med", "high", "low"];
    const next = order[(order.indexOf(sensitivity) + 1) % order.length];
    setSensitivity(next);
  }, [sensitivity, setSensitivity]);

  // Clean up audio analyzer
  const stopAudioMeter = useCallback(() => {
    if (animFrameRef.current) {
      cancelAnimationFrame(animFrameRef.current);
      animFrameRef.current = null;
    }
    if (audioStreamRef.current) {
      audioStreamRef.current.getTracks().forEach((t) => t.stop());
      audioStreamRef.current = null;
    }
    if (audioContextRef.current && audioContextRef.current.state !== "closed") {
      audioContextRef.current.close().catch(() => {});
      audioContextRef.current = null;
    }
    setAudioLevel(0);
  }, []);

  // Start real-time audio volume monitoring
  const startAudioMeter = useCallback(async () => {
    maxVolumeRef.current = 0;
    try {
      if (!navigator.mediaDevices?.getUserMedia) return;
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      audioStreamRef.current = stream;

      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      const ctx = new AudioCtx();
      audioContextRef.current = ctx;

      const source = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 256;
      source.connect(analyser);

      const dataArray = new Uint8Array(analyser.frequencyBinCount);

      const checkLevel = () => {
        analyser.getByteFrequencyData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) {
          sum += dataArray[i];
        }
        const avg = sum / dataArray.length; // 0 to 255
        const normalized = Math.min(1, avg / 128); // 0.0 to 1.0
        if (normalized > maxVolumeRef.current) {
          maxVolumeRef.current = normalized;
        }
        setAudioLevel(Math.round(normalized * 100));
        animFrameRef.current = requestAnimationFrame(checkLevel);
      };
      checkLevel();
    } catch {
      // Audio meter fallback if mic stream already in exclusive use
    }
  }, []);

  const stop = useCallback(() => {
    setListening(false);
    stopAudioMeter();
    invoke("cancel_voice_recognition").catch(() => {});
  }, [stopAudioMeter]);

  const start = useCallback(async () => {
    if (listening) {
      stop();
      return;
    }

    setListening(true);
    setTranscript("");
    const cfg = SENSITIVITY_CONFIG[sensitivity];
    showFeedback(`🎙️ Listening… [Sens: ${cfg.label}]`, 15000);
    startAudioMeter();

    try {
      // Invoke native Windows speech recognition
      const result = await invoke<string>("start_voice_recognition");
      stopAudioMeter();
      setListening(false);

      if (result && result.trim()) {
        const spoken = result.trim();
        const peak = maxVolumeRef.current;
        const required = cfg.threshold;

        // If audio volume was monitored and user set sensitivity high threshold check
        if (peak > 0 && peak < required) {
          showFeedback(
            `Audio too quiet for ${cfg.label} sensitivity. Tap Sens to adjust.`,
            3500
          );
          return;
        }

        setTranscript(spoken);
        const action = parseVoiceCommand(spoken);
        if (action) {
          showFeedback(action.feedback, 3000);
          onAction(action);
        } else {
          showFeedback(`Heard: "${spoken}"`, 3000);
        }
      } else {
        showFeedback("No speech detected", 2000);
      }
    } catch (err: any) {
      stopAudioMeter();
      setListening(false);
      const errStr = String(err);
      if (errStr.includes("aborted") || errStr.includes("cancel")) {
        showFeedback("Voice cancelled", 1500);
      } else {
        console.warn("Native speech error, fallback:", err);
        // Fallback: If Web Speech API is present in browser environment
        const SpeechRec =
          (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
        if (SpeechRec) {
          try {
            const rec = new SpeechRec();
            rec.lang = navigator.language || "en-US";
            rec.onstart = () => {
              setListening(true);
              startAudioMeter();
            };
            rec.onresult = (e: any) => {
              stopAudioMeter();
              const text = e.results[0][0].transcript;
              setTranscript(text);
              const action = parseVoiceCommand(text);
              if (action) {
                showFeedback(action.feedback, 3000);
                onAction(action);
              }
            };
            rec.onerror = () => {
              stopAudioMeter();
              setListening(false);
              showFeedback("Microphone error", 2000);
            };
            rec.onend = () => {
              stopAudioMeter();
              setListening(false);
            };
            rec.start();
            return;
          } catch {
            showFeedback("Voice recognition error", 2500);
          }
        } else {
          showFeedback("Voice error: Check mic permissions", 3000);
        }
      }
    }
  }, [listening, onAction, sensitivity, showFeedback, startAudioMeter, stop, stopAudioMeter]);

  useEffect(() => {
    return () => {
      stopAudioMeter();
    };
  }, [stopAudioMeter]);

  const thresholdPct = Math.round(SENSITIVITY_CONFIG[sensitivity].threshold * 100);

  return {
    listening,
    transcript,
    feedback,
    sensitivity,
    audioLevel,
    thresholdPct,
    setSensitivity,
    cycleSensitivity,
    start,
    stop,
    showFeedback,
  };
}
