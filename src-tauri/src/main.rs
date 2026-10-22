#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod media;
mod voice;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;
use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    Manager,
};

static GEN: AtomicU64 = AtomicU64::new(0);

/// Smoothly resize the island (ease-out) and keep it centred at the top of the primary monitor.
fn place(app: &tauri::AppHandle, lw: f64, lh: f64, animate: bool) {
    let Some(w) = app.get_webview_window("main") else { return };
    let Ok(Some(m)) = w.primary_monitor() else { return };
    let sf = m.scale_factor();
    let (mx, my, mw) = (m.position().x as f64, m.position().y as f64, m.size().width as f64);
    let (tw, th) = (lw * sf, lh * sf);
    let (fw, fh) = match w.outer_size() {
        Ok(s) => (s.width as f64, s.height as f64),
        Err(_) => (tw, th),
    };
    let gen = GEN.fetch_add(1, Ordering::SeqCst) + 1;

    let put = move |w: &tauri::WebviewWindow, cw: f64, ch: f64| {
        let x = mx + (mw - cw) / 2.0;
        let y = my + 8.0 * sf;
        let _ = w.set_size(tauri::PhysicalSize::new(cw as u32, ch as u32));
        let _ = w.set_position(tauri::PhysicalPosition::new(x as i32, y as i32));
    };

    if !animate {
        put(&w, tw, th);
        return;
    }
    std::thread::spawn(move || {
        let steps = 24;
        for i in 1..=steps {
            if GEN.load(Ordering::SeqCst) != gen {
                return; // a newer resize took over
            }
            let t = i as f64 / steps as f64;
            let e = 1.0 - (1.0 - t).powi(4); // ease-out quart
            put(&w, fw + (tw - fw) * e, fh + (th - fh) * e);
            std::thread::sleep(Duration::from_millis(8));
        }
    });
}

#[tauri::command]
fn set_open(app: tauri::AppHandle, open: bool) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.set_focusable(false); // never steal focus from games / typing
    }
    let (lw, lh) = if open { (420.0, 190.0) } else { (260.0, 48.0) };
    place(&app, lw, lh, true);
}

#[tauri::command]
fn set_search(app: tauri::AppHandle, on: bool) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.set_focusable(on);
        if on {
            let _ = w.set_focus();
        }
    }
    place(&app, 420.0, if on { 460.0 } else { 190.0 }, true);
}

#[tauri::command]
fn set_visible(app: tauri::AppHandle, visible: bool) {
    if let Some(w) = app.get_webview_window("main") {
        if visible {
            let _ = w.show();
        } else {
            let _ = w.hide();
        }
    }
}

/// Opens spotify: / https: links with the default app (used by the search panel).
#[tauri::command]
fn open_uri(uri: String) {
    if !(uri.starts_with("spotify:") || uri.starts_with("https://")) {
        return;
    }
    #[cfg(windows)]
    {
        let _ = std::process::Command::new("rundll32")
            .args(["url.dll,FileProtocolHandler", &uri])
            .spawn();
    }
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_http::init())
        .setup(|app| {
            // tray icon = the only way to quit while the island is hidden
            let quit = MenuItem::with_id(app, "quit", "Quit Lizmo", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&quit])?;
            TrayIconBuilder::new()
                .icon(app.default_window_icon().unwrap().clone())
                .tooltip("Lizmo")
                .menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "quit" => app.exit(0),
                    _ => {}
                })
                .build(app)?;

            place(app.handle(), 260.0, 48.0, false);
            media::start(app.handle().clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            set_open,
            set_search,
            set_visible,
            open_uri,
            media::media_cmd,
            voice::start_voice_recognition,
            voice::cancel_voice_recognition
        ])
        .run(tauri::generate_context!())
        .expect("failed to run Lizmo");
}
