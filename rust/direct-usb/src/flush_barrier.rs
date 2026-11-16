#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct FlushBarrier {
    requested: u64,
    completed: u64,
}

impl FlushBarrier {
    pub(crate) fn request(&mut self) -> u64 {
        self.requested = self.requested.wrapping_add(1);
        self.requested
    }

    pub(crate) fn complete(&mut self, sequence: u64) {
        self.completed = sequence;
    }

    pub(crate) fn is_pending(&self) -> bool {
        self.requested != self.completed
    }

    pub(crate) fn is_completed(&self, sequence: u64) -> bool {
        self.completed >= sequence
    }

    pub(crate) fn requested(&self) -> u64 {
        self.requested
    }

    #[cfg(test)]
    pub(crate) fn completed(&self) -> u64 {
        self.completed
    }

    pub(crate) fn reset(&mut self) {
        self.requested = 0;
        self.completed = 0;
    }
}

#[cfg(test)]
mod tests {
    use super::FlushBarrier;

    use std::sync::{Arc, Mutex, mpsc};
    use std::thread;

    #[test]
    fn writer_observes_pending_sequence_through_shared_mutex() {
        let shared = Arc::new(Mutex::new(FlushBarrier::default()));
        let (request_sender, request_receiver) = mpsc::channel();
        let (observation_sender, observation_receiver) = mpsc::channel();
        let (complete_sender, complete_receiver) = mpsc::channel();

        let worker_state = Arc::clone(&shared);
        let worker = thread::spawn(move || {
            let sequence = worker_state.lock().unwrap().request();
            request_sender.send(sequence).unwrap();
            complete_receiver.recv().unwrap();
            worker_state.lock().unwrap().complete(sequence);
        });

        let producer_state = Arc::clone(&shared);
        let producer = thread::spawn(move || {
            let sequence = request_receiver.recv().unwrap();
            let state = producer_state.lock().unwrap();
            let observation = (
                sequence,
                state.is_pending(),
                state.requested(),
                state.completed(),
            );
            observation_sender.send(observation).unwrap();
        });

        let observation = observation_receiver.recv().unwrap();
        assert_eq!(observation.0, 1);
        assert!(observation.1);
        assert_eq!(observation.2, 1);
        assert_eq!(observation.3, 0);

        complete_sender.send(()).unwrap();
        producer.join().unwrap();
        worker.join().unwrap();

        let state = shared.lock().unwrap();
        assert!(!state.is_pending());
        assert!(state.is_completed(1));
    }

    #[test]
    fn writer_may_legitimately_run_after_a_fast_flush_completed() {
        let shared = Arc::new(Mutex::new(FlushBarrier::default()));
        {
            let mut state = shared.lock().unwrap();
            let sequence = state.request();
            state.complete(sequence);
        }

        let producer_state = Arc::clone(&shared);
        let producer = thread::spawn(move || {
            let state = producer_state.lock().unwrap();
            (state.is_pending(), state.requested(), state.completed())
        });

        let observation = producer.join().unwrap();
        assert!(!observation.0);
        assert_eq!(observation.1, 1);
        assert_eq!(observation.2, 1);
    }

    #[test]
    fn reset_clears_both_flush_sequences() {
        let mut state = FlushBarrier::default();
        let sequence = state.request();
        state.complete(sequence);
        state.request();
        assert!(state.is_pending());
        state.reset();
        assert_eq!(state.requested(), 0);
        assert_eq!(state.completed(), 0);
        assert!(!state.is_pending());
    }
}
