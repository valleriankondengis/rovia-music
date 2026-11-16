use crate::clock::{self, PacketClock};
use crate::flush_barrier::FlushBarrier;
use crate::volume::{self, Volume};
use crate::DacVolumeDto;
use crate::descriptors::{
    self, ClockControlTarget, ParsedDescriptors, PcmOutputTarget,
};
use crate::pcm::{fnv1a64, fnv1a64_update, PcmQueue, FNV1A64_OFFSET};
use crate::playback::{is_lossless_codec, PcmSink, PlaybackEngine, PlaybackStatusSnapshot};
use crate::{
    SourceInfoDto, UsbPlaybackConfiguration, UsbTransportStatus,
};
use libusb1_sys as usb;
use rovia_decoder::DecoderEngine;
use std::ffi::CStr;
use std::os::unix::io::FromRawFd;
use std::ptr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{self, Receiver, SyncSender, TryRecvError};
use std::sync::{Arc, Condvar, Mutex, MutexGuard};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

const LIBUSB_OPTION_NO_DEVICE_DISCOVERY: u32 = 0x02;
const TRANSFER_PACKET_WINDOW_MICROSECONDS: u32 = 4_000;
const MAX_TRANSFER_PACKETS: usize = 32;
const DATA_TRANSFER_SLOTS: usize = 4;
const MIN_PCM_QUEUE_BYTES: usize = 16 * 1024;
const MAX_PCM_QUEUE_BYTES: usize = 1024 * 1024;
const FEEDBACK_TIMEOUT: Duration = Duration::from_secs(2);
const FLUSH_TIMEOUT: Duration = Duration::from_secs(2);

struct NativeUsbResources {
    context: *mut usb::libusb_context,
    handle: *mut usb::libusb_device_handle,
    claimed_interfaces: Vec<i32>,
    alternate_interface: Option<i32>,
    alternate_setting: u8,
}

struct ResourceCleanupResult {
    interface_reset_succeeded: bool,
    released_interface_count: u32,
    handle_closed: bool,
    context_released: bool,
    error: Option<String>,
}

impl NativeUsbResources {
    fn claim_interface(&mut self, interface_number: u8) -> Result<(), String> {
        let interface = i32::from(interface_number);
        if self.claimed_interfaces.contains(&interface) {
            return Ok(());
        }
        let result = unsafe { usb::libusb_claim_interface(self.handle, interface) };
        if result < 0 {
            return Err(format!(
                "libusb_claim_interface({interface_number}): {}",
                error_name(result)
            ));
        }
        self.claimed_interfaces.push(interface);
        Ok(())
    }

    fn set_interface_alt_setting(
        &mut self,
        interface_number: u8,
        alternate_setting: u8,
    ) -> Result<(), String> {
        let interface = i32::from(interface_number);
        let result = unsafe {
            usb::libusb_set_interface_alt_setting(
                self.handle,
                interface,
                i32::from(alternate_setting),
            )
        };
        if result < 0 {
            return Err(format!(
                "libusb_set_interface_alt_setting(interface={interface_number}, alternate={alternate_setting}): {}",
                error_name(result)
            ));
        }
        self.alternate_interface = Some(interface);
        self.alternate_setting = alternate_setting;
        Ok(())
    }
}

impl NativeUsbResources {
    fn cleanup(&mut self) -> ResourceCleanupResult {
        let mut errors = Vec::new();
        let mut interface_reset_succeeded = true;
        let mut released_interface_count = 0u32;
        let mut handle_closed = self.handle.is_null();
        let mut context_released = self.context.is_null();

        unsafe {
            if !self.handle.is_null() {
                if let Some(interface) = self.alternate_interface {
                    if self.alternate_setting != 0 {
                        let result = usb::libusb_set_interface_alt_setting(self.handle, interface, 0);
                        interface_reset_succeeded = result == 0
                            || result == usb::constants::LIBUSB_ERROR_NO_DEVICE;
                        if !interface_reset_succeeded {
                            errors.push(format!(
                                "libusb_set_interface_alt_setting(interface={interface}, alternate=0): {}",
                                error_name(result)
                            ));
                        }
                    }
                }
                self.alternate_interface = None;
                self.alternate_setting = 0;

                while let Some(interface) = self.claimed_interfaces.pop() {
                    let result = usb::libusb_release_interface(self.handle, interface);
                    if result == 0 {
                        released_interface_count = released_interface_count.saturating_add(1);
                    } else if result != usb::constants::LIBUSB_ERROR_NO_DEVICE {
                        errors.push(format!(
                            "libusb_release_interface({interface}): {}",
                            error_name(result)
                        ));
                    }
                }
                usb::libusb_close(self.handle);
                self.handle = ptr::null_mut();
                handle_closed = true;
            }

            if !self.context.is_null() {
                usb::libusb_exit(self.context);
                self.context = ptr::null_mut();
                context_released = true;
            }
        }

        ResourceCleanupResult {
            interface_reset_succeeded,
            released_interface_count,
            handle_closed,
            context_released,
            error: if errors.is_empty() { None } else { Some(errors.join("; ")) },
        }
    }
}

impl Drop for NativeUsbResources {
    fn drop(&mut self) {
        let _ = self.cleanup();
    }
}

impl volume::Control for NativeUsbResources {
    fn transfer(
        &self,
        input: bool,
        request: u8,
        value: u16,
        index: u16,
        data: &mut [u8],
    ) -> Result<(), String> {
        let result = unsafe {
            usb::libusb_control_transfer(
                self.handle,
                clock::interface_request_type(input),
                request,
                value,
                index,
                data.as_mut_ptr(),
                data.len() as u16,
                clock::CONTROL_TRANSFER_TIMEOUT_MS,
            )
        };
        if result < 0 {
            return Err(error_name(result));
        }
        if result as usize != data.len() {
            return Err(format!(
                "Short USB control response: expected {} bytes, received {result}",
                data.len()
            ));
        }
        Ok(())
    }
}

struct NativeUsbSession {
    resources: NativeUsbResources,
    high_speed: bool,
    usb_speed: i32,
    clock_target: Option<ClockControlTarget>,
    raw_descriptors: Vec<u8>,
    volume: Option<Volume>,
    volume_attempted: bool,
}

impl NativeUsbSession {
    fn open(
        file_descriptor: i32,
        parsed: &ParsedDescriptors,
        raw_descriptors: Vec<u8>,
    ) -> Result<Self, String> {
        if file_descriptor < 0 {
            return Err("file_descriptor_validation: Android supplied an invalid USB file descriptor".to_string());
        }

        let option_result = unsafe {
            usb::libusb_set_option(
                ptr::null_mut(),
                LIBUSB_OPTION_NO_DEVICE_DISCOVERY,
            )
        };
        if option_result < 0 {
            return Err(format!(
                "libusb_set_option_no_device_discovery: {}",
                error_name(option_result)
            ));
        }

        let mut context = ptr::null_mut();
        let init_result = unsafe { usb::libusb_init(&mut context) };
        if init_result < 0 {
            if !context.is_null() {
                unsafe { usb::libusb_exit(context) };
            }
            return Err(format!("libusb_init: {}", error_name(init_result)));
        }
        if context.is_null() {
            return Err("libusb_init: libusb returned success without a context".to_string());
        }

        let mut resources = NativeUsbResources {
            context,
            handle: ptr::null_mut(),
            claimed_interfaces: Vec::new(),
            alternate_interface: None,
            alternate_setting: 0,
        };
        let wrap_result = unsafe {
            usb::libusb_wrap_sys_device(
                resources.context,
                file_descriptor as usize as *mut libc::c_int,
                &mut resources.handle,
            )
        };
        if wrap_result < 0 || resources.handle.is_null() {
            return Err(format!(
                "libusb_wrap_sys_device: {}",
                error_name(wrap_result)
            ));
        }

        let auto_detach_result = unsafe {
            usb::libusb_set_auto_detach_kernel_driver(resources.handle, 1)
        };
        if auto_detach_result < 0 {
            return Err(format!(
                "libusb_set_auto_detach_kernel_driver: {}",
                error_name(auto_detach_result)
            ));
        }

        let clock_target = descriptors::resolve_clock_control_target(parsed)
            .map_err(|failure| format!("clock_control_target: {failure}"))?;
        if let Some(target) = clock_target {
            resources.claim_interface(target.control_interface)?;
            resources.claim_interface(target.streaming_interface)?;
            resources.set_interface_alt_setting(target.streaming_interface, 0)?;
        }

        let device = unsafe { usb::libusb_get_device(resources.handle) };
        if device.is_null() {
            return Err("libusb_get_device: libusb returned an empty device handle".to_string());
        }

        let mut native_descriptor: usb::libusb_device_descriptor = unsafe { std::mem::zeroed() };
        let descriptor_result = unsafe {
            usb::libusb_get_device_descriptor(device, &mut native_descriptor)
        };
        if descriptor_result < 0 {
            return Err(format!(
                "libusb_get_device_descriptor: {}",
                error_name(descriptor_result)
            ));
        }

        if parsed.vendor_id != 0 && parsed.vendor_id != native_descriptor.idVendor {
            return Err("identity_validation: Android descriptor VID and libusb device VID do not match".to_string());
        }
        if parsed.product_id != 0 && parsed.product_id != native_descriptor.idProduct {
            return Err("identity_validation: Android descriptor PID and libusb device PID do not match".to_string());
        }

        let speed = unsafe { usb::libusb_get_device_speed(device) };
        Ok(Self {
            resources,
            high_speed: speed == 3,
            usb_speed: speed,
            clock_target,
            raw_descriptors,
            volume: None,
            volume_attempted: false,
        })
    }

    fn clock_control_target(&self) -> Option<ClockControlTarget> {
        self.clock_target
    }

    fn configure_pcm(
        &mut self,
        target: &PcmOutputTarget,
        sample_rate: u32,
        requested_bit_depth: u8,
    ) -> Result<UsbPlaybackConfiguration, String> {
        if sample_rate == 0 {
            return Err("PCM sample rate must be greater than zero".to_string());
        }
        if !matches!(self.usb_speed, 2 | 3) {
            return Err(format!(
                "Direct USB isochronous transport supports Full Speed and High Speed devices; detected {}",
                speed_name(self.usb_speed)
            ));
        }
        if self.clock_target.is_none_or(|clock_target| {
            clock_target.control_interface != target.control_interface
                || clock_target.streaming_interface != target.streaming_interface
                || clock_target.clock_source_id != target.clock_source_id
        }) {
            return Err("Selected PCM format does not match the claimed UAC2 clock path".to_string());
        }

        let interval_microseconds = clock::service_interval_microseconds(
            self.high_speed,
            target.interval,
        )?;
        let required_packet_bytes = clock::validate_packet_capacity(
            sample_rate,
            target.channels,
            target.subslot_size,
            target.maximum_packet_size,
            interval_microseconds,
        )?;
        if target.synchronization_type == "Asynchronous"
            && target.feedback_endpoint_address == 0
        {
            return Err("Asynchronous PCM output requires an explicit feedback endpoint".to_string());
        }

        self.resources
            .set_interface_alt_setting(target.streaming_interface, 0)
            .map_err(|failure| format!("streaming_alternate_reset: {failure}"))?;

        let index = clock::clock_control_index(
            target.clock_source_id,
            target.control_interface,
        );
        let mut current_data = [0u8; 4];
        self.control_transfer(
            true,
            clock::UAC2_GET_CUR,
            clock::UAC2_SAMPLING_FREQUENCY_CONTROL,
            index,
            &mut current_data,
        )
        .map_err(|failure| format!("clock_get_cur_before_set: {failure}"))?;
        let previous_clock_rate = clock::parse_current_sample_rate(&current_data)?;
        let requested_rate_bytes = sample_rate.to_le_bytes();
        let clock_changed = previous_clock_rate != sample_rate;

        if clock_changed {
            if target.clock_controls & 0x03 != 0x03 {
                return Err(format!(
                    "Clock Source {} is not host-programmable; current rate is {} Hz and requested rate is {} Hz",
                    target.clock_source_id, previous_clock_rate, sample_rate
                ));
            }
            let mut rate_data = requested_rate_bytes;
            self.control_transfer(
                false,
                clock::UAC2_SET_CUR,
                clock::UAC2_SAMPLING_FREQUENCY_CONTROL,
                index,
                &mut rate_data,
            )
            .map_err(|failure| format!("clock_set_cur: {failure}"))?;
        }

        let mut verified_data = [0u8; 4];
        self.control_transfer(
            true,
            clock::UAC2_GET_CUR,
            clock::UAC2_SAMPLING_FREQUENCY_CONTROL,
            index,
            &mut verified_data,
        )
        .map_err(|failure| format!("clock_get_cur_readback: {failure}"))?;
        let verified_clock_rate = clock::parse_current_sample_rate(&verified_data)?;
        if verified_clock_rate != sample_rate {
            return Err(format!(
                "USB clock rate could not be verified: requested {} Hz, device reports {} Hz",
                sample_rate, verified_clock_rate
            ));
        }

        self.resources
            .set_interface_alt_setting(target.streaming_interface, target.alternate_setting)
            .map_err(|failure| format!("streaming_alternate_select: {failure}"))?;

        Ok(UsbPlaybackConfiguration {
            sample_rate,
            channels: target.channels,
            requested_bit_depth,
            device_bit_resolution: target.bit_resolution,
            subslot_size: target.subslot_size,
            control_interface: target.control_interface,
            streaming_interface: target.streaming_interface,
            alternate_setting: target.alternate_setting,
            clock_source_id: target.clock_source_id,
            data_endpoint_address: target.data_endpoint_address,
            feedback_endpoint_address: target.feedback_endpoint_address,
            maximum_packet_size: target.maximum_packet_size,
            feedback_maximum_packet_size: target.feedback_maximum_packet_size,
            interval_microseconds,
            required_packet_bytes,
            previous_clock_rate,
            verified_clock_rate,
            clock_changed,
        })
    }

    fn initialize_volume(&mut self, target: &PcmOutputTarget, parsed: &ParsedDescriptors) {
        if self.volume_attempted {
            return;
        }
        self.volume_attempted = true;
        let terminal_link = parsed
            .audio_formats
            .iter()
            .find(|format| {
                format.interface_number == target.streaming_interface
                    && format.alternate_setting == target.alternate_setting
            })
            .map(|format| format.terminal_link);
        let Some(terminal_link) = terminal_link else {
            return;
        };
        let Some(feature) = volume::find_feature_unit(
            &self.raw_descriptors,
            target.control_interface,
            terminal_link,
            target.channels,
        ) else {
            return;
        };
        if let Ok(opened) = Volume::open(&self.resources, feature) {
            self.volume = Some(opened);
        }
    }

    fn dac_volume(&self) -> DacVolumeDto {
        self.volume
            .as_ref()
            .map(|volume| volume.snapshot().into())
            .unwrap_or_else(DacVolumeDto::unavailable)
    }

    fn set_dac_volume(&mut self, db: f64) -> Result<DacVolumeDto, String> {
        let Some(volume) = self.volume.as_mut() else {
            return Err("DAC volume control is not available".to_string());
        };
        volume.set(&self.resources, db).map(Into::into)
    }

    fn reset_streaming_alternate(&mut self) {
        if let Some(target) = self.clock_target {
            let _ = self
                .resources
                .set_interface_alt_setting(target.streaming_interface, 0);
        }
    }

    fn control_transfer(
        &self,
        input: bool,
        request: u8,
        value: u16,
        index: u16,
        data: &mut [u8],
    ) -> Result<(), String> {
        let result = unsafe {
            usb::libusb_control_transfer(
                self.resources.handle,
                clock::interface_request_type(input),
                request,
                value,
                index,
                data.as_mut_ptr(),
                data.len() as u16,
                clock::CONTROL_TRANSFER_TIMEOUT_MS,
            )
        };
        if result < 0 {
            return Err(error_name(result));
        }
        if result as usize != data.len() {
            return Err(format!(
                "Short USB control response: expected {} bytes, received {result}",
                data.len()
            ));
        }
        Ok(())
    }

}

struct StreamState {
    queue: PcmQueue,
    configured: bool,
    playing: bool,
    closed: bool,
    flush_barrier: FlushBarrier,
    accepted_frames: u64,
    submitted_frames: u64,
    in_flight_frames: u64,
    completed_frames: u64,
    discarded_frames: u64,
    submitted_silence_frames: u64,
    in_flight_silence_frames: u64,
    completed_silence_frames: u64,
    discarded_silence_frames: u64,
    completed_transfers: u64,
    failed_transfers: u64,
    valid_feedback_packets: u64,
    accepted_pcm_hash: u64,
    submitted_pcm_hash: u64,
    payload_integrity_errors: u64,
    format_configuration_count: u64,
    transport_start_count: u64,
    flush_count: u64,
    pending_transfer_count: u32,
    cancellation_requests: u64,
    callbacks_drained: u64,
    cancellation_errors: u64,
    interface_reset_succeeded: bool,
    released_interface_count: u32,
    handle_closed: bool,
    context_released: bool,
    error: Option<String>,
}

impl Default for StreamState {
    fn default() -> Self {
        Self {
            queue: PcmQueue::default(),
            configured: false,
            playing: false,
            closed: false,
            flush_barrier: FlushBarrier::default(),
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
            accepted_pcm_hash: FNV1A64_OFFSET,
            submitted_pcm_hash: FNV1A64_OFFSET,
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
            error: None,
        }
    }
}

type SharedState = Arc<(Mutex<StreamState>, Condvar)>;

enum WorkerCommand {
    ConfigurePcm {
        sample_rate: u32,
        channels: u8,
        bit_depth: u8,
        reply: SyncSender<Result<UsbPlaybackConfiguration, String>>,
    },
    GetDacVolume {
        reply: SyncSender<DacVolumeDto>,
    },
    SetDacVolume {
        db: f64,
        reply: SyncSender<Result<DacVolumeDto, String>>,
    },
}

pub(crate) struct NativeUsbWorker {
    shared: SharedState,
    commands: mpsc::Sender<WorkerCommand>,
    thread: Mutex<Option<JoinHandle<()>>>,
    connected: Arc<AtomicBool>,
    playback: Mutex<Option<PlaybackEngine<Arc<NativePcmSink>>>>,
    playback_sink: Arc<NativePcmSink>,
}

impl NativeUsbWorker {
    pub fn open(
        file_descriptor: i32,
        parsed: ParsedDescriptors,
        raw_descriptors: Vec<u8>,
    ) -> Result<Self, String> {
        let shared = Arc::new((Mutex::new(StreamState::default()), Condvar::new()));
        let connected = Arc::new(AtomicBool::new(false));
        let worker_shared = shared.clone();
        let worker_connected = connected.clone();
        let (command_sender, command_receiver) = mpsc::channel();
        let (initialization_sender, initialization_receiver) = mpsc::sync_channel(1);

        let handle = thread::Builder::new()
            .name("rovia-usb-iso".to_string())
            .spawn(move || {

                unsafe {
                    libc::setpriority(libc::PRIO_PROCESS, 0, -16);
                }

                let native = NativeUsbSession::open(file_descriptor, &parsed, raw_descriptors);
                match native {
                    Ok(session) => {

                        worker_connected.store(true, Ordering::Release);
                        if initialization_sender.send(Ok(())).is_ok() {
                            run_worker(
                                session,
                                parsed,
                                command_receiver,
                                worker_shared.clone(),
                            );
                        }
                    }
                    Err(error) => {

                        let _ = initialization_sender.send(Err(error));
                    }
                }

                worker_connected.store(false, Ordering::Release);
                let mut state = lock_state(&worker_shared);
                state.playing = false;
                state.closed = true;
                worker_shared.1.notify_all();
            })
            .map_err(|failure| format!("USB worker thread could not start: {failure}"))?;

        match initialization_receiver.recv() {
            Ok(Ok(())) => {}
            Ok(Err(error)) => {
                let _ = handle.join();
                return Err(error);
            }
            Err(error) => {
                let _ = handle.join();
                return Err(format!("USB worker initialization response failed: {error}"));
            }
        };

        let playback_sink = Arc::new(NativePcmSink {
            shared: shared.clone(),
            commands: command_sender.clone(),
        });
        Ok(Self {
            shared,
            commands: command_sender,
            thread: Mutex::new(Some(handle)),
            connected,
            playback: Mutex::new(None),
            playback_sink,
        })
    }

    pub fn is_open(&self) -> bool {
        self.connected.load(Ordering::Acquire)
    }

    pub fn transport_status(&self) -> UsbTransportStatus {
        let state = lock_state(&self.shared);
        transport_status_from_state(&state)
    }

    pub fn open_source(
        &self,
        source_file_descriptor: i32,
        extension_hint: String,
    ) -> Result<SourceInfoDto, String> {
        let duplicated = unsafe { libc::dup(source_file_descriptor) };
        if duplicated < 0 {
            let error = std::io::Error::last_os_error();
            unsafe {
                libc::close(source_file_descriptor);
            }
            return Err(format!("source_descriptor_dup: {error}"));
        }
        unsafe {
            libc::close(source_file_descriptor);
        }
        let file = unsafe { std::fs::File::from_raw_fd(duplicated) };
        let hint = if extension_hint.is_empty() {
            None
        } else {
            Some(extension_hint.as_str())
        };
        let decoder = DecoderEngine::open_file(file, hint)
            .map_err(|error| format!("source_open: {error}"))?;
        let mut playback = self
            .playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner());
        if playback.is_none() {
            *playback = Some(PlaybackEngine::new(self.playback_sink.clone()));
        }
        let engine = playback.as_mut().unwrap();
        let lossless = is_lossless_codec(&decoder.info().codec, &decoder.info().container);
        engine
            .open_source(decoder, lossless)
            .map(SourceInfoDto::from)
    }

    pub fn prepare_playback(&self) -> Result<PlaybackStatusSnapshot, String> {
        let mut playback = self
            .playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner());
        let engine = playback
            .as_mut()
            .ok_or_else(|| "Open a source before preparing playback".to_string())?;
        engine.prepare()
    }

    pub fn start_playback(&self) -> Result<(), String> {
        let mut playback = self
            .playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner());
        let engine = playback
            .as_mut()
            .ok_or_else(|| "Open a source before starting playback".to_string())?;
        engine.start()
    }

    pub fn pause_playback(&self) -> Result<(), String> {
        let mut playback = self
            .playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner());
        let engine = playback
            .as_mut()
            .ok_or_else(|| "Open a source before pausing playback".to_string())?;
        engine.pause()
    }

    pub fn seek_playback(&self, position_us: u64) -> Result<(), String> {
        let mut playback = self
            .playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner());
        let engine = playback
            .as_mut()
            .ok_or_else(|| "Open a source before seeking playback".to_string())?;
        engine.seek(position_us)
    }

    pub fn stop_playback(&self) {
        let mut playback = self
            .playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner());
        if let Some(engine) = playback.as_mut() {
            engine.stop();
        }
    }

    pub fn playback_status(&self) -> PlaybackStatusSnapshot {
        let playback = self
            .playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner());
        match playback.as_ref() {
            Some(engine) => engine.status(),
            None => PlaybackStatusSnapshot::default(),
        }
    }

    pub fn close_source(&self) {
        let mut playback = self
            .playback
            .lock()
            .unwrap_or_else(|failure| failure.into_inner());
        if let Some(engine) = playback.as_mut() {
            engine.close_source();
        }
        *playback = None;
    }

    pub fn dac_volume(&self) -> DacVolumeDto {
        let (reply_sender, reply_receiver) = mpsc::sync_channel(1);
        if self
            .commands
            .send(WorkerCommand::GetDacVolume { reply: reply_sender })
            .is_err()
        {
            return DacVolumeDto::unavailable();
        }
        self.shared.1.notify_all();
        reply_receiver
            .recv_timeout(Duration::from_secs(5))
            .unwrap_or_else(|_| DacVolumeDto::unavailable())
    }

    pub fn set_dac_volume(&self, db: f64) -> Result<DacVolumeDto, String> {
        let (reply_sender, reply_receiver) = mpsc::sync_channel(1);
        self.commands
            .send(WorkerCommand::SetDacVolume {
                db,
                reply: reply_sender,
            })
            .map_err(|_| "USB worker is no longer available".to_string())?;
        self.shared.1.notify_all();
        reply_receiver
            .recv_timeout(Duration::from_secs(15))
            .map_err(|error| format!("USB DAC volume worker timed out: {error}"))?
    }

    pub fn shutdown(&self) {
        self.close_source();
        {
            let mut state = lock_state(&self.shared);
            state.closed = true;
            state.playing = false;
            self.shared.1.notify_all();
        }
        if let Some(handle) = self
            .thread
            .lock()
            .unwrap_or_else(|failure| failure.into_inner())
            .take()
        {
            let _ = handle.join();
        }
    }
}

fn transport_status_from_state(state: &StreamState) -> UsbTransportStatus {
    UsbTransportStatus {
        configured: state.configured,
        playing: state.playing,
        queued_bytes: state.queue.queued_bytes().min(u32::MAX as usize) as u32,
        accepted_frames: state.accepted_frames,
        submitted_frames: state.submitted_frames,
        in_flight_frames: state.in_flight_frames,
        completed_frames: state.completed_frames,
        discarded_frames: state.discarded_frames,
        submitted_silence_frames: state.submitted_silence_frames,
        in_flight_silence_frames: state.in_flight_silence_frames,
        completed_silence_frames: state.completed_silence_frames,
        discarded_silence_frames: state.discarded_silence_frames,
        completed_transfers: state.completed_transfers,
        failed_transfers: state.failed_transfers,
        valid_feedback_packets: state.valid_feedback_packets,
        accepted_pcm_hash: state.accepted_pcm_hash,
        submitted_pcm_hash: state.submitted_pcm_hash,
        payload_integrity_errors: state.payload_integrity_errors,
        format_configuration_count: state.format_configuration_count,
        transport_start_count: state.transport_start_count,
        flush_count: state.flush_count,
        pending_transfer_count: state.pending_transfer_count,
        cancellation_requests: state.cancellation_requests,
        callbacks_drained: state.callbacks_drained,
        cancellation_errors: state.cancellation_errors,
        interface_reset_succeeded: state.interface_reset_succeeded,
        released_interface_count: state.released_interface_count,
        handle_closed: state.handle_closed,
        context_released: state.context_released,
        worker_closed: state.closed,
        error_message: state.error.clone().unwrap_or_default(),
    }
}

pub(crate) struct NativePcmSink {
    shared: SharedState,
    commands: mpsc::Sender<WorkerCommand>,
}

impl PcmSink for NativePcmSink {
    fn configure(
        &self,
        sample_rate: u32,
        channels: u8,
        bit_depth: u8,
    ) -> Result<UsbPlaybackConfiguration, String> {
        let (reply_sender, reply_receiver) = mpsc::sync_channel(1);
        self.commands
            .send(WorkerCommand::ConfigurePcm {
                sample_rate,
                channels,
                bit_depth,
                reply: reply_sender,
            })
            .map_err(|_| "USB worker is no longer available".to_string())?;
        self.shared.1.notify_all();
        reply_receiver
            .recv_timeout(Duration::from_secs(15))
            .map_err(|error| format!("USB PCM configuration worker timed out: {error}"))?
    }

    fn write(&self, data: &[u8]) -> Result<usize, String> {
        let mut state = lock_state(&self.shared);
        check_stream_state(&state)?;
        if !state.configured {
            return Err("Configure a PCM USB output before writing samples".to_string());
        }
        if state.flush_barrier.is_pending() {
            return Err("PCM writes are paused while USB transfers are being flushed".to_string());
        }
        let accepted = state.queue.write(data)?;
        let frame_bytes = state.queue.frame_bytes();
        state.accepted_frames = state
            .accepted_frames
            .saturating_add((accepted / frame_bytes) as u64);
        state.accepted_pcm_hash = fnv1a64_update(state.accepted_pcm_hash, &data[..accepted]);
        self.shared.1.notify_all();
        Ok(accepted)
    }

    fn available(&self) -> Result<usize, String> {
        let state = lock_state(&self.shared);
        check_stream_state(&state)?;
        if !state.configured {
            return Err("Configure a PCM USB output before querying its queue capacity".to_string());
        }
        Ok(state.queue.available_bytes())
    }

    fn start(&self) -> Result<(), String> {
        let mut state = lock_state(&self.shared);
        check_stream_state(&state)?;
        if !state.configured {
            return Err("Configure a PCM USB output before starting isochronous transport".to_string());
        }
        if state.flush_barrier.is_pending() {
            return Err("USB transport cannot start while a flush is pending".to_string());
        }
        if !state.playing {
            state.transport_start_count = state.transport_start_count.saturating_add(1);
        }
        state.playing = true;
        self.shared.1.notify_all();
        Ok(())
    }

    fn flush(&self) -> Result<(), String> {
        let mut state = lock_state(&self.shared);
        check_stream_state(&state)?;
        if !state.configured {
            return Ok(());
        }
        state.playing = false;
        state.flush_count = state.flush_count.saturating_add(1);
        let requested = state.flush_barrier.request();
        self.shared.1.notify_all();
        let (state, timeout) = self
            .shared
            .1
            .wait_timeout_while(state, FLUSH_TIMEOUT, |state| {
                !state.flush_barrier.is_completed(requested) && state.error.is_none() && !state.closed
            })
            .unwrap_or_else(|failure| failure.into_inner());
        if let Some(error) = &state.error {
            return Err(error.clone());
        }
        if state.closed {
            return Err("USB worker closed while flushing transfers".to_string());
        }
        if timeout.timed_out() && !state.flush_barrier.is_completed(requested) {
            return Err("USB transfer cancellation did not complete before the flush timeout".to_string());
        }
        Ok(())
    }

    fn status(&self) -> UsbTransportStatus {
        let state = lock_state(&self.shared);
        transport_status_from_state(&state)
    }
}

impl Drop for NativeUsbWorker {
    fn drop(&mut self) {
        self.shutdown();
    }
}

fn run_worker(
    mut session: NativeUsbSession,
    parsed: ParsedDescriptors,
    commands: Receiver<WorkerCommand>,
    shared: SharedState,
) {
    let mut output: Option<NativeIsochronousOutput> = None;
    let mut logged_mode = 0u8;

    loop {
        let closed_at_entry = lock_state(&shared).closed;
        if closed_at_entry {

            break;
        }

        acknowledge_flush(&mut output, &shared);

        loop {
            match commands.try_recv() {
                Ok(command) => apply_command(command, &mut session, &parsed, &mut output, &shared),
                Err(TryRecvError::Empty) => break,
                Err(TryRecvError::Disconnected) => {

                    let mut state = lock_state(&shared);
                    state.closed = true;
                    state.playing = false;
                    break;
                }
            }
        }

        let (closed, playing) = {
            let state = lock_state(&shared);
            (state.closed, state.playing)
        };
        if closed {

            break;
        }

        if playing {
            if logged_mode != 1 {

                logged_mode = 1;
            }
            if let Some(transport) = output.as_mut() {
                if let Err(error) = transport.pump(&shared) {

                    let mut state = lock_state(&shared);
                    state.error = Some(error);
                    state.playing = false;
                    shared.1.notify_all();
                    break;
                }
            } else {
                let error = "USB transport was started before its endpoint buffers were prepared";

                let mut state = lock_state(&shared);
                state.error = Some(error.to_string());
                state.playing = false;
                shared.1.notify_all();
                break;
            }
        } else {
            if logged_mode != 2 {

                logged_mode = 2;
            }
            let state = lock_state(&shared);
            if !state.closed {
                let (state, _) = shared
                    .1
                    .wait_timeout(state, Duration::from_millis(10))
                    .unwrap_or_else(|failure| failure.into_inner());
                if state.closed || state.playing {

                    logged_mode = 0;
                } else if state.flush_barrier.is_pending() {

                    logged_mode = 0;
                }
                drop(state);
            }
        }
    }

    if let Some(transport) = output.as_mut() {

        let reset = transport.reset();

        let mut state = lock_state(&shared);
        apply_reset_result(&mut state, reset);
        let discarded = state.queue.clear() as u64;
        state.discarded_frames = state.discarded_frames.saturating_add(discarded);
        state.playing = false;
        shared.1.notify_all();
    }

    drop(output);

    let cleanup = session.resources.cleanup();

    {
        let mut state = lock_state(&shared);
        state.interface_reset_succeeded = cleanup.interface_reset_succeeded;
        state.released_interface_count = cleanup.released_interface_count;
        state.handle_closed = cleanup.handle_closed;
        state.context_released = cleanup.context_released;
        if let Some(error) = cleanup.error {
            state.error = Some(match state.error.take() {
                Some(existing) => format!("{existing}; resource cleanup: {error}"),
                None => format!("resource cleanup: {error}"),
            });
        }
        shared.1.notify_all();
    }
    drop(session);

}

fn apply_command(
    command: WorkerCommand,
    session: &mut NativeUsbSession,
    parsed: &ParsedDescriptors,
    output: &mut Option<NativeIsochronousOutput>,
    shared: &SharedState,
) {
    match command {
        WorkerCommand::GetDacVolume { reply } => {
            let _ = reply.send(session.dac_volume());
            return;
        }
        WorkerCommand::SetDacVolume { db, reply } => {
            let _ = reply.send(session.set_dac_volume(db));
            return;
        }
        WorkerCommand::ConfigurePcm {
            sample_rate,
            channels,
            bit_depth,
            reply,
        } => {
            {
                let mut state = lock_state(shared);
                state.playing = false;
                shared.1.notify_all();
            }
            if let Some(mut previous_output) = output.take() {
                let reset = previous_output.reset();
                let mut state = lock_state(shared);
                apply_reset_result(&mut state, reset);
            }
            let result = (|| {
                let clock_target = session
                    .clock_control_target()
                    .ok_or_else(|| "No unique UAC2 clock path is available".to_string())?;
                let target = descriptors::resolve_pcm_output_target(
                    &parsed.audio_formats,
                    &parsed.endpoints,
                    clock_target,
                    channels,
                    bit_depth,
                )?;
                let configuration = session.configure_pcm(&target, sample_rate, bit_depth)?;
                session.initialize_volume(&target, parsed);
                let transport = NativeIsochronousOutput::new(session, &target, &configuration)?;
                Ok((configuration, transport))
            })();

            match result {
                Ok((configuration, transport)) => {
                    let frame_bytes = usize::from(configuration.channels)
                        * usize::from(configuration.subslot_size);
                    let queue_capacity = (configuration.sample_rate as usize)
                        .saturating_mul(frame_bytes)
                        / 5;
                    let queue_capacity = queue_capacity
                        .clamp(MIN_PCM_QUEUE_BYTES, MAX_PCM_QUEUE_BYTES);
                    let mut state = lock_state(shared);
                    let _ = state.queue.clear();
                    state.queue.configure(frame_bytes, queue_capacity).unwrap();
                    state.configured = true;
                    state.playing = false;
                    state.flush_barrier.reset();
                    state.accepted_frames = 0;
                    state.submitted_frames = 0;
                    state.in_flight_frames = 0;
                    state.completed_frames = 0;
                    state.discarded_frames = 0;
                    state.submitted_silence_frames = 0;
                    state.in_flight_silence_frames = 0;
                    state.completed_silence_frames = 0;
                    state.discarded_silence_frames = 0;
                    state.completed_transfers = 0;
                    state.failed_transfers = 0;
                    state.valid_feedback_packets = 0;
                    state.accepted_pcm_hash = FNV1A64_OFFSET;
                    state.submitted_pcm_hash = FNV1A64_OFFSET;
                    state.payload_integrity_errors = 0;
                    state.pending_transfer_count = 0;
                    state.format_configuration_count = state.format_configuration_count.saturating_add(1);
                    state.error = None;
                    *output = Some(transport);
                    shared.1.notify_all();
                    let _ = reply.send(Ok(configuration));
                }
                Err(error) => {
                    session.reset_streaming_alternate();
                    let mut state = lock_state(shared);
                    let _ = state.queue.reset();
                    state.configured = false;
                    state.playing = false;
                    state.accepted_frames = 0;
                    state.submitted_frames = 0;
                    state.in_flight_frames = 0;
                    state.completed_frames = 0;
                    state.discarded_frames = 0;
                    state.submitted_silence_frames = 0;
                    state.in_flight_silence_frames = 0;
                    state.completed_silence_frames = 0;
                    state.discarded_silence_frames = 0;
                    state.completed_transfers = 0;
                    state.failed_transfers = 0;
                    state.valid_feedback_packets = 0;
                    state.accepted_pcm_hash = FNV1A64_OFFSET;
                    state.submitted_pcm_hash = FNV1A64_OFFSET;
                    state.payload_integrity_errors = 0;
                    state.pending_transfer_count = 0;
                    state.error = None;
                    shared.1.notify_all();
                    let _ = reply.send(Err(error));
                }
            }
        }
    }
}

#[derive(Default)]
struct TransferResetResult {
    completed_frames: u64,
    discarded_frames: u64,
    completed_silence_frames: u64,
    discarded_silence_frames: u64,
    completed_transfers: u64,
    failed_transfers: u64,
    payload_integrity_errors: u64,
    cancellation_requests: u64,
    callbacks_drained: u64,
    cancellation_errors: u64,
}

fn apply_reset_result(state: &mut StreamState, result: TransferResetResult) {
    state.completed_frames = state.completed_frames.saturating_add(result.completed_frames);
    state.discarded_frames = state.discarded_frames.saturating_add(result.discarded_frames);
    state.completed_transfers = state.completed_transfers.saturating_add(result.completed_transfers);
    state.failed_transfers = state.failed_transfers.saturating_add(result.failed_transfers);
    state.payload_integrity_errors = state
        .payload_integrity_errors
        .saturating_add(result.payload_integrity_errors);
    state.completed_silence_frames = state
        .completed_silence_frames
        .saturating_add(result.completed_silence_frames);
    state.discarded_silence_frames = state
        .discarded_silence_frames
        .saturating_add(result.discarded_silence_frames);
    state.in_flight_silence_frames = state
        .in_flight_silence_frames
        .saturating_sub(result.completed_silence_frames.saturating_add(result.discarded_silence_frames));
    state.in_flight_frames = state
        .in_flight_frames
        .saturating_sub(result.completed_frames.saturating_add(result.discarded_frames));
    state.cancellation_requests = state
        .cancellation_requests
        .saturating_add(result.cancellation_requests);
    state.callbacks_drained = state
        .callbacks_drained
        .saturating_add(result.callbacks_drained);
    state.cancellation_errors = state
        .cancellation_errors
        .saturating_add(result.cancellation_errors);
    state.pending_transfer_count = state
        .pending_transfer_count
        .saturating_sub(result.callbacks_drained.min(u32::MAX as u64) as u32);
}

fn acknowledge_flush(output: &mut Option<NativeIsochronousOutput>, shared: &SharedState) {
    let requested = {
        let state = lock_state(shared);
        if !state.flush_barrier.is_pending() {
            return;
        }
        state.flush_barrier.requested()
    };
    let reset = output
        .as_mut()
        .map(|transport| transport.reset())
        .unwrap_or_default();
    let mut state = lock_state(shared);
    apply_reset_result(&mut state, reset);
    let discarded = state.queue.clear() as u64;
    state.discarded_frames = state.discarded_frames.saturating_add(discarded);
    state.flush_barrier.complete(requested);
    shared.1.notify_all();
    drop(state);
}

fn lock_state(shared: &SharedState) -> MutexGuard<'_, StreamState> {
    shared
        .0
        .lock()
        .unwrap_or_else(|failure| failure.into_inner())
}

fn check_stream_state(state: &StreamState) -> Result<(), String> {
    if let Some(error) = &state.error {
        return Err(error.clone());
    }
    if state.closed {
        return Err("USB transport worker is closed".to_string());
    }
    Ok(())
}

struct TransferSlot {
    transfer: *mut usb::libusb_transfer,
    bytes: Vec<u8>,
    pending: bool,
    music_frames: u64,
    music_bytes: usize,
    silence_frames: u64,
    payload_hash: u64,
    submitted: bool,
}

extern "system" fn transfer_completed(transfer: *mut usb::libusb_transfer) {
    if transfer.is_null() {
        return;
    }
    unsafe {
        let slot = (*transfer).user_data as *mut TransferSlot;
        if !slot.is_null() {
            (*slot).pending = false;
        }
    }
}

impl TransferSlot {
    fn new(packet_count: usize, maximum_packet_bytes: usize) -> Result<Box<Self>, String> {
        let transfer = unsafe { usb::libusb_alloc_transfer(packet_count as i32) };
        if transfer.is_null() {
            return Err("USB isochronous transfer allocation failed".to_string());
        }
        Ok(Box::new(Self {
            transfer,
            bytes: vec![0; packet_count.saturating_mul(maximum_packet_bytes)],
            pending: false,
            music_frames: 0,
            music_bytes: 0,
            silence_frames: 0,
            payload_hash: FNV1A64_OFFSET,
            submitted: false,
        }))
    }

    fn submit(
        &mut self,
        handle: *mut usb::libusb_device_handle,
        endpoint_address: u8,
        packet_lengths: &[usize],
    ) -> Result<(), String> {
        let total_length = packet_lengths.iter().sum::<usize>();
        if total_length > self.bytes.len() || total_length > i32::MAX as usize {
            return Err("USB isochronous transfer buffer length is invalid".to_string());
        }
        unsafe {
            usb::libusb_fill_iso_transfer(
                self.transfer,
                handle,
                endpoint_address,
                self.bytes.as_mut_ptr(),
                total_length as i32,
                packet_lengths.len() as i32,
                transfer_completed,
                self as *mut Self as *mut _,
                500,
            );
            for (index, length) in packet_lengths.iter().enumerate() {
                (*(*self.transfer).iso_packet_desc.as_mut_ptr().add(index)).length =
                    *length as u32;
            }
            let result = usb::libusb_submit_transfer(self.transfer);
            if result < 0 {
                return Err(error_name(result));
            }
        }
        self.pending = true;
        self.submitted = true;
        Ok(())
    }

    fn succeeded(&self) -> bool {
        unsafe {
            (*self.transfer).status == usb::constants::LIBUSB_TRANSFER_COMPLETED
                && (0..(*self.transfer).num_iso_packets as usize).all(|index| {
                    let packet = &*(*self.transfer).iso_packet_desc.as_ptr().add(index);
                    packet.status == usb::constants::LIBUSB_TRANSFER_COMPLETED
                        && ((*self.transfer).endpoint & 0x80 != 0
                            || packet.actual_length == packet.length)
                })
        }
    }

    fn cancelled(&self) -> bool {
        unsafe { (*self.transfer).status == usb::constants::LIBUSB_TRANSFER_CANCELLED }
    }

    fn clear_payload(&mut self) {
        self.music_frames = 0;
        self.music_bytes = 0;
        self.silence_frames = 0;
        self.payload_hash = FNV1A64_OFFSET;
        self.submitted = false;
    }

    fn actual_length(&self) -> usize {
        unsafe {
            if (*self.transfer).num_iso_packets <= 0 {
                return 0;
            }
            (*(*self.transfer).iso_packet_desc.as_ptr()).actual_length as usize
        }
    }
}

impl Drop for TransferSlot {
    fn drop(&mut self) {
        assert!(!self.pending, "USB transfer was freed before cancellation completed");
        if !self.transfer.is_null() {
            unsafe { usb::libusb_free_transfer(self.transfer) };
            self.transfer = ptr::null_mut();
        }
    }
}

struct NativeIsochronousOutput {
    context: *mut usb::libusb_context,
    handle: *mut usb::libusb_device_handle,
    data_endpoint_address: u8,
    feedback_endpoint_address: u8,
    feedback_maximum_packet_size: usize,
    maximum_packet_size: usize,
    interval_microseconds: u32,
    high_speed: bool,
    sample_rate: u32,
    frame_bytes: usize,
    packet_count: usize,
    packet_clock: PacketClock,
    slots: Vec<Box<TransferSlot>>,
    feedback_slot: Option<Box<TransferSlot>>,
    feedback_started_at: Option<Instant>,
    last_valid_feedback: Option<Instant>,
}

impl NativeIsochronousOutput {
    fn new(
        session: &NativeUsbSession,
        target: &PcmOutputTarget,
        configuration: &UsbPlaybackConfiguration,
    ) -> Result<Self, String> {
        if target.synchronization_type == "Asynchronous"
            && target.feedback_endpoint_address == 0
        {
            return Err("Asynchronous USB audio requires an explicit feedback endpoint".to_string());
        }
        let feedback_maximum_packet_size =
            usize::try_from(configuration.feedback_maximum_packet_size)
                .map_err(|_| "USB feedback endpoint packet size is invalid".to_string())?;
        if target.feedback_endpoint_address != 0 {
            let expected_feedback_bytes = if session.high_speed { 4 } else { 3 };
            if feedback_maximum_packet_size != expected_feedback_bytes {
                return Err(format!(
                    "USB feedback endpoint on this bus speed must report {expected_feedback_bytes} bytes, descriptor reports {feedback_maximum_packet_size}"
                ));
            }
        }
        let maximum_packet_size = usize::try_from(configuration.maximum_packet_size)
            .map_err(|_| "USB data endpoint packet size is invalid".to_string())?;
        let interval_microseconds = configuration.interval_microseconds;
        if maximum_packet_size == 0 || interval_microseconds == 0 {
            return Err("USB data endpoint packet size and service interval must be nonzero".to_string());
        }
        let packet_count = (TRANSFER_PACKET_WINDOW_MICROSECONDS / interval_microseconds)
            .clamp(1, MAX_TRANSFER_PACKETS as u32) as usize;
        let frame_bytes = usize::from(configuration.channels)
            .checked_mul(usize::from(configuration.subslot_size))
            .filter(|bytes| *bytes > 0)
            .ok_or_else(|| "PCM USB frame size is invalid".to_string())?;
        let mut slots = Vec::with_capacity(DATA_TRANSFER_SLOTS);
        for _ in 0..DATA_TRANSFER_SLOTS {
            slots.push(TransferSlot::new(packet_count, maximum_packet_size)?);
        }
        let feedback_slot = if target.feedback_endpoint_address != 0 {
            Some(TransferSlot::new(1, feedback_maximum_packet_size)?)
        } else {
            None
        };

        Ok(Self {
            context: session.resources.context,
            handle: session.resources.handle,
            data_endpoint_address: configuration.data_endpoint_address,
            feedback_endpoint_address: configuration.feedback_endpoint_address,
            feedback_maximum_packet_size,
            maximum_packet_size,
            interval_microseconds,
            high_speed: session.high_speed,
            sample_rate: configuration.sample_rate,
            frame_bytes,
            packet_count,
            packet_clock: PacketClock::new(
                configuration.sample_rate,
                configuration.interval_microseconds,
            ),
            slots,
            feedback_slot,
            feedback_started_at: None,
            last_valid_feedback: None,
        })
    }

    fn pump(&mut self, shared: &SharedState) -> Result<(), String> {
        self.handle_events()?;
        let mut state = lock_state(shared);
        if !state.playing {
            return Ok(());
        }

        if self.feedback_endpoint_address != 0 {
            let now = Instant::now();
            self.feedback_started_at.get_or_insert(now);
            let feedback_slot = self
                .feedback_slot
                .as_mut()
                .ok_or_else(|| "USB feedback endpoint has no transfer buffer".to_string())?;
            if !feedback_slot.pending {
                if feedback_slot.submitted {
                    if !feedback_slot.succeeded() {
                        return Err("USB feedback transfer failed".to_string());
                    }
                    let actual_length = feedback_slot.actual_length().min(feedback_slot.bytes.len());
                    if actual_length > 0
                        && self.packet_clock.update_feedback(
                            &feedback_slot.bytes[..actual_length],
                            self.high_speed,
                            self.interval_microseconds,
                        )
                    {
                        self.last_valid_feedback = Some(now);
                        state.valid_feedback_packets =
                            state.valid_feedback_packets.saturating_add(1);
                    }
                    feedback_slot.submitted = false;
                    state.pending_transfer_count = state.pending_transfer_count.saturating_sub(1);
                }
                feedback_slot.submit(
                    self.handle,
                    self.feedback_endpoint_address,
                    &[self.feedback_maximum_packet_size],
                )?;
                state.pending_transfer_count = state.pending_transfer_count.saturating_add(1);
            }
            let last_feedback = self.last_valid_feedback.or(self.feedback_started_at);
            if last_feedback.is_some_and(|last| now.duration_since(last) > FEEDBACK_TIMEOUT) {
                return Err("USB feedback stopped or no longer reports a valid rate".to_string());
            }
        }

        for slot in &mut self.slots {
            if slot.pending {
                continue;
            }
            if slot.submitted {
                let music_frames = slot.music_frames;
                state.pending_transfer_count = state.pending_transfer_count.saturating_sub(1);
                if !slot.succeeded() {
                    state.in_flight_frames = state.in_flight_frames.saturating_sub(music_frames);
                    state.in_flight_silence_frames = state
                        .in_flight_silence_frames
                        .saturating_sub(slot.silence_frames);
                    state.discarded_silence_frames = state
                        .discarded_silence_frames
                        .saturating_add(slot.silence_frames);
                    state.discarded_frames = state.discarded_frames.saturating_add(music_frames);
                    state.failed_transfers = state.failed_transfers.saturating_add(1);
                    slot.clear_payload();
                    return Err("USB isochronous audio OUT transfer failed".to_string());
                }
                if slot.music_bytes > 0
                    && fnv1a64(&slot.bytes[..slot.music_bytes]) != slot.payload_hash
                {
                    state.payload_integrity_errors =
                        state.payload_integrity_errors.saturating_add(1);
                }
                state.in_flight_frames = state.in_flight_frames.saturating_sub(music_frames);
                state.in_flight_silence_frames = state
                    .in_flight_silence_frames
                    .saturating_sub(slot.silence_frames);
                state.completed_silence_frames = state
                    .completed_silence_frames
                    .saturating_add(slot.silence_frames);
                state.completed_frames = state.completed_frames.saturating_add(music_frames);
                state.completed_transfers = state.completed_transfers.saturating_add(1);
                slot.clear_payload();
            }

            let mut packet_lengths = vec![0usize; self.packet_count];
            for length in &mut packet_lengths {
                *length = self.packet_clock.next_frames().saturating_mul(self.frame_bytes);
                if *length > self.maximum_packet_size {
                    return Err("USB feedback exceeded the selected endpoint packet capacity".to_string());
                }
            }
            let total_length = packet_lengths.iter().sum::<usize>();
            let source_bytes = state
                .queue
                .copy_prefix(&mut slot.bytes[..total_length], total_length)?;
            let source_frames = (source_bytes / self.frame_bytes) as u64;
            let silence_frames = (total_length / self.frame_bytes) as u64 - source_frames;
            let payload_hash = fnv1a64(&slot.bytes[..source_bytes]);
            slot.submit(self.handle, self.data_endpoint_address, &packet_lengths)?;
            state.pending_transfer_count = state.pending_transfer_count.saturating_add(1);
            state.queue.consume_prefix(source_bytes)?;
            state.submitted_frames = state.submitted_frames.saturating_add(source_frames);
            state.in_flight_frames = state.in_flight_frames.saturating_add(source_frames);
            state.in_flight_silence_frames = state
                .in_flight_silence_frames
                .saturating_add(silence_frames);
            state.submitted_silence_frames = state
                .submitted_silence_frames
                .saturating_add(silence_frames);
            state.submitted_pcm_hash =
                fnv1a64_update(state.submitted_pcm_hash, &slot.bytes[..source_bytes]);
            slot.music_frames = source_frames;
            slot.music_bytes = source_bytes;
            slot.silence_frames = silence_frames;
            slot.payload_hash = payload_hash;
        }
        Ok(())
    }

    fn handle_events(&self) -> Result<(), String> {
        let timeout = libc::timeval {
            tv_sec: 0,
            tv_usec: 1_000,
        };
        let result = unsafe { usb::libusb_handle_events_timeout(self.context, &timeout) };
        if result < 0 && result != usb::constants::LIBUSB_ERROR_INTERRUPTED {
            return Err(format!("libusb_handle_events_timeout: {}", error_name(result)));
        }
        Ok(())
    }

    fn has_pending_transfers(&self) -> bool {
        self.slots
            .iter()
            .chain(self.feedback_slot.iter())
            .any(|slot| slot.pending)
    }

    fn drain(&mut self) -> TransferResetResult {
        let mut result = TransferResetResult::default();
        let pending_before = self
            .slots
            .iter()
            .chain(self.feedback_slot.iter())
            .filter(|slot| slot.submitted)
            .count() as u64;
        for slot in self.slots.iter_mut() {
            if slot.pending {
                let cancel_result = unsafe { usb::libusb_cancel_transfer(slot.transfer) };
                if cancel_result == 0 {
                    result.cancellation_requests = result.cancellation_requests.saturating_add(1);
                } else if cancel_result != usb::constants::LIBUSB_ERROR_NOT_FOUND {
                    result.cancellation_errors = result.cancellation_errors.saturating_add(1);
                }
            }
        }
        if let Some(slot) = self.feedback_slot.as_mut() {
            if slot.pending {
                let cancel_result = unsafe { usb::libusb_cancel_transfer(slot.transfer) };
                if cancel_result == 0 {
                    result.cancellation_requests = result.cancellation_requests.saturating_add(1);
                } else if cancel_result != usb::constants::LIBUSB_ERROR_NOT_FOUND {
                    result.cancellation_errors = result.cancellation_errors.saturating_add(1);
                }
            }
        }
        while self.has_pending_transfers() {
            if self.handle_events().is_err() {
                result.cancellation_errors = result.cancellation_errors.saturating_add(1);
                thread::yield_now();
            }
        }
        result.callbacks_drained = pending_before;
        result
    }

    fn reset(&mut self) -> TransferResetResult {
        let mut result = self.drain();
        for slot in &mut self.slots {
            if slot.submitted {
                if slot.succeeded() {
                    result.completed_frames = result.completed_frames.saturating_add(slot.music_frames);
                    result.completed_silence_frames = result
                        .completed_silence_frames
                        .saturating_add(slot.silence_frames);
                    result.completed_transfers = result.completed_transfers.saturating_add(1);
                    if slot.music_bytes > 0
                        && fnv1a64(&slot.bytes[..slot.music_bytes]) != slot.payload_hash
                    {
                        result.payload_integrity_errors =
                            result.payload_integrity_errors.saturating_add(1);
                    }
                } else {
                    result.discarded_frames = result.discarded_frames.saturating_add(slot.music_frames);
                    result.discarded_silence_frames = result
                        .discarded_silence_frames
                        .saturating_add(slot.silence_frames);
                    if !slot.cancelled() {
                        result.failed_transfers = result.failed_transfers.saturating_add(1);
                    }
                }
                slot.clear_payload();
            }
        }
        if let Some(slot) = self.feedback_slot.as_mut() {
            slot.submitted = false;
        }
        self.packet_clock = PacketClock::new(self.sample_rate, self.interval_microseconds);
        self.feedback_started_at = None;
        self.last_valid_feedback = None;

        result
    }
}

impl Drop for NativeIsochronousOutput {
    fn drop(&mut self) {

        self.drain();

        self.slots.clear();
        self.feedback_slot = None;

    }
}

fn error_name(code: i32) -> String {
    let pointer = unsafe { usb::libusb_error_name(code) };
    if pointer.is_null() {
        return format!("libusb error {code}");
    }
    unsafe { CStr::from_ptr(pointer).to_string_lossy().into_owned() }
}

fn speed_name(speed: i32) -> String {
    match speed {
        1 => "Low Speed".to_string(),
        2 => "Full Speed".to_string(),
        3 => "High Speed".to_string(),
        4 => "SuperSpeed".to_string(),
        5 => "SuperSpeedPlus".to_string(),
        _ => "Unknown".to_string(),
    }
}
