use std::sync::{Arc, Mutex};

use crate::dap::{aaudio, DapPcmFormat};
use crate::pcm_source::open_decoder_from_fd;
use crate::playback::{is_lossless_codec, PlaybackEngine, PcmSink};
use crate::{
    DirectUsbError, PlaybackStatusDto, SourceInfoDto, UsbPlaybackConfiguration,
    UsbTransportStatus,
};

const WRITE_TIMEOUT_MS: i64 = 50;

struct SinkState {
    slot: aaudio::StreamSlot,
    configured: bool,
    frame_bytes: usize,
    capacity_frames: u64,
    accepted_frames: u64,
    epoch_accepted_frames: u64,
    last_raw_read: u64,
    completed_base: u64,
    epoch_completed_base: u64,
}

impl SinkState {
    fn new() -> Self {
        Self {
            slot: aaudio::StreamSlot::default(),
            configured: false,
            frame_bytes: 0,
            capacity_frames: 0,
            accepted_frames: 0,
            epoch_accepted_frames: 0,
            last_raw_read: 0,
            completed_base: 0,
            epoch_completed_base: 0,
        }
    }

    fn completed(&mut self) -> u64 {
        let raw = aaudio::frames_read(&self.slot);
        if raw < self.last_raw_read {
            self.completed_base += self.last_raw_read;
        }
        self.last_raw_read = raw;
        self.completed_base + raw
    }
}

pub(crate) struct AaudioSink {
    device_id: i32,
    state: Mutex<SinkState>,
}

impl AaudioSink {
    fn new(device_id: i32) -> Self {
        Self {
            device_id,
            state: Mutex::new(SinkState::new()),
        }
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, SinkState> {
        self.state
            .lock()
            .unwrap_or_else(|failure| failure.into_inner())
    }
}

impl Drop for AaudioSink {
    fn drop(&mut self) {
        let mut state = self.lock();
        aaudio::close_slot(&mut state.slot);
        state.configured = false;
    }
}

impl PcmSink for AaudioSink {
    fn configure(
        &self,
        sample_rate: u32,
        channels: u8,
        bit_depth: u8,
    ) -> Result<UsbPlaybackConfiguration, String> {
        let (format, width) = match bit_depth {
            1..=16 => (DapPcmFormat::Int16, 16),
            17..=24 => (DapPcmFormat::Int24Packed, 24),
            25..=32 => (DapPcmFormat::Int32, 32),
            _ => return Err(format!("DAP bit depth {bit_depth} is not supported")),
        };
        let mut state = self.lock();
        let info = aaudio::open(&mut state.slot, sample_rate, channels, format, self.device_id)
            .map_err(|error| error.to_string())?;
        state.configured = true;
        state.frame_bytes = usize::from(channels) * format.bytes_per_sample();
        state.capacity_frames = u64::from(info.buffer_capacity_frames);
        state.accepted_frames = 0;
        state.epoch_accepted_frames = 0;
        state.last_raw_read = 0;
        state.completed_base = 0;
        state.epoch_completed_base = 0;
        Ok(UsbPlaybackConfiguration {
            sample_rate,
            channels,
            requested_bit_depth: bit_depth,
            device_bit_resolution: width,
            subslot_size: format.bytes_per_sample() as u8,
            control_interface: 0,
            streaming_interface: 0,
            alternate_setting: 0,
            clock_source_id: 0,
            data_endpoint_address: 0,
            feedback_endpoint_address: 0,
            maximum_packet_size: 0,
            feedback_maximum_packet_size: 0,
            interval_microseconds: 0,
            required_packet_bytes: 0,
            previous_clock_rate: 0,
            verified_clock_rate: 0,
            clock_changed: false,
        })
    }

    fn write(&self, data: &[u8]) -> Result<usize, String> {
        let mut state = self.lock();
        if !state.configured {
            return Err("Configure a DAP output before writing samples".to_string());
        }
        let written = aaudio::write_some(&mut state.slot, data, WRITE_TIMEOUT_MS)
            .map_err(|error| error.to_string())?;
        let frames = (written / state.frame_bytes) as u64;
        state.accepted_frames += frames;
        state.epoch_accepted_frames += frames;
        Ok(written)
    }

    fn available(&self) -> Result<usize, String> {
        let state = self.lock();
        if !state.configured {
            return Ok(0);
        }
        let queued = aaudio::frames_written(&state.slot)
            .saturating_sub(aaudio::frames_read(&state.slot));
        Ok((state.capacity_frames.saturating_sub(queued) as usize) * state.frame_bytes)
    }

    fn start(&self) -> Result<(), String> {
        let mut state = self.lock();
        aaudio::start(&mut state.slot).map_err(|error| error.to_string())
    }

    fn flush(&self) -> Result<(), String> {
        let mut state = self.lock();
        if !state.configured {
            return Ok(());
        }
        let _ = state.completed();
        aaudio::flush(&mut state.slot).map_err(|error| error.to_string())?;
        let completed = state.completed();
        state.epoch_accepted_frames = 0;
        state.epoch_completed_base = completed;
        Ok(())
    }

    fn status(&self) -> UsbTransportStatus {
        let mut state = self.lock();
        let completed = state.completed();
        let queued_frames = state
            .epoch_accepted_frames
            .saturating_sub(completed.saturating_sub(state.epoch_completed_base));
        UsbTransportStatus {
            configured: state.configured,
            playing: state.configured
                && aaudio::state(&state.slot) == aaudio::STATE_STARTED,
            queued_bytes: (queued_frames * state.frame_bytes as u64)
                .min(u64::from(u32::MAX)) as u32,
            accepted_frames: state.accepted_frames,
            submitted_frames: state.accepted_frames,
            in_flight_frames: 0,
            completed_frames: completed,
            discarded_frames: 0,
            submitted_silence_frames: 0,
            in_flight_silence_frames: 0,
            completed_silence_frames: 0,
            discarded_silence_frames: 0,
            completed_transfers: 0,
            failed_transfers: 0,
            valid_feedback_packets: 0,
            accepted_pcm_hash: 0,
            submitted_pcm_hash: 0,
            payload_integrity_errors: 0,
            format_configuration_count: 0,
            transport_start_count: 0,
            flush_count: 0,
            pending_transfer_count: 0,
            cancellation_requests: 0,
            callbacks_drained: 0,
            cancellation_errors: 0,
            interface_reset_succeeded: false,
            released_interface_count: 0,
            handle_closed: false,
            context_released: false,
            worker_closed: false,
            error_message: String::new(),
        }
    }
}

#[derive(uniffi::Object)]
pub struct DapSession {
    playback: Mutex<Option<PlaybackEngine<Arc<AaudioSink>>>>,
    sink: Arc<AaudioSink>,
}

#[uniffi::export]
impl DapSession {
    #[uniffi::constructor]
    pub fn new(device_id: i32) -> Arc<Self> {
        let sink = Arc::new(AaudioSink::new(device_id));
        Arc::new(Self {
            playback: Mutex::new(None),
            sink,
        })
    }

    pub fn open_source(
        &self,
        source_file_descriptor: i32,
        extension_hint: String,
    ) -> Result<SourceInfoDto, DirectUsbError> {
        let decoder = open_decoder_from_fd(source_file_descriptor, &extension_hint)?;
        let mut playback = self.lock_playback();
        if playback.is_none() {
            *playback = Some(PlaybackEngine::new(self.sink.clone()));
        }
        let engine = playback.as_mut().unwrap();
        let lossless = is_lossless_codec(&decoder.info().codec, &decoder.info().container);
        engine
            .open_source(decoder, lossless)
            .map(SourceInfoDto::from)
            .map_err(DirectUsbError::from)
    }

    pub fn prepare_playback(&self) -> Result<PlaybackStatusDto, DirectUsbError> {
        let mut playback = self.lock_playback();
        let engine = playback
            .as_mut()
            .ok_or_else(|| DirectUsbError::Failed {
                message: "Open a source before preparing playback".to_string(),
            })?;
        engine
            .prepare()
            .map(PlaybackStatusDto::from)
            .map_err(DirectUsbError::from)
    }

    pub fn start_playback(&self) -> Result<(), DirectUsbError> {
        let mut playback = self.lock_playback();
        let engine = playback
            .as_mut()
            .ok_or_else(|| DirectUsbError::Failed {
                message: "Open a source before starting playback".to_string(),
            })?;
        engine.start().map_err(DirectUsbError::from)
    }

    pub fn pause_playback(&self) -> Result<(), DirectUsbError> {
        let mut playback = self.lock_playback();
        let engine = playback
            .as_mut()
            .ok_or_else(|| DirectUsbError::Failed {
                message: "Open a source before pausing playback".to_string(),
            })?;
        engine.pause().map_err(DirectUsbError::from)
    }

    pub fn seek_playback(&self, position_us: u64) -> Result<(), DirectUsbError> {
        let mut playback = self.lock_playback();
        let engine = playback
            .as_mut()
            .ok_or_else(|| DirectUsbError::Failed {
                message: "Open a source before seeking".to_string(),
            })?;
        engine.seek(position_us).map_err(DirectUsbError::from)
    }

    pub fn stop_playback(&self) -> Result<(), DirectUsbError> {
        let mut playback = self.lock_playback();
        if let Some(engine) = playback.as_mut() {
            engine.stop();
        }
        Ok(())
    }

    pub fn playback_status(&self) -> PlaybackStatusDto {
        let playback = self.lock_playback();
        match playback.as_ref() {
            Some(engine) => PlaybackStatusDto::from(engine.status()),
            None => PlaybackStatusDto::idle(),
        }
    }

    pub fn release_source(&self) -> Result<(), DirectUsbError> {
        let mut playback = self.lock_playback();
        if let Some(engine) = playback.as_mut() {
            engine.close_source();
        }
        Ok(())
    }
}

impl DapSession {
    fn lock_playback(
        &self,
    ) -> std::sync::MutexGuard<'_, Option<PlaybackEngine<Arc<AaudioSink>>>> {
        self.playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[cfg(unix)]
    use std::os::unix::io::AsRawFd;

    #[test]
    fn status_is_idle_without_a_source() {
        let session = DapSession::new(0);
        let status = session.playback_status();
        assert_eq!(status.state, "Idle");
    }

    #[test]
    fn prepare_without_a_source_is_an_error() {
        let session = DapSession::new(0);
        assert!(session.prepare_playback().is_err());
    }

    #[cfg(unix)]
    #[test]
    fn open_source_opens_a_real_file() {
        let path = std::env::temp_dir().join(format!(
            "rovia_dap_session_test_{}.wav",
            std::process::id()
        ));
        crate::pcm_source::write_test_wav(&path, 4_800);
        let file = std::fs::File::open(&path).unwrap();
        let fd = unsafe { libc::dup(file.as_raw_fd()) };
        assert!(fd >= 0);
        let session = DapSession::new(0);
        let info = session.open_source(fd, "wav".to_string()).unwrap();
        let _ = std::fs::remove_file(&path);
        assert_eq!(info.sample_rate, 48_000);
        assert_eq!(info.channels, 2);
        #[cfg(not(target_os = "android"))]
        assert!(session.prepare_playback().is_err());
    }

    #[test]
    fn sink_configure_requires_android() {
        let sink = AaudioSink::new(0);
        #[cfg(not(target_os = "android"))]
        assert!(sink.configure(48_000, 2, 16).is_err());
        #[cfg(target_os = "android")]
        let _ = sink.configure(48_000, 2, 16);
    }
}
