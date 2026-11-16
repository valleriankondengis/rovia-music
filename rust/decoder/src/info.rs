use crate::SampleFormat;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SourceInfo {
    pub container: String,
    pub codec: String,
    pub sample_rate: u32,
    pub channels: u16,
    pub bits_per_sample: Option<u32>,
    pub bits_per_coded_sample: Option<u32>,
    pub sample_format: Option<SampleFormat>,
    pub duration_us: Option<u64>,
    pub total_frames: Option<u64>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DecodedBlock {
    pub sample_rate: u32,
    pub channels: u16,
    pub format: SampleFormat,
    pub valid_bits: u32,
    pub frames: u64,
    pub timestamp_us: Option<u64>,
    pub data: Vec<u8>,
}
