use std::sync::Mutex;
use tauri::{AppHandle, Emitter};
use windows::core::Interface;
use windows::Foundation::IAsyncInfo;
use windows::Media::SpeechRecognition::*;
use windows::Win32::System::Com::{CoInitializeEx, COINIT_MULTITHREADED};

struct SendAsyncInfo(IAsyncInfo);
unsafe impl Send for SendAsyncInfo {}

static ACTIVE_OP: Mutex<Option<SendAsyncInfo>> = Mutex::new(None);

#[tauri::command]
pub async fn start_voice_recognition(app: AppHandle) -> Result<String, String> {
    // Cancel any previous pending session
    cancel_voice_recognition();

    let app_clone = app.clone();
    let res = tauri::async_runtime::spawn_blocking(move || -> Result<String, String> {
        unsafe {
            let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
        }

        let recognizer = SpeechRecognizer::new()
            .map_err(|e| format!("Failed to create SpeechRecognizer: {e}"))?;

        let compile_op = recognizer
            .CompileConstraintsAsync()
            .map_err(|e| format!("CompileConstraintsAsync failed: {e}"))?;
        
        let compilation_res = compile_op
            .get()
            .map_err(|e| format!("Constraint compilation failed: {e}"))?;

        if compilation_res.Status().map_err(|e| e.to_string())?
            != SpeechRecognitionResultStatus::Success
        {
            return Err("Speech recognition constraints failed to compile".to_string());
        }

        let rec_op = recognizer
            .RecognizeAsync()
            .map_err(|e| format!("RecognizeAsync failed: {e}"))?;

        // Store active operation for cancellation
        if let Ok(info) = rec_op.cast::<IAsyncInfo>() {
            let mut lock = ACTIVE_OP.lock().unwrap();
            *lock = Some(SendAsyncInfo(info));
        }

        let _ = app_clone.emit("voice://status", "listening");

        let rec_res = match rec_op.get() {
            Ok(r) => r,
            Err(e) => {
                let mut lock = ACTIVE_OP.lock().unwrap();
                *lock = None;
                return Err(format!("Recognition aborted or failed: {e}"));
            }
        };

        // Clear active operation
        {
            let mut lock = ACTIVE_OP.lock().unwrap();
            *lock = None;
        }

        let text = rec_res.Text().map(|h| h.to_string()).unwrap_or_default();
        let _ = app_clone.emit("voice://result", &text);
        Ok(text)
    })
    .await
    .map_err(|e| format!("Task join failed: {e}"))?;

    res
}

#[tauri::command]
pub fn cancel_voice_recognition() {
    let mut lock = ACTIVE_OP.lock().unwrap();
    if let Some(wrapped) = lock.take() {
        let _ = wrapped.0.Cancel();
    }
}
