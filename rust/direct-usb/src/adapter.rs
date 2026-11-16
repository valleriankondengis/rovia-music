use rovia_decoder::{DecodedBlock, SampleFormat};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct TargetFormat {
    pub sample_rate: u32,
    pub channels: u16,
    pub bit_resolution: u32,
    pub subslot_bytes: usize,
}

pub fn adapt(block: &DecodedBlock, target: &TargetFormat, lossless: bool) -> Result<Vec<u8>, String> {
    if block.sample_rate != target.sample_rate {
        return Err(format!(
            "Decoded sample rate {} does not match the output sample rate {}",
            block.sample_rate, target.sample_rate
        ));
    }
    if block.channels != target.channels {
        return Err(format!(
            "Decoded channel count {} does not match the output channel count {}",
            block.channels, target.channels
        ));
    }
    if target.bit_resolution == 0 || target.bit_resolution > 32 {
        return Err(format!(
            "Output bit resolution {} is not supported",
            target.bit_resolution
        ));
    }
    if target.subslot_bytes == 0 || target.subslot_bytes > 8 {
        return Err(format!(
            "Output subslot size {} is not supported",
            target.subslot_bytes
        ));
    }
    if target.subslot_bytes * 8 < target.bit_resolution as usize {
        return Err(format!(
            "Output subslot of {} bytes cannot carry {} bits",
            target.subslot_bytes, target.bit_resolution
        ));
    }
    if block.channels == 0 {
        return Err("Decoded block has no channels".to_string());
    }
    let source_width = block.format.bytes_per_sample();
    if source_width == 0 {
        return Err("Decoded block has an empty sample width".to_string());
    }
    let expected_bytes = (block.frames as usize)
        .checked_mul(block.channels as usize)
        .and_then(|samples| samples.checked_mul(source_width))
        .ok_or_else(|| "Decoded block size overflow".to_string())?;
    if block.data.len() != expected_bytes {
        return Err(format!(
            "Decoded block carries {} bytes for {} frames, expected {}",
            block.data.len(),
            block.frames,
            expected_bytes
        ));
    }
    if lossless && block.format.is_integer() && target.bit_resolution < block.valid_bits {
        return Err(format!(
            "Output bit resolution {} cannot preserve {} valid bits",
            target.bit_resolution, block.valid_bits
        ));
    }
    let sample_count = (block.frames as usize) * (block.channels as usize);
    let mut output = vec![0u8; sample_count * target.subslot_bytes];
    for index in 0..sample_count {
        let start = index * source_width;
        let source = &block.data[start..start + source_width];
        let value = convert_sample(source, block, target, lossless)?;
        let destination = &mut output[index * target.subslot_bytes..(index + 1) * target.subslot_bytes];
        destination.copy_from_slice(&value.to_le_bytes()[..target.subslot_bytes]);
    }
    Ok(output)
}

fn convert_sample(
    source: &[u8],
    block: &DecodedBlock,
    target: &TargetFormat,
    lossless: bool,
) -> Result<i64, String> {
    if block.format.is_float() {
        let sample = read_float(source, block.format)?;
        convert_float_sample(sample, target.bit_resolution, lossless)
    } else if block.format.is_signed() {
        let raw = read_signed(source, block.format);
        let shift = justification_shift(block);
        fit_integer(raw >> shift, target.bit_resolution)
    } else {
        let raw = read_unsigned(source);
        let shift = justification_shift(block);
        let effective_bits = block.format.bit_width() - shift;
        let midpoint = 1i64 << (effective_bits - 1);
        fit_integer((raw >> shift) as i64 - midpoint, target.bit_resolution)
    }
}

fn justification_shift(block: &DecodedBlock) -> u32 {
    let width = block.format.bit_width();
    if block.valid_bits == 0 || block.valid_bits >= width {
        0
    } else {
        width - block.valid_bits
    }
}

fn target_range(bit_resolution: u32) -> (i64, i64) {
    let minimum = -(1i64 << (bit_resolution - 1));
    let maximum = (1i64 << (bit_resolution - 1)) - 1;
    (minimum, maximum)
}

fn fit_integer(value: i64, bit_resolution: u32) -> Result<i64, String> {
    let (minimum, maximum) = target_range(bit_resolution);
    if value < minimum || value > maximum {
        return Err(format!(
            "Sample value {value} does not fit exactly in {bit_resolution} bits"
        ));
    }
    Ok(value)
}

fn convert_float_sample(sample: f64, bit_resolution: u32, lossless: bool) -> Result<i64, String> {
    let (minimum, maximum) = target_range(bit_resolution);
    let scale = (1u64 << (bit_resolution - 1)) as f64;
    if lossless {
        if !sample.is_finite() {
            return Err("Lossless output requires finite samples".to_string());
        }
        let scaled = sample * scale;
        if scaled.fract() != 0.0 {
            return Err(format!(
                "Sample {sample} is not exactly representable in {bit_resolution} bits"
            ));
        }
        if scaled < minimum as f64 || scaled > maximum as f64 {
            return Err(format!(
                "Sample {sample} is outside the {bit_resolution}-bit range"
            ));
        }
        Ok(scaled as i64)
    } else {
        if !sample.is_finite() {
            return Err("Decoded sample is not finite".to_string());
        }
        let scaled = (sample * scale).round_ties_even();
        Ok(scaled.clamp(minimum as f64, maximum as f64) as i64)
    }
}

fn read_unsigned(source: &[u8]) -> u64 {
    let mut value = 0u64;
    for (index, byte) in source.iter().enumerate() {
        value |= (*byte as u64) << (index * 8);
    }
    value
}

fn read_signed(source: &[u8], format: SampleFormat) -> i64 {
    let width_bits = (source.len() * 8) as u32;
    let unsigned = read_unsigned(source);
    let _ = format;
    if width_bits == 64 {
        unsigned as i64
    } else if unsigned & (1u64 << (width_bits - 1)) != 0 {
        (unsigned as i64) - (1i64 << width_bits)
    } else {
        unsigned as i64
    }
}

fn read_float(source: &[u8], format: SampleFormat) -> Result<f64, String> {
    match format {
        SampleFormat::F32 => {
            let bytes: [u8; 4] = source
                .try_into()
                .map_err(|_| "Invalid 32-bit float sample width".to_string())?;
            Ok(f64::from(f32::from_le_bytes(bytes)))
        }
        SampleFormat::F64 => {
            let bytes: [u8; 8] = source
                .try_into()
                .map_err(|_| "Invalid 64-bit float sample width".to_string())?;
            Ok(f64::from_le_bytes(bytes))
        }
        _ => Err("Decoded sample is not a float format".to_string()),
    }
}

trait SampleFormatExt {
    fn is_signed(self) -> bool;
}

impl SampleFormatExt for SampleFormat {
    fn is_signed(self) -> bool {
        matches!(
            self,
            SampleFormat::S8 | SampleFormat::S16 | SampleFormat::S24 | SampleFormat::S32
        )
    }
}

#[cfg(test)]
mod tests {
    use super::{adapt, TargetFormat};
    use rovia_decoder::{DecodedBlock, SampleFormat};

    fn block(format: SampleFormat, valid_bits: u32, samples: &[i64], channels: u16) -> DecodedBlock {
        let width = format.bytes_per_sample();
        let mut data = Vec::new();
        for sample in samples {
            data.extend_from_slice(&sample.to_le_bytes()[..width]);
        }
        DecodedBlock {
            sample_rate: 48_000,
            channels,
            format,
            valid_bits,
            frames: (samples.len() / channels as usize) as u64,
            timestamp_us: Some(0),
            data,
        }
    }

    fn float_block(format: SampleFormat, samples: &[f64], channels: u16) -> DecodedBlock {
        let mut data = Vec::new();
        for sample in samples {
            match format {
                SampleFormat::F32 => data.extend_from_slice(&(*sample as f32).to_le_bytes()),
                SampleFormat::F64 => data.extend_from_slice(&sample.to_le_bytes()),
                _ => unreachable!(),
            }
        }
        DecodedBlock {
            sample_rate: 48_000,
            channels,
            format,
            valid_bits: format.bit_width(),
            frames: (samples.len() / channels as usize) as u64,
            timestamp_us: Some(0),
            data,
        }
    }

    fn target(bit_resolution: u32, subslot_bytes: usize) -> TargetFormat {
        TargetFormat {
            sample_rate: 48_000,
            channels: 2,
            bit_resolution,
            subslot_bytes,
        }
    }

    fn read_output(output: &[u8], subslot_bytes: usize) -> Vec<i64> {
        output
            .chunks_exact(subslot_bytes)
            .map(|chunk| {
                let mut bytes = [0u8; 8];
                bytes[..subslot_bytes].copy_from_slice(chunk);
                if subslot_bytes < 8 && chunk[subslot_bytes - 1] & 0x80 != 0 {
                    for byte in &mut bytes[subslot_bytes..] {
                        *byte = 0xFF;
                    }
                }
                i64::from_le_bytes(bytes)
            })
            .collect()
    }

    #[test]
    fn identity_16_bit() {
        let samples = [0, 1, -1, 32_767, -32_768, 12_345];
        let decoded = block(SampleFormat::S16, 16, &samples, 2);
        let output = adapt(&decoded, &target(16, 2), true).unwrap();
        assert_eq!(output, decoded.data);
        assert_eq!(read_output(&output, 2), samples);
    }

    #[test]
    fn identity_24_bit() {
        let samples = [0, 1, -1, 8_388_607, -8_388_608, -123_456];
        let decoded = block(SampleFormat::S24, 24, &samples, 2);
        let output = adapt(&decoded, &target(24, 3), true).unwrap();
        assert_eq!(output.len(), samples.len() * 3);
        assert_eq!(read_output(&output, 3), samples);
    }

    #[test]
    fn identity_32_bit() {
        let samples = [0, 1, -1, i32::MAX as i64, i32::MIN as i64, -987_654_321];
        let decoded = block(SampleFormat::S32, 32, &samples, 2);
        let output = adapt(&decoded, &target(32, 4), true).unwrap();
        assert_eq!(output, decoded.data);
    }

    #[test]
    fn pads_24_bit_into_32_bit_subslot_with_sign_extension() {
        let samples = [-1, -8_388_608, 8_388_607, 1];
        let decoded = block(SampleFormat::S24, 24, &samples, 2);
        let output = adapt(&decoded, &target(24, 4), true).unwrap();
        assert_eq!(output.len(), samples.len() * 4);
        assert_eq!(&output[0..4], &[0xFF, 0xFF, 0xFF, 0xFF]);
        assert_eq!(&output[4..8], &[0x00, 0x00, 0x80, 0xFF]);
        assert_eq!(&output[8..12], &[0xFF, 0xFF, 0x7F, 0x00]);
        assert_eq!(read_output(&output, 4), samples);
    }

    #[test]
    fn widens_16_bit_to_24_bit_resolution_exactly() {
        let samples = [-32_768, -1, 0, 32_767];
        let decoded = block(SampleFormat::S16, 16, &samples, 2);
        let output = adapt(&decoded, &target(24, 3), true).unwrap();
        assert_eq!(read_output(&output, 3), samples);
    }

    #[test]
    fn narrows_only_when_every_sample_fits() {
        let fitting = block(SampleFormat::S32, 32, &[100, -100, 32_767, -32_768], 2);
        let output = adapt(&fitting, &target(16, 2), false).unwrap();
        assert_eq!(read_output(&output, 2), [100, -100, 32_767, -32_768]);
        let overflowing = block(SampleFormat::S32, 32, &[0, 32_768, 0, 0], 2);
        assert!(adapt(&overflowing, &target(16, 2), false).is_err());
        let underflowing = block(SampleFormat::S32, 32, &[0, -32_769, 0, 0], 2);
        assert!(adapt(&underflowing, &target(16, 2), false).is_err());
    }

    #[test]
    fn normalizes_left_justified_signed_samples() {
        let values = [0i64, 1, -1, 32_767, -32_768, 12_345, -12_345];
        let justified: Vec<i64> = values.iter().map(|value| value << 16).collect();
        let decoded = block(SampleFormat::S32, 16, &justified, 1);
        let mono_target = TargetFormat {
            channels: 1,
            ..target(16, 2)
        };
        let output = adapt(&decoded, &mono_target, true).unwrap();
        assert_eq!(read_output(&output, 2), values);
        let wide_target = TargetFormat {
            channels: 1,
            ..target(24, 3)
        };
        let output = adapt(&decoded, &wide_target, true).unwrap();
        assert_eq!(read_output(&output, 3), values);
    }

    #[test]
    fn left_justified_lossless_rejection_uses_valid_bits() {
        let justified: Vec<i64> = [100i64, -100].iter().map(|value| value << 16).collect();
        let decoded = block(SampleFormat::S32, 16, &justified, 1);
        let narrow_target = TargetFormat {
            channels: 1,
            ..target(8, 1)
        };
        assert!(adapt(&decoded, &narrow_target, true).is_err());
        let exact_target = TargetFormat {
            channels: 1,
            ..target(16, 2)
        };
        assert!(adapt(&decoded, &exact_target, true).is_ok());
    }

    #[test]
    fn normalizes_left_justified_unsigned_samples() {
        let mut decoded = block(SampleFormat::U32, 16, &[], 1);
        let raw: Vec<u32> = [0u32, 32_768, 65_535, 16_384]
            .iter()
            .map(|value| value << 16)
            .collect();
        let mut data = Vec::new();
        for value in raw {
            data.extend_from_slice(&value.to_le_bytes());
        }
        decoded.data = data;
        decoded.frames = 4;
        decoded.channels = 1;
        let mono_target = TargetFormat {
            channels: 1,
            ..target(16, 2)
        };
        let output = adapt(&decoded, &mono_target, true).unwrap();
        assert_eq!(read_output(&output, 2), [-32_768, 0, 32_767, -16_384]);
    }

    #[test]
    fn rejects_lossless_target_below_valid_bits() {
        let decoded = block(SampleFormat::S24, 24, &[0, 1, -1, 2], 2);
        assert!(adapt(&decoded, &target(16, 2), true).is_err());
    }

    #[test]
    fn centers_unsigned_samples() {
        let mut decoded = block(SampleFormat::U16, 16, &[], 2);
        let raw = [0u16, 32_768, 65_535, 16_384];
        let mut data = Vec::new();
        for value in raw {
            data.extend_from_slice(&value.to_le_bytes());
        }
        decoded.data = data;
        decoded.frames = 2;
        let output = adapt(&decoded, &target(16, 2), true).unwrap();
        assert_eq!(read_output(&output, 2), [-32_768, 0, 32_767, -16_384]);
    }

    #[test]
    fn centers_unsigned_8_bit_samples() {
        let decoded = DecodedBlock {
            sample_rate: 48_000,
            channels: 2,
            format: SampleFormat::U8,
            valid_bits: 8,
            frames: 2,
            timestamp_us: Some(0),
            data: vec![0, 128, 255, 64],
        };
        let output = adapt(&decoded, &target(16, 2), true).unwrap();
        assert_eq!(read_output(&output, 2), [-128, 0, 127, -64]);
    }

    #[test]
    fn float_lossless_accepts_exact_values() {
        let decoded = float_block(SampleFormat::F32, &[0.0, 0.5, -0.5, -1.0], 2);
        let output = adapt(&decoded, &target(16, 2), true).unwrap();
        assert_eq!(read_output(&output, 2), [0, 16_384, -16_384, -32_768]);
    }

    #[test]
    fn float_lossless_rejects_inexact_and_out_of_range_values() {
        let inexact = float_block(SampleFormat::F32, &[0.1, 0.0, 0.0, 0.0], 2);
        assert!(adapt(&inexact, &target(16, 2), true).is_err());
        let positive_full_scale = float_block(SampleFormat::F32, &[1.0, 0.0, 0.0, 0.0], 2);
        assert!(adapt(&positive_full_scale, &target(16, 2), true).is_err());
        let non_finite = float_block(SampleFormat::F64, &[f64::NAN, 0.0, 0.0, 0.0], 2);
        assert!(adapt(&non_finite, &target(16, 2), true).is_err());
    }

    #[test]
    fn float_lossy_maps_boundaries_deterministically() {
        let decoded = float_block(
            SampleFormat::F32,
            &[0.0, 0.5, -1.0, 0.999_969_5, 1.0, -1.5],
            2,
        );
        let output = adapt(&decoded, &target(16, 2), false).unwrap();
        assert_eq!(
            read_output(&output, 2),
            [0, 16_384, -32_768, 32_767, 32_767, -32_768]
        );
    }

    #[test]
    fn float_lossy_rejects_non_finite_samples() {
        let decoded = float_block(SampleFormat::F32, &[f64::INFINITY, 0.0, 0.0, 0.0], 2);
        assert!(adapt(&decoded, &target(16, 2), false).is_err());
    }

    #[test]
    fn rejects_format_mismatches() {
        let decoded = block(SampleFormat::S16, 16, &[0, 1, -1, 2], 2);
        let wrong_rate = TargetFormat {
            sample_rate: 44_100,
            ..target(16, 2)
        };
        assert!(adapt(&decoded, &wrong_rate, true).is_err());
        let wrong_channels = TargetFormat {
            channels: 1,
            ..target(16, 2)
        };
        assert!(adapt(&decoded, &wrong_channels, true).is_err());
        let narrow_subslot = TargetFormat {
            subslot_bytes: 2,
            ..target(24, 3)
        };
        assert!(adapt(&decoded, &narrow_subslot, true).is_err());
    }

    #[test]
    fn rejects_truncated_block_data() {
        let mut decoded = block(SampleFormat::S16, 16, &[0, 1, -1, 2], 2);
        decoded.data.truncate(4);
        assert!(adapt(&decoded, &target(16, 2), true).is_err());
    }
}
