use crate::adapter::{adapt, TargetFormat};
use crate::pcm::MAX_PCM_WRITE_BYTES;
use crate::{UsbPlaybackConfiguration, UsbTransportStatus};
use rovia_decoder::{DecodedBlock, DecoderEngine, SourceInfo};
use std::sync::{Arc, Condvar, Mutex};
use std::thread::JoinHandle;
use std::time::Duration;

pub trait PcmSink: Send + Sync {
    fn configure(&self, sample_rate: u32, channels: u8, bit_depth: u8) -> Result<UsbPlaybackConfiguration, String>;
    fn write(&self, data: &[u8]) -> Result<usize, String>;
    fn available(&self) -> Result<usize, String>;
    fn start(&self) -> Result<(), String>;
    fn flush(&self) -> Result<(), String>;
    fn status(&self) -> UsbTransportStatus;
}

impl<T: PcmSink> PcmSink for Arc<T> {
    fn configure(&self, sample_rate: u32, channels: u8, bit_depth: u8) -> Result<UsbPlaybackConfiguration, String> {
        (**self).configure(sample_rate, channels, bit_depth)
    }

    fn write(&self, data: &[u8]) -> Result<usize, String> {
        (**self).write(data)
    }

    fn available(&self) -> Result<usize, String> {
        (**self).available()
    }

    fn start(&self) -> Result<(), String> {
        (**self).start()
    }

    fn flush(&self) -> Result<(), String> {
        (**self).flush()
    }

    fn status(&self) -> UsbTransportStatus {
        (**self).status()
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PlaybackState {
    Idle,
    Prepared,
    Playing,
    Paused,
    Ended,
    Failed,
}

impl PlaybackState {
    pub fn name(self) -> &'static str {
        match self {
            PlaybackState::Idle => "Idle",
            PlaybackState::Prepared => "Prepared",
            PlaybackState::Playing => "Playing",
            PlaybackState::Paused => "Paused",
            PlaybackState::Ended => "Ended",
            PlaybackState::Failed => "Failed",
        }
    }
}

#[derive(Clone, Debug)]
pub struct PlaybackStatusSnapshot {
    pub state: PlaybackState,
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
    pub error: String,
}

impl Default for PlaybackStatusSnapshot {
    fn default() -> Self {
        Self {
            state: PlaybackState::Idle,
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
            error: String::new(),
        }
    }
}

pub fn is_lossless_codec(codec: &str, container: &str) -> bool {
    let codec = codec.to_ascii_uppercase();
    let container = container.to_ascii_uppercase();
    codec.contains("FLAC")
        || codec.contains("ALAC")
        || codec.contains("PCM")
        || container.contains("WAV")
        || container.contains("AIFF")
}

struct Control {
    state: PlaybackState,
    generation: u64,
    intent_playing: bool,
    stop_requested: bool,
    pause_requested: bool,
    seek_request: Option<u64>,
    pending_seek_us: Option<u64>,
    producer_running: bool,
    position_base_frames: u64,
    skip_frames: u64,
    trim_target_frames: u64,
    completed_anchor: u64,
    accepted_engine_anchor: u64,
    decoded_frames: u64,
    accepted_frames: u64,
    decoder_eos: bool,
    output_eos: bool,
    error: Option<String>,
    sample_rate: u32,
    channels: u16,
    valid_bits: u32,
    duration_us: u64,
    target: Option<TargetFormat>,
    lossless: bool,
}

impl Default for Control {
    fn default() -> Self {
        Self {
            state: PlaybackState::Idle,
            generation: 0,
            intent_playing: false,
            stop_requested: false,
            pause_requested: false,
            seek_request: None,
            pending_seek_us: None,
            producer_running: false,
            position_base_frames: 0,
            skip_frames: 0,
            trim_target_frames: 0,
            completed_anchor: 0,
            accepted_engine_anchor: 0,
            decoded_frames: 0,
            accepted_frames: 0,
            decoder_eos: false,
            output_eos: false,
            error: None,
            sample_rate: 0,
            channels: 0,
            valid_bits: 0,
            duration_us: 0,
            target: None,
            lossless: false,
        }
    }
}

type Shared = Arc<(Mutex<Control>, Condvar)>;

fn lock(shared: &Shared) -> std::sync::MutexGuard<'_, Control> {
    shared.0.lock().unwrap_or_else(|failure| failure.into_inner())
}

fn frames_to_us(frames: u64, sample_rate: u32) -> u64 {
    if sample_rate == 0 {
        return 0;
    }
    frames.saturating_mul(1_000_000) / u64::from(sample_rate)
}

fn us_to_frames(position_us: u64, sample_rate: u32) -> u64 {
    if sample_rate == 0 {
        return 0;
    }
    position_us.saturating_mul(u64::from(sample_rate)) / 1_000_000
}

pub struct PlaybackEngine<S: PcmSink + 'static> {
    sink: Arc<S>,
    shared: Shared,
    decoder_slot: Arc<Mutex<Option<DecoderEngine>>>,
    primed_block: Arc<Mutex<Option<DecodedBlock>>>,
    info: Option<SourceInfo>,
    producer: Option<JoinHandle<()>>,
}

impl<S: PcmSink + 'static> PlaybackEngine<S> {
    pub fn new(sink: S) -> Self {
        Self {
            sink: Arc::new(sink),
            shared: Arc::new((Mutex::new(Control::default()), Condvar::new())),
            decoder_slot: Arc::new(Mutex::new(None)),
            primed_block: Arc::new(Mutex::new(None)),
            info: None,
            producer: None,
        }
    }


    pub fn open_source(&mut self, decoder: DecoderEngine, lossless: bool) -> Result<SourceInfo, String> {
        self.stop_producer();
        let _ = self.sink.flush();
        let info = decoder.info().clone();
        {
            let mut slot = self.decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
            *slot = Some(decoder);
        }
        {
            let mut primed = self.primed_block.lock().unwrap_or_else(|failure| failure.into_inner());
            *primed = None;
        }
        {
            let mut control = lock(&self.shared);
            control.state = PlaybackState::Idle;
            control.generation = control.generation.saturating_add(1);
            control.intent_playing = false;
            control.stop_requested = false;
            control.pause_requested = false;
            control.seek_request = None;
            control.pending_seek_us = None;
            control.position_base_frames = 0;
            control.completed_anchor = 0;
            control.accepted_engine_anchor = 0;
            control.decoded_frames = 0;
            control.accepted_frames = 0;
            control.decoder_eos = false;
            control.output_eos = false;
            control.error = None;
            control.sample_rate = info.sample_rate;
            control.channels = info.channels;
            control.valid_bits = info.bits_per_coded_sample.or(info.bits_per_sample).unwrap_or(0);
            control.duration_us = info.duration_us.unwrap_or(0);
            control.target = None;
            control.lossless = lossless;
        }
        self.shared.1.notify_all();
        self.info = Some(info.clone());
        Ok(info)
    }

    pub fn prepare(&mut self) -> Result<PlaybackStatusSnapshot, String> {
        let info = self
            .info
            .clone()
            .ok_or_else(|| "Open a source before preparing playback".to_string())?;
        if info.sample_rate == 0 || info.channels == 0 || info.channels > u16::from(u8::MAX) {
            return Err("The source has an invalid sample rate or channel count".to_string());
        }
        let mut requested_bits = requested_bit_depth(&info);
        if requested_bits.is_none() {
            let primed = self.prime_first_block()?;
            requested_bits = Some(match primed {
                Some((format, valid_bits)) => {
                    if format.is_float() {
                        32
                    } else if valid_bits > 0 {
                        valid_bits
                    } else {
                        format.bit_width()
                    }
                }
                None => 16,
            });
        }
        let bits = requested_bits.unwrap();
        if bits == 0 || bits > 32 {
            return Err(format!("Source bit depth {bits} is not supported"));
        }
        let configuration = self.sink.configure(info.sample_rate, info.channels as u8, bits as u8)?;
        let target = TargetFormat {
            sample_rate: configuration.sample_rate,
            channels: u16::from(configuration.channels),
            bit_resolution: u32::from(configuration.device_bit_resolution),
            subslot_bytes: usize::from(configuration.subslot_size),
        };
        let status = self.sink.status();
        {
            let mut control = lock(&self.shared);
            control.target = Some(target);
            if control.valid_bits == 0 {
                control.valid_bits = bits;
            }
            control.position_base_frames = 0;
            control.completed_anchor = status.completed_frames;
            control.accepted_engine_anchor = 0;
            control.decoded_frames = 0;
            control.accepted_frames = 0;
            control.decoder_eos = false;
            control.output_eos = false;
            control.error = None;
            if control.state != PlaybackState::Playing {
                control.state = PlaybackState::Prepared;
            }
        }
        self.shared.1.notify_all();
        Ok(self.status())
    }

    fn prime_first_block(&mut self) -> Result<Option<(rovia_decoder::SampleFormat, u32)>, String> {
        {
            let primed = self.primed_block.lock().unwrap_or_else(|failure| failure.into_inner());
            if let Some(block) = primed.as_ref() {
                return Ok(Some((block.format, block.valid_bits)));
            }
        }
        let mut decoder = {
            let mut slot = self.decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
            slot.take().ok_or_else(|| "Open a source before preparing playback".to_string())?
        };
        let result = decoder.decode_next().map_err(|error| error.to_string());
        {
            let mut slot = self.decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
            *slot = Some(decoder);
        }
        let block = result?;
        match block {
            Some(block) => {
                let summary = (block.format, block.valid_bits);
                let mut primed = self.primed_block.lock().unwrap_or_else(|failure| failure.into_inner());
                *primed = Some(block);
                Ok(Some(summary))
            }
            None => Ok(None),
        }
    }

    pub fn start(&mut self) -> Result<(), String> {
        {
            let control = lock(&self.shared);
            if control.target.is_none() {
                return Err("Prepare playback before starting it".to_string());
            }
            if control.state == PlaybackState::Failed {
                return Err(control.error.clone().unwrap_or_else(|| "Playback has failed".to_string()));
            }
        }
        let producer_running = {
            let mut control = lock(&self.shared);
            control.intent_playing = true;
            control.stop_requested = false;
            control.pause_requested = false;
            control.state = PlaybackState::Playing;
            control.output_eos = false;
            control.producer_running
        };
        self.shared.1.notify_all();
        if !producer_running {
            self.spawn_producer()?;
        }
        Ok(())
    }

    pub fn pause(&mut self) -> Result<(), String> {
        let producer_running = {
            let mut control = lock(&self.shared);
            control.intent_playing = false;
            if control.producer_running {
                control.pause_requested = true;
                true
            } else {
                if control.state == PlaybackState::Playing {
                    control.state = PlaybackState::Paused;
                }
                false
            }
        };
        self.shared.1.notify_all();
        if producer_running {
            self.wait_for(|control| !control.pause_requested || control.state == PlaybackState::Failed);
        }
        Ok(())
    }

    pub fn seek(&mut self, position_us: u64) -> Result<(), String> {
        let producer_running = {
            let mut control = lock(&self.shared);
            control.generation = control.generation.saturating_add(1);
            control.output_eos = false;
            control.decoder_eos = false;
            if control.producer_running {
                control.seek_request = Some(position_us);
                true
            } else {
                control.pending_seek_us = Some(position_us);
                control.position_base_frames = us_to_frames(position_us, control.sample_rate);
                false
            }
        };
        self.shared.1.notify_all();
        if producer_running {
            self.wait_for(|control| control.seek_request.is_none());
        } else if lock(&self.shared).target.is_some() {
            self.apply_slot_seek(position_us)?;
        }
        Ok(())
    }

    fn apply_slot_seek(&mut self, position_us: u64) -> Result<(), String> {
        let mut decoder = {
            let mut slot = self.decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
            match slot.take() {
                Some(decoder) => decoder,
                None => return Ok(()),
            }
        };
        let result = decoder.seek(position_us).map_err(|error| error.to_string());
        {
            let mut slot = self.decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
            *slot = Some(decoder);
        }
        let actual_us = result?;
        let status = self.sink.status();
        let mut control = lock(&self.shared);
        let requested_frames = us_to_frames(position_us, control.sample_rate);
        let actual_frames = us_to_frames(actual_us, control.sample_rate);
        control.position_base_frames = requested_frames;
        control.skip_frames = requested_frames.saturating_sub(actual_frames);
        control.trim_target_frames = requested_frames;
        control.completed_anchor = status.completed_frames;
        control.accepted_engine_anchor = control.accepted_frames;
        control.pending_seek_us = None;
        Ok(())
    }

    pub fn stop(&mut self) {
        self.stop_producer();
        let _ = self.sink.flush();
        {
            let mut control = lock(&self.shared);
            control.intent_playing = false;
            control.pause_requested = false;
            control.seek_request = None;
            if control.state != PlaybackState::Failed {
                control.state = PlaybackState::Idle;
            }
        }
        self.shared.1.notify_all();
    }

    pub fn close_source(&mut self) {
        self.stop();
        {
            let mut slot = self.decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
            *slot = None;
        }
        {
            let mut primed = self.primed_block.lock().unwrap_or_else(|failure| failure.into_inner());
            *primed = None;
        }
        self.info = None;
        {
            let mut control = lock(&self.shared);
            control.target = None;
            control.state = PlaybackState::Idle;
            control.sample_rate = 0;
            control.channels = 0;
            control.valid_bits = 0;
            control.duration_us = 0;
        }
        self.shared.1.notify_all();
    }

    pub fn status(&self) -> PlaybackStatusSnapshot {
        let (control_state, generation, base, anchor, sample_rate, duration_us, decoded, accepted, decoder_eos, output_eos, channels, valid_bits, error, frame_bytes) = {
            let control = lock(&self.shared);
            (
                control.state,
                control.generation,
                control.position_base_frames,
                control.completed_anchor,
                control.sample_rate,
                control.duration_us,
                control.decoded_frames,
                control.accepted_frames,
                control.decoder_eos,
                control.output_eos,
                control.channels,
                control.valid_bits,
                control.error.clone().unwrap_or_default(),
                control.target.map(|target| target.subslot_bytes * usize::from(target.channels)).unwrap_or(0),
            )
        };
        let sink_status = self.sink.status();
        let completed_delta = sink_status.completed_frames.saturating_sub(anchor);
        let buffered_frames = if frame_bytes > 0 {
            u64::from(sink_status.queued_bytes) / frame_bytes as u64
        } else {
            0
        };
        PlaybackStatusSnapshot {
            state: control_state,
            generation,
            position_us: frames_to_us(base.saturating_add(completed_delta), sample_rate),
            duration_us,
            buffered_us: frames_to_us(buffered_frames, sample_rate),
            decoded_frames: decoded,
            accepted_frames: accepted,
            completed_frames: sink_status.completed_frames,
            discarded_frames: sink_status.discarded_frames,
            silence_frames: sink_status.completed_silence_frames.saturating_add(sink_status.in_flight_silence_frames),
            decoder_eos,
            output_eos,
            sample_rate,
            channels: channels as u8,
            valid_bits: valid_bits as u8,
            error,
        }
    }

    fn wait_for(&self, predicate: impl Fn(&Control) -> bool) {
        let deadline = std::time::Instant::now() + Duration::from_secs(10);
        let mut control = lock(&self.shared);
        while !predicate(&control) {
            if std::time::Instant::now() >= deadline {
                return;
            }
            let (guard, _) = self
                .shared
                .1
                .wait_timeout(control, Duration::from_millis(50))
                .unwrap_or_else(|failure| failure.into_inner());
            control = guard;
        }
    }

    fn stop_producer(&mut self) {
        {
            let mut control = lock(&self.shared);
            control.stop_requested = true;
            control.intent_playing = false;
        }
        self.shared.1.notify_all();
        if let Some(handle) = self.producer.take() {
            let _ = handle.join();
        }
        {
            let mut control = lock(&self.shared);
            control.stop_requested = false;
            control.producer_running = false;
        }
        self.shared.1.notify_all();
    }

    fn spawn_producer(&mut self) -> Result<(), String> {
        let mut decoder = {
            let mut slot = self.decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
            slot.take().ok_or_else(|| "Open a source before starting playback".to_string())?
        };
        let mut sought = false;
        if let Some(seek_us) = lock(&self.shared).pending_seek_us.take() {
            match decoder.seek(seek_us) {
                Ok(actual_us) => {
                    sought = true;
                    let status = self.sink.status();
                    let mut control = lock(&self.shared);
                    let requested_frames = us_to_frames(seek_us, control.sample_rate);
                    let actual_frames = us_to_frames(actual_us, control.sample_rate);
                    control.position_base_frames = requested_frames;
                    control.skip_frames = requested_frames.saturating_sub(actual_frames);
                    control.trim_target_frames = requested_frames;
                    control.completed_anchor = status.completed_frames;
                    control.accepted_engine_anchor = control.accepted_frames;
                }
                Err(error) => {
                    let mut slot = self.decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
                    *slot = Some(decoder);
                    return Err(error.to_string());
                }
            }
        }
        let first_block = {
            let mut primed = self.primed_block.lock().unwrap_or_else(|failure| failure.into_inner());
            let taken = primed.take();
            if sought {
                None
            } else {
                taken
            }
        };
        let sink = self.sink.clone();
        let shared = self.shared.clone();
        let decoder_slot = self.decoder_slot.clone();
        {
            let mut control = lock(&self.shared);
            control.producer_running = true;
        }
        let handle = std::thread::Builder::new()
            .name("rovia-playback-producer".to_string())
            .spawn(move || {
                let decoder = run_producer(sink, shared.clone(), decoder, first_block);
                {
                    let mut slot = decoder_slot.lock().unwrap_or_else(|failure| failure.into_inner());
                    *slot = Some(decoder);
                }
                {
                    let mut control = lock(&shared);
                    control.producer_running = false;
                }
                shared.1.notify_all();
            })
            .map_err(|error| format!("Playback producer thread could not start: {error}"))?;
        self.producer = Some(handle);
        Ok(())
    }
}

impl<S: PcmSink + 'static> Drop for PlaybackEngine<S> {
    fn drop(&mut self) {
        self.stop_producer();
    }
}

fn requested_bit_depth(info: &SourceInfo) -> Option<u32> {
    let format = info.sample_format?;
    if format.is_float() {
        return Some(32);
    }
    info.bits_per_coded_sample.or(info.bits_per_sample).or(Some(format.bit_width()))
}

fn run_producer<S: PcmSink>(
    sink: Arc<S>,
    shared: Shared,
    mut decoder: DecoderEngine,
    first_block: Option<DecodedBlock>,
) -> DecoderEngine {
    let (target, lossless, sample_rate) = {
        let control = lock(&shared);
        match control.target {
            Some(target) => (target, control.lossless, control.sample_rate),
            None => return decoder,
        }
    };
    let frame_bytes = target.subslot_bytes * usize::from(target.channels);
    let mut stash = first_block;
    let mut pending: Vec<u8> = Vec::new();
    let mut pending_offset = 0usize;
    let mut transport_started = false;
    let mut local_eos = false;
    let mut skip_frames = {
        let mut control = lock(&shared);
        let skip = control.skip_frames;
        control.skip_frames = 0;
        skip
    };
    let mut trim_target_frames = lock(&shared).trim_target_frames;

    loop {
        let (stop, pause, seek) = {
            let control = lock(&shared);
            (control.stop_requested, control.pause_requested, control.seek_request)
        };
        if stop {
            return decoder;
        }
        if pause {
            let status = sink.status();
            let position_frames = {
                let control = lock(&shared);
                control.position_base_frames.saturating_add(status.completed_frames.saturating_sub(control.completed_anchor))
            };
            let position_us = frames_to_us(position_frames, sample_rate);
            let _ = sink.flush();
            match decoder.seek(position_us) {
                Ok(actual_us) => {
                    let status = sink.status();
                    let mut control = lock(&shared);
                    control.position_base_frames = position_frames;
                    control.completed_anchor = status.completed_frames;
                    control.accepted_engine_anchor = control.accepted_frames;
                    control.pause_requested = false;
                    control.state = PlaybackState::Paused;
                    control.decoder_eos = false;
                    skip_frames = position_frames.saturating_sub(us_to_frames(actual_us, sample_rate));
                    trim_target_frames = position_frames;
                    local_eos = false;
                }
                Err(error) => {
                    fail(&shared, &sink, error.to_string());
                    return decoder;
                }
            }
            pending.clear();
            pending_offset = 0;
            stash = None;
            transport_started = false;
            shared.1.notify_all();
            continue;
        }
        if let Some(seek_us) = seek {
            let _ = sink.flush();
            match decoder.seek(seek_us) {
                Ok(actual_us) => {
                    let status = sink.status();
                    let requested_frames = us_to_frames(seek_us, sample_rate);
                    let mut control = lock(&shared);
                    control.position_base_frames = requested_frames;
                    control.completed_anchor = status.completed_frames;
                    control.accepted_engine_anchor = control.accepted_frames;
                    control.seek_request = None;
                    control.decoder_eos = false;
                    control.output_eos = false;
                    skip_frames = requested_frames.saturating_sub(us_to_frames(actual_us, sample_rate));
                    trim_target_frames = requested_frames;
                    local_eos = false;
                }
                Err(error) => {
                    fail(&shared, &sink, error.to_string());
                    return decoder;
                }
            }
            pending.clear();
            pending_offset = 0;
            stash = None;
            transport_started = false;
            shared.1.notify_all();
            continue;
        }
        if !lock(&shared).intent_playing {
            wait_briefly(&shared);
            continue;
        }

        if pending_offset >= pending.len() {
            pending.clear();
            pending_offset = 0;
            if !local_eos {
                let block = match stash.take() {
                    Some(block) => Some(block),
                    None => match decoder.decode_next() {
                        Ok(block) => block,
                        Err(error) => {
                            fail(&shared, &sink, error.to_string());
                            return decoder;
                        }
                    },
                };
                match block {
                    Some(mut block) => {
                        let mut drop_frames = 0u64;
                        if let Some(timestamp_us) = block.timestamp_us {
                            let block_start = us_to_frames(timestamp_us, sample_rate);
                            if block_start + block.frames <= trim_target_frames {
                                continue;
                            }
                            if block_start < trim_target_frames {
                                drop_frames = trim_target_frames - block_start;
                            }
                        } else if skip_frames > 0 {
                            if block.frames <= skip_frames {
                                skip_frames -= block.frames;
                                continue;
                            }
                            drop_frames = skip_frames;
                            skip_frames = 0;
                        }
                        if drop_frames > 0 {
                            let source_frame_bytes =
                                usize::from(block.channels) * block.format.bytes_per_sample();
                            let offset = (drop_frames as usize) * source_frame_bytes;
                            block.data.drain(..offset);
                            block.frames -= drop_frames;
                        }
                        {
                            let mut control = lock(&shared);
                            control.decoded_frames = control.decoded_frames.saturating_add(block.frames);
                        }
                        match adapt(&block, &target, lossless) {
                            Ok(bytes) => {
                                pending = bytes;
                                pending_offset = 0;
                            }
                            Err(error) => {
                                fail(&shared, &sink, error);
                                return decoder;
                            }
                        }
                    }
                    None => {
                        local_eos = true;
                        {
                            let mut control = lock(&shared);
                            control.decoder_eos = true;
                        }
                        shared.1.notify_all();
                    }
                }
            }
        }

        if pending_offset < pending.len() {
            let chunk_len = (pending.len() - pending_offset).min(MAX_PCM_WRITE_BYTES / frame_bytes * frame_bytes);
            match sink.write(&pending[pending_offset..pending_offset + chunk_len]) {
                Ok(accepted) => {
                    if accepted > chunk_len || accepted % frame_bytes != 0 {
                        fail(&shared, &sink, format!("PCM sink returned an invalid accepted byte count: {accepted}"));
                        return decoder;
                    }
                    if accepted == 0 {
                        wait_briefly(&shared);
                    } else {
                        pending_offset += accepted;
                        {
                            let mut control = lock(&shared);
                            control.accepted_frames = control.accepted_frames.saturating_add((accepted / frame_bytes) as u64);
                        }
                        shared.1.notify_all();
                    }
                }
                Err(error) => {
                    fail(&shared, &sink, error);
                    return decoder;
                }
            }
        }

        if !transport_started {
            let status = sink.status();
            let queued_frames = u64::from(status.queued_bytes) / frame_bytes as u64;
            let available = sink.available().unwrap_or(0) as u64;
            let capacity_frames = (available + u64::from(status.queued_bytes)) / frame_bytes as u64;
            let watermark = (capacity_frames / 2).min(u64::from(sample_rate) / 5);
            let ready = queued_frames >= watermark || (local_eos && pending_offset >= pending.len() && queued_frames > 0);
            if ready {
                match sink.start() {
                    Ok(()) => {
                        transport_started = true;
                        {
                            let mut control = lock(&shared);
                            control.state = PlaybackState::Playing;
                        }
                        shared.1.notify_all();
                    }
                    Err(error) => {
                        fail(&shared, &sink, error);
                        return decoder;
                    }
                }
            }
        }

        if local_eos && pending_offset >= pending.len() {
            let status = sink.status();
            let drained = {
                let control = lock(&shared);
                status.queued_bytes == 0
                    && status.in_flight_frames == 0
                    && status.completed_frames.saturating_sub(control.completed_anchor)
                        == control.accepted_frames.saturating_sub(control.accepted_engine_anchor)
            };
            if drained {
                let _ = sink.flush();
                {
                    let mut control = lock(&shared);
                    control.state = PlaybackState::Ended;
                    control.output_eos = true;
                    control.intent_playing = false;
                }
                shared.1.notify_all();
                return decoder;
            }
            wait_briefly(&shared);
        }
    }
}

fn wait_briefly(shared: &Shared) {
    let control = lock(shared);
    let _ = shared.1.wait_timeout(control, Duration::from_millis(5)).unwrap_or_else(|failure| failure.into_inner());
}

fn fail<S: PcmSink>(shared: &Shared, sink: &Arc<S>, message: String) {
    let _ = sink.flush();
    {
        let mut control = lock(shared);
        control.state = PlaybackState::Failed;
        control.error = Some(message);
        control.intent_playing = false;
    }
    shared.1.notify_all();
}


#[cfg(test)]
mod tests {
    use super::{is_lossless_codec, PcmSink, PlaybackEngine, PlaybackState};
    use crate::pcm::PcmQueue;
    use crate::{UsbPlaybackConfiguration, UsbTransportStatus};
    use rovia_decoder::DecoderEngine;
    use std::io::Write as _;
    use std::sync::{Arc, Mutex};
    use std::time::{Duration, Instant};

    struct FakeInner {
        queue: PcmQueue,
        configured: bool,
        playing: bool,
        accepted_frames: u64,
        completed_frames: u64,
        discarded_frames: u64,
        consumed: Vec<u8>,
        frame_bytes: usize,
    }

    struct FakeSink {
        inner: Mutex<FakeInner>,
        capacity_bytes: usize,
    }

    impl FakeSink {
        fn new(capacity_bytes: usize) -> Arc<Self> {
            Arc::new(Self {
                inner: Mutex::new(FakeInner {
                    queue: PcmQueue::default(),
                    configured: false,
                    playing: false,
                    accepted_frames: 0,
                    completed_frames: 0,
                    discarded_frames: 0,
                    consumed: Vec::new(),
                    frame_bytes: 0,
                }),
                capacity_bytes,
            })
        }

        fn consume_frames(&self, wanted: usize) -> usize {
            let mut inner = self.inner.lock().unwrap();
            let frames = inner.queue.queued_frames().min(wanted);
            if frames == 0 {
                return 0;
            }
            let bytes = frames * inner.frame_bytes;
            let mut buffer = vec![0u8; bytes];
            let copied = inner.queue.copy_prefix(&mut buffer, bytes).unwrap();
            inner.queue.consume_prefix(copied).unwrap();
            inner.consumed.extend_from_slice(&buffer[..copied]);
            inner.completed_frames += (copied / inner.frame_bytes) as u64;
            copied / inner.frame_bytes
        }

        fn consume_all(&self) -> usize {
            let mut inner = self.inner.lock().unwrap();
            let frames = inner.queue.queued_frames();
            if frames == 0 {
                return 0;
            }
            let bytes = frames * inner.frame_bytes;
            let mut buffer = vec![0u8; bytes];
            let copied = inner.queue.copy_prefix(&mut buffer, bytes).unwrap();
            inner.queue.consume_prefix(copied).unwrap();
            inner.consumed.extend_from_slice(&buffer[..copied]);
            inner.completed_frames += (copied / inner.frame_bytes) as u64;
            copied / inner.frame_bytes
        }

        fn consumed(&self) -> Vec<u8> {
            self.inner.lock().unwrap().consumed.clone()
        }

        fn consumed_frames(&self) -> u64 {
            self.inner.lock().unwrap().completed_frames
        }
    }

    impl PcmSink for FakeSink {
        fn configure(&self, sample_rate: u32, channels: u8, bit_depth: u8) -> Result<UsbPlaybackConfiguration, String> {
            let mut inner = self.inner.lock().unwrap();
            let subslot = usize::from(bit_depth / 8);
            inner.frame_bytes = usize::from(channels) * subslot;
            let frame_bytes = inner.frame_bytes;
            inner.queue.configure(frame_bytes, self.capacity_bytes)?;
            inner.configured = true;
            Ok(UsbPlaybackConfiguration {
                sample_rate,
                channels,
                requested_bit_depth: bit_depth,
                device_bit_resolution: bit_depth,
                subslot_size: subslot as u8,
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
                verified_clock_rate: sample_rate,
                clock_changed: false,
            })
        }

        fn write(&self, data: &[u8]) -> Result<usize, String> {
            let mut inner = self.inner.lock().unwrap();
            let accepted = inner.queue.write(data)?;
            inner.accepted_frames += (accepted / inner.frame_bytes) as u64;
            Ok(accepted)
        }

        fn available(&self) -> Result<usize, String> {
            let inner = self.inner.lock().unwrap();
            Ok(inner.queue.available_bytes())
        }

        fn start(&self) -> Result<(), String> {
            let mut inner = self.inner.lock().unwrap();
            inner.playing = true;
            Ok(())
        }

        fn flush(&self) -> Result<(), String> {
            let mut inner = self.inner.lock().unwrap();
            inner.discarded_frames += inner.queue.clear() as u64;
            inner.playing = false;
            Ok(())
        }

        fn status(&self) -> UsbTransportStatus {
            let inner = self.inner.lock().unwrap();
            UsbTransportStatus {
                configured: inner.configured,
                playing: inner.playing,
                queued_bytes: inner.queue.queued_bytes() as u32,
                accepted_frames: inner.accepted_frames,
                submitted_frames: inner.accepted_frames,
                in_flight_frames: 0,
                completed_frames: inner.completed_frames,
                discarded_frames: inner.discarded_frames,
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
                interface_reset_succeeded: true,
                released_interface_count: 0,
                handle_closed: false,
                context_released: false,
                worker_closed: false,
                error_message: String::new(),
            }
        }
    }

    fn wav_bytes(samples: &[i16], sample_rate: u32) -> Vec<u8> {
        let data_len = (samples.len() * 2) as u32;
        let mut bytes = Vec::new();
        bytes.extend_from_slice(b"RIFF");
        bytes.extend_from_slice(&(36 + data_len).to_le_bytes());
        bytes.extend_from_slice(b"WAVE");
        bytes.extend_from_slice(b"fmt ");
        bytes.extend_from_slice(&16u32.to_le_bytes());
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.extend_from_slice(&sample_rate.to_le_bytes());
        bytes.extend_from_slice(&(sample_rate * 2).to_le_bytes());
        bytes.extend_from_slice(&2u16.to_le_bytes());
        bytes.extend_from_slice(&16u16.to_le_bytes());
        bytes.extend_from_slice(b"data");
        bytes.extend_from_slice(&data_len.to_le_bytes());
        for sample in samples {
            bytes.extend_from_slice(&sample.to_le_bytes());
        }
        bytes
    }

    fn fixture_samples(frames: usize) -> Vec<i16> {
        (0..frames)
            .map(|index| ((index * 37 + 11) % 2000) as i16 - 1000)
            .collect()
    }

    fn open_engine(
        test_name: &str,
        samples: &[i16],
        sample_rate: u32,
    ) -> (PlaybackEngine<Arc<FakeSink>>, Arc<FakeSink>, std::path::PathBuf) {
        let path = std::env::temp_dir().join(format!("rovia-playback-{test_name}-{}.wav", std::process::id()));
        let mut file = std::fs::File::create(&path).unwrap();
        file.write_all(&wav_bytes(samples, sample_rate)).unwrap();
        drop(file);
        let decoder = DecoderEngine::open_path(&path, Some("wav")).unwrap();
        let sink = FakeSink::new(64 * 1024);
        let mut engine = PlaybackEngine::new(sink.clone());
        engine.open_source(decoder, is_lossless_codec("PCM", "WAV")).unwrap();
        (engine, sink, path)
    }

    fn drive_until_ended(
        engine: &PlaybackEngine<Arc<FakeSink>>,
        sink: &Arc<FakeSink>,
        timeout: Duration,
    ) -> bool {
        let deadline = Instant::now() + timeout;
        loop {
            sink.consume_all();
            let status = engine.status();
            if status.state == PlaybackState::Ended {
                return true;
            }
            if status.state == PlaybackState::Failed {
                return false;
            }
            if Instant::now() >= deadline {
                return false;
            }
            std::thread::sleep(Duration::from_millis(2));
        }
    }

    #[test]
    fn play_to_eos_delivers_exact_source_pcm_and_completed_frame_position() {
        let samples = fixture_samples(8_000);
        let (mut engine, sink, path) = open_engine("eos", &samples, 8_000);
        let prepared = engine.prepare().unwrap();
        assert_eq!(prepared.state, PlaybackState::Prepared);
        assert_eq!(prepared.sample_rate, 8_000);
        engine.start().unwrap();

        let deadline = Instant::now() + Duration::from_secs(10);
        while sink.consumed_frames() == 0 && Instant::now() < deadline {
            if sink.consume_frames(400) > 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(2));
        }
        assert_eq!(sink.consumed_frames(), 400);
        let early = engine.status();
        assert_eq!(early.position_us, 50_000);
        assert_ne!(early.state, PlaybackState::Ended);

        assert!(drive_until_ended(&engine, &sink, Duration::from_secs(10)));
        let mut expected = Vec::new();
        for sample in &samples {
            expected.extend_from_slice(&sample.to_le_bytes());
        }
        assert_eq!(sink.consumed(), expected);
        let status = engine.status();
        assert_eq!(status.state, PlaybackState::Ended);
        assert!(status.output_eos);
        assert!(status.decoder_eos);
        assert_eq!(status.accepted_frames, 8_000);
        assert_eq!(status.completed_frames, 8_000);
        assert_eq!(status.position_us, 1_000_000);
        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn pause_flushes_and_resume_continues_without_loss_or_duplication() {
        let samples = fixture_samples(8_000);
        let (mut engine, sink, path) = open_engine("pause", &samples, 8_000);
        engine.prepare().unwrap();
        engine.start().unwrap();

        let deadline = Instant::now() + Duration::from_secs(10);
        while sink.consumed_frames() < 1_000 && Instant::now() < deadline {
            sink.consume_all();
            std::thread::sleep(Duration::from_millis(2));
        }
        assert!(sink.consumed_frames() >= 1_000);
        engine.pause().unwrap();
        let paused = engine.status();
        assert_eq!(paused.state, PlaybackState::Paused);
        let frames_at_pause = sink.consumed_frames();
        assert_eq!(paused.position_us, frames_at_pause * 1_000_000 / 8_000);
        let consumed_at_pause = sink.consumed();
        let mut expected_prefix = Vec::new();
        for sample in &samples[..frames_at_pause as usize] {
            expected_prefix.extend_from_slice(&sample.to_le_bytes());
        }
        assert_eq!(consumed_at_pause, expected_prefix);

        engine.start().unwrap();
        assert!(drive_until_ended(&engine, &sink, Duration::from_secs(10)));
        let mut expected = Vec::new();
        for sample in &samples {
            expected.extend_from_slice(&sample.to_le_bytes());
        }
        assert_eq!(sink.consumed(), expected);
        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn seek_while_playing_delivers_exact_suffix_to_eos() {
        let samples = fixture_samples(8_000);
        let path = std::env::temp_dir().join(format!("rovia-playback-seeklive-{}.wav", std::process::id()));
        let mut file = std::fs::File::create(&path).unwrap();
        file.write_all(&wav_bytes(&samples, 8_000)).unwrap();
        drop(file);
        let decoder = DecoderEngine::open_path(&path, Some("wav")).unwrap();
        let sink = FakeSink::new(2 * 1024);
        let mut engine = PlaybackEngine::new(sink.clone());
        engine.open_source(decoder, is_lossless_codec("PCM", "WAV")).unwrap();
        engine.prepare().unwrap();
        engine.start().unwrap();

        let deadline = Instant::now() + Duration::from_secs(10);
        while sink.consumed_frames() < 1_000 && Instant::now() < deadline {
            sink.consume_frames(100);
            std::thread::sleep(Duration::from_millis(2));
        }
        assert!(sink.consumed_frames() >= 1_000);
        let consumed_before_seek = sink.consumed_frames();

        engine.seek(900_000).unwrap();
        assert!(drive_until_ended(&engine, &sink, Duration::from_secs(10)));
        let consumed = sink.consumed();
        let frame_bytes = 2usize;
        let pre: Vec<u8> = consumed[..consumed_before_seek as usize * frame_bytes].to_vec();
        let mut expected_pre = Vec::new();
        for sample in &samples[..consumed_before_seek as usize] {
            expected_pre.extend_from_slice(&sample.to_le_bytes());
        }
        assert_eq!(pre, expected_pre);
        let suffix: Vec<u8> = consumed[consumed_before_seek as usize * frame_bytes..].to_vec();
        let mut expected_suffix = Vec::new();
        for sample in &samples[7_200..] {
            expected_suffix.extend_from_slice(&sample.to_le_bytes());
        }
        assert_eq!(suffix.len(), expected_suffix.len(), "suffix frame count differs: got {} frames, want {}", suffix.len() / frame_bytes, expected_suffix.len() / frame_bytes);
        assert_eq!(suffix, expected_suffix);
        let status = engine.status();
        assert_eq!(status.state, PlaybackState::Ended);
        assert_eq!(status.position_us, 1_000_000);
        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn seek_before_start_delivers_exact_suffix() {
        let samples = fixture_samples(8_000);
        let (mut engine, sink, path) = open_engine("seek", &samples, 8_000);
        engine.prepare().unwrap();
        engine.seek(500_000).unwrap();
        assert_eq!(engine.status().position_us, 500_000);
        engine.start().unwrap();
        assert!(drive_until_ended(&engine, &sink, Duration::from_secs(10)));
        let mut expected = Vec::new();
        for sample in &samples[4_000..] {
            expected.extend_from_slice(&sample.to_le_bytes());
        }
        assert_eq!(sink.consumed(), expected);
        assert_eq!(engine.status().position_us, 1_000_000);
        let _ = std::fs::remove_file(path);
    }
}
