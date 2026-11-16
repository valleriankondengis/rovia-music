use crate::DacVolumeDto;

const REQUEST_SET_CURRENT: u8 = 0x01;
const REQUEST_UAC2_GET_CURRENT: u8 = 0x01;
const REQUEST_UAC2_GET_RANGE: u8 = 0x02;
const REQUEST_UAC1_GET_CURRENT: u8 = 0x81;
const REQUEST_UAC1_GET_MINIMUM: u8 = 0x82;
const REQUEST_UAC1_GET_MAXIMUM: u8 = 0x83;
const REQUEST_UAC1_GET_RESOLUTION: u8 = 0x84;
const VOLUME_CONTROL_SELECTOR: u16 = 0x02;
const STARTUP_CEILING_DB_256: i32 = -40 * 256;
const SILENCE_DB_256: i32 = i16::MIN as i32;

pub(crate) trait Control {
    fn transfer(
        &self,
        input: bool,
        request: u8,
        value: u16,
        index: u16,
        data: &mut [u8],
    ) -> Result<(), String>;
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct FeatureUnit {
    pub unit_id: u8,
    pub interface_number: u8,
    pub uac2: bool,
    pub channels: Vec<u8>,
}

pub(crate) fn find_feature_unit(
    raw: &[u8],
    control_interface: u8,
    terminal_link: u8,
    channels: u8,
) -> Option<FeatureUnit> {
    let configuration = first_configuration(raw)?;
    let mut on_target_interface = false;
    let mut uac2 = false;
    let mut sources: Vec<(u8, u8)> = Vec::new();
    let mut output_terminals: Vec<u8> = Vec::new();
    let mut feature_units: Vec<FeatureUnit> = Vec::new();
    let mut offset = 0;
    while offset + 2 <= configuration.len() {
        let length = configuration[offset] as usize;
        if length < 2 || length > configuration.len() - offset {
            return None;
        }
        let descriptor = &configuration[offset..offset + length];
        offset += length;
        if descriptor[1] == 0x04 && length >= 9 {
            on_target_interface = descriptor[2] == control_interface
                && descriptor[5] == 0x01
                && descriptor[6] == 0x01;
            if on_target_interface {
                uac2 = descriptor[7] == 0x20;
            }
        } else if on_target_interface && descriptor[1] == 0x24 && length >= 4 {
            match descriptor[2] {
                0x02 if length >= 9 => {
                    sources.push((descriptor[3], 0));
                }
                0x03 if length >= 9 => {
                    sources.push((descriptor[3], descriptor[7]));
                    output_terminals.push(descriptor[3]);
                }
                0x06 if length >= 7 => {
                    sources.push((descriptor[3], descriptor[4]));
                    let control_size = if uac2 { 4 } else { descriptor[5] as usize };
                    let control_start = if uac2 { 5 } else { 6 };
                    if control_size == 0 || control_size > 4 {
                        continue;
                    }
                    let channel_writable = |channel: usize| -> bool {
                        let position = control_start + channel * control_size;
                        if position >= length {
                            return false;
                        }
                        if uac2 {
                            (descriptor[position] >> 2) & 0x03 == 0x03
                        } else {
                            descriptor[position] & 0x02 != 0
                        }
                    };
                    let controlled = if channel_writable(0) {
                        vec![0]
                    } else if (1..=channels as usize).all(channel_writable) {
                        (1..=channels).collect()
                    } else {
                        continue;
                    };
                    feature_units.push(FeatureUnit {
                        unit_id: descriptor[3],
                        interface_number: control_interface,
                        uac2,
                        channels: controlled,
                    });
                }
                _ => {}
            }
        }
    }
    let source_of = |id: u8| -> Option<u8> {
        sources
            .iter()
            .find(|(entity, _)| *entity == id)
            .map(|(_, source)| *source)
    };
    let mut matches: Vec<FeatureUnit> = Vec::new();
    for output in &output_terminals {
        let mut id = *output;
        let mut seen: Vec<u8> = Vec::new();
        let mut candidate: Option<FeatureUnit> = None;
        while id != terminal_link && !seen.contains(&id) {
            seen.push(id);
            if candidate.is_none() {
                candidate = feature_units.iter().find(|unit| unit.unit_id == id).cloned();
            }
            id = source_of(id)?;
        }
        if id == terminal_link {
            if let Some(unit) = candidate {
                if !matches.contains(&unit) {
                    matches.push(unit);
                }
            }
        }
    }
    if matches.len() == 1 {
        matches.pop()
    } else {
        None
    }
}

fn first_configuration(raw: &[u8]) -> Option<&[u8]> {
    let mut offset = 0;
    while offset + 9 <= raw.len() {
        let length = raw[offset] as usize;
        if length < 2 || length > raw.len() - offset {
            return None;
        }
        if raw[offset + 1] == 0x02 {
            let total = u16::from_le_bytes([raw[offset + 2], raw[offset + 3]]) as usize;
            let end = offset.saturating_add(total).min(raw.len());
            return Some(&raw[offset..end]);
        }
        offset += length;
    }
    None
}

#[derive(Clone, Copy, Debug)]
struct VolumeRange {
    minimum: i16,
    maximum: i16,
    step: i16,
}

impl VolumeRange {
    fn parse(bytes: &[u8]) -> Result<Self, String> {
        if bytes.len() < 6 {
            return Err("USB volume range is truncated".to_string());
        }
        let range = Self {
            minimum: i16::from_le_bytes([bytes[0], bytes[1]]),
            maximum: i16::from_le_bytes([bytes[2], bytes[3]]),
            step: i16::from_le_bytes([bytes[4], bytes[5]]),
        };
        if range.minimum == i16::MIN || range.minimum > range.maximum || range.step < 0 {
            return Err("USB volume range is invalid".to_string());
        }
        Ok(range)
    }

    fn floor(self, value: i32) -> i16 {
        let value = value.clamp(self.minimum as i32, self.maximum as i32);
        let step = i32::from(self.step).max(1);
        (self.minimum as i32 + (value - self.minimum as i32) / step * step) as i16
    }
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct VolumeSnapshot {
    pub min_db: f64,
    pub max_db: f64,
    pub current_db: f64,
}

impl From<VolumeSnapshot> for DacVolumeDto {
    fn from(snapshot: VolumeSnapshot) -> Self {
        Self {
            available: true,
            min_db: snapshot.min_db,
            max_db: snapshot.max_db,
            current_db: snapshot.current_db,
        }
    }
}

pub(crate) struct Volume {
    feature: FeatureUnit,
    ranges: Vec<Vec<VolumeRange>>,
    snapshot: VolumeSnapshot,
}

impl Volume {
    pub(crate) fn open(control: &impl Control, feature: FeatureUnit) -> Result<Self, String> {
        let index = (feature.unit_id as u16) << 8 | feature.interface_number as u16;
        let mut ranges: Vec<Vec<VolumeRange>> = Vec::new();
        let mut current = i16::MIN;
        let mut startup: Vec<i32> = Vec::new();
        for channel in &feature.channels {
            let selector = (VOLUME_CONTROL_SELECTOR << 8) | *channel as u16;
            let mut data = [0u8; 2];
            control.transfer(
                true,
                if feature.uac2 {
                    REQUEST_UAC2_GET_CURRENT
                } else {
                    REQUEST_UAC1_GET_CURRENT
                },
                selector,
                index,
                &mut data,
            )?;
            let channel_current = i16::from_le_bytes(data);
            current = current.max(channel_current);
            startup.push(i32::from(channel_current).min(STARTUP_CEILING_DB_256));
            let channel_ranges = if feature.uac2 {
                let mut header = [0u8; 2];
                control.transfer(true, REQUEST_UAC2_GET_RANGE, selector, index, &mut header)?;
                let count = u16::from_le_bytes(header) as usize;
                if !(1..=16).contains(&count) {
                    return Err("USB volume range count is unsupported".to_string());
                }
                let mut data = vec![0u8; 2 + 6 * count];
                control.transfer(true, REQUEST_UAC2_GET_RANGE, selector, index, &mut data)?;
                if u16::from_le_bytes([data[0], data[1]]) as usize != count {
                    return Err("USB volume range changed while reading".to_string());
                }
                data[2..]
                    .chunks_exact(6)
                    .map(VolumeRange::parse)
                    .collect::<Result<Vec<_>, _>>()?
            } else {
                let mut data = [0u8; 6];
                for (request, chunk) in [
                    REQUEST_UAC1_GET_MINIMUM,
                    REQUEST_UAC1_GET_MAXIMUM,
                    REQUEST_UAC1_GET_RESOLUTION,
                ]
                .into_iter()
                .zip(data.chunks_exact_mut(2))
                {
                    control.transfer(true, request, selector, index, chunk)?;
                }
                vec![VolumeRange::parse(&data)?]
            };
            ranges.push(channel_ranges);
        }
        let min = ranges
            .iter()
            .map(|channel| channel.iter().map(|range| range.minimum).min().unwrap())
            .max()
            .unwrap();
        let max = ranges
            .iter()
            .map(|channel| channel.iter().map(|range| range.maximum).max().unwrap())
            .min()
            .unwrap()
            .min(0);
        if min >= max || i32::from(min) > STARTUP_CEILING_DB_256 {
            return Err("USB volume cannot provide the startup attenuation".to_string());
        }
        let mut volume = Self {
            feature,
            ranges,
            snapshot: VolumeSnapshot {
                min_db: f64::from(min) / 256.0,
                max_db: f64::from(max) / 256.0,
                current_db: f64::from(current) / 256.0,
            },
        };
        volume.set_targets(control, &startup)?;
        Ok(volume)
    }

    pub(crate) fn snapshot(&self) -> VolumeSnapshot {
        self.snapshot
    }

    pub(crate) fn set(
        &mut self,
        control: &impl Control,
        db: f64,
    ) -> Result<VolumeSnapshot, String> {
        if !db.is_finite() || db > 0.0 || db < -128.0 {
            return Err("USB volume must be between -128 dB and 0 dB".to_string());
        }
        let target = (db.min(self.snapshot.max_db) * 256.0).floor() as i32;
        self.set_targets(control, &vec![target; self.feature.channels.len()])
    }

    fn set_targets(
        &mut self,
        control: &impl Control,
        targets: &[i32],
    ) -> Result<VolumeSnapshot, String> {
        let index = (self.feature.unit_id as u16) << 8 | self.feature.interface_number as u16;
        let mut loudest = i16::MIN;
        for ((channel, ranges), target) in self
            .feature
            .channels
            .iter()
            .zip(&self.ranges)
            .zip(targets.iter().copied())
        {
            let value = if target == SILENCE_DB_256 {
                i16::MIN
            } else {
                ranges
                    .iter()
                    .filter(|range| i32::from(range.minimum) <= target)
                    .map(|range| range.floor(target))
                    .max()
                    .ok_or_else(|| "USB volume is below the supported range".to_string())?
            };
            let selector = (VOLUME_CONTROL_SELECTOR << 8) | *channel as u16;
            control.transfer(
                false,
                REQUEST_SET_CURRENT,
                selector,
                index,
                &mut value.to_le_bytes(),
            )?;
            let mut readback = [0u8; 2];
            control.transfer(
                true,
                if self.feature.uac2 {
                    REQUEST_UAC2_GET_CURRENT
                } else {
                    REQUEST_UAC1_GET_CURRENT
                },
                selector,
                index,
                &mut readback,
            )?;
            let actual = i16::from_le_bytes(readback);
            if actual != value {
                return Err("USB volume read-back does not match the set value".to_string());
            }
            loudest = loudest.max(actual);
        }
        self.snapshot.current_db = f64::from(loudest) / 256.0;
        Ok(self.snapshot)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::Cell;

    struct FakeControl {
        current: Cell<i16>,
        mismatch: bool,
    }

    impl Control for FakeControl {
        fn transfer(
            &self,
            input: bool,
            request: u8,
            _value: u16,
            _index: u16,
            data: &mut [u8],
        ) -> Result<(), String> {
            if !input {
                if !self.mismatch {
                    self.current.set(i16::from_le_bytes([data[0], data[1]]));
                }
            } else if request == REQUEST_UAC2_GET_RANGE {
                let range = [1u8, 0, 0, 0xa0, 0, 0, 0, 1];
                data.copy_from_slice(&range[..data.len()]);
            } else {
                data.copy_from_slice(&self.current.get().to_le_bytes());
            }
            Ok(())
        }
    }

    fn master_feature() -> FeatureUnit {
        FeatureUnit {
            unit_id: 2,
            interface_number: 0,
            uac2: true,
            channels: vec![0],
        }
    }

    #[test]
    fn startup_attenuates_to_minus_40_db_and_set_rounds_down() {
        let control = FakeControl {
            current: Cell::new(0),
            mismatch: false,
        };
        let mut volume = Volume::open(&control, master_feature()).unwrap();
        assert_eq!(volume.snapshot().current_db, -40.0);
        assert_eq!(volume.set(&control, -25.2).unwrap().current_db, -26.0);
    }

    #[test]
    fn startup_never_raises_a_quiet_device() {
        let control = FakeControl {
            current: Cell::new(-70 * 256),
            mismatch: false,
        };
        let volume = Volume::open(&control, master_feature()).unwrap();
        assert_eq!(volume.snapshot().current_db, -70.0);
    }

    #[test]
    fn read_back_mismatch_fails_the_open() {
        let control = FakeControl {
            current: Cell::new(0),
            mismatch: true,
        };
        assert!(Volume::open(&control, master_feature()).is_err());
    }

    #[test]
    fn silent_channel_is_preserved() {
        let control = FakeControl {
            current: Cell::new(i16::MIN),
            mismatch: false,
        };
        let volume = Volume::open(&control, master_feature()).unwrap();
        assert_eq!(volume.snapshot().current_db, -128.0);
        assert_eq!(control.current.get(), i16::MIN);
    }

    #[test]
    fn set_rejects_invalid_values() {
        let control = FakeControl {
            current: Cell::new(0),
            mismatch: false,
        };
        let mut volume = Volume::open(&control, master_feature()).unwrap();
        assert!(volume.set(&control, f64::NAN).is_err());
        assert!(volume.set(&control, 2.0).is_err());
        assert!(volume.set(&control, -200.0).is_err());
    }

    #[test]
    fn topology_finds_the_playback_feature_unit() {
        let raw: &[u8] = &[
            9, 2, 74, 0, 1, 1, 0, 0, 0,
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            9, 0x24, 1, 0, 1, 0, 30, 0, 0,
            17, 0x24, 2, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            18, 0x24, 6, 2, 1, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            12, 0x24, 3, 3, 1, 3, 0, 2, 1, 0, 0, 0,
        ];
        let feature = find_feature_unit(raw, 0, 1, 2).unwrap();
        assert_eq!(feature.unit_id, 2);
        assert_eq!(feature.channels, vec![0]);
        assert!(find_feature_unit(raw, 0, 9, 2).is_none());
        assert!(find_feature_unit(raw, 1, 1, 2).is_none());
    }
}
