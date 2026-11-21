//! Decoder backend trait + registry.
//!
//! This is the Rust mirror of the Kotlin `DecoderBackend` interface
//! (`playback/decoder/DecoderBackend.kt`). Playback code in
//! `rovia-uniffi` / `direct-usb` talks to a backend through this trait,
//! never to a concrete decoder: adding a decoder means implementing the
//! trait and registering it in [`BackendRegistry`], replacing a decoder
//! means registering a different default. No playback file changes.
//!
//! Backend ids follow the `<decoder>-<language/tech>` rule (locked
//! 2026-11-21): the Symphonia backend's canonical id is
//! `"symphonia-rust"`, a future FFmpeg backend would be `"ffmpeg-c"`.
//! Never register a bare decoder name such as `"symphonia"`.
//!
//! The crate itself stays `rovia-decoder` and the Symphonia
//! implementation stays `DecoderEngine`; only the backend id exposed
//! to Kotlin / the UI / docs follows the `<decoder>-<tech>` rule.

use std::path::Path;

use crate::{DecodedBlock, DecoderEngine, DecoderError, SourceInfo};

/// The only contract a decoder backend must satisfy.
pub trait DecoderBackend {
    /// Canonical backend id, e.g. `"symphonia-rust"`.
    fn backend_id(&self) -> &'static str;

    /// Opens a source file for decoding. The hint is a file extension
    /// such as `"m4a"`; the container/codec are probed, never assumed.
    fn open_path(
        &mut self,
        path: &Path,
        extension_hint: Option<&str>,
    ) -> Result<SourceInfo, DecoderError>;

    /// Decodes the next block, or `None` at end of stream.
    fn decode_next(&mut self) -> Result<Option<DecodedBlock>, DecoderError>;

    /// Seeks; returns the actual position in microseconds.
    fn seek(&mut self, position_us: u64) -> Result<u64, DecoderError>;

    /// Properties of the currently open source, if any.
    fn info(&self) -> Option<&SourceInfo>;
}

/// The Symphonia backend: [`DecoderEngine`] behind the trait.
pub struct SymphoniaBackend {
    engine: Option<DecoderEngine>,
}

impl SymphoniaBackend {
    pub fn new() -> Self {
        Self { engine: None }
    }
}

impl Default for SymphoniaBackend {
    fn default() -> Self {
        Self::new()
    }
}

impl DecoderBackend for SymphoniaBackend {
    fn backend_id(&self) -> &'static str {
        BackendRegistry::SYMPHONIA_RUST_ID
    }

    fn open_path(
        &mut self,
        path: &Path,
        extension_hint: Option<&str>,
    ) -> Result<SourceInfo, DecoderError> {
        let engine = DecoderEngine::open_path(path, extension_hint)?;
        let info = engine.info().clone();
        self.engine = Some(engine);
        Ok(info)
    }

    fn decode_next(&mut self) -> Result<Option<DecodedBlock>, DecoderError> {
        match self.engine.as_mut() {
            Some(engine) => engine.decode_next(),
            None => Err(DecoderError::Unsupported(
                "no source is open for decoding".to_string(),
            )),
        }
    }

    fn seek(&mut self, position_us: u64) -> Result<u64, DecoderError> {
        match self.engine.as_mut() {
            Some(engine) => engine.seek(position_us),
            None => Err(DecoderError::Unsupported(
                "no source is open for seeking".to_string(),
            )),
        }
    }

    fn info(&self) -> Option<&SourceInfo> {
        self.engine.as_ref().map(DecoderEngine::info)
    }
}

/// Which codecs the Symphonia backend can decode, by codec name
/// (upper-cased, as reported by Symphonia / the container).
fn symphonia_supports(codec: &str) -> bool {
    matches!(
        codec.to_ascii_uppercase().as_str(),
        "PCM" | "FLAC" | "ALAC" | "AAC" | "AAC-LC" | "MP3" | "MP1" | "MP2" | "VORBIS" | "ADPCM"
    )
}

/// Human-readable reason a codec is unsupported by Symphonia, if any.
pub fn symphonia_unsupported_reason(codec: &str) -> Option<&'static str> {
    let key = codec.to_ascii_uppercase();
    match key.as_str() {
        "HE-AAC" | "HE-AACV1" => Some("HE-AAC (SBR) is not supported by Symphonia"),
        "HE-AACV2" => {
            Some("HE-AACv2 (SBR + parametric stereo) is not supported by Symphonia")
        }
        "AAC-ELD" => Some("AAC-ELD is not supported by Symphonia"),
        "XHE-AAC" => Some("xHE-AAC is not supported by Symphonia"),
        "OPUS" => Some("Opus decoding is not supported by Symphonia in this build"),
        "DSD" | "DSF" | "DFF" => Some("DSD is not supported by Symphonia"),
        "WAVPACK" => Some("WavPack is not supported by Symphonia"),
        "APE" => Some("APE audio decoding is not supported by Symphonia"),
        "WMA" => Some("WMA is not supported by Symphonia"),
        "AC-3" | "AC3" => Some("AC-3 is not supported by Symphonia"),
        "E-AC-3" | "EAC3" => Some("E-AC-3 is not supported by Symphonia"),
        _ => None,
    }
}

/// The registry: the only place a backend id is resolved.
///
/// A future backend registers its id here (e.g. `"ffmpeg-c"`) and
/// implements [`DecoderBackend`]; playback code then resolves it by id
/// without knowing which decoder it is.
pub struct BackendRegistry;

impl BackendRegistry {
    /// Canonical id of the Symphonia backend.
    pub const SYMPHONIA_RUST_ID: &'static str = "symphonia-rust";

    /// The default backend id.
    pub fn default_id() -> &'static str {
        Self::SYMPHONIA_RUST_ID
    }

    /// All registered backend ids, in preference order.
    pub fn backend_ids() -> &'static [&'static str] {
        &[Self::SYMPHONIA_RUST_ID]
    }

    /// Resolves a codec name to the backend that can decode it.
    /// Returns `Err(DecoderError::Unsupported)` naming the codec when
    /// no registered backend supports it.
    pub fn resolve(codec: &str) -> Result<&'static str, DecoderError> {
        if symphonia_supports(codec) {
            return Ok(Self::SYMPHONIA_RUST_ID);
        }
        let reason = symphonia_unsupported_reason(codec)
            .map(str::to_string)
            .unwrap_or_else(|| format!("{codec} is not supported by Symphonia"));
        Err(DecoderError::Unsupported(format!(
            "unsupported source: {codec} — {reason}"
        )))
    }

    /// Creates a backend by id. Unknown ids are an error, not a
    /// silent fallback to another decoder.
    pub fn create(id: &str) -> Result<Box<dyn DecoderBackend>, DecoderError> {
        match id {
            Self::SYMPHONIA_RUST_ID => Ok(Box::new(SymphoniaBackend::new())),
            other => Err(DecoderError::Unsupported(format!(
                "unsupported source: decoder backend {other} is not registered"
            ))),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn fixture(name: &str) -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("tests")
            .join("fixtures")
            .join(name)
    }

    #[test]
    fn registry_resolves_supported_codecs_to_symphonia_rust() {
        for codec in ["AAC-LC", "ALAC", "FLAC", "MP3", "VORBIS", "PCM"] {
            assert_eq!(
                BackendRegistry::resolve(codec).expect(codec),
                "symphonia-rust",
                "{codec}"
            );
        }
        assert_eq!(BackendRegistry::default_id(), "symphonia-rust");
        assert_eq!(BackendRegistry::backend_ids().len(), 1);
        assert_eq!(BackendRegistry::backend_ids()[0], "symphonia-rust");
    }

    #[test]
    fn registry_rejects_unsupported_codecs_with_the_codec_name() {
        for codec in ["HE-AAC", "HE-AACv2", "Opus", "DSD", "WMA", "AC-3"] {
            let failure = BackendRegistry::resolve(codec).expect_err(codec);
            let message = failure.to_string();
            assert!(
                message.contains(codec),
                "{codec}: message did not name the codec: {message}"
            );
            assert!(
                matches!(failure, DecoderError::Unsupported(_)),
                "{codec}: expected Unsupported, got {failure:?}"
            );
        }
        // HE-AAC is the sacrificed format (2026-11-21): the reason must
        // say SBR, so the UI can explain it instead of skipping silently.
        let message = BackendRegistry::resolve("HE-AAC").unwrap_err().to_string();
        assert!(message.contains("SBR"), "HE-AAC reason: {message}");
    }

    #[test]
    fn symphonia_backend_decodes_an_m4a_fixture_through_the_trait() {
        let mut backend = SymphoniaBackend::new();
        assert_eq!(backend.backend_id(), "symphonia-rust");
        let info = backend
            .open_path(&fixture("tone_lossy.m4a"), Some("m4a"))
            .expect("open tone_lossy.m4a");
        // Codec short name is Symphonia's ("AAC" in 0.6.1); do not
        // hard-code it beyond non-empty, the rate is the stable fact.
        assert!(!info.codec.is_empty());
        assert_eq!(info.sample_rate, 44_100);
        assert!(backend.decode_next().expect("decode").is_some());
        assert!(backend.info().is_some());
    }

    #[test]
    fn registry_create_rejects_unknown_backend_ids() {
        let failure = BackendRegistry::create("symphonia").expect_err("bare id");
        assert!(
            failure.to_string().contains("not registered"),
            "bare 'symphonia' must not resolve: {failure}"
        );
    }
}
