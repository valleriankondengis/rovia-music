#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SampleFormat {
    U8,
    U16,
    U24,
    U32,
    S8,
    S16,
    S24,
    S32,
    F32,
    F64,
}

impl SampleFormat {
    pub fn bytes_per_sample(self) -> usize {
        match self {
            Self::U8 | Self::S8 => 1,
            Self::U16 | Self::S16 => 2,
            Self::U24 | Self::S24 => 3,
            Self::U32 | Self::S32 | Self::F32 => 4,
            Self::F64 => 8,
        }
    }

    pub fn bit_width(self) -> u32 {
        match self {
            Self::U8 | Self::S8 => 8,
            Self::U16 | Self::S16 => 16,
            Self::U24 | Self::S24 => 24,
            Self::U32 | Self::S32 | Self::F32 => 32,
            Self::F64 => 64,
        }
    }

    pub fn is_integer(self) -> bool {
        !self.is_float()
    }

    pub fn is_float(self) -> bool {
        matches!(self, Self::F32 | Self::F64)
    }
}

#[cfg(test)]
mod tests {
    use super::SampleFormat;

    #[test]
    fn reports_width_and_kind_for_every_format() {
        let cases = [
            (SampleFormat::U8, 1, 8, true, false),
            (SampleFormat::U16, 2, 16, true, false),
            (SampleFormat::U24, 3, 24, true, false),
            (SampleFormat::U32, 4, 32, true, false),
            (SampleFormat::S8, 1, 8, true, false),
            (SampleFormat::S16, 2, 16, true, false),
            (SampleFormat::S24, 3, 24, true, false),
            (SampleFormat::S32, 4, 32, true, false),
            (SampleFormat::F32, 4, 32, false, true),
            (SampleFormat::F64, 8, 64, false, true),
        ];
        for (format, bytes, bits, integer, float) in cases {
            assert_eq!(format.bytes_per_sample(), bytes);
            assert_eq!(format.bit_width(), bits);
            assert_eq!(format.is_integer(), integer);
            assert_eq!(format.is_float(), float);
        }
    }
}
