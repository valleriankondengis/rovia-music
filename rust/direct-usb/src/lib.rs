mod adapter;
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
mod clock;
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
mod dap;
mod dap_session;
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
mod descriptors;
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
mod pcm;
mod pcm_source;
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
mod volume;
mod playback;

pub use dap_session::DapSession;

#[uniffi::export]
pub fn dap_output_supported(device_id: i32) -> bool {
    dap::probe_supported(device_id)
}

pub use pcm_source::{NativePcmSource, PcmBlockDto};
#[cfg(any(target_os = "android", test))]
mod flush_barrier;
#[cfg(target_os = "android")]
#[allow(unsafe_code)]
mod native;

use std::sync::Arc;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum DirectUsbError {
    #[error("{message}")]
    Failed { message: String },
}

impl From<String> for DirectUsbError {
    fn from(message: String) -> Self {
        Self::Failed { message }
    }
}

#[derive(Clone)]
pub struct UsbSampleRateRange {
    pub minimum_rate: u32,
    pub maximum_rate: u32,
    pub resolution: u32,
}

#[derive(Clone)]
pub struct UsbInterfaceInfo {
    pub interface_number: u8,
    pub alternate_setting: u8,
    pub interface_class: u8,
    pub interface_subclass: u8,
    pub interface_protocol: u8,
    pub class_name: String,
    pub subclass_name: String,
    pub protocol_name: String,
    pub endpoint_count: u8,
    pub string_index: u8,
}

#[derive(Clone)]
pub struct UsbEndpointInfo {
    pub interface_number: u8,
    pub alternate_setting: u8,
    pub address: u8,
    pub direction: String,
    pub transfer_type: String,
    pub synchronization_type: String,
    pub usage_type: String,
    pub maximum_packet_size: u32,
    pub sampling_frequency_control: bool,
    pub pitch_control: bool,
    pub maximum_packets_only: bool,
    pub interval: u8,
    pub synchronization_address: u8,
    pub refresh: u8,
    pub maximum_burst: u8,
    pub transactions_per_microframe: u8,
}

#[derive(Clone)]
pub struct UsbAudioFormatInfo {
    pub interface_number: u8,
    pub alternate_setting: u8,
    pub uac_version: String,
    pub terminal_link: u8,
    pub format_type: u8,
    pub format_tag: u16,
    pub format_name: String,
    pub pcm_supported: bool,
    pub channels: u8,
    pub channel_configuration: u32,
    pub bit_resolution: u8,
    pub subslot_size: u8,
    pub clock_source_id: u8,
    pub current_sample_rate: u32,
    pub sample_rates: Vec<u32>,
    pub sample_rate_ranges: Vec<UsbSampleRateRange>,
}

#[derive(Clone)]
pub struct UsbPlaybackConfiguration {
    pub sample_rate: u32,
    pub channels: u8,
    pub requested_bit_depth: u8,
    pub device_bit_resolution: u8,
    pub subslot_size: u8,
    pub control_interface: u8,
    pub streaming_interface: u8,
    pub alternate_setting: u8,
    pub clock_source_id: u8,
    pub data_endpoint_address: u8,
    pub feedback_endpoint_address: u8,
    pub maximum_packet_size: u32,
    pub feedback_maximum_packet_size: u32,
    pub interval_microseconds: u32,
    pub required_packet_bytes: u32,
    pub previous_clock_rate: u32,
    pub verified_clock_rate: u32,
    pub clock_changed: bool,
}

#[derive(Clone, uniffi::Record)]
pub struct DacVolumeDto {
    pub available: bool,
    pub min_db: f64,
    pub max_db: f64,
    pub current_db: f64,
}

impl DacVolumeDto {
    fn unavailable() -> Self {
        Self {
            available: false,
            min_db: 0.0,
            max_db: 0.0,
            current_db: 0.0,
        }
    }
}

#[derive(Clone, uniffi::Record)]
pub struct UsbTransportStatus {
    pub configured: bool,
    pub playing: bool,
    pub queued_bytes: u32,
    pub accepted_frames: u64,
    pub submitted_frames: u64,
    pub in_flight_frames: u64,
    pub completed_frames: u64,
    pub discarded_frames: u64,
    pub submitted_silence_frames: u64,
    pub in_flight_silence_frames: u64,
    pub completed_silence_frames: u64,
    pub discarded_silence_frames: u64,
    pub completed_transfers: u64,
    pub failed_transfers: u64,
    pub valid_feedback_packets: u64,
    pub accepted_pcm_hash: u64,
    pub submitted_pcm_hash: u64,
    pub payload_integrity_errors: u64,
    pub format_configuration_count: u64,
    pub transport_start_count: u64,
    pub flush_count: u64,
    pub pending_transfer_count: u32,
    pub cancellation_requests: u64,
    pub callbacks_drained: u64,
    pub cancellation_errors: u64,
    pub interface_reset_succeeded: bool,
    pub released_interface_count: u32,
    pub handle_closed: bool,
    pub context_released: bool,
    pub worker_closed: bool,
    pub error_message: String,
}

#[derive(Clone, uniffi::Record)]
pub struct SourceInfoDto {
    pub container: String,
    pub codec: String,
    pub sample_rate: u32,
    pub channels: u8,
    pub bits_per_sample: u32,
    pub duration_us: u64,
    pub total_frames: u64,
}

#[derive(Clone, uniffi::Record)]
pub struct PlaybackStatusDto {
    pub state: String,
    pub generation: u64,
    pub position_us: u64,
    pub duration_us: u64,
    pub buffered_us: u64,
    pub decoded_frames: u64,
    pub accepted_frames: u64,
    pub completed_frames: u64,
    pub discarded_frames: u64,
    pub silence_frames: u64,
    pub decoder_eos: bool,
    pub output_eos: bool,
    pub sample_rate: u32,
    pub channels: u8,
    pub valid_bits: u8,
    pub error_message: String,
}

impl From<rovia_decoder::SourceInfo> for SourceInfoDto {
    fn from(info: rovia_decoder::SourceInfo) -> Self {
        Self {
            container: info.container,
            codec: info.codec,
            sample_rate: info.sample_rate,
            channels: info.channels as u8,
            bits_per_sample: info.bits_per_coded_sample.or(info.bits_per_sample).unwrap_or(0),
            duration_us: info.duration_us.unwrap_or(0),
            total_frames: info.total_frames.unwrap_or(0),
        }
    }
}

impl From<playback::PlaybackStatusSnapshot> for PlaybackStatusDto {
    fn from(status: playback::PlaybackStatusSnapshot) -> Self {
        Self {
            state: status.state.name().to_string(),
            generation: status.generation,
            position_us: status.position_us,
            duration_us: status.duration_us,
            buffered_us: status.buffered_us,
            decoded_frames: status.decoded_frames,
            accepted_frames: status.accepted_frames,
            completed_frames: status.completed_frames,
            discarded_frames: status.discarded_frames,
            silence_frames: status.silence_frames,
            decoder_eos: status.decoder_eos,
            output_eos: status.output_eos,
            sample_rate: status.sample_rate,
            channels: status.channels,
            valid_bits: status.valid_bits,
            error_message: status.error,
        }
    }
}

impl PlaybackStatusDto {
    fn idle() -> Self {
        Self {
            state: playback::PlaybackState::Idle.name().to_string(),
            generation: 0,
            position_us: 0,
            duration_us: 0,
            buffered_us: 0,
            decoded_frames: 0,
            accepted_frames: 0,
            completed_frames: 0,
            discarded_frames: 0,
            silence_frames: 0,
            decoder_eos: false,
            output_eos: false,
            sample_rate: 0,
            channels: 0,
            valid_bits: 0,
            error_message: String::new(),
        }
    }
}

#[derive(uniffi::Object)]
pub struct UsbDirectSession {
    #[cfg(target_os = "android")]
    native: native::NativeUsbWorker,
}

#[uniffi::export]
impl UsbDirectSession {
    #[uniffi::constructor]
    pub fn open(
        file_descriptor: i32,
        raw_descriptors: Vec<u8>,
    ) -> Result<Arc<Self>, DirectUsbError> {
        let parsed = descriptors::parse(&raw_descriptors)
            .map_err(|failure| format!("descriptor_parse: {failure}"))?;
        #[cfg(target_os = "android")]
        {
            let native = native::NativeUsbWorker::open(file_descriptor, parsed, raw_descriptors)
            .map_err(|failure| format!("libusb_session_open: {failure}"))?;
            Ok(Arc::new(Self { native }))
        }
        #[cfg(not(target_os = "android"))]
        {
            let _ = (file_descriptor, parsed);
            Err("Direct USB sessions require an Android target".to_string().into())
        }
    }

    pub fn is_connected(&self) -> bool {
        #[cfg(target_os = "android")]
        {
            return self.native.is_open();
        }
        #[cfg(not(target_os = "android"))]
        false
    }

    pub fn shutdown(&self) -> UsbTransportStatus {
        #[cfg(target_os = "android")]
        {
            self.native.shutdown();
            return self.native.transport_status();
        }
        #[cfg(not(target_os = "android"))]
        {
            UsbTransportStatus {
                configured: false,
                playing: false,
                queued_bytes: 0,
                accepted_frames: 0,
                submitted_frames: 0,
                in_flight_frames: 0,
                completed_frames: 0,
                discarded_frames: 0,
                submitted_silence_frames: 0,
                in_flight_silence_frames: 0,
                completed_silence_frames: 0,
                discarded_silence_frames: 0,
                completed_transfers: 0,
                failed_transfers: 0,
                valid_feedback_packets: 0,
                accepted_pcm_hash: pcm::FNV1A64_OFFSET,
                submitted_pcm_hash: pcm::FNV1A64_OFFSET,
                payload_integrity_errors: 0,
                format_configuration_count: 0,
                transport_start_count: 0,
                flush_count: 0,
                pending_transfer_count: 0,
                cancellation_requests: 0,
                callbacks_drained: 0,
                cancellation_errors: 0,
                interface_reset_succeeded: true,
                released_interface_count: 0,
                handle_closed: true,
                context_released: true,
                worker_closed: true,
                error_message: "Direct USB PCM output requires Android".to_string(),
            }
        }
    }

    pub fn open_source(
        &self,
        source_file_descriptor: i32,
        extension_hint: String,
    ) -> Result<SourceInfoDto, DirectUsbError> {
        #[cfg(target_os = "android")]
        {
            return self
                .native
                .open_source(source_file_descriptor, extension_hint)
                .map(Into::into)
                .map_err(Into::into);
        }
        #[cfg(not(target_os = "android"))]
        {
            let _ = (source_file_descriptor, extension_hint);
            Err("Direct USB playback requires an Android target".to_string().into())
        }
    }

    pub fn prepare_playback(&self) -> Result<PlaybackStatusDto, DirectUsbError> {
        #[cfg(target_os = "android")]
        {
            return self.native.prepare_playback().map(Into::into).map_err(Into::into);
        }
        #[cfg(not(target_os = "android"))]
        {
            Err("Direct USB playback requires an Android target".to_string().into())
        }
    }

    pub fn start_playback(&self) -> Result<(), DirectUsbError> {
        #[cfg(target_os = "android")]
        {
            return self.native.start_playback().map_err(Into::into);
        }
        #[cfg(not(target_os = "android"))]
        {
            Err("Direct USB playback requires an Android target".to_string().into())
        }
    }

    pub fn pause_playback(&self) -> Result<(), DirectUsbError> {
        #[cfg(target_os = "android")]
        {
            return self.native.pause_playback().map_err(Into::into);
        }
        #[cfg(not(target_os = "android"))]
        {
            Err("Direct USB playback requires an Android target".to_string().into())
        }
    }

    pub fn seek_playback(&self, position_us: u64) -> Result<(), DirectUsbError> {
        #[cfg(target_os = "android")]
        {
            return self.native.seek_playback(position_us).map_err(Into::into);
        }
        #[cfg(not(target_os = "android"))]
        {
            let _ = position_us;
            Err("Direct USB playback requires an Android target".to_string().into())
        }
    }

    pub fn stop_playback(&self) -> Result<(), DirectUsbError> {
        #[cfg(target_os = "android")]
        {
            self.native.stop_playback();
            return Ok(());
        }
        #[cfg(not(target_os = "android"))]
        {
            Err("Direct USB playback requires an Android target".to_string().into())
        }
    }

    pub fn playback_status(&self) -> PlaybackStatusDto {
        #[cfg(target_os = "android")]
        {
            return self.native.playback_status().into();
        }
        #[cfg(not(target_os = "android"))]
        {
            PlaybackStatusDto::idle()
        }
    }

    pub fn release_source(&self) -> Result<(), DirectUsbError> {
        #[cfg(target_os = "android")]
        {
            self.native.close_source();
            return Ok(());
        }
        #[cfg(not(target_os = "android"))]
        {
            Err("Direct USB playback requires an Android target".to_string().into())
        }
    }

    pub fn dac_volume(&self) -> DacVolumeDto {
        #[cfg(target_os = "android")]
        {
            return self.native.dac_volume();
        }
        #[cfg(not(target_os = "android"))]
        {
            DacVolumeDto::unavailable()
        }
    }

    pub fn set_dac_volume(&self, db: f64) -> Result<DacVolumeDto, DirectUsbError> {
        #[cfg(target_os = "android")]
        {
            return self.native.set_dac_volume(db).map_err(DirectUsbError::from);
        }
        #[cfg(not(target_os = "android"))]
        {
            let _ = db;
            Err("Direct USB playback requires an Android target".to_string().into())
        }
    }

}
