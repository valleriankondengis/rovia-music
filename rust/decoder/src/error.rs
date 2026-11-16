#[derive(Debug, thiserror::Error)]
pub enum DecoderError {
    #[error("unsupported source: {0}")]
    Unsupported(String),
    #[error("source io failure: {0}")]
    Io(String),
    #[error("decode failure: {0}")]
    Decode(String),
}
