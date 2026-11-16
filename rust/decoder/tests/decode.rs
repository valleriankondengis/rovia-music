use std::path::PathBuf;

use rovia_decoder::{DecodedBlock, DecoderEngine, DecoderError, SampleFormat};

const EXPECTED_FRAMES: u64 = 22_050;
const EXPECTED_RATE: u32 = 44_100;

fn fixture(name: &str) -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests").join("fixtures").join(name)
}

fn open(name: &str) -> DecoderEngine {
    let path = fixture(name);
    let extension = path.extension().and_then(|value| value.to_str()).map(str::to_owned);
    DecoderEngine::open_path(&path, extension.as_deref())
        .unwrap_or_else(|failure| panic!("open {name}: {failure}"))
}

fn reference(name: &str, width: usize) -> Vec<i64> {
    let bytes = std::fs::read(fixture(name)).expect("reference fixture");
    bytes
        .chunks_exact(width)
        .map(|chunk| {
            let mut value: i64 = 0;
            for (index, byte) in chunk.iter().enumerate() {
                value |= i64::from(*byte) << (8 * index);
            }
            let bits = width * 8;
            if value >= (1_i64 << (bits - 1)) {
                value -= 1_i64 << bits;
            }
            value
        })
        .collect()
}

fn align_value(block: &DecodedBlock, value: i64) -> i64 {
    if block.format.is_integer() && block.valid_bits > 0 && block.valid_bits < block.format.bit_width() {
        value >> (block.format.bit_width() - block.valid_bits)
    } else {
        value
    }
}

fn block_samples(block: &DecodedBlock) -> Vec<i64> {
    let width = block.format.bytes_per_sample();
    assert_eq!(block.data.len() as u64, block.frames * u64::from(block.channels) * width as u64);
    block
        .data
        .chunks_exact(width)
        .map(|chunk| match block.format {
            SampleFormat::U8 => i64::from(chunk[0]) - 128,
            SampleFormat::U16 | SampleFormat::U24 | SampleFormat::U32 => {
                let mut value: i64 = 0;
                for (index, byte) in chunk.iter().enumerate() {
                    value |= i64::from(*byte) << (8 * index);
                }
                align_value(block, value)
            }
            SampleFormat::S8 => i64::from(chunk[0] as i8),
            SampleFormat::S16 | SampleFormat::S24 | SampleFormat::S32 => {
                let mut value: i64 = 0;
                for (index, byte) in chunk.iter().enumerate() {
                    value |= i64::from(*byte) << (8 * index);
                }
                let bits = width * 8;
                if value >= (1_i64 << (bits - 1)) {
                    value -= 1_i64 << bits;
                }
                align_value(block, value)
            }
            SampleFormat::F32 => {
                let value = f64::from(f32::from_le_bytes(chunk.try_into().expect("f32 chunk")));
                (value * ((1_i64 << (block.valid_bits - 1)) as f64)).round() as i64
            }
            SampleFormat::F64 => {
                let value = f64::from_le_bytes(chunk.try_into().expect("f64 chunk"));
                (value * ((1_i64 << (block.valid_bits - 1)) as f64)).round() as i64
            }
        })
        .collect()
}

fn decode_all(engine: &mut DecoderEngine) -> (Vec<i64>, u64) {
    let mut samples = Vec::new();
    let mut frames = 0_u64;
    while let Some(block) = engine.decode_next().expect("decode") {
        assert_eq!(block.sample_rate, EXPECTED_RATE);
        assert_eq!(block.channels, 2);
        frames += block.frames;
        samples.extend(block_samples(&block));
    }
    (samples, frames)
}

#[test]
fn lossless_sources_decode_to_exact_reference_samples() {
    let cases: [(&str, &str, usize); 7] = [
        ("tone16.wav", "ref16.raw", 2),
        ("tone24.wav", "ref24.raw", 3),
        ("tone32.wav", "ref32.raw", 4),
        ("tone16.flac", "ref16.raw", 2),
        ("tone24.flac", "ref24.raw", 3),
        ("tone16.aif", "ref16.raw", 2),
        ("tone_lossless.m4a", "ref16.raw", 2),
    ];
    for (name, reference_name, width) in cases {
        let mut engine = open(name);
        assert_eq!(engine.info().sample_rate, EXPECTED_RATE, "{name}");
        assert_eq!(engine.info().channels, 2, "{name}");
        let (samples, frames) = decode_all(&mut engine);
        assert_eq!(frames, EXPECTED_FRAMES, "{name}");
        assert_eq!(samples, reference(reference_name, width), "{name}");
    }
}

#[test]
fn lossy_sources_decode_with_expected_shape() {
    const LOSSY_FRAMES: u64 = 110_250;
    for name in ["tone_lossy.mp3", "tone_lossy.m4a", "tone_lossy.ogg"] {
        let mut engine = open(name);
        assert_eq!(engine.info().sample_rate, EXPECTED_RATE, "{name}");
        assert_eq!(engine.info().channels, 2, "{name}");
        let (samples, frames) = decode_all(&mut engine);
        let tolerance = LOSSY_FRAMES / 50;
        assert!(
            frames.abs_diff(LOSSY_FRAMES) <= tolerance,
            "{name}: frames={frames} expected={LOSSY_FRAMES}"
        );
        assert_eq!(samples.len() as u64, frames * 2, "{name}");
        assert!(samples.iter().any(|sample| *sample != 0), "{name}");
    }
}

#[test]
fn source_info_reports_stream_properties() {
    let engine = open("tone16.flac");
    let info = engine.info();
    assert_eq!(info.sample_rate, EXPECTED_RATE);
    assert_eq!(info.channels, 2);
    assert_eq!(info.bits_per_sample, Some(16));
    assert!(!info.container.is_empty());
    assert!(!info.codec.is_empty());
    let duration = info.duration_us.expect("duration");
    assert!(duration.abs_diff(500_000) <= 25_000, "duration={duration}");
}

#[test]
fn seek_lands_at_or_before_the_requested_position() {
    let mut engine = open("tone16.flac");
    for _ in 0..3 {
        assert!(engine.decode_next().expect("decode").is_some());
    }
    let requested = 250_000_u64;
    let actual = engine.seek(requested).expect("seek");
    assert!(actual <= requested, "actual={actual}");
    assert!(actual >= requested - 120_000, "actual={actual}");
    let block = engine.decode_next().expect("decode after seek").expect("block after seek");
    let timestamp = block.timestamp_us.expect("timestamp after seek");
    assert!(timestamp + 5_000 >= actual, "timestamp={timestamp} actual={actual}");
    assert!(timestamp <= requested, "timestamp={timestamp}");
    let mut frames = block.frames;
    while let Some(block) = engine.decode_next().expect("decode to end") {
        frames += block.frames;
    }
    assert!(frames > 0);
    assert!(engine.decode_next().expect("stable end").is_none());
    assert!(engine.decode_next().expect("stable end").is_none());
}

#[test]
fn end_of_stream_is_stable() {
    let mut engine = open("tone16.wav");
    let (_, frames) = decode_all(&mut engine);
    assert_eq!(frames, EXPECTED_FRAMES);
    assert!(engine.decode_next().expect("end").is_none());
    assert!(engine.decode_next().expect("end").is_none());
}

#[test]
fn unsupported_input_is_rejected() {
    let path = std::env::temp_dir().join("rovia_decoder_unsupported_input.bin");
    std::fs::write(&path, b"this is not a media file at all, just plain text bytes").expect("write");
    let result = DecoderEngine::open_path(&path, Some("bin"));
    assert!(
        matches!(result, Err(DecoderError::Unsupported(_))),
        "expected Unsupported, got {}",
        match &result {
            Ok(_) => "an opened engine".to_string(),
            Err(failure) => failure.to_string(),
        }
    );
    let _ = std::fs::remove_file(&path);
}

#[test]
fn truncated_input_never_panics() {
    let bytes = std::fs::read(fixture("tone16.wav")).expect("fixture");
    let path = std::env::temp_dir().join("rovia_decoder_truncated.wav");
    std::fs::write(&path, &bytes[..bytes.len() / 3]).expect("write truncated");
    match DecoderEngine::open_path(&path, Some("wav")) {
        Ok(mut engine) => {
            let mut frames = 0_u64;
            for _ in 0..10_000 {
                match engine.decode_next() {
                    Ok(Some(block)) => frames += block.frames,
                    Ok(None) => break,
                    Err(_) => break,
                }
            }
            assert!(frames < EXPECTED_FRAMES, "frames={frames}");
        }
        Err(_) => {}
    }
    let _ = std::fs::remove_file(&path);
}
