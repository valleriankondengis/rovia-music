#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DapPcmFormat {
    Int16,
    Int24Packed,
    Int32,
    Float32,
}

impl DapPcmFormat {
    pub fn bytes_per_sample(self) -> usize {
        match self {
            Self::Int16 => 2,
            Self::Int24Packed => 3,
            Self::Int32 | Self::Float32 => 4,
        }
    }
}

#[derive(Clone, Debug)]
pub struct DapStreamInfo {
    pub buffer_capacity_frames: u32,
}

pub(crate) fn probe_supported(device_id: i32) -> bool {
    let mut slot = aaudio::StreamSlot::default();
    let opened = aaudio::open(&mut slot, 48_000, 2, DapPcmFormat::Int16, device_id).is_ok();
    if opened {
        aaudio::close_slot(&mut slot);
    }
    opened
}

#[cfg(target_os = "android")]
pub(crate) mod aaudio {
    use std::ffi::c_void;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::Arc;

    use super::{DapPcmFormat, DapStreamInfo};
    use crate::DirectUsbError;

    type AAudioStream = c_void;
    type AAudioStreamBuilder = c_void;
    type Result_ = i32;

    const AAUDIO_OK: Result_ = 0;
    const AAUDIO_ERROR_TIMEOUT: Result_ = -885;
    const AAUDIO_ERROR_WOULD_BLOCK: Result_ = -884;

    const SHARING_EXCLUSIVE: i32 = 0;
    const SHARING_SHARED: i32 = 1;
    const PERFORMANCE_LOW_LATENCY: i32 = 12;
    const USAGE_MEDIA: i32 = 1;
    const CONTENT_MUSIC: i32 = 2;

    pub const STATE_STARTED: i32 = 4;
    const STATE_PAUSED: i32 = 6;
    const STATE_FLUSHED: i32 = 8;
    const STATE_STOPPED: i32 = 10;

    type ErrorCallback =
        unsafe extern "C" fn(*mut AAudioStream, *mut c_void, Result_);

    #[link(name = "aaudio")]
    unsafe extern "C" {
        fn AAudio_createStreamBuilder(builder: *mut *mut AAudioStreamBuilder) -> Result_;
        fn AAudioStreamBuilder_setDeviceId(builder: *mut AAudioStreamBuilder, device_id: i32);
        fn AAudioStreamBuilder_setSampleRate(builder: *mut AAudioStreamBuilder, sample_rate: i32);
        fn AAudioStreamBuilder_setChannelCount(builder: *mut AAudioStreamBuilder, channels: i32);
        fn AAudioStreamBuilder_setFormat(builder: *mut AAudioStreamBuilder, format: i32);
        fn AAudioStreamBuilder_setSharingMode(builder: *mut AAudioStreamBuilder, mode: i32);
        fn AAudioStreamBuilder_setPerformanceMode(builder: *mut AAudioStreamBuilder, mode: i32);
        fn AAudioStreamBuilder_setUsage(builder: *mut AAudioStreamBuilder, usage: i32);
        fn AAudioStreamBuilder_setContentType(builder: *mut AAudioStreamBuilder, content: i32);
        fn AAudioStreamBuilder_setErrorCallback(
            builder: *mut AAudioStreamBuilder,
            callback: ErrorCallback,
            user_data: *mut c_void,
        );
        fn AAudioStreamBuilder_openStream(
            builder: *mut AAudioStreamBuilder,
            stream: *mut *mut AAudioStream,
        ) -> Result_;
        fn AAudioStreamBuilder_delete(builder: *mut AAudioStreamBuilder) -> Result_;
        fn AAudioStream_requestStart(stream: *mut AAudioStream) -> Result_;
        fn AAudioStream_requestPause(stream: *mut AAudioStream) -> Result_;
        fn AAudioStream_requestFlush(stream: *mut AAudioStream) -> Result_;
        fn AAudioStream_requestStop(stream: *mut AAudioStream) -> Result_;
        fn AAudioStream_write(
            stream: *mut AAudioStream,
            buffer: *const c_void,
            num_frames: i32,
            timeout_nanoseconds: i64,
        ) -> Result_;
        fn AAudioStream_getFramesRead(stream: *mut AAudioStream) -> i64;
        fn AAudioStream_getFramesWritten(stream: *mut AAudioStream) -> i64;
        fn AAudioStream_getSampleRate(stream: *mut AAudioStream) -> i32;
        fn AAudioStream_getChannelCount(stream: *mut AAudioStream) -> i32;
        fn AAudioStream_getFormat(stream: *mut AAudioStream) -> i32;
        fn AAudioStream_getBufferCapacityInFrames(stream: *mut AAudioStream) -> i32;
        fn AAudioStream_setBufferSizeInFrames(stream: *mut AAudioStream, num_frames: i32) -> i32;
        fn AAudioStream_getState(stream: *mut AAudioStream) -> i32;
        fn AAudioStream_waitForStateChange(
            stream: *mut AAudioStream,
            input_state: i32,
            next_state: *mut i32,
            timeout_nanoseconds: i64,
        ) -> Result_;
        fn AAudioStream_close(stream: *mut AAudioStream) -> Result_;
    }

    fn aaudio_format(format: DapPcmFormat) -> i32 {
        match format {
            DapPcmFormat::Int16 => 1,
            DapPcmFormat::Float32 => 2,
            DapPcmFormat::Int24Packed => 3,
            DapPcmFormat::Int32 => 4,
        }
    }

    fn dap_format(value: i32) -> Option<DapPcmFormat> {
        match value {
            1 => Some(DapPcmFormat::Int16),
            2 => Some(DapPcmFormat::Float32),
            3 => Some(DapPcmFormat::Int24Packed),
            4 => Some(DapPcmFormat::Int32),
            _ => None,
        }
    }

    unsafe extern "C" fn on_error(
        _stream: *mut AAudioStream,
        user_data: *mut c_void,
        _error: Result_,
    ) {
        if !user_data.is_null() {
            let flag = unsafe { &*(user_data as *const AtomicBool) };
            flag.store(true, Ordering::SeqCst);
        }
    }

    struct Stream {
        ptr: *mut AAudioStream,
        error_flag: *const AtomicBool,
        disconnected: Arc<AtomicBool>,
        channels: u8,
        format: DapPcmFormat,
    }

    unsafe impl Send for Stream {}

    #[derive(Default)]
    pub struct StreamSlot {
        stream: Option<Stream>,
    }

    fn check(result: Result_, action: &str) -> Result<(), DirectUsbError> {
        if result == AAUDIO_OK {
            Ok(())
        } else {
            Err(DirectUsbError::Failed {
                message: format!("AAudio {action} failed: {result}"),
            })
        }
    }

    fn wait_for_state(
        stream: *mut AAudioStream,
        target: i32,
        timeout_ms: i64,
    ) -> Result<(), DirectUsbError> {
        unsafe {
            let mut current = AAudioStream_getState(stream);
            let deadline_ns = timeout_ms * 1_000_000;
            let mut waited: i64 = 0;
            while current != target {
                let mut next: i32 = 0;
                let step: i64 = 50_000_000;
                let result =
                    AAudioStream_waitForStateChange(stream, current, &mut next, step);
                if result < 0 && result != AAUDIO_ERROR_TIMEOUT {
                    return Err(DirectUsbError::Failed {
                        message: format!("AAudio state wait failed: {result}"),
                    });
                }
                current = if next != 0 { next } else { AAudioStream_getState(stream) };
                waited += step;
                if waited >= deadline_ns {
                    return Err(DirectUsbError::Failed {
                        message: format!(
                            "AAudio state wait timed out (state {current}, target {target})"
                        ),
                    });
                }
            }
            Ok(())
        }
    }

    fn open_with_sharing(
        sample_rate: u32,
        channels: u8,
        format: DapPcmFormat,
        device_id: i32,
        sharing: i32,
        error_flag: *const AtomicBool,
    ) -> Result<*mut AAudioStream, DirectUsbError> {
        unsafe {
            let mut builder: *mut AAudioStreamBuilder = std::ptr::null_mut();
            check(AAudio_createStreamBuilder(&mut builder), "create builder")?;
            AAudioStreamBuilder_setDeviceId(builder, device_id);
            AAudioStreamBuilder_setSampleRate(builder, sample_rate as i32);
            AAudioStreamBuilder_setChannelCount(builder, channels as i32);
            AAudioStreamBuilder_setFormat(builder, aaudio_format(format));
            AAudioStreamBuilder_setSharingMode(builder, sharing);
            AAudioStreamBuilder_setPerformanceMode(builder, PERFORMANCE_LOW_LATENCY);
            AAudioStreamBuilder_setUsage(builder, USAGE_MEDIA);
            AAudioStreamBuilder_setContentType(builder, CONTENT_MUSIC);
            AAudioStreamBuilder_setErrorCallback(
                builder,
                on_error,
                error_flag as *mut c_void,
            );
            let mut stream: *mut AAudioStream = std::ptr::null_mut();
            let result = AAudioStreamBuilder_openStream(builder, &mut stream);
            AAudioStreamBuilder_delete(builder);
            check(result, "open stream")?;
            Ok(stream)
        }
    }

    pub fn open(
        slot: &mut StreamSlot,
        sample_rate: u32,
        channels: u8,
        format: DapPcmFormat,
        device_id: i32,
    ) -> Result<DapStreamInfo, DirectUsbError> {
        close_slot(slot);
        if sample_rate == 0 || channels == 0 || channels > 2 {
            return Err(DirectUsbError::Failed {
                message: format!("Unsupported DAP stream shape: {sample_rate} Hz, {channels} ch"),
            });
        }
        let disconnected = Arc::new(AtomicBool::new(false));
        let error_flag: *const AtomicBool =
            Arc::into_raw(Arc::clone(&disconnected)) as *const AtomicBool;

        let stream = match open_with_sharing(
            sample_rate,
            channels,
            format,
            device_id,
            SHARING_EXCLUSIVE,
            error_flag,
        ) {
            Ok(stream) => stream,
            Err(_) => {
                match open_with_sharing(
                    sample_rate,
                    channels,
                    format,
                    device_id,
                    SHARING_SHARED,
                    error_flag,
                ) {
                    Ok(stream) => stream,
                    Err(error) => {
                        unsafe { drop(Arc::from_raw(error_flag)) };
                        return Err(error);
                    }
                }
            }
        };

        unsafe {
            let actual_rate = AAudioStream_getSampleRate(stream);
            let actual_channels = AAudioStream_getChannelCount(stream);
            let actual_format = dap_format(AAudioStream_getFormat(stream));
            let capacity = AAudioStream_getBufferCapacityInFrames(stream);
            if actual_rate != sample_rate as i32
                || actual_channels != channels as i32
                || actual_format != Some(format)
            {
                AAudioStream_close(stream);
                drop(Arc::from_raw(error_flag));
                return Err(DirectUsbError::Failed {
                    message: format!(
                        "AAudio negotiated a different format (requested {sample_rate} Hz/{channels} ch/{format:?}, got {actual_rate} Hz/{actual_channels} ch/{actual_format:?}); refusing to alter samples"
                    ),
                });
            }
            if capacity > 0 {
                AAudioStream_setBufferSizeInFrames(stream, capacity);
            }
            slot.stream = Some(Stream {
                ptr: stream,
                error_flag,
                disconnected,
                channels,
                format,
            });
            Ok(DapStreamInfo {
                buffer_capacity_frames: capacity.max(0) as u32,
            })
        }
    }

    pub fn start(slot: &mut StreamSlot) -> Result<(), DirectUsbError> {
        let stream = require(slot)?;
        unsafe {
            check(AAudioStream_requestStart(stream.ptr), "start")?;
            wait_for_state(stream.ptr, STATE_STARTED, 1000)
        }
    }

    pub fn pause(slot: &mut StreamSlot) -> Result<(), DirectUsbError> {
        let stream = require(slot)?;
        unsafe {
            let state = AAudioStream_getState(stream.ptr);
            if state == STATE_PAUSED {
                return Ok(());
            }
            check(AAudioStream_requestPause(stream.ptr), "pause")?;
            wait_for_state(stream.ptr, STATE_PAUSED, 1000)
        }
    }

    pub fn flush(slot: &mut StreamSlot) -> Result<(), DirectUsbError> {
        pause(slot)?;
        let stream = require(slot)?;
        unsafe {
            check(AAudioStream_requestFlush(stream.ptr), "flush")?;
            wait_for_state(stream.ptr, STATE_FLUSHED, 1000)
        }
    }

    pub fn write_some(
        slot: &mut StreamSlot,
        data: &[u8],
        timeout_ms: i64,
    ) -> Result<usize, DirectUsbError> {
        let stream = require(slot)?;
        let frame_size = stream.channels as usize * stream.format.bytes_per_sample();
        if frame_size == 0 || data.len() % frame_size != 0 {
            return Err(DirectUsbError::Failed {
                message: "DAP write is not frame aligned".to_string(),
            });
        }
        if stream.disconnected.load(Ordering::SeqCst) {
            return Err(DirectUsbError::Failed {
                message: "AAudio stream disconnected".to_string(),
            });
        }
        let frames = (data.len() / frame_size) as i32;
        let result = unsafe {
            AAudioStream_write(
                stream.ptr,
                data.as_ptr() as *const c_void,
                frames,
                timeout_ms * 1_000_000,
            )
        };
        if result >= 0 {
            Ok(result as usize * frame_size)
        } else if result == AAUDIO_ERROR_TIMEOUT || result == AAUDIO_ERROR_WOULD_BLOCK {
            Ok(0)
        } else {
            Err(DirectUsbError::Failed {
                message: format!("AAudio write failed: {result}"),
            })
        }
    }

    pub fn frames_read(slot: &StreamSlot) -> u64 {
        match &slot.stream {
            Some(stream) => unsafe { AAudioStream_getFramesRead(stream.ptr).max(0) as u64 },
            None => 0,
        }
    }

    pub fn frames_written(slot: &StreamSlot) -> u64 {
        match &slot.stream {
            Some(stream) => unsafe { AAudioStream_getFramesWritten(stream.ptr).max(0) as u64 },
            None => 0,
        }
    }

    pub fn state(slot: &StreamSlot) -> i32 {
        match &slot.stream {
            Some(stream) => unsafe { AAudioStream_getState(stream.ptr) },
            None => -1,
        }
    }

    pub fn close_slot(slot: &mut StreamSlot) {
        if let Some(stream) = slot.stream.take() {
            unsafe {
                let _ = AAudioStream_requestStop(stream.ptr);
                let _ = wait_for_state(stream.ptr, STATE_STOPPED, 300);
                AAudioStream_close(stream.ptr);
                drop(Arc::from_raw(stream.error_flag));
            }
        }
    }

    fn require(slot: &StreamSlot) -> Result<&Stream, DirectUsbError> {
        slot.stream.as_ref().ok_or_else(|| DirectUsbError::Failed {
            message: "DAP output is not open".to_string(),
        })
    }
}

#[cfg(not(target_os = "android"))]
pub(crate) mod aaudio {
    use super::{DapPcmFormat, DapStreamInfo};
    use crate::DirectUsbError;

    pub const STATE_STARTED: i32 = 4;

    #[derive(Default)]
    pub struct StreamSlot {
        _private: (),
    }

    fn unavailable() -> DirectUsbError {
        DirectUsbError::Failed {
            message: "DAP output requires Android (AAudio)".to_string(),
        }
    }

    pub fn open(
        _slot: &mut StreamSlot,
        _sample_rate: u32,
        _channels: u8,
        _format: DapPcmFormat,
        _device_id: i32,
    ) -> Result<DapStreamInfo, DirectUsbError> {
        Err(unavailable())
    }

    pub fn start(_slot: &mut StreamSlot) -> Result<(), DirectUsbError> {
        Err(unavailable())
    }

    pub fn pause(_slot: &mut StreamSlot) -> Result<(), DirectUsbError> {
        Err(unavailable())
    }

    pub fn flush(_slot: &mut StreamSlot) -> Result<(), DirectUsbError> {
        Err(unavailable())
    }

    pub fn write_some(
        _slot: &mut StreamSlot,
        _data: &[u8],
        _timeout_ms: i64,
    ) -> Result<usize, DirectUsbError> {
        Err(unavailable())
    }

    pub fn frames_read(_slot: &StreamSlot) -> u64 {
        0
    }

    pub fn frames_written(_slot: &StreamSlot) -> u64 {
        0
    }

    pub fn state(_slot: &StreamSlot) -> i32 {
        -1
    }

    pub fn close_slot(_slot: &mut StreamSlot) {}
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn format_frame_sizes() {
        assert_eq!(DapPcmFormat::Int16.bytes_per_sample(), 2);
        assert_eq!(DapPcmFormat::Int24Packed.bytes_per_sample(), 3);
        assert_eq!(DapPcmFormat::Int32.bytes_per_sample(), 4);
        assert_eq!(DapPcmFormat::Float32.bytes_per_sample(), 4);
    }

    #[test]
    fn open_without_android_is_an_error() {
        let mut slot = aaudio::StreamSlot::default();
        let result = aaudio::open(&mut slot, 48_000, 2, DapPcmFormat::Int16, 0);
        #[cfg(not(target_os = "android"))]
        assert!(result.is_err());
        #[cfg(target_os = "android")]
        let _ = result;
    }
}
