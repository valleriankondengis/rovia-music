use crate::{UsbAudioFormatInfo, UsbEndpointInfo, UsbInterfaceInfo, UsbSampleRateRange};
use std::collections::{HashMap, HashSet};

#[allow(dead_code)]
pub(crate) struct ClockEntityDescriptor {
    pub interface_number: u8,
    pub entity_id: u8,
    pub entity_type: String,
    pub attributes: u8,
    pub controls: u8,
    pub associated_terminal: u8,
    pub source_ids: Vec<u8>,
}

struct AudioEntityDescriptor {
    interface_number: u8,
    entity_id: u8,
    entity_type: String,
    terminal_type: u16,
    source_ids: Vec<u8>,
    clock_source_id: u8,
    channels: u8,
    channel_configuration: u32,
}

struct ConfigurationState {
    expected_length: usize,
    observed_length: usize,
    expected_interfaces: usize,
    interface_numbers: HashSet<u8>,
}

struct InterfaceAssociation {
    first_interface: u8,
    interface_count: u8,
    function_class: u8,
    function_subclass: u8,
}

#[allow(dead_code)]
pub(crate) struct ParsedDescriptors {
    pub vendor_id: u16,
    pub product_id: u16,
    pub usb_version: String,
    pub device_revision: String,
    pub device_class: u8,
    pub device_subclass: u8,
    pub device_protocol: u8,
    pub device_class_name: String,
    pub maximum_control_packet_size: u8,
    pub configuration_count: u8,
    pub unique_interface_count: u8,
    pub audio_control_interface_count: u8,
    pub audio_streaming_interface_count: u8,
    pub interfaces: Vec<UsbInterfaceInfo>,
    pub endpoints: Vec<UsbEndpointInfo>,
    pub audio_formats: Vec<UsbAudioFormatInfo>,
    pub clock_entities: Vec<ClockEntityDescriptor>,
    pub audio_protocols: Vec<String>,
    pub warnings: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(crate) struct ClockControlTarget {
    pub control_interface: u8,
    pub streaming_interface: u8,
    pub clock_source_id: u8,
    pub clock_controls: u8,
}

#[derive(Clone)]
pub(crate) struct PcmOutputTarget {
    pub control_interface: u8,
    pub streaming_interface: u8,
    pub alternate_setting: u8,
    pub clock_source_id: u8,
    pub clock_controls: u8,
    pub channels: u8,
    pub bit_resolution: u8,
    pub subslot_size: u8,
    pub data_endpoint_address: u8,
    pub feedback_endpoint_address: u8,
    pub maximum_packet_size: u32,
    pub feedback_maximum_packet_size: u32,
    pub interval: u8,
    pub synchronization_type: String,
}

pub(crate) fn resolve_clock_control_target(
    parsed: &ParsedDescriptors,
) -> Result<Option<ClockControlTarget>, String> {
    let mut targets = HashSet::new();

    for format in parsed.audio_formats.iter().filter(|format| {
        format.uac_version == "UAC2" && format.clock_source_id > 0
    }) {
        let Some(clock) = parsed.clock_entities.iter().find(|clock| {
            clock.entity_type == "Clock Source"
                && clock.entity_id == format.clock_source_id
        }) else {
            continue;
        };

        let has_control_interface = parsed.interfaces.iter().any(|interface| {
            interface.interface_number == clock.interface_number
                && interface.interface_class == 0x01
                && interface.interface_subclass == 0x01
                && interface.interface_protocol == 0x20
        });
        let has_streaming_interface = parsed.interfaces.iter().any(|interface| {
            interface.interface_number == format.interface_number
                && interface.interface_class == 0x01
                && interface.interface_subclass == 0x02
                && interface.interface_protocol == 0x20
        });

        if has_control_interface && has_streaming_interface {
            targets.insert(ClockControlTarget {
                control_interface: clock.interface_number,
                streaming_interface: format.interface_number,
                clock_source_id: clock.entity_id,
                clock_controls: clock.controls,
            });
        }
    }

    match targets.len() {
        0 => Ok(None),
        1 => Ok(targets.into_iter().next()),
        _ => Err("Multiple UAC2 AudioStreaming clock paths are available; a playback format must select one before interface claiming".to_string()),
    }
}

pub(crate) fn resolve_pcm_output_target(
    audio_formats: &[UsbAudioFormatInfo],
    endpoints: &[UsbEndpointInfo],
    clock_target: ClockControlTarget,
    channels: u8,
    bit_depth: u8,
) -> Result<PcmOutputTarget, String> {
    if !(1..=2).contains(&channels) {
        return Err(format!("Unsupported PCM channel count: {channels}"));
    }
    if !matches!(bit_depth, 16 | 24 | 32) {
        return Err(format!("Unsupported PCM source bit depth: {bit_depth}"));
    }

    let mut candidates = audio_formats
        .iter()
        .filter(|format| {
            format.uac_version == "UAC2"
                && format.format_type == 1
                && format.pcm_supported
                && format.interface_number == clock_target.streaming_interface
                && format.clock_source_id == clock_target.clock_source_id
                && format.channels == channels
                && (2..=4).contains(&format.subslot_size)
                && format.bit_resolution >= bit_depth
                && format.bit_resolution <= format.subslot_size.saturating_mul(8)
        })
        .collect::<Vec<_>>();

    candidates.sort_by_key(|format| (format.subslot_size, format.alternate_setting));

    let mut last_failure = "No compatible UAC2 PCM alternate setting was found".to_string();

    for format in candidates {
        let data_endpoint = endpoints.iter().find(|endpoint| {
            endpoint.interface_number == format.interface_number
                && endpoint.alternate_setting == format.alternate_setting
                && endpoint.direction == "OUT"
                && endpoint.transfer_type == "Isochronous"
                && endpoint.usage_type == "Data"
        });
        let Some(data_endpoint) = data_endpoint else {
            last_failure = format!(
                "PCM interface {} alternate {} has no isochronous OUT data endpoint",
                format.interface_number, format.alternate_setting
            );
            continue;
        };

        if !(1..=16).contains(&data_endpoint.interval)
            || data_endpoint.maximum_packet_size == 0
            || data_endpoint.maximum_packet_size > 3072
        {
            last_failure = format!(
                "PCM interface {} alternate {} has an unsupported endpoint interval or packet size",
                format.interface_number, format.alternate_setting
            );
            continue;
        }

        let feedback_endpoint = endpoints.iter().find(|endpoint| {
            endpoint.interface_number == format.interface_number
                && endpoint.alternate_setting == format.alternate_setting
                && endpoint.direction == "IN"
                && endpoint.transfer_type == "Isochronous"
                && (endpoint.address == data_endpoint.synchronization_address
                    || endpoint.usage_type == "Feedback")
        });

        if data_endpoint.synchronization_type == "Asynchronous" && feedback_endpoint.is_none() {
            last_failure = format!(
                "Asynchronous PCM interface {} alternate {} has no explicit feedback endpoint",
                format.interface_number, format.alternate_setting
            );
            continue;
        }

        if let Some(feedback) = feedback_endpoint {
            if !(3..=64).contains(&feedback.maximum_packet_size) {
                last_failure = format!(
                    "Feedback endpoint 0x{:02X} on interface {} alternate {} has an invalid packet size",
                    feedback.address, format.interface_number, format.alternate_setting
                );
                continue;
            }
        }

        return Ok(PcmOutputTarget {
            control_interface: clock_target.control_interface,
            streaming_interface: format.interface_number,
            alternate_setting: format.alternate_setting,
            clock_source_id: clock_target.clock_source_id,
            clock_controls: clock_target.clock_controls,
            channels: format.channels,
            bit_resolution: format.bit_resolution,
            subslot_size: format.subslot_size,
            data_endpoint_address: data_endpoint.address,
            feedback_endpoint_address: feedback_endpoint.map(|endpoint| endpoint.address).unwrap_or(0),
            maximum_packet_size: data_endpoint.maximum_packet_size,
            feedback_maximum_packet_size: feedback_endpoint
                .map(|endpoint| endpoint.maximum_packet_size)
                .unwrap_or(0),
            interval: data_endpoint.interval,
            synchronization_type: data_endpoint.synchronization_type.clone(),
        });
    }

    Err(last_failure)
}

fn read_u16(data: &[u8], offset: usize) -> Option<u16> {
    Some(u16::from_le_bytes([*data.get(offset)?, *data.get(offset + 1)?]))
}

fn read_u24(data: &[u8], offset: usize) -> Option<u32> {
    Some(
        u32::from(*data.get(offset)?)
            | (u32::from(*data.get(offset + 1)?) << 8)
            | (u32::from(*data.get(offset + 2)?) << 16),
    )
}

fn read_u32(data: &[u8], offset: usize) -> Option<u32> {
    Some(u32::from_le_bytes([
        *data.get(offset)?,
        *data.get(offset + 1)?,
        *data.get(offset + 2)?,
        *data.get(offset + 3)?,
    ]))
}

fn bcd_string(value: u16) -> String {
    let high = (value >> 8) as u8;
    let low = value as u8;
    let major = (high >> 4) * 10 + (high & 0x0F);
    let minor = (low >> 4) * 10 + (low & 0x0F);
    format!("{major}.{minor:02}")
}

fn device_class_name(class: u8) -> String {
    match class {
        0x00 => "Defined per interface".to_string(),
        0x01 => "Audio".to_string(),
        0x02 => "Communications".to_string(),
        0x03 => "Human Interface Device".to_string(),
        0x05 => "Physical".to_string(),
        0x06 => "Image".to_string(),
        0x07 => "Printer".to_string(),
        0x08 => "Mass Storage".to_string(),
        0x09 => "Hub".to_string(),
        0x0A => "CDC Data".to_string(),
        0x0B => "Smart Card".to_string(),
        0x0D => "Content Security".to_string(),
        0x0E => "Video".to_string(),
        0x0F => "Personal Healthcare".to_string(),
        0x10 => "Audio/Video".to_string(),
        0x11 => "Billboard".to_string(),
        0xDC => "Diagnostic".to_string(),
        0xE0 => "Wireless Controller".to_string(),
        0xEF => "Miscellaneous".to_string(),
        0xFE => "Application Specific".to_string(),
        0xFF => "Vendor Specific".to_string(),
        _ => format!("Class 0x{class:02X}"),
    }
}

fn interface_class_name(class: u8) -> String {
    if class == 0x01 {
        return "Audio".to_string();
    }
    device_class_name(class)
}

fn interface_subclass_name(class: u8, subclass: u8) -> String {
    if class == 0x01 {
        return match subclass {
            0x01 => "Audio Control".to_string(),
            0x02 => "Audio Streaming".to_string(),
            0x03 => "MIDI Streaming".to_string(),
            _ => format!("Audio subclass 0x{subclass:02X}"),
        };
    }
    format!("Subclass 0x{subclass:02X}")
}

fn protocol_name(class: u8, subclass: u8, protocol: u8) -> String {
    if class == 0x01 {
        return match protocol {
            0x00 => "UAC1".to_string(),
            0x20 => "UAC2".to_string(),
            0x30 => "UAC3".to_string(),
            _ => format!("Audio protocol 0x{protocol:02X}"),
        };
    }
    let _ = subclass;
    format!("Protocol 0x{protocol:02X}")
}

fn transfer_type(attributes: u8) -> String {
    match attributes & 0x03 {
        0 => "Control".to_string(),
        1 => "Isochronous".to_string(),
        2 => "Bulk".to_string(),
        _ => "Interrupt".to_string(),
    }
}

fn synchronization_type(attributes: u8) -> String {
    if attributes & 0x03 != 1 {
        return "Not applicable".to_string();
    }
    match (attributes >> 2) & 0x03 {
        0 => "None".to_string(),
        1 => "Asynchronous".to_string(),
        2 => "Adaptive".to_string(),
        _ => "Synchronous".to_string(),
    }
}

fn usage_type(attributes: u8) -> String {
    if attributes & 0x03 != 1 {
        return "Not applicable".to_string();
    }
    match (attributes >> 4) & 0x03 {
        0 => "Data".to_string(),
        1 => "Feedback".to_string(),
        2 => "Implicit feedback".to_string(),
        _ => "Reserved".to_string(),
    }
}

fn terminal_type_name(terminal_type: u16) -> String {
    match terminal_type {
        0x0101 => "USB Streaming".to_string(),
        0x0201 => "Microphone".to_string(),
        0x0202 => "Desktop Microphone".to_string(),
        0x0301 => "Speaker".to_string(),
        0x0302 => "Headphones".to_string(),
        0x0303 => "Head Mounted Display Audio".to_string(),
        0x0402 => "Headset".to_string(),
        _ => format!("Terminal 0x{terminal_type:04X}"),
    }
}

fn format_tag_name(format_tag: u16) -> String {
    match format_tag {
        0x0001 => "PCM".to_string(),
        0x0002 => "PCM8".to_string(),
        0x0003 => "IEEE Float".to_string(),
        0x0004 => "ALaw".to_string(),
        0x0005 => "MuLaw".to_string(),
        _ => format!("Format tag 0x{format_tag:04X}"),
    }
}

fn add_rate(format: &mut UsbAudioFormatInfo, rate: u32) {
    if rate > 0 && !format.sample_rates.contains(&rate) {
        format.sample_rates.push(rate);
    }
}

fn push_audio_entity(
    entities: &mut Vec<AudioEntityDescriptor>,
    interface_number: u8,
    entity_id: u8,
    entity_type: &str,
    terminal_type: u16,
    source_ids: Vec<u8>,
    clock_source_id: u8,
    channels: u8,
    channel_configuration: u32,
) {
    if entity_id == 0 {
        return;
    }
    entities.push(AudioEntityDescriptor {
        interface_number,
        entity_id,
        entity_type: entity_type.to_string(),
        terminal_type,
        source_ids: source_ids.into_iter().filter(|id| *id > 0).collect(),
        clock_source_id,
        channels,
        channel_configuration,
    });
}

fn finish_configuration(configuration: ConfigurationState, warnings: &mut Vec<String>) {
    if configuration.expected_length != configuration.observed_length {
        warnings.push(format!(
            "Configuration descriptor declares {} bytes but {} bytes were parsed for that configuration",
            configuration.expected_length, configuration.observed_length
        ));
    }
    if configuration.expected_interfaces != configuration.interface_numbers.len() {
        warnings.push(format!(
            "Configuration descriptor declares {} interfaces but {} unique interface numbers were parsed",
            configuration.expected_interfaces,
            configuration.interface_numbers.len()
        ));
    }
}

fn reference_graph_has_cycle(
    key: (u8, u8),
    audio_entities: &HashMap<(u8, u8), &AudioEntityDescriptor>,
    clock_entities: &HashMap<(u8, u8), &ClockEntityDescriptor>,
    visiting: &mut HashSet<(u8, u8)>,
    completed: &mut HashSet<(u8, u8)>,
) -> bool {
    if completed.contains(&key) {
        return false;
    }
    if !visiting.insert(key) {
        return true;
    }

    let mut references = Vec::new();
    if let Some(entity) = audio_entities.get(&key) {
        references.extend(entity.source_ids.iter().map(|entity_id| (key.0, *entity_id)));
        if entity.clock_source_id > 0 {
            references.push((key.0, entity.clock_source_id));
        }
    }
    if let Some(entity) = clock_entities.get(&key) {
        references.extend(entity.source_ids.iter().map(|entity_id| (key.0, *entity_id)));
    }

    for reference in references {
        if audio_entities.contains_key(&reference) || clock_entities.contains_key(&reference) {
            if reference_graph_has_cycle(
                reference,
                audio_entities,
                clock_entities,
                visiting,
                completed,
            ) {
                return true;
            }
        }
    }

    visiting.remove(&key);
    completed.insert(key);
    false
}

fn resolve_clock_sources(
    interface_number: u8,
    entity_id: u8,
    audio_entities: &HashMap<(u8, u8), &AudioEntityDescriptor>,
    clock_entities: &HashMap<(u8, u8), &ClockEntityDescriptor>,
    visited: &mut HashSet<(u8, u8)>,
    resolved: &mut HashSet<u8>,
) {
    if entity_id == 0 || !visited.insert((interface_number, entity_id)) {
        return;
    }

    if let Some(clock) = clock_entities.get(&(interface_number, entity_id)) {
        if clock.entity_type == "Clock Source" {
            resolved.insert(clock.entity_id);
        } else {
            for source_id in &clock.source_ids {
                resolve_clock_sources(
                    interface_number,
                    *source_id,
                    audio_entities,
                    clock_entities,
                    visited,
                    resolved,
                );
            }
        }
    }

    if let Some(entity) = audio_entities.get(&(interface_number, entity_id)) {
        if entity.clock_source_id > 0 {
            resolve_clock_sources(
                interface_number,
                entity.clock_source_id,
                audio_entities,
                clock_entities,
                visited,
                resolved,
            );
        }
        for source_id in &entity.source_ids {
            resolve_clock_sources(
                interface_number,
                *source_id,
                audio_entities,
                clock_entities,
                visited,
                resolved,
            );
        }
    }
}

fn resolve_control_interface(
    streaming_interface: u8,
    control_interfaces: &HashSet<u8>,
    interface_associations: &[InterfaceAssociation],
    streaming_associations: &HashMap<u8, u8>,
) -> Result<u8, String> {
    if let Some(control_interface) = streaming_associations.get(&streaming_interface) {
        if control_interfaces.contains(control_interface) {
            return Ok(*control_interface);
        }
    }

    let mut associated = HashSet::new();
    for association in interface_associations {
        let end = u16::from(association.first_interface)
            + u16::from(association.interface_count);
        if association.function_class == 0x01
            && (association.function_subclass == 0x01 || association.function_subclass == 0x00)
            && u16::from(streaming_interface) >= u16::from(association.first_interface)
            && u16::from(streaming_interface) < end
        {
            for control in control_interfaces {
                if *control >= association.first_interface && u16::from(*control) < end {
                    associated.insert(*control);
                }
            }
        }
    }

    if associated.len() == 1 {
        return Ok(*associated.iter().next().unwrap_or(&0));
    }
    if associated.len() > 1 {
        return Err(format!(
            "AudioStreaming interface {streaming_interface} belongs to an association with multiple AudioControl interfaces"
        ));
    }

    if control_interfaces.len() == 1 {
        return Ok(*control_interfaces.iter().next().unwrap_or(&0));
    }

    Err(format!(
        "AudioStreaming interface {streaming_interface} cannot be associated with one AudioControl interface"
    ))
}

fn validate_topology(
    interfaces: &[UsbInterfaceInfo],
    endpoints: &[UsbEndpointInfo],
    audio_formats: &mut [UsbAudioFormatInfo],
    audio_entities: &[AudioEntityDescriptor],
    clock_entities: &[ClockEntityDescriptor],
    control_interfaces: &HashSet<u8>,
    interface_associations: &[InterfaceAssociation],
    streaming_associations: &HashMap<u8, u8>,
    warnings: &mut Vec<String>,
) {
    let mut audio_entity_map = HashMap::new();
    let mut clock_entity_map = HashMap::new();
    let mut entity_ids = HashSet::new();

    for entity in audio_entities {
        let key = (entity.interface_number, entity.entity_id);
        if !entity_ids.insert(key) {
            warnings.push(format!(
                "Duplicate AudioControl entity ID {} on interface {}",
                entity.entity_id, entity.interface_number
            ));
        }
        audio_entity_map.insert(key, entity);
    }

    for entity in clock_entities {
        let key = (entity.interface_number, entity.entity_id);
        if !entity_ids.insert(key) {
            warnings.push(format!(
                "Duplicate AudioControl entity ID {} on interface {}",
                entity.entity_id, entity.interface_number
            ));
        }
        clock_entity_map.insert(key, entity);
    }

    for entity in audio_entities {
        for source_id in &entity.source_ids {
            if !audio_entity_map.contains_key(&(entity.interface_number, *source_id))
                && !clock_entity_map.contains_key(&(entity.interface_number, *source_id))
            {
                warnings.push(format!(
                    "AudioControl interface {} entity {} references missing source entity {}",
                    entity.interface_number, entity.entity_id, source_id
                ));
            }
        }
        if entity.clock_source_id > 0
            && !clock_entity_map.contains_key(&(entity.interface_number, entity.clock_source_id))
        {
            warnings.push(format!(
                "AudioControl interface {} entity {} references missing clock source {}",
                entity.interface_number, entity.entity_id, entity.clock_source_id
            ));
        }
        let terminal = if entity.terminal_type > 0 {
            format!(" · {}", terminal_type_name(entity.terminal_type))
        } else {
            String::new()
        };
        let sources = if entity.source_ids.is_empty() {
            String::new()
        } else {
            format!(
                " · source {}",
                entity.source_ids.iter().map(u8::to_string).collect::<Vec<_>>().join(", ")
            )
        };
        let clock = if entity.clock_source_id > 0 {
            format!(" · clock {}", entity.clock_source_id)
        } else {
            String::new()
        };
        let channels = if entity.channels > 0 {
            format!(" · {} channel(s) · channel map 0x{:08X}", entity.channels, entity.channel_configuration)
        } else {
            String::new()
        };
        warnings.push(format!(
            "[TOPOLOGY] AudioControl interface {} entity {}: {}{}{}{}{}",
            entity.interface_number,
            entity.entity_id,
            entity.entity_type,
            terminal,
            sources,
            clock,
            channels
        ));
    }

    for clock in clock_entities {
        let sources = if clock.source_ids.is_empty() {
            String::new()
        } else {
            format!(
                " · source {}",
                clock.source_ids.iter().map(u8::to_string).collect::<Vec<_>>().join(", ")
            )
        };
        warnings.push(format!(
            "[TOPOLOGY] AudioControl interface {} entity {}: {} · controls 0x{:02X}{}",
            clock.interface_number, clock.entity_id, clock.entity_type, clock.controls, sources
        ));
        for source_id in &clock.source_ids {
            if !clock_entity_map.contains_key(&(clock.interface_number, *source_id)) {
                warnings.push(format!(
                    "Clock entity {} on AudioControl interface {} references missing clock entity {}",
                    clock.entity_id, clock.interface_number, source_id
                ));
            }
        }
        if clock.associated_terminal > 0
            && !audio_entity_map.contains_key(&(clock.interface_number, clock.associated_terminal))
        {
            warnings.push(format!(
                "Clock Source {} on AudioControl interface {} references missing associated terminal {}",
                clock.entity_id, clock.interface_number, clock.associated_terminal
            ));
        }
    }

    let mut cycle_interfaces = HashSet::new();
    let mut completed_entities = HashSet::new();
    for key in audio_entity_map.keys().chain(clock_entity_map.keys()) {
        let mut visiting_entities = HashSet::new();
        if reference_graph_has_cycle(
            *key,
            &audio_entity_map,
            &clock_entity_map,
            &mut visiting_entities,
            &mut completed_entities,
        ) {
            cycle_interfaces.insert(key.0);
        }
    }
    for interface_number in cycle_interfaces {
        warnings.push(format!(
            "AudioControl interface {interface_number} contains a cyclic entity or clock reference graph"
        ));
    }

    for interface in interfaces {
        if interface.interface_class == 0x01
            && interface.interface_subclass == 0x02
            && interface.interface_protocol == 0x30
        {
            warnings.push(format!(
                "AudioStreaming interface {} uses UAC3; Phase 2 topology decoding currently targets UAC1 and UAC2",
                interface.interface_number
            ));
        }
    }

    for format in audio_formats.iter_mut() {
        if format.terminal_link == 0 {
            warnings.push(format!(
                "AudioStreaming interface {} alternate {} has no terminal link in its AudioStreaming General descriptor",
                format.interface_number, format.alternate_setting
            ));
            continue;
        }

        let control_interface = match resolve_control_interface(
            format.interface_number,
            control_interfaces,
            interface_associations,
            streaming_associations,
        ) {
            Ok(value) => value,
            Err(message) => {
                warnings.push(format!(
                    "AudioStreaming interface {} alternate {}: {message}",
                    format.interface_number, format.alternate_setting
                ));
                continue;
            }
        };

        let terminal = match audio_entity_map.get(&(control_interface, format.terminal_link)) {
            Some(value) => *value,
            None => {
                warnings.push(format!(
                    "AudioStreaming interface {} alternate {} terminal link {} is not present on AudioControl interface {}",
                    format.interface_number, format.alternate_setting, format.terminal_link, control_interface
                ));
                continue;
            }
        };

        let terminal_label = if terminal.terminal_type > 0 {
            format!("{} ({})", terminal.entity_type, terminal_type_name(terminal.terminal_type))
        } else {
            terminal.entity_type.clone()
        };
        warnings.push(format!(
            "[TOPOLOGY] AudioStreaming interface {} alternate {} terminal link {} resolves to AudioControl interface {} {}",
            format.interface_number,
            format.alternate_setting,
            format.terminal_link,
            control_interface,
            terminal_label
        ));
        if terminal.terminal_type != 0x0101 {
            warnings.push(format!(
                "AudioStreaming interface {} alternate {} links to terminal {} of type 0x{:04X}; the expected USB Streaming terminal type 0x0101 was not confirmed",
                format.interface_number,
                format.alternate_setting,
                format.terminal_link,
                terminal.terminal_type
            ));
        }

        if let Some(control) = interfaces.iter().find(|candidate| {
            candidate.interface_number == control_interface
                && candidate.interface_class == 0x01
                && candidate.interface_subclass == 0x01
        }) {
            let expected_protocol = match format.uac_version.as_str() {
                "UAC1" => Some(0x00),
                "UAC2" => Some(0x20),
                _ => None,
            };
            if let Some(expected_protocol) = expected_protocol {
                if control.interface_protocol != expected_protocol {
                    warnings.push(format!(
                        "AudioStreaming interface {} alternate {} protocol {} does not match AudioControl interface {} protocol {}",
                        format.interface_number,
                        format.alternate_setting,
                        format.uac_version,
                        control_interface,
                        control.protocol_name
                    ));
                }
            }
        }

        if format.uac_version == "UAC2" {
            let mut visited = HashSet::new();
            let mut resolved = HashSet::new();
            resolve_clock_sources(
                control_interface,
                format.terminal_link,
                &audio_entity_map,
                &clock_entity_map,
                &mut visited,
                &mut resolved,
            );
            if resolved.len() == 1 {
                format.clock_source_id = *resolved.iter().next().unwrap_or(&0);
                warnings.push(format!(
                    "[TOPOLOGY] AudioStreaming interface {} alternate {} resolves to clock source {}",
                    format.interface_number, format.alternate_setting, format.clock_source_id
                ));
            } else if resolved.is_empty() {
                warnings.push(format!(
                    "AudioStreaming interface {} alternate {} has no resolvable UAC2 clock source path",
                    format.interface_number, format.alternate_setting
                ));
            } else {
                let mut ids = resolved.into_iter().collect::<Vec<_>>();
                ids.sort_unstable();
                warnings.push(format!(
                    "AudioStreaming interface {} alternate {} has an ambiguous clock path through sources {}",
                    format.interface_number,
                    format.alternate_setting,
                    ids.iter().map(u8::to_string).collect::<Vec<_>>().join(", ")
                ));
            }
        }
    }

    for endpoint in endpoints {
        let belongs_to_audio_streaming = interfaces.iter().any(|interface| {
            interface.interface_number == endpoint.interface_number
                && interface.alternate_setting == endpoint.alternate_setting
                && interface.interface_class == 0x01
                && interface.interface_subclass == 0x02
        });
        if belongs_to_audio_streaming {
            let sync_address = if endpoint.synchronization_address == 0 {
                "none".to_string()
            } else {
                format!("0x{:02X}", endpoint.synchronization_address)
            };
            warnings.push(format!(
                "[TOPOLOGY] AudioStreaming interface {} alternate {} {} endpoint 0x{:02X}: {} · {} · {} · max packet {} bytes · interval {} · synchronization address {}",
                endpoint.interface_number,
                endpoint.alternate_setting,
                endpoint.direction,
                endpoint.address,
                endpoint.transfer_type,
                endpoint.synchronization_type,
                endpoint.usage_type,
                endpoint.maximum_packet_size,
                endpoint.interval,
                sync_address
            ));
        }
        if belongs_to_audio_streaming
            && endpoint.transfer_type == "Isochronous"
            && endpoint.direction == "OUT"
            && endpoint.synchronization_type == "Asynchronous"
            && endpoint.usage_type == "Data"
        {
            let feedback_endpoints = endpoints
                .iter()
                .filter(|candidate| {
                    candidate.interface_number == endpoint.interface_number
                        && candidate.alternate_setting == endpoint.alternate_setting
                        && candidate.transfer_type == "Isochronous"
                        && candidate.direction == "IN"
                        && candidate.usage_type == "Feedback"
                })
                .collect::<Vec<_>>();
            match feedback_endpoints.as_slice() {
                [feedback] => warnings.push(format!(
                    "[TOPOLOGY] AudioStreaming interface {} alternate {} asynchronous OUT endpoint 0x{:02X} uses explicit feedback endpoint 0x{:02X}",
                    endpoint.interface_number,
                    endpoint.alternate_setting,
                    endpoint.address,
                    feedback.address
                )),
                [] => warnings.push(format!(
                    "AudioStreaming interface {} alternate {} asynchronous OUT endpoint 0x{:02X} has no explicit feedback endpoint in the same alternate setting",
                    endpoint.interface_number, endpoint.alternate_setting, endpoint.address
                )),
                _ => warnings.push(format!(
                    "AudioStreaming interface {} alternate {} asynchronous OUT endpoint 0x{:02X} has multiple explicit feedback endpoints",
                    endpoint.interface_number, endpoint.alternate_setting, endpoint.address
                )),
            }
        }
        if endpoint.synchronization_address == 0 {
            continue;
        }
        let target = endpoints.iter().find(|candidate| {
            candidate.interface_number == endpoint.interface_number
                && candidate.alternate_setting == endpoint.alternate_setting
                && candidate.address == endpoint.synchronization_address
        });
        match target {
            Some(target) if target.usage_type == "Feedback" => {
                warnings.push(format!(
                    "[TOPOLOGY] Interface {} alternate {} endpoint 0x{:02X} references feedback endpoint 0x{:02X}",
                    endpoint.interface_number,
                    endpoint.alternate_setting,
                    endpoint.address,
                    target.address
                ));
            }
            Some(target) => warnings.push(format!(
                "Endpoint 0x{:02X} synchronization address 0x{:02X} resolves to a non-feedback endpoint ({})",
                endpoint.address, target.address, target.usage_type
            )),
            None => warnings.push(format!(
                "Endpoint 0x{:02X} on interface {} alternate {} references missing synchronization endpoint 0x{:02X}",
                endpoint.address,
                endpoint.interface_number,
                endpoint.alternate_setting,
                endpoint.synchronization_address
            )),
        }
    }

    for interface in interfaces {
        let actual_endpoint_count = endpoints
            .iter()
            .filter(|endpoint| {
                endpoint.interface_number == interface.interface_number
                    && endpoint.alternate_setting == interface.alternate_setting
            })
            .count();
        if actual_endpoint_count != usize::from(interface.endpoint_count) {
            warnings.push(format!(
                "Interface {} alternate {} declares {} endpoint(s) but {} endpoint descriptor(s) were parsed",
                interface.interface_number,
                interface.alternate_setting,
                interface.endpoint_count,
                actual_endpoint_count
            ));
        }
    }

    let mut endpoint_addresses = HashSet::new();
    for endpoint in endpoints {
        let key = (endpoint.interface_number, endpoint.alternate_setting, endpoint.address);
        if !endpoint_addresses.insert(key) {
            warnings.push(format!(
                "Duplicate endpoint address 0x{:02X} on interface {} alternate {}",
                endpoint.address, endpoint.interface_number, endpoint.alternate_setting
            ));
        }
        if endpoint.transfer_type == "Isochronous" && endpoint.maximum_packet_size == 0 {
            warnings.push(format!(
                "Isochronous endpoint 0x{:02X} on interface {} alternate {} has zero packet capacity",
                endpoint.address, endpoint.interface_number, endpoint.alternate_setting
            ));
        }
    }
}

pub(crate) fn parse(bytes: &[u8]) -> Result<ParsedDescriptors, String> {
    if bytes.is_empty() {
        return Err("Android returned an empty USB descriptor block".to_string());
    }

    let mut vendor_id = 0;
    let mut product_id = 0;
    let mut usb_version = String::new();
    let mut device_revision = String::new();
    let mut device_class = 0;
    let mut device_subclass = 0;
    let mut device_protocol = 0;
    let mut maximum_control_packet_size = 0;
    let mut configuration_count = 0u8;
    let mut configuration_descriptors_seen = 0usize;
    let mut current_configuration: Option<ConfigurationState> = None;
    let mut interfaces = Vec::new();
    let mut endpoints = Vec::new();
    let mut audio_formats: Vec<UsbAudioFormatInfo> = Vec::new();
    let mut audio_entities = Vec::new();
    let mut clock_entities = Vec::new();
    let mut audio_protocols = Vec::new();
    let mut warnings = Vec::new();
    let mut current_interface_index: Option<usize> = None;
    let mut current_endpoint_index: Option<usize> = None;
    let mut current_format_index: Option<usize> = None;
    let mut current_control_interface: Option<u8> = None;
    let mut current_control_protocol = 0u8;
    let mut format_general_seen = Vec::new();
    let mut format_type_seen = Vec::new();
    let mut control_interfaces = HashSet::new();
    let mut streaming_interfaces = HashSet::new();
    let mut unique_interfaces = HashSet::new();
    let mut interface_alternates = HashSet::new();
    let mut interface_associations = Vec::new();
    let mut streaming_associations = HashMap::new();
    let mut control_total_lengths: HashMap<u8, usize> = HashMap::new();
    let mut control_total_observed: HashMap<u8, usize> = HashMap::new();
    let mut control_headers = HashSet::new();
    let mut offset = 0usize;

    while offset < bytes.len() {
        let descriptor_offset = offset;
        let length = usize::from(*bytes.get(offset).ok_or("Truncated USB descriptor")?);
        if length < 2 || offset.checked_add(length).filter(|end| *end <= bytes.len()).is_none() {
            return Err(format!("Malformed USB descriptor at byte {offset}"));
        }
        let descriptor = &bytes[offset..offset + length];
        offset += length;
        let descriptor_type = descriptor[1];

        if descriptor_type == 0x02 {
            if let Some(configuration) = current_configuration.take() {
                finish_configuration(configuration, &mut warnings);
            }
            configuration_descriptors_seen += 1;
            if length < 9 {
                warnings.push(format!(
                    "Configuration descriptor at byte {descriptor_offset} is shorter than 9 bytes"
                ));
                current_configuration = None;
            } else {
                current_configuration = Some(ConfigurationState {
                    expected_length: usize::from(read_u16(descriptor, 2).unwrap_or_default()),
                    observed_length: length,
                    expected_interfaces: usize::from(descriptor[4]),
                    interface_numbers: HashSet::new(),
                });
            }
        } else if let Some(configuration) = current_configuration.as_mut() {
            configuration.observed_length = configuration.observed_length.saturating_add(length);
            if descriptor_type == 0x04 && length >= 9 {
                configuration.interface_numbers.insert(descriptor[2]);
            }
        }

        match descriptor_type {
            0x01 if length >= 18 => {
                usb_version = bcd_string(read_u16(descriptor, 2).unwrap_or_default());
                device_class = descriptor[4];
                device_subclass = descriptor[5];
                device_protocol = descriptor[6];
                maximum_control_packet_size = descriptor[7];
                vendor_id = read_u16(descriptor, 8).unwrap_or_default();
                product_id = read_u16(descriptor, 10).unwrap_or_default();
                device_revision = bcd_string(read_u16(descriptor, 12).unwrap_or_default());
                configuration_count = descriptor[17];
            }
            0x01 => warnings.push(format!(
                "Device descriptor at byte {descriptor_offset} is shorter than 18 bytes"
            )),
            0x0B if length >= 8 => {
                interface_associations.push(InterfaceAssociation {
                    first_interface: descriptor[2],
                    interface_count: descriptor[3],
                    function_class: descriptor[4],
                    function_subclass: descriptor[5],
                });
            }
            0x04 if length >= 9 => {
                let number = descriptor[2];
                let alternate = descriptor[3];
                let class = descriptor[5];
                let subclass = descriptor[6];
                let protocol = descriptor[7];
                unique_interfaces.insert(number);
                if !interface_alternates.insert((number, alternate)) {
                    warnings.push(format!(
                        "Duplicate interface descriptor for interface {number} alternate {alternate}"
                    ));
                }

                let is_control = class == 0x01 && subclass == 0x01;
                let is_streaming = class == 0x01 && subclass == 0x02;
                if is_control {
                    control_interfaces.insert(number);
                    current_control_interface = Some(number);
                    current_control_protocol = protocol;
                    let version = protocol_name(class, subclass, protocol);
                    if !audio_protocols.contains(&version) {
                        audio_protocols.push(version);
                    }
                    if !matches!(protocol, 0x00 | 0x20) {
                        warnings.push(format!(
                            "AudioControl interface {number} uses unsupported protocol 0x{protocol:02X} for Phase 2 topology parsing"
                        ));
                    }
                } else {
                    current_control_interface = None;
                    current_control_protocol = 0;
                }

                if is_streaming {
                    streaming_interfaces.insert(number);
                    let version = protocol_name(class, subclass, protocol);
                    if !audio_protocols.contains(&version) {
                        audio_protocols.push(version);
                    }
                    if !matches!(protocol, 0x00 | 0x20) {
                        warnings.push(format!(
                            "AudioStreaming interface {number} uses unsupported protocol 0x{protocol:02X} for Phase 2 topology parsing"
                        ));
                    }
                }

                interfaces.push(UsbInterfaceInfo {
                    interface_number: number,
                    alternate_setting: alternate,
                    interface_class: class,
                    interface_subclass: subclass,
                    interface_protocol: protocol,
                    class_name: interface_class_name(class),
                    subclass_name: interface_subclass_name(class, subclass),
                    protocol_name: protocol_name(class, subclass, protocol),
                    endpoint_count: descriptor[4],
                    string_index: descriptor[8],
                });
                current_interface_index = Some(interfaces.len() - 1);
                current_endpoint_index = None;
                current_format_index = None;

                if is_streaming && alternate > 0 {
                    audio_formats.push(UsbAudioFormatInfo {
                        interface_number: number,
                        alternate_setting: alternate,
                        uac_version: protocol_name(class, subclass, protocol),
                        terminal_link: 0,
                        format_type: 0,
                        format_tag: 0,
                        format_name: "Unknown".to_string(),
                        pcm_supported: false,
                        channels: 0,
                        channel_configuration: 0,
                        bit_resolution: 0,
                        subslot_size: 0,
                        clock_source_id: 0,
                        current_sample_rate: 0,
                        sample_rates: Vec::new(),
                        sample_rate_ranges: Vec::new(),
                    });
                    current_format_index = Some(audio_formats.len() - 1);
                    format_general_seen.push(false);
                    format_type_seen.push(false);
                }
            }
            0x04 => {
                current_interface_index = None;
                current_endpoint_index = None;
                current_format_index = None;
                current_control_interface = None;
                current_control_protocol = 0;
                warnings.push(format!(
                    "Interface descriptor at byte {descriptor_offset} is shorter than 9 bytes"
                ));
            }
            0x24 => {
                if length < 3 {
                    warnings.push(format!(
                        "Class-specific interface descriptor at byte {descriptor_offset} is shorter than 3 bytes"
                    ));
                    continue;
                }
                let subtype = descriptor[2];

                if let Some(control_interface) = current_control_interface {
                    *control_total_observed.entry(control_interface).or_insert(0) += length;
                    if subtype == 0x01 {
                        if current_control_protocol == 0x20 && length >= 9 {
                            let total = usize::from(read_u16(descriptor, 6).unwrap_or_default());
                            control_total_lengths.insert(control_interface, total);
                            control_total_observed.insert(control_interface, length);
                            control_headers.insert(control_interface);
                        } else if current_control_protocol == 0x00 && length >= 8 {
                            let total = usize::from(read_u16(descriptor, 5).unwrap_or_default());
                            control_total_lengths.insert(control_interface, total);
                            control_total_observed.insert(control_interface, length);
                            control_headers.insert(control_interface);
                            let count = usize::from(descriptor[7]);
                            if length >= 8 + count {
                                for streaming_interface in descriptor[8..8 + count].iter().copied() {
                                    if let Some(previous) = streaming_associations.insert(streaming_interface, control_interface) {
                                        if previous != control_interface {
                                            warnings.push(format!(
                                                "AudioStreaming interface {streaming_interface} is listed by multiple AudioControl interfaces: {previous} and {control_interface}"
                                            ));
                                        }
                                    }
                                }
                            } else {
                                warnings.push(format!(
                                    "UAC1 AudioControl header on interface {control_interface} truncates its streaming-interface collection"
                                ));
                            }
                        } else {
                            warnings.push(format!(
                                "AudioControl header on interface {control_interface} is shorter than required for its protocol"
                            ));
                        }
                    } else if current_control_protocol == 0x20 {
                        match subtype {
                            0x02 if length >= 17 => {
                                let entity_id = descriptor[3];
                                let terminal_type = read_u16(descriptor, 4).unwrap_or_default();
                                let clock_source_id = descriptor[7];
                                push_audio_entity(
                                    &mut audio_entities,
                                    control_interface,
                                    entity_id,
                                    "Input Terminal",
                                    terminal_type,
                                    Vec::new(),
                                    clock_source_id,
                                    descriptor[8],
                                    read_u32(descriptor, 9).unwrap_or_default(),
                                );
                            }
                            0x03 if length >= 12 => {
                                push_audio_entity(
                                    &mut audio_entities,
                                    control_interface,
                                    descriptor[3],
                                    "Output Terminal",
                                    read_u16(descriptor, 4).unwrap_or_default(),
                                    vec![descriptor[7]],
                                    descriptor[8],
                                    0,
                                    0,
                                );
                            }
                            0x04 | 0x05 if length >= 6 => {
                                let source_count = usize::from(descriptor[4]);
                                if length >= 5 + source_count {
                                    let entity_type = if subtype == 0x04 {
                                        "Mixer Unit"
                                    } else {
                                        "Selector Unit"
                                    };
                                    push_audio_entity(
                                        &mut audio_entities,
                                        control_interface,
                                        descriptor[3],
                                        entity_type,
                                        0,
                                        descriptor[5..5 + source_count].to_vec(),
                                        0,
                                        0,
                                        0,
                                    );
                                } else {
                                    warnings.push(format!(
                                        "AudioControl interface {control_interface} entity descriptor subtype 0x{subtype:02X} truncates its source list"
                                    ));
                                }
                            }
                            0x08 | 0x09 if length >= 8 => {
                                let source_count = usize::from(descriptor[6]);
                                if length >= 7 + source_count {
                                    let entity_type = if subtype == 0x08 {
                                        "Processing Unit"
                                    } else {
                                        "Extension Unit"
                                    };
                                    push_audio_entity(
                                        &mut audio_entities,
                                        control_interface,
                                        descriptor[3],
                                        entity_type,
                                        0,
                                        descriptor[7..7 + source_count].to_vec(),
                                        0,
                                        0,
                                        0,
                                    );
                                } else {
                                    warnings.push(format!(
                                        "AudioControl interface {} {} {} descriptor truncates its source list",
                                        control_interface,
                                        if subtype == 0x08 { "Processing Unit" } else { "Extension Unit" },
                                        descriptor[3]
                                    ));
                                }
                            }
                            0x06 if length >= 10 => {
                                push_audio_entity(
                                    &mut audio_entities,
                                    control_interface,
                                    descriptor[3],
                                    "Feature Unit",
                                    0,
                                    vec![descriptor[4]],
                                    0,
                                    0,
                                    0,
                                );
                            }
                            0x0A if length >= 8 => {
                                clock_entities.push(ClockEntityDescriptor {
                                    interface_number: control_interface,
                                    entity_id: descriptor[3],
                                    entity_type: "Clock Source".to_string(),
                                    attributes: descriptor[4],
                                    controls: descriptor[5],
                                    associated_terminal: descriptor[6],
                                    source_ids: Vec::new(),
                                });
                            }
                            0x0B if length >= 7 => {
                                let count = usize::from(descriptor[4]);
                                let required_length = 7 + count;
                                if length >= required_length {
                                    clock_entities.push(ClockEntityDescriptor {
                                        interface_number: control_interface,
                                        entity_id: descriptor[3],
                                        entity_type: "Clock Selector".to_string(),
                                        attributes: 0,
                                        controls: descriptor[5 + count],
                                        associated_terminal: 0,
                                        source_ids: descriptor[5..5 + count].to_vec(),
                                    });
                                } else {
                                    warnings.push(format!(
                                        "Clock Selector {} on AudioControl interface {control_interface} has a truncated source list",
                                        descriptor[3]
                                    ));
                                }
                            }
                            0x0C if length >= 7 => {
                                clock_entities.push(ClockEntityDescriptor {
                                    interface_number: control_interface,
                                    entity_id: descriptor[3],
                                    entity_type: "Clock Multiplier".to_string(),
                                    attributes: 0,
                                    controls: descriptor[5],
                                    associated_terminal: 0,
                                    source_ids: vec![descriptor[4]],
                                });
                            }
                            0x07 => warnings.push(format!(
                                "AudioControl interface {control_interface} contains a UAC2 Effect Unit descriptor that is not modelled in the Phase 2 entity graph"
                            )),
                            0x02 | 0x03 | 0x04 | 0x05 | 0x06 | 0x08 | 0x09 | 0x0A | 0x0B | 0x0C => {
                                warnings.push(format!(
                                    "AudioControl interface {control_interface} entity subtype 0x{subtype:02X} has an incomplete descriptor"
                                ));
                            }
                            _ => {}
                        }
                    } else if current_control_protocol == 0x00 {
                        match subtype {
                            0x02 if length >= 12 => {
                                push_audio_entity(
                                    &mut audio_entities,
                                    control_interface,
                                    descriptor[3],
                                    "Input Terminal",
                                    read_u16(descriptor, 4).unwrap_or_default(),
                                    Vec::new(),
                                    0,
                                    descriptor[7],
                                    u32::from(read_u16(descriptor, 8).unwrap_or_default()),
                                );
                            }
                            0x03 if length >= 9 => {
                                push_audio_entity(
                                    &mut audio_entities,
                                    control_interface,
                                    descriptor[3],
                                    "Output Terminal",
                                    read_u16(descriptor, 4).unwrap_or_default(),
                                    vec![descriptor[7]],
                                    0,
                                    0,
                                    0,
                                );
                            }
                            0x04 | 0x05 if length >= 6 => {
                                let source_count = usize::from(descriptor[4]);
                                if length >= 5 + source_count {
                                    let entity_type = if subtype == 0x04 {
                                        "Mixer Unit"
                                    } else {
                                        "Selector Unit"
                                    };
                                    push_audio_entity(
                                        &mut audio_entities,
                                        control_interface,
                                        descriptor[3],
                                        entity_type,
                                        0,
                                        descriptor[5..5 + source_count].to_vec(),
                                        0,
                                        0,
                                        0,
                                    );
                                } else {
                                    warnings.push(format!(
                                        "AudioControl interface {control_interface} entity descriptor subtype 0x{subtype:02X} truncates its source list"
                                    ));
                                }
                            }
                            0x07 | 0x08 if length >= 8 => {
                                let source_count = usize::from(descriptor[6]);
                                if length >= 7 + source_count {
                                    let entity_type = if subtype == 0x07 {
                                        "Processing Unit"
                                    } else {
                                        "Extension Unit"
                                    };
                                    push_audio_entity(
                                        &mut audio_entities,
                                        control_interface,
                                        descriptor[3],
                                        entity_type,
                                        0,
                                        descriptor[7..7 + source_count].to_vec(),
                                        0,
                                        0,
                                        0,
                                    );
                                } else {
                                    warnings.push(format!(
                                        "AudioControl interface {control_interface} entity descriptor subtype 0x{subtype:02X} truncates its source list"
                                    ));
                                }
                            }
                            0x06 if length >= 7 => {
                                push_audio_entity(
                                    &mut audio_entities,
                                    control_interface,
                                    descriptor[3],
                                    "Feature Unit",
                                    0,
                                    vec![descriptor[4]],
                                    0,
                                    0,
                                    0,
                                );
                            }
                            _ => {}
                        }
                    }
                }

                if let Some(index) = current_format_index {
                    let format = &mut audio_formats[index];
                    if subtype == 0x01 {
                        if format.uac_version == "UAC2" && length >= 16 {
                            format.terminal_link = descriptor[3];
                            format.format_type = descriptor[5];
                            let format_bitmap = read_u32(descriptor, 6).unwrap_or_default();
                            format.pcm_supported = format_bitmap == 0x0000_0001;
                            format.channels = descriptor[10];
                            format.channel_configuration = read_u32(descriptor, 11).unwrap_or_default();
                            format.format_name = match format_bitmap {
                                0x0000_0001 => "PCM · UAC2 format mask 0x00000001".to_string(),
                                0x8000_0000 => "Raw Data".to_string(),
                                _ => format!("UAC2 format mask 0x{format_bitmap:08X}"),
                            };
                            if format_bitmap.count_ones() > 1 {
                                warnings.push(format!(
                                    "AudioStreaming interface {} alternate {} declares multiple UAC2 format bits 0x{format_bitmap:08X}",
                                    format.interface_number, format.alternate_setting
                                ));
                            }
                            format_general_seen[index] = true;
                        } else if format.uac_version == "UAC1" && length >= 7 {
                            format.terminal_link = descriptor[3];
                            format.format_tag = read_u16(descriptor, 5).unwrap_or_default();
                            format.pcm_supported = format.format_tag == 0x0001;
                            format.format_name = format_tag_name(format.format_tag);
                            format_general_seen[index] = true;
                        } else {
                            warnings.push(format!(
                                "AudioStreaming General descriptor on interface {} alternate {} is incomplete",
                                format.interface_number, format.alternate_setting
                            ));
                        }
                    }
                    if subtype == 0x02 {
                        if length >= 6
                            && descriptor[3] == 0x01
                            && (format.uac_version == "UAC2" || length >= 8)
                        {
                            format.format_type = descriptor[3];
                            if format.uac_version == "UAC2" {
                                format.subslot_size = descriptor[4];
                                format.bit_resolution = descriptor[5];
                                if format.format_name == "Unknown" {
                                    format.format_name = "Type I".to_string();
                                }
                            } else if format.uac_version == "UAC1" && length >= 8 {
                                format.channels = descriptor[4];
                                format.subslot_size = descriptor[5];
                                format.bit_resolution = descriptor[6];
                                let rate_count = usize::from(descriptor[7]);
                                if rate_count == 0 {
                                    if length >= 14 {
                                        let minimum = read_u24(descriptor, 8).unwrap_or_default();
                                        let maximum = read_u24(descriptor, 11).unwrap_or_default();
                                        if minimum > 0 && maximum >= minimum {
                                            format.sample_rate_ranges.push(UsbSampleRateRange {
                                                minimum_rate: minimum,
                                                maximum_rate: maximum,
                                                resolution: 0,
                                            });
                                        } else {
                                            warnings.push(format!(
                                                "UAC1 Type I format descriptor on interface {} alternate {} contains an invalid continuous sample-rate range",
                                                format.interface_number, format.alternate_setting
                                            ));
                                        }
                                    } else {
                                        warnings.push(format!(
                                            "UAC1 Type I format descriptor on interface {} alternate {} truncates its continuous sample-rate range",
                                            format.interface_number, format.alternate_setting
                                        ));
                                    }
                                } else if length >= 8 + rate_count.saturating_mul(3) {
                                    for position in 0..rate_count {
                                        if let Some(rate) = read_u24(descriptor, 8 + position * 3) {
                                            add_rate(format, rate);
                                        }
                                    }
                                } else {
                                    warnings.push(format!(
                                        "UAC1 Type I format descriptor on interface {} alternate {} truncates its sample-rate list",
                                        format.interface_number, format.alternate_setting
                                    ));
                                }
                            }
                            if format.format_name == "Unknown" {
                                format.format_name = if format.pcm_supported {
                                    "PCM".to_string()
                                } else {
                                    "Type I".to_string()
                                };
                            }
                            format_type_seen[index] = true;
                        } else {
                            warnings.push(format!(
                                "AudioStreaming format descriptor on interface {} alternate {} is not a complete Type I descriptor",
                                format.interface_number, format.alternate_setting
                            ));
                        }
                    }
                }
            }
            0x05 if length >= 7 => {
                if let Some(interface_index) = current_interface_index {
                    let number = interfaces[interface_index].interface_number;
                    let alternate = interfaces[interface_index].alternate_setting;
                    let raw_packet = read_u16(descriptor, 4).unwrap_or_default();
                    let transfer_kind = descriptor[3] & 0x03;
                    let supports_high_bandwidth_transactions = matches!(transfer_kind, 1 | 3);
                    let transaction_code = ((raw_packet >> 11) & 0x03) as u8;
                    let transactions = if supports_high_bandwidth_transactions && transaction_code <= 2 {
                        transaction_code
                    } else {
                        0
                    };
                    if supports_high_bandwidth_transactions && transaction_code == 3 {
                        warnings.push(format!(
                            "Endpoint 0x{:02X} on interface {} alternate {} uses the reserved high-bandwidth transaction encoding",
                            descriptor[2], number, alternate
                        ));
                    }
                    if !supports_high_bandwidth_transactions && transaction_code != 0 {
                        warnings.push(format!(
                            "Endpoint 0x{:02X} on interface {} alternate {} sets reserved wMaxPacketSize transaction bits",
                            descriptor[2], number, alternate
                        ));
                    }
                    let transaction_multiplier = if supports_high_bandwidth_transactions {
                        u32::from(transactions) + 1
                    } else {
                        1
                    };
                    let maximum_packet_size = u32::from(raw_packet & 0x07FF)
                        .saturating_mul(transaction_multiplier);
                    let address = descriptor[2];
                    endpoints.push(UsbEndpointInfo {
                        interface_number: number,
                        alternate_setting: alternate,
                        address,
                        direction: if address & 0x80 != 0 { "IN".to_string() } else { "OUT".to_string() },
                        transfer_type: transfer_type(descriptor[3]),
                        synchronization_type: synchronization_type(descriptor[3]),
                        usage_type: usage_type(descriptor[3]),
                        maximum_packet_size,
                        sampling_frequency_control: false,
                        pitch_control: false,
                        maximum_packets_only: false,
                        interval: descriptor[6],
                        synchronization_address: if transfer_kind == 1 {
                            *descriptor.get(8).unwrap_or(&0)
                        } else {
                            0
                        },
                        refresh: if transfer_kind == 1 {
                            *descriptor.get(7).unwrap_or(&0)
                        } else {
                            0
                        },
                        maximum_burst: 0,
                        transactions_per_microframe: transactions,
                    });
                    current_endpoint_index = Some(endpoints.len() - 1);
                }
            }
            0x25 if length >= 4 && descriptor[2] == 0x01 => {
                if let Some(index) = current_endpoint_index {
                    if let Some(endpoint) = endpoints.get_mut(index) {
                        endpoint.sampling_frequency_control = descriptor[3] & 0x01 != 0;
                        endpoint.pitch_control = descriptor[3] & 0x02 != 0;
                        endpoint.maximum_packets_only = descriptor[3] & 0x04 != 0;
                    }
                }
            }
            0x30 if length >= 6 => {
                if let Some(index) = current_endpoint_index {
                    if let Some(endpoint) = endpoints.get_mut(index) {
                        endpoint.maximum_burst = descriptor[2];
                        let bytes_per_interval = read_u16(descriptor, 4).unwrap_or_default();
                        warnings.push(format!(
                            "[TOPOLOGY] SuperSpeed companion for endpoint 0x{:02X}: max burst {}, bytes per interval {}",
                            endpoint.address, endpoint.maximum_burst, bytes_per_interval
                        ));
                    }
                }
            }
            _ => {}
        }
    }

    if let Some(configuration) = current_configuration.take() {
        finish_configuration(configuration, &mut warnings);
    }

    if usb_version.is_empty() {
        warnings.push("The raw descriptor block did not contain a complete device descriptor".to_string());
    }
    if configuration_descriptors_seen == 0 {
        warnings.push("No configuration descriptor was found in the raw descriptor block".to_string());
    }
    if configuration_count > 0 && usize::from(configuration_count) != configuration_descriptors_seen {
        warnings.push(format!(
            "Device descriptor declares {configuration_count} configuration(s), but {configuration_descriptors_seen} configuration descriptor(s) were present in the raw block"
        ));
    }
    if interfaces.is_empty() {
        warnings.push("No interface descriptors were found in the raw descriptor block".to_string());
    }

    for interface_number in &control_interfaces {
        if !control_headers.contains(interface_number) {
            warnings.push(format!(
                "AudioControl interface {interface_number} has no complete AudioControl header descriptor"
            ));
        }
    }

    if configuration_descriptors_seen > 1 {
        warnings.push(format!(
            "The raw descriptor block contains {configuration_descriptors_seen} configurations; Phase 2 reports the combined descriptor set and does not resolve configuration-specific entity namespaces"
        ));
    }

    for (interface_number, expected) in &control_total_lengths {
        let observed = control_total_observed.get(interface_number).copied().unwrap_or_default();
        if *expected != observed {
            warnings.push(format!(
                "AudioControl interface {interface_number} header declares {expected} class-specific bytes but {observed} were parsed"
            ));
        }
    }

    for (index, format) in audio_formats.iter_mut().enumerate() {
        if !format_general_seen.get(index).copied().unwrap_or(false) {
            warnings.push(format!(
                "AudioStreaming interface {} alternate {} has no complete AudioStreaming General descriptor",
                format.interface_number, format.alternate_setting
            ));
        }
        if !format_type_seen.get(index).copied().unwrap_or(false) {
            warnings.push(format!(
                "AudioStreaming interface {} alternate {} has no complete Type I format descriptor",
                format.interface_number, format.alternate_setting
            ));
        }
        if format.format_type != 1 {
            warnings.push(format!(
                "AudioStreaming interface {} alternate {} uses unsupported or unresolved format type {}",
                format.interface_number, format.alternate_setting, format.format_type
            ));
        }
        if format.subslot_size == 0 || format.subslot_size > 4 {
            warnings.push(format!(
                "AudioStreaming interface {} alternate {} reports an unsupported Type I subslot size of {} byte(s)",
                format.interface_number, format.alternate_setting, format.subslot_size
            ));
        }
        if format.bit_resolution == 0
            || u16::from(format.bit_resolution) > u16::from(format.subslot_size).saturating_mul(8)
        {
            warnings.push(format!(
                "AudioStreaming interface {} alternate {} reports an invalid bit resolution {} for a {}-byte subslot",
                format.interface_number, format.alternate_setting, format.bit_resolution, format.subslot_size
            ));
        }
        format.sample_rates.sort_unstable();
        format.sample_rates.dedup();
        if format.uac_version == "UAC1"
            && format.sample_rates.is_empty()
            && format.sample_rate_ranges.is_empty()
        {
            warnings.push(format!(
                "Streaming interface {} alternate {} does not expose discrete or continuous rates in its format descriptor",
                format.interface_number, format.alternate_setting
            ));
        }
    }

    validate_topology(
        &interfaces,
        &endpoints,
        &mut audio_formats,
        &audio_entities,
        &clock_entities,
        &control_interfaces,
        &interface_associations,
        &streaming_associations,
        &mut warnings,
    );

    audio_protocols.sort();
    audio_protocols.dedup();

    Ok(ParsedDescriptors {
        vendor_id,
        product_id,
        usb_version,
        device_revision,
        device_class,
        device_subclass,
        device_protocol,
        device_class_name: device_class_name(device_class),
        maximum_control_packet_size,
        configuration_count,
        unique_interface_count: unique_interfaces.len().min(u8::MAX as usize) as u8,
        audio_control_interface_count: control_interfaces.len().min(u8::MAX as usize) as u8,
        audio_streaming_interface_count: streaming_interfaces.len().min(u8::MAX as usize) as u8,
        interfaces,
        endpoints,
        audio_formats,
        clock_entities,
        audio_protocols,
        warnings,
    })
}

#[cfg(test)]
mod tests {
    use super::{
        parse, resolve_clock_control_target, resolve_pcm_output_target, ClockControlTarget,
    };
    use crate::{UsbAudioFormatInfo, UsbEndpointInfo};

    fn uac1_topology_fixture() -> Vec<u8> {
        let mut raw = vec![
            18, 1, 0, 2, 0, 0, 0, 64, 0x34, 0x12, 0x78, 0x56, 0, 1, 1, 2, 3, 1,
            9, 2, 0, 0, 2, 1, 0, 0x80, 50,
            9, 4, 0, 0, 0, 1, 1, 0, 0,
            9, 0x24, 1, 0, 1, 0, 0, 1, 1,
            12, 0x24, 2, 1, 1, 1, 0, 2, 3, 0, 0, 0,
            9, 0x24, 3, 2, 2, 3, 0, 1, 0,
            9, 4, 1, 1, 1, 1, 2, 0, 0,
            7, 0x24, 1, 1, 1, 1, 0,
            14, 0x24, 2, 1, 2, 3, 24, 0, 0x44, 0xAC, 0, 0, 0x77, 1,
            7, 5, 1, 5, 8, 3, 1,
            7, 0x25, 1, 1, 0, 0, 0,
        ];
        let configuration_length = raw.len() - 18;
        raw[20] = configuration_length as u8;
        raw[21] = (configuration_length >> 8) as u8;
        let control_header = raw
            .windows(3)
            .position(|window| window == [9, 0x24, 1])
            .unwrap();
        raw[control_header + 5] = 30;
        raw[control_header + 6] = 0;
        raw
    }

    fn uac2_topology_fixture() -> Vec<u8> {
        let mut raw = vec![
            18, 1, 0, 2, 0, 0, 0, 64, 0x87, 0x2D, 0x3B, 0x00, 1, 0, 1, 2, 3, 1,
            9, 2, 0, 0, 2, 1, 0, 0x80, 50,
            9, 4, 1, 0, 0, 1, 1, 0x20, 0,
            9, 0x24, 1, 0, 2, 0, 46, 0, 0,
            8, 0x24, 0x0A, 10, 3, 1, 0, 0,
            17, 0x24, 0x02, 1, 0x01, 0x01, 0, 10, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            12, 0x24, 0x03, 3, 0x02, 0x03, 0, 1, 10, 0, 0, 0,
            9, 4, 2, 1, 2, 1, 2, 0x20, 0,
            16, 0x24, 0x01, 1, 0, 1, 1, 0, 0, 0, 2, 3, 0, 0, 0, 0,
            6, 0x24, 0x02, 1, 4, 32,
            7, 5, 0x01, 0x05, 0x08, 0x03, 1,
            8, 0x25, 0x01, 0, 0, 0, 0, 0,
            7, 5, 0x83, 0x11, 4, 0, 4,
        ];
        let configuration_length = raw.len() - 18;
        raw[20] = configuration_length as u8;
        raw[21] = (configuration_length >> 8) as u8;
        raw
    }

    #[test]
    fn rejects_zero_length_descriptor() {
        assert!(parse(&[0, 1]).is_err());
    }

    #[test]
    fn rejects_truncated_descriptor() {
        assert!(parse(&[9, 2, 0, 0]).is_err());
    }

    #[test]
    fn parses_device_identity_and_interfaces() {
        let raw = [
            18, 1, 0, 2, 0, 0, 0, 64, 0x34, 0x12, 0x78, 0x56, 0, 1, 1, 2, 3, 1,
            9, 2, 9, 0, 1, 1, 0, 0x80, 50,
            9, 4, 0, 0, 0, 1, 1, 0, 0,
            9, 4, 1, 0, 0, 1, 2, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0, 0,
        ];
        let result = parse(&raw).unwrap();
        assert_eq!(result.vendor_id, 0x1234);
        assert_eq!(result.product_id, 0x5678);
        assert_eq!(result.unique_interface_count, 2);
        assert_eq!(result.audio_control_interface_count, 1);
        assert_eq!(result.audio_streaming_interface_count, 1);
        assert_eq!(result.audio_protocols, vec!["UAC1"]);
    }

    #[test]
    fn resolves_uac1_usb_streaming_terminal_and_continuous_rates() {
        let result = parse(&uac1_topology_fixture()).unwrap();
        assert_eq!(result.vendor_id, 0x1234);
        assert_eq!(result.product_id, 0x5678);
        assert_eq!(result.audio_protocols, vec!["UAC1"]);
        assert_eq!(result.audio_formats.len(), 1);
        assert_eq!(result.audio_formats[0].terminal_link, 1);
        assert_eq!(result.audio_formats[0].format_tag, 1);
        assert!(result.audio_formats[0].pcm_supported);
        assert_eq!(result.audio_formats[0].channels, 2);
        assert_eq!(result.audio_formats[0].bit_resolution, 24);
        assert_eq!(result.audio_formats[0].sample_rate_ranges.len(), 1);
        assert_eq!(result.audio_formats[0].sample_rate_ranges[0].minimum_rate, 44_100);
        assert_eq!(result.audio_formats[0].sample_rate_ranges[0].maximum_rate, 96_000);
        assert!(result.warnings.iter().any(|entry| entry.contains("terminal link 1 resolves")));
    }

    #[test]
    fn resolves_uac1_discrete_sample_rates() {
        let mut raw = uac1_topology_fixture();
        let format_descriptor_offset = raw
            .windows(3)
            .position(|window| window == [14, 0x24, 0x02])
            .unwrap();
        raw[format_descriptor_offset + 7] = 2;
        raw[format_descriptor_offset + 11] = 0x80;
        raw[format_descriptor_offset + 12] = 0xBB;
        raw[format_descriptor_offset + 13] = 0x00;

        let result = parse(&raw).unwrap();
        assert_eq!(result.audio_formats[0].sample_rates, vec![44_100, 48_000]);
        assert!(result.audio_formats[0].sample_rate_ranges.is_empty());
    }

    #[test]
    fn resolves_uac2_terminal_clock_and_feedback_topology() {
        let result = parse(&uac2_topology_fixture()).unwrap();
        assert_eq!(result.vendor_id, 0x2D87);
        assert_eq!(result.product_id, 0x003B);
        assert_eq!(result.unique_interface_count, 2);
        assert_eq!(result.audio_control_interface_count, 1);
        assert_eq!(result.audio_streaming_interface_count, 1);
        assert_eq!(result.audio_formats.len(), 1);
        assert_eq!(result.audio_formats[0].terminal_link, 1);
        assert_eq!(result.audio_formats[0].clock_source_id, 10);
        assert_eq!(result.audio_formats[0].channels, 2);
        assert_eq!(result.audio_formats[0].subslot_size, 4);
        assert_eq!(result.audio_formats[0].bit_resolution, 32);
        assert!(result.audio_formats[0].format_name.contains("UAC2 format mask 0x00000001"));
        assert_eq!(result.endpoints.len(), 2);
        assert_eq!(result.endpoints[0].maximum_packet_size, 776);
        assert_eq!(result.endpoints[0].synchronization_address, 0);
        assert!(result.warnings.iter().any(|entry| entry.contains("terminal link 1 resolves")));
        assert!(result.warnings.iter().any(|entry| entry.contains("uses explicit feedback endpoint 0x83")));
        assert!(!result.warnings.iter().any(|entry| entry.contains("feedback endpoint 0x83") && entry.contains("no synchronization address")));
        assert!(result.warnings.iter().any(|entry| {
            entry.contains("OUT endpoint 0x01")
                && entry.contains("max packet 776 bytes")
                && entry.contains("synchronization address none")
        }));
    }

    #[test]
    fn resolves_uac2_clock_control_interface_pair() {
        let parsed = parse(&uac2_topology_fixture()).unwrap();
        let target = resolve_clock_control_target(&parsed).unwrap().unwrap();

        assert_eq!(target.control_interface, 1);
        assert_eq!(target.streaming_interface, 2);
        assert_eq!(target.clock_source_id, 10);
    }

    #[test]
    fn does_not_select_a_uac1_clock_control_target() {
        let parsed = parse(&uac1_topology_fixture()).unwrap();
        assert_eq!(resolve_clock_control_target(&parsed).unwrap(), None);
    }

    #[test]
    fn identifies_uac2_type_i_raw_data_format() {
        let mut raw = uac2_topology_fixture();
        let format_general_offset = raw
            .windows(3)
            .position(|window| window == [16, 0x24, 0x01])
            .unwrap();
        raw[format_general_offset + 6..format_general_offset + 10]
            .copy_from_slice(&0x8000_0000u32.to_le_bytes());

        let result = parse(&raw).unwrap();
        assert_eq!(result.audio_formats[0].format_name, "Raw Data");
        assert!(!result.audio_formats[0].pcm_supported);
    }

    #[test]
    fn resolves_uac2_clock_selector_and_multiplier_chain() {
        let mut raw = uac2_topology_fixture();
        let input_terminal_offset = raw
            .windows(3)
            .position(|window| window == [17, 0x24, 0x02])
            .unwrap();
        let clock_selector = [8, 0x24, 0x0B, 11, 1, 10, 1, 0];
        let clock_multiplier = [7, 0x24, 0x0C, 12, 11, 1, 0];
        raw.splice(
            input_terminal_offset..input_terminal_offset,
            clock_selector.into_iter().chain(clock_multiplier),
        );
        let updated_input_terminal_offset = raw
            .windows(3)
            .position(|window| window == [17, 0x24, 0x02])
            .unwrap();
        raw[updated_input_terminal_offset + 7] = 12;
        let output_terminal_offset = raw
            .windows(3)
            .position(|window| window == [12, 0x24, 0x03])
            .unwrap();
        raw[output_terminal_offset + 8] = 12;
        let control_header_offset = raw
            .windows(3)
            .position(|window| window == [9, 0x24, 0x01])
            .unwrap();
        let control_total = 61u16.to_le_bytes();
        raw[control_header_offset + 6] = control_total[0];
        raw[control_header_offset + 7] = control_total[1];
        let configuration_total = (u16::from_le_bytes([raw[20], raw[21]]) + 15).to_le_bytes();
        raw[20] = configuration_total[0];
        raw[21] = configuration_total[1];

        let result = parse(&raw).unwrap();
        assert_eq!(result.audio_formats[0].clock_source_id, 10);
        assert!(result.warnings.iter().any(|entry| entry.contains("Clock Selector")));
        assert!(result.warnings.iter().any(|entry| entry.contains("Clock Multiplier")));
        assert!(!result.warnings.iter().any(|entry| entry.contains("ambiguous clock path")));
    }

    #[test]
    fn parses_uac2_processing_unit_source_offset() {
        let mut raw = uac2_topology_fixture();
        let output_terminal_offset = raw
            .windows(3)
            .position(|window| window == [12, 0x24, 0x03])
            .unwrap();
        let processing_unit = [
            17, 0x24, 0x08, 4, 1, 0, 1, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0,
        ];
        raw.splice(output_terminal_offset..output_terminal_offset, processing_unit);
        let updated_output_terminal_offset = raw
            .windows(3)
            .position(|window| window == [12, 0x24, 0x03])
            .unwrap();
        raw[updated_output_terminal_offset + 7] = 4;
        let control_header_offset = raw
            .windows(3)
            .position(|window| window == [9, 0x24, 0x01])
            .unwrap();
        let control_total = 63u16.to_le_bytes();
        raw[control_header_offset + 6] = control_total[0];
        raw[control_header_offset + 7] = control_total[1];
        let configuration_total = (u16::from_le_bytes([raw[20], raw[21]]) + 17).to_le_bytes();
        raw[20] = configuration_total[0];
        raw[21] = configuration_total[1];

        let result = parse(&raw).unwrap();
        assert!(result.warnings.iter().any(|entry| {
            entry.contains("entity 4: Processing Unit · source 1")
        }));
        assert!(!result.warnings.iter().any(|entry| {
            entry.contains("Processing Unit") && entry.contains("missing source entity")
        }));
    }

    #[test]
    fn reports_missing_clock_source_associated_terminal() {
        let mut raw = uac2_topology_fixture();
        let clock_source_offset = raw
            .windows(3)
            .position(|window| window == [8, 0x24, 0x0A])
            .unwrap();
        raw[clock_source_offset + 6] = 99;

        let result = parse(&raw).unwrap();
        assert!(result.warnings.iter().any(|entry| {
            entry.contains("Clock Source 10") && entry.contains("missing associated terminal 99")
        }));
    }

    #[test]
    fn reports_cyclic_audio_entity_references() {
        let mut raw = uac2_topology_fixture();
        let output_terminal_offset = raw
            .windows(3)
            .position(|window| window == [12, 0x24, 0x03])
            .unwrap();
        raw[output_terminal_offset + 7] = 3;
        let result = parse(&raw).unwrap();
        assert!(result.warnings.iter().any(|entry| {
            entry.contains("contains a cyclic entity or clock reference graph")
        }));
    }

    #[test]
    fn reports_missing_audio_entity_references() {
        let mut raw = uac2_topology_fixture();
        let output_terminal_offset = raw
            .windows(3)
            .position(|window| window == [12, 0x24, 0x03])
            .unwrap();
        raw[output_terminal_offset + 7] = 99;
        let result = parse(&raw).unwrap();
        assert!(result.warnings.iter().any(|entry| entry.contains("references missing source entity 99")));
    }

    fn test_pcm_format(alternate_setting: u8, bit_resolution: u8, subslot_size: u8, pcm: bool) -> UsbAudioFormatInfo {
        UsbAudioFormatInfo {
            interface_number: 2,
            alternate_setting,
            uac_version: "UAC2".to_string(),
            terminal_link: 3,
            format_type: 1,
            format_tag: 1,
            format_name: if pcm { "PCM".to_string() } else { "Raw Data".to_string() },
            pcm_supported: pcm,
            channels: 2,
            channel_configuration: 3,
            bit_resolution,
            subslot_size,
            clock_source_id: 1,
            current_sample_rate: 768_000,
            sample_rates: Vec::new(),
            sample_rate_ranges: Vec::new(),
        }
    }

    fn test_pcm_endpoint(
        alternate_setting: u8,
        address: u8,
        direction: &str,
        usage_type: &str,
        maximum_packet_size: u32,
    ) -> UsbEndpointInfo {
        UsbEndpointInfo {
            interface_number: 2,
            alternate_setting,
            address,
            direction: direction.to_string(),
            transfer_type: "Isochronous".to_string(),
            synchronization_type: if direction == "OUT" {
                "Asynchronous".to_string()
            } else {
                "None".to_string()
            },
            usage_type: usage_type.to_string(),
            maximum_packet_size,
            sampling_frequency_control: false,
            pitch_control: false,
            maximum_packets_only: false,
            interval: if direction == "OUT" { 1 } else { 4 },
            synchronization_address: 0,
            refresh: 0,
            maximum_burst: 0,
            transactions_per_microframe: 0,
        }
    }

    #[test]
    fn selects_exact_pcm_alternate_and_explicit_feedback_endpoint() {
        let formats = vec![
            test_pcm_format(1, 16, 2, true),
            test_pcm_format(2, 24, 3, true),
            test_pcm_format(3, 32, 4, true),
            test_pcm_format(4, 32, 4, false),
        ];
        let mut endpoints = Vec::new();
        for alternate in 1..=4 {
            endpoints.push(test_pcm_endpoint(alternate, 0x07, "OUT", "Data", 776));
            endpoints.push(test_pcm_endpoint(alternate, 0x83, "IN", "Feedback", 4));
        }
        let clock_target = ClockControlTarget {
            control_interface: 1,
            streaming_interface: 2,
            clock_source_id: 1,
            clock_controls: 0x07,
        };

        let result = resolve_pcm_output_target(&formats, &endpoints, clock_target, 2, 24).unwrap();
        assert_eq!(result.alternate_setting, 2);
        assert_eq!(result.bit_resolution, 24);
        assert_eq!(result.subslot_size, 3);
        assert_eq!(result.data_endpoint_address, 0x07);
        assert_eq!(result.feedback_endpoint_address, 0x83);
    }

    #[test]
    fn selects_smallest_compatible_pcm_subslot_when_exact_precision_is_unavailable() {
        let formats = vec![
            test_pcm_format(2, 24, 3, true),
            test_pcm_format(3, 32, 4, true),
        ];
        let endpoints = vec![
            test_pcm_endpoint(2, 0x07, "OUT", "Data", 776),
            test_pcm_endpoint(2, 0x83, "IN", "Feedback", 4),
            test_pcm_endpoint(3, 0x07, "OUT", "Data", 776),
            test_pcm_endpoint(3, 0x83, "IN", "Feedback", 4),
        ];
        let clock_target = ClockControlTarget {
            control_interface: 1,
            streaming_interface: 2,
            clock_source_id: 1,
            clock_controls: 0x07,
        };

        let result = resolve_pcm_output_target(&formats, &endpoints, clock_target, 2, 16).unwrap();
        assert_eq!(result.alternate_setting, 2);
        assert_eq!(result.bit_resolution, 24);
    }

    #[test]
    fn rejects_pcm_source_bit_depth_outside_supported_values() {
        let clock_target = ClockControlTarget {
            control_interface: 1,
            streaming_interface: 2,
            clock_source_id: 1,
            clock_controls: 0x07,
        };

        assert!(resolve_pcm_output_target(&[], &[], clock_target, 2, 7).is_err());
        assert!(resolve_pcm_output_target(&[], &[], clock_target, 2, 20).is_err());
        assert!(resolve_pcm_output_target(&[], &[], clock_target, 2, 33).is_err());
    }

    #[test]
    fn rejects_asynchronous_pcm_alternate_without_feedback() {
        let formats = vec![test_pcm_format(2, 24, 3, true)];
        let endpoints = vec![test_pcm_endpoint(2, 0x07, "OUT", "Data", 776)];
        let clock_target = ClockControlTarget {
            control_interface: 1,
            streaming_interface: 2,
            clock_source_id: 1,
            clock_controls: 0x07,
        };

        assert!(resolve_pcm_output_target(&formats, &endpoints, clock_target, 2, 24).is_err());
    }

    #[test]
    fn rejects_non_pcm_and_unmatched_clock_formats() {
        let formats = vec![test_pcm_format(4, 32, 4, false)];
        let endpoints = vec![
            test_pcm_endpoint(4, 0x07, "OUT", "Data", 776),
            test_pcm_endpoint(4, 0x83, "IN", "Feedback", 4),
        ];
        let clock_target = ClockControlTarget {
            control_interface: 1,
            streaming_interface: 2,
            clock_source_id: 1,
            clock_controls: 0x07,
        };

        assert!(resolve_pcm_output_target(&formats, &endpoints, clock_target, 2, 32).is_err());
        let mut mismatched = formats;
        mismatched[0].clock_source_id = 10;
        assert!(resolve_pcm_output_target(&mismatched, &endpoints, clock_target, 2, 32).is_err());
    }

    #[test]
    fn reports_configuration_and_endpoint_count_mismatches() {
        let mut raw = uac2_topology_fixture();
        raw[22] = 3;
        let result = parse(&raw).unwrap();
        assert!(result.warnings.iter().any(|entry| entry.contains("declares 3 interfaces")));

        let mut raw = uac2_topology_fixture();
        let streaming_interface_offset = raw
            .windows(9)
            .position(|window| window == [9, 4, 2, 1, 2, 1, 2, 0x20, 0])
            .unwrap();
        raw[streaming_interface_offset + 4] = 1;
        let result = parse(&raw).unwrap();
        assert!(result.warnings.iter().any(|entry| entry.contains("declares 1 endpoint(s) but 2 endpoint descriptor(s) were parsed")));
    }
}
