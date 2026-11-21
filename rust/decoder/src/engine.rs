use std::fs::File;
use std::path::Path;

use symphonia::core::audio::GenericAudioBufferRef;
use symphonia::core::audio::sample::SampleFormat as NativeSampleFormat;
use symphonia::core::codecs::audio::{AudioDecoder, AudioDecoderOptions};
use symphonia::core::errors::Error;
use symphonia::core::formats::{FormatOptions, FormatReader, SeekMode, SeekTo};
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::formats::probe::Hint;
use symphonia::core::units::{Time, TimeBase, Timestamp};

use crate::{DecodedBlock, DecoderError, SampleFormat, SourceInfo};

pub struct DecoderEngine {
    format: Box<dyn FormatReader>,
    decoder: Box<dyn AudioDecoder>,
    track_id: u32,
    time_base: Option<TimeBase>,
    info: SourceInfo,
    decoded_frames: u64,
    finished: bool,
    finalized: bool,
}

impl DecoderEngine {
    pub fn open_path(path: &Path, extension_hint: Option<&str>) -> Result<Self, DecoderError> {
        let file = File::open(path)
            .map_err(|failure| DecoderError::Io(format!("open {}: {failure}", path.display())))?;
        Self::open_file(file, extension_hint)
    }

    pub fn open_file(file: File, extension_hint: Option<&str>) -> Result<Self, DecoderError> {
        let stream = MediaSourceStream::new(Box::new(file), Default::default());
        let mut hint = Hint::new();
        if let Some(extension) = extension_hint {
            hint.with_extension(extension);
        }
        let mut format = symphonia::default::get_probe()
            .probe(&hint, stream, FormatOptions::default(), MetadataOptions::default())
            .map_err(map_probe_error)?;
        let container = format.format_info().short_name.to_string();
        let selected = select_track(&mut format)?;
        let (track_id, time_base, params, duration, total_frames) = selected;
        let decoder = symphonia::default::get_codecs()
            .make_audio_decoder(&params, &AudioDecoderOptions::default())
            .map_err(|failure| {
                DecoderError::Unsupported(format!(
                    "unsupported source: audio codec {:?} is not supported by Symphonia ({failure})",
                    params.codec
                ))
            })?;
        let duration_us = match (time_base, duration) {
            (Some(base), Some(value)) => base
                .calc_duration(value)
                .map(|time| time_to_us(time))
                .filter(|value| *value > 0),
            _ => None,
        };
        let info = SourceInfo {
            container,
            codec: decoder.codec_info().short_name.to_string(),
            sample_rate: params.sample_rate.unwrap_or(0),
            channels: params.channels.map(|channels| channels.count() as u16).unwrap_or(0),
            bits_per_sample: params.bits_per_sample,
            bits_per_coded_sample: params.bits_per_coded_sample,
            sample_format: params.sample_format.map(map_sample_format),
            duration_us,
            total_frames,
        };
        Ok(Self {
            format,
            decoder,
            track_id,
            time_base,
            info,
            decoded_frames: 0,
            finished: false,
            finalized: false,
        })
    }

    pub fn info(&self) -> &SourceInfo {
        &self.info
    }

    pub fn decode_next(&mut self) -> Result<Option<DecodedBlock>, DecoderError> {
        if self.finished {
            return Ok(None);
        }
        loop {
            let packet = match self.format.next_packet() {
                Ok(Some(packet)) => packet,
                Ok(None) => {
                    self.finish();
                    return Ok(None);
                }
                Err(Error::ResetRequired) => {
                    return Err(DecoderError::Decode("track list changed".to_string()));
                }
                Err(Error::IoError(failure)) => {
                    return Err(DecoderError::Io(failure.to_string()));
                }
                Err(failure) => {
                    return Err(DecoderError::Decode(failure.to_string()));
                }
            };
            if packet.track_id != self.track_id {
                continue;
            }
            let packet_timestamp_us = self.packet_timestamp_us(packet.pts);
            let frames_before = self.decoded_frames;
            let codec_bits = self.decoder.codec_params().bits_per_sample;
            let decoded = match self.decoder.decode(&packet) {
                Ok(decoded) => decoded,
                Err(Error::IoError(_)) | Err(Error::DecodeError(_)) => continue,
                Err(Error::ResetRequired) => {
                    return Err(DecoderError::Decode("decoder reset required".to_string()));
                }
                Err(Error::Unsupported(feature)) => {
                    return Err(DecoderError::Unsupported(feature.to_string()));
                }
                Err(failure) => {
                    return Err(DecoderError::Decode(failure.to_string()));
                }
            };
            if decoded.frames() == 0 {
                continue;
            }
            let block = build_block(
                &decoded,
                codec_bits,
                self.info.sample_rate,
                packet_timestamp_us,
                frames_before,
            );
            self.decoded_frames += block.frames;
            return Ok(Some(block));
        }
    }

    pub fn seek(&mut self, position_us: u64) -> Result<u64, DecoderError> {
        let seeked = self
            .format
            .seek(
                SeekMode::Accurate,
                SeekTo::Time { time: Time::from_micros_u64(position_us), track_id: Some(self.track_id) },
            )
            .map_err(|failure| DecoderError::Decode(format!("seek: {failure}")))?;
        self.decoder.reset();
        self.finished = false;
        self.finalized = false;
        let actual_us = self
            .time_base
            .and_then(|base| base.calc_time(seeked.actual_ts))
            .map(time_to_us)
            .unwrap_or(position_us);
        let rate = u64::from(self.info.sample_rate);
        self.decoded_frames = if rate > 0 { actual_us.saturating_mul(rate) / 1_000_000 } else { 0 };
        Ok(actual_us)
    }

    fn finish(&mut self) {
        if !self.finalized {
            self.decoder.finalize();
            self.finalized = true;
        }
        self.finished = true;
    }

    fn packet_timestamp_us(&self, pts: Timestamp) -> Option<u64> {
        if pts.is_negative() {
            return None;
        }
        self.time_base.and_then(|base| base.calc_time(pts)).map(time_to_us)
    }

}

fn build_block(
    decoded: &GenericAudioBufferRef<'_>,
    codec_bits: Option<u32>,
    fallback_rate: u32,
    packet_timestamp_us: Option<u64>,
    frames_before: u64,
) -> DecodedBlock {
    let format = buffer_format(decoded);
    let bit_width = format.bit_width();
    let valid_bits = codec_bits
        .filter(|bits| *bits > 0 && *bits <= bit_width)
        .unwrap_or(bit_width);
    let mut data = Vec::new();
    decoded.copy_bytes_to_vec_interleaved(&mut data);
    let timestamp_us = packet_timestamp_us.or_else(|| {
        let rate = u64::from(fallback_rate);
        if rate > 0 {
            Some(frames_before.saturating_mul(1_000_000) / rate)
        } else {
            None
        }
    });
    DecodedBlock {
        sample_rate: decoded.spec().rate(),
        channels: decoded.spec().channels().count() as u16,
        format,
        valid_bits,
        frames: decoded.frames() as u64,
        timestamp_us,
        data,
    }
}

fn select_track(
    format: &mut Box<dyn FormatReader>,
) -> Result<
    (
        u32,
        Option<TimeBase>,
        symphonia::core::codecs::audio::AudioCodecParameters,
        Option<symphonia::core::units::Duration>,
        Option<u64>,
    ),
    DecoderError,
> {
    let mut saw_audio_track = false;
    let mut first_codec: Option<String> = None;
    for track in format.tracks() {
        let Some(params) = track.codec_params.as_ref().and_then(|params| params.audio()) else {
            continue;
        };
        saw_audio_track = true;
        if first_codec.is_none() {
            first_codec = Some(format!("{:?}", params.codec));
        }
        if symphonia::default::get_codecs()
            .make_audio_decoder(params, &AudioDecoderOptions::default())
            .is_ok()
        {
            return Ok((track.id, track.time_base, params.clone(), track.duration, track.num_frames));
        }
    }
    if saw_audio_track {
        // Name the codec: a bare "not supported" gives the UI nothing to
        // show, and the Kotlin side matches on the codec name (e.g. the
        // HE-AAC recording case) to explain the failure.
        let codec = first_codec.unwrap_or_else(|| "unknown".to_string());
        Err(DecoderError::Unsupported(format!(
            "unsupported source: audio codec {codec} is not supported by Symphonia"
        )))
    } else {
        Err(DecoderError::Unsupported("source has no audio track".to_string()))
    }
}

fn map_probe_error(failure: Error) -> DecoderError {
    match failure {
        Error::IoError(inner) => DecoderError::Io(inner.to_string()),
        Error::Unsupported(feature) => DecoderError::Unsupported(feature.to_string()),
        other => DecoderError::Unsupported(other.to_string()),
    }
}

fn map_sample_format(format: NativeSampleFormat) -> SampleFormat {
    match format {
        NativeSampleFormat::U8 => SampleFormat::U8,
        NativeSampleFormat::U16 => SampleFormat::U16,
        NativeSampleFormat::U24 => SampleFormat::U24,
        NativeSampleFormat::U32 => SampleFormat::U32,
        NativeSampleFormat::S8 => SampleFormat::S8,
        NativeSampleFormat::S16 => SampleFormat::S16,
        NativeSampleFormat::S24 => SampleFormat::S24,
        NativeSampleFormat::S32 => SampleFormat::S32,
        NativeSampleFormat::F32 => SampleFormat::F32,
        NativeSampleFormat::F64 => SampleFormat::F64,
    }
}

fn buffer_format(decoded: &GenericAudioBufferRef<'_>) -> SampleFormat {
    match decoded {
        GenericAudioBufferRef::U8(_) => SampleFormat::U8,
        GenericAudioBufferRef::U16(_) => SampleFormat::U16,
        GenericAudioBufferRef::U24(_) => SampleFormat::U24,
        GenericAudioBufferRef::U32(_) => SampleFormat::U32,
        GenericAudioBufferRef::S8(_) => SampleFormat::S8,
        GenericAudioBufferRef::S16(_) => SampleFormat::S16,
        GenericAudioBufferRef::S24(_) => SampleFormat::S24,
        GenericAudioBufferRef::S32(_) => SampleFormat::S32,
        GenericAudioBufferRef::F32(_) => SampleFormat::F32,
        GenericAudioBufferRef::F64(_) => SampleFormat::F64,
    }
}

fn time_to_us(time: Time) -> u64 {
    time.as_micros().clamp(0, u64::MAX as i128) as u64
}
