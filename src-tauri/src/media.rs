use base64::Engine;
use serde::Serialize;
use std::{thread, time::Duration};
use tauri::{AppHandle, Emitter};
use windows::Media::Control::*;
use windows::Storage::Streams::DataReader;
use windows::Win32::System::Com::{CoInitializeEx, COINIT_MULTITHREADED};

#[derive(Serialize, Clone, Default, PartialEq)]
pub struct Track {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub app: String,
    pub playing: bool,
    pub position_ms: i64,
    pub duration_ms: i64,
    pub art: Option<String>,
}

fn session() -> Option<GlobalSystemMediaTransportControlsSession> {
    GlobalSystemMediaTransportControlsSessionManager::RequestAsync()
        .ok()?
        .get()
        .ok()?
        .GetCurrentSession()
        .ok()
}

fn read_art(p: &GlobalSystemMediaTransportControlsSessionMediaProperties) -> Option<String> {
    let stream = p.Thumbnail().ok()?.OpenReadAsync().ok()?.get().ok()?;
    let size = stream.Size().ok()? as u32;
    let reader = DataReader::CreateDataReader(&stream).ok()?;
    reader.LoadAsync(size).ok()?.get().ok()?;
    let mut buf = vec![0u8; size as usize];
    reader.ReadBytes(&mut buf).ok()?;
    Some(format!(
        "data:image/jpeg;base64,{}",
        base64::engine::general_purpose::STANDARD.encode(buf)
    ))
}

pub fn start(app: AppHandle) {
    thread::spawn(move || {
        unsafe {
            let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
        }
        let mut last = Track::default();
        let mut last_key = String::new();
        loop {
            thread::sleep(Duration::from_millis(500));
            let Some(s) = session() else {
                if last != Track::default() {
                    last = Track::default();
                    last_key.clear();
                    let _ = app.emit("media://update", &last);
                }
                continue;
            };
            let Ok(props) = s.TryGetMediaPropertiesAsync().and_then(|o| o.get()) else {
                continue;
            };
            let (Ok(tl), Ok(pb)) = (s.GetTimelineProperties(), s.GetPlaybackInfo()) else {
                continue;
            };

            let title = props.Title().map(|h| h.to_string()).unwrap_or_default();
            let artist = props.Artist().map(|h| h.to_string()).unwrap_or_default();
            let key = format!("{title}|{artist}");

            let mut t = Track {
                title,
                artist,
                album: props.AlbumTitle().map(|h| h.to_string()).unwrap_or_default(),
                app: s.SourceAppUserModelId().map(|h| h.to_string()).unwrap_or_default(),
                playing: pb.PlaybackStatus().ok()
                    == Some(GlobalSystemMediaTransportControlsSessionPlaybackStatus::Playing),
                position_ms: tl.Position().map(|d| d.Duration / 10_000).unwrap_or(0),
                duration_ms: match (tl.EndTime(), tl.StartTime()) {
                    (Ok(e), Ok(b)) => (e.Duration - b.Duration) / 10_000,
                    _ => 0,
                },
                art: None,
            };

            if key != last_key {
                t.art = read_art(&props);
                last_key = key;
            } else {
                t.art = last.art.clone();
            }

            let drift = (t.position_ms - last.position_ms).abs();
            let changed = t.title != last.title
                || t.artist != last.artist
                || t.playing != last.playing
                || t.duration_ms != last.duration_ms
                || (t.playing && drift > 2000)
                || (!t.playing && drift > 300);
            if changed {
                let _ = app.emit("media://update", &t);
                last = t;
            }
        }
    });
}

#[tauri::command]
pub fn media_cmd(action: String) {
    if let Some(s) = session() {
        match action.as_str() {
            "toggle" => { let _ = s.TryTogglePlayPauseAsync(); }
            "next" => { let _ = s.TrySkipNextAsync(); }
            "prev" => { let _ = s.TrySkipPreviousAsync(); }
            _ => {}
        }
    }
}
