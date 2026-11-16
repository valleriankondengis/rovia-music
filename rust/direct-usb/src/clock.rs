pub(crate) const UAC2_GET_CUR: u8 = 0x01;
pub(crate) const UAC2_SET_CUR: u8 = 0x01;
pub(crate) const UAC2_SAMPLING_FREQUENCY_CONTROL: u16 = 0x0100;
pub(crate) const CONTROL_TRANSFER_TIMEOUT_MS: u32 = 500;

pub(crate) const fn interface_request_type(input: bool) -> u8 {
    (if input { 0x80 } else { 0 }) | 0x20 | 0x01
}

pub(crate) const fn clock_control_index(clock_source_id: u8, control_interface: u8) -> u16 {
    ((clock_source_id as u16) << 8) | control_interface as u16
}

pub(crate) fn service_interval_microseconds(
    high_speed: bool,
    interval: u8,
) -> Result<u32, String> {
    if !(1..=16).contains(&interval) {
        return Err(format!("Unsupported isochronous endpoint interval: {interval}"));
    }
    let bus_interval_microseconds = if high_speed { 125 } else { 1_000 };
    Ok(bus_interval_microseconds * (1u32 << (interval - 1)))
}

pub(crate) fn validate_packet_capacity(
    sample_rate: u32,
    channels: u8,
    subslot_size: u8,
    maximum_packet_size: u32,
    interval_microseconds: u32,
) -> Result<u32, String> {
    if sample_rate == 0 || channels == 0 || subslot_size == 0 || interval_microseconds == 0 {
        return Err("PCM endpoint capacity inputs must be nonzero".to_string());
    }
    let maximum_frames = (u64::from(sample_rate) * u64::from(interval_microseconds))
        .div_ceil(1_000_000);
    let frame_bytes = u64::from(channels) * u64::from(subslot_size);
    let required = maximum_frames.saturating_mul(frame_bytes);
    if required > u64::from(maximum_packet_size) {
        return Err(format!(
            "USB endpoint bandwidth too small: requires {required} bytes per service interval, endpoint allows {maximum_packet_size}"
        ));
    }
    Ok(required as u32)
}


pub(crate) struct PacketClock {
    nominal_frames: f64,
    feedback_frames: Option<f64>,
    fractional_frames: f64,
}

impl PacketClock {
    pub(crate) fn new(sample_rate: u32, interval_microseconds: u32) -> Self {
        Self {
            nominal_frames: f64::from(sample_rate) * f64::from(interval_microseconds) / 1_000_000.0,
            feedback_frames: None,
            fractional_frames: 0.0,
        }
    }

    pub(crate) fn update_feedback(
        &mut self,
        data: &[u8],
        high_speed: bool,
        interval_microseconds: u32,
    ) -> bool {
        let base_frames = match (data.len(), high_speed) {
            (3, false) => {
                let raw = u32::from(data[0])
                    | (u32::from(data[1]) << 8)
                    | (u32::from(data[2]) << 16);
                f64::from(raw) / 16_384.0
            }
            (4, true) => {
                let raw = u32::from_le_bytes([data[0], data[1], data[2], data[3]]);
                f64::from(raw) / 65_536.0
            }
            _ => return false,
        };
        let interval_base = if high_speed { 125.0 } else { 1_000.0 };
        let frames = base_frames * f64::from(interval_microseconds) / interval_base;
        if !frames.is_finite()
            || self.nominal_frames <= 0.0
            || (frames - self.nominal_frames).abs() > self.nominal_frames * 0.02
        {
            return false;
        }
        self.feedback_frames = Some(frames);
        true
    }

    pub(crate) fn next_frames(&mut self) -> usize {
        self.fractional_frames += self.feedback_frames.unwrap_or(self.nominal_frames);
        let frames = self.fractional_frames.floor() as usize;
        self.fractional_frames -= frames as f64;
        frames
    }

}

pub(crate) fn parse_current_sample_rate(data: &[u8]) -> Result<u32, String> {
    if data.len() != 4 {
        return Err(format!(
            "UAC2 Clock Source GET_CUR response must contain 4 bytes, received {}",
            data.len()
        ));
    }

    let rate = u32::from_le_bytes([data[0], data[1], data[2], data[3]]);
    if rate == 0 {
        return Err("UAC2 Clock Source GET_CUR returned a zero sample rate".to_string());
    }

    Ok(rate)
}

#[cfg(test)]
mod tests {
    use super::{
        clock_control_index, interface_request_type, parse_current_sample_rate,
        service_interval_microseconds, validate_packet_capacity, PacketClock, UAC2_GET_CUR,
        UAC2_SET_CUR, UAC2_SAMPLING_FREQUENCY_CONTROL,
    };

    #[test]
    fn builds_uac2_clock_control_request_types() {
        assert_eq!(interface_request_type(true), 0xA1);
        assert_eq!(interface_request_type(false), 0x21);
    }

    #[test]
    fn uses_uac2_clock_frequency_get_cur_selector() {
        assert_eq!(UAC2_GET_CUR, 0x01);
        assert_eq!(UAC2_SAMPLING_FREQUENCY_CONTROL, 0x0100);
    }

    #[test]
    fn builds_clock_source_interface_index() {
        assert_eq!(clock_control_index(1, 1), 0x0101);
        assert_eq!(clock_control_index(10, 3), 0x0A03);
    }

    #[test]
    fn uses_the_uac2_clock_frequency_set_cur_request() {
        assert_eq!(UAC2_SET_CUR, 0x01);
    }

    #[test]
    fn calculates_usb_audio_service_intervals() {
        assert_eq!(service_interval_microseconds(true, 1).unwrap(), 125);
        assert_eq!(service_interval_microseconds(true, 2).unwrap(), 250);
        assert_eq!(service_interval_microseconds(false, 1).unwrap(), 1_000);
        assert_eq!(service_interval_microseconds(false, 2).unwrap(), 2_000);
        assert_eq!(service_interval_microseconds(false, 5).unwrap(), 16_000);
        assert_eq!(service_interval_microseconds(false, 16).unwrap(), 32_768_000);
        assert!(service_interval_microseconds(true, 0).is_err());
        assert_eq!(service_interval_microseconds(true, 5).unwrap(), 2_000);
        assert_eq!(service_interval_microseconds(true, 16).unwrap(), 4_096_000);
        assert!(service_interval_microseconds(true, 17).is_err());
    }

    #[test]
    fn validates_pcm_packet_capacity_for_high_speed_audio() {
        assert_eq!(
            validate_packet_capacity(768_000, 2, 4, 776, 125).unwrap(),
            768
        );
        assert!(validate_packet_capacity(768_000, 2, 4, 767, 125).is_err());
        assert_eq!(
            validate_packet_capacity(44_100, 2, 3, 64, 125).unwrap(),
            36
        );
    }

    #[test]
    fn parses_uac2_clock_current_rate_as_little_endian() {
        assert_eq!(parse_current_sample_rate(&44_100u32.to_le_bytes()).unwrap(), 44_100);
        assert_eq!(parse_current_sample_rate(&192_000u32.to_le_bytes()).unwrap(), 192_000);
    }

    #[test]
    fn rejects_invalid_uac2_clock_current_rate_responses() {
        assert!(parse_current_sample_rate(&[0, 0, 0]).is_err());
        assert!(parse_current_sample_rate(&[0, 0, 0, 0, 0]).is_err());
        assert!(parse_current_sample_rate(&[0, 0, 0, 0]).is_err());
    }

    #[test]
    fn schedules_44100_frames_without_long_term_drift() {
        let mut clock = PacketClock::new(44_100, 125);
        let total = (0..8_000).map(|_| clock.next_frames()).sum::<usize>();
        assert_eq!(total, 44_100);
    }

    #[test]
    fn schedules_48000_as_six_frames_per_high_speed_microframe() {
        let mut clock = PacketClock::new(48_000, 125);
        assert!((0..128).all(|_| clock.next_frames() == 6));
    }

    #[test]
    fn accepts_high_speed_explicit_feedback_and_uses_its_rate() {
        let mut clock = PacketClock::new(48_000, 125);
        assert!(clock.update_feedback(&(6u32 << 16).to_le_bytes(), true, 125));
        assert_eq!(clock.next_frames(), 6);
    }

    #[test]
    fn accepts_full_speed_explicit_feedback_format() {
        let mut clock = PacketClock::new(48_000, 1_000);
        let raw = 48u32 << 14;
        let data = [raw as u8, (raw >> 8) as u8, (raw >> 16) as u8];
        assert!(clock.update_feedback(&data, false, 1_000));
        assert_eq!(clock.next_frames(), 48);
    }

    #[test]
    fn rejects_invalid_or_out_of_tolerance_feedback() {
        let mut clock = PacketClock::new(48_000, 125);
        assert!(!clock.update_feedback(&[0; 4], true, 125));
        assert!(!clock.update_feedback(&(7u32 << 16).to_le_bytes(), true, 125));
        assert!(!clock.update_feedback(&[0; 3], true, 125));
        assert!(!clock.update_feedback(&[0; 4], false, 1_000));
    }
}
