use std::sync::{Arc, Mutex};

use rovia_decoder::DecoderEngine;

use crate::adapter::{adapt, TargetFormat};
use crate::playback::is_lossless_codec;
use crate::{DirectUsbError, SourceInfoDto};

#[derive(Clone, uniffi::Record)]
pub struct PcmBlockDto {
    pub data: Vec<u8>,
    pub frames: u64,
    pub eos: bool,
}

struct SourceInner {
    engine: DecoderEngine,
    target: TargetFormat,
    lossless: bool,
    pending: Vec<u8>,
    eos: bool,
}

#[derive(uniffi::Object)]
pub struct NativePcmSource {
    inner: Mutex<Option<SourceInner>>,
}

#[uniffi::export]
impl NativePcmSource {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            inner: Mutex::new(None),
        })
    }

    pub fn open_source(
        &self,
        source_file_descriptor: i32,
        extension_hint: String,
    ) -> Result<SourceInfoDto, DirectUsbError> {
        let mut engine = open_decoder_from_fd(source_file_descriptor, &extension_hint)?;
        let info = engine.info().clone();
        let lossless = is_lossless_codec(&info.codec, &info.container);
        let mut pending = Vec::new();
        let mut eos = false;
        let bits = match engine.decode_next() {
            Ok(Some(block)) => {
                let source_bits = if block.format.is_float() {
                    32
                } else if block.valid_bits > 0 {
                    block.valid_bits
                } else {
                    block.format.bit_width()
                };
                let bits = track_width(source_bits);
                let target = target_format(&info, bits);
                pending = adapt(&block, &target, lossless).map_err(DirectUsbError::from)?;
                bits
            }
            Ok(None) => {
                eos = true;
                track_width(
                    info.bits_per_coded_sample
                        .or(info.bits_per_sample)
                        .unwrap_or(16),
                )
            }
            Err(error) => {
                return Err(DirectUsbError::Failed {
                    message: format!("source_decode: {error}"),
                })
            }
        };
        if bits == 0 || bits > 32 {
            return Err(DirectUsbError::Failed {
                message: format!("Source bit depth {bits} is not supported"),
            });
        }
        let target = target_format(&info, bits);
        let dto = SourceInfoDto {
            container: info.container.clone(),
            codec: info.codec.clone(),
            sample_rate: info.sample_rate,
            channels: info.channels as u8,
            bits_per_sample: bits,
            duration_us: info.duration_us.unwrap_or(0),
            total_frames: info.total_frames.unwrap_or(0),
        };
        let mut inner = self.lock();
        *inner = Some(SourceInner {
            engine,
            target,
            lossless,
            pending,
            eos,
        });
        Ok(dto)
    }

    pub fn read_block(&self, max_bytes: u32) -> Result<PcmBlockDto, DirectUsbError> {
        let mut guard = self.lock();
        let inner = guard.as_mut().ok_or_else(|| DirectUsbError::Failed {
            message: "Open a source before reading PCM".to_string(),
        })?;
        let frame_bytes = usize::from(inner.target.channels) * inner.target.subslot_bytes;
        if frame_bytes == 0 {
            return Err(DirectUsbError::Failed {
                message: "The source has an invalid frame size".to_string(),
            });
        }
        let want = (max_bytes as usize / frame_bytes) * frame_bytes;
        while inner.pending.len() < want && !inner.eos {
            match inner.engine.decode_next() {
                Ok(Some(block)) => {
                    let bytes = adapt(&block, &inner.target, inner.lossless)
                        .map_err(DirectUsbError::from)?;
                    inner.pending.extend_from_slice(&bytes);
                }
                Ok(None) => inner.eos = true,
                Err(error) => {
                    return Err(DirectUsbError::Failed {
                        message: format!("source_decode: {error}"),
                    })
                }
            }
        }
        let take = (inner.pending.len().min(want) / frame_bytes) * frame_bytes;
        let data: Vec<u8> = inner.pending.drain(..take).collect();
        let eos = inner.eos && inner.pending.is_empty();
        Ok(PcmBlockDto {
            frames: (take / frame_bytes) as u64,
            data,
            eos,
        })
    }

    pub fn seek(&self, position_us: u64) -> Result<u64, DirectUsbError> {
        let mut guard = self.lock();
        let inner = guard.as_mut().ok_or_else(|| DirectUsbError::Failed {
            message: "Open a source before seeking".to_string(),
        })?;
        let actual = inner
            .engine
            .seek(position_us)
            .map_err(|error| DirectUsbError::Failed {
                message: format!("source_seek: {error}"),
            })?;
        inner.pending.clear();
        inner.eos = false;
        Ok(actual)
    }

    pub fn release_source(&self) {
        let mut inner = self.lock();
        *inner = None;
    }
}

impl NativePcmSource {
    fn lock(&self) -> std::sync::MutexGuard<'_, Option<SourceInner>> {
        self.inner
            .lock()
            .unwrap_or_else(|failure| failure.into_inner())
    }
}

fn track_width(source_bits: u32) -> u32 {
    match source_bits {
        1..=16 => 16,
        17..=24 => 24,
        _ => 32,
    }
}

fn target_format(info: &rovia_decoder::SourceInfo, bits: u32) -> TargetFormat {
    TargetFormat {
        sample_rate: info.sample_rate,
        channels: info.channels,
        bit_resolution: bits,
        subslot_bytes: ((bits + 7) / 8) as usize,
    }
}

#[cfg(unix)]
pub(crate) fn open_decoder_from_fd(
    source_file_descriptor: i32,
    extension_hint: &str,
) -> Result<DecoderEngine, DirectUsbError> {
    use std::os::unix::io::FromRawFd;

    let duplicated = unsafe { libc::dup(source_file_descriptor) };
    if duplicated < 0 {
        let error = std::io::Error::last_os_error();
        unsafe {
            libc::close(source_file_descriptor);
        }
        return Err(DirectUsbError::Failed {
            message: format!("source_descriptor_dup: {error}"),
        });
    }
    unsafe {
        libc::close(source_file_descriptor);
    }
    let file = unsafe { std::fs::File::from_raw_fd(duplicated) };
    let hint = if extension_hint.is_empty() {
        None
    } else {
        Some(extension_hint)
    };
    DecoderEngine::open_file(file, hint).map_err(|error| DirectUsbError::Failed {
        message: format!("source_open: {error}"),
    })
}

#[cfg(not(unix))]
pub(crate) fn open_decoder_from_fd(
    _source_file_descriptor: i32,
    _extension_hint: &str,
) -> Result<DecoderEngine, DirectUsbError> {
    Err(DirectUsbError::Failed {
        message: "PCM source playback requires a Unix target".to_string(),
    })
}

#[cfg(all(test, unix))]
pub(crate) fn write_test_wav(path: &std::path::Path, frames: usize) {
    let sample_rate: u32 = 48_000;
    let channels: u16 = 2;
    let bits: u16 = 16;
    let data_len = (frames * channels as usize * 2) as u32;
    let mut bytes = Vec::new();
    bytes.extend_from_slice(b"RIFF");
    bytes.extend_from_slice(&(36 + data_len).to_le_bytes());
    bytes.extend_from_slice(b"WAVE");
    bytes.extend_from_slice(b"fmt ");
    bytes.extend_from_slice(&16u32.to_le_bytes());
    bytes.extend_from_slice(&1u16.to_le_bytes());
    bytes.extend_from_slice(&channels.to_le_bytes());
    bytes.extend_from_slice(&sample_rate.to_le_bytes());
    bytes.extend_from_slice(&(sample_rate * u32::from(channels) * 2).to_le_bytes());
    bytes.extend_from_slice(&(channels * 2).to_le_bytes());
    bytes.extend_from_slice(&bits.to_le_bytes());
    bytes.extend_from_slice(b"data");
    bytes.extend_from_slice(&data_len.to_le_bytes());
    for frame in 0..frames {
        let sample = (frame % 1024) as i16;
        bytes.extend_from_slice(&sample.to_le_bytes());
        bytes.extend_from_slice(&(-sample).to_le_bytes());
    }
    std::fs::write(path, bytes).unwrap();
}

#[cfg(test)]
mod tests {
    use super::*;
    #[cfg(unix)]
    use std::os::unix::io::AsRawFd;

    #[cfg(unix)]
    fn open_test_source(
        frames: usize,
    ) -> (Arc<NativePcmSource>, std::fs::File, std::path::PathBuf) {
        static SEQUENCE: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let path = std::env::temp_dir().join(format!(
            "rovia_pcm_source_test_{}_{}.wav",
            std::process::id(),
            SEQUENCE.fetch_add(1, std::sync::atomic::Ordering::Relaxed),
        ));
        write_test_wav(&path, frames);
        let file = std::fs::File::open(&path).unwrap();
        let fd = unsafe { libc::dup(file.as_raw_fd()) };
        assert!(fd >= 0);
        let source = NativePcmSource::new();
        let info = source.open_source(fd, "wav".to_string()).unwrap();
        assert_eq!(info.sample_rate, 48_000);
        assert_eq!(info.channels, 2);
        assert_eq!(info.bits_per_sample, 16);
        (source, file, path)
    }

    #[cfg(unix)]
    #[test]
    fn read_blocks_until_eos() {
        let (source, _file, path) = open_test_source(4_800);
        let mut total_frames = 0u64;
        let mut saw_eos = false;
        for _ in 0..16 {
            let block = source.read_block(4_096).unwrap();
            total_frames += block.frames;
            if block.eos {
                saw_eos = true;
                break;
            }
        }
        let _ = std::fs::remove_file(&path);
        assert!(saw_eos);
        assert_eq!(total_frames, 4_800);
    }

    #[cfg(unix)]
    #[test]
    fn seek_repositions_the_source() {
        let (source, _file, path) = open_test_source(48_000);
        let actual = source.seek(500_000).unwrap();
        let block = source.read_block(4_096).unwrap();
        let _ = std::fs::remove_file(&path);
        assert!(actual <= 500_000);
        assert!(block.frames > 0);
    }

    #[test]
    fn read_without_source_is_an_error() {
        let source = NativePcmSource::new();
        assert!(source.read_block(1_024).is_err());
    }
}
