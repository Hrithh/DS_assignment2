package au.edu.adelaide.ds.assignment2;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * LamportClock implements a thread-safe logical clock using Lamport timestamps.
 * - Each process/thread maintains its own instance.
 * - Ensures logical ordering of distributed events.
 * - Internally uses AtomicInteger for safe concurrent access.
 */
public final class LamportClock {

    private static final int INITIAL_TIME = 0;
    private final AtomicInteger clock;  // Thread-safe logical clock counter

    /**
     * Constructs a LamportClock starting at INITIAL_TIME (0).
     */
    public LamportClock() {
        this.clock = new AtomicInteger(INITIAL_TIME);
    }

    /**
     * Advances the clock for a local event or when sending a message.
     *
     * @return the updated timestamp after increment
     */
    public int tick() {
        return clock.incrementAndGet();
    }

    /**
     * Updates the clock when receiving a message from another process.
     * Rule: max(local, received) + 1
     *
     * @param receivedTimestamp the Lamport timestamp from the received message
     * @return the updated local timestamp
     */
    public int update(int receivedTimestamp) {
        int current;
        int updated;
        do {
            current = clock.get();
            updated = Math.max(current, receivedTimestamp) + 1;
        } while (!clock.compareAndSet(current, updated));  // CAS ensures atomic update
        return updated;
    }

    /**
     * Returns the current Lamport clock value.
     *
     * @return the current timestamp
     */
    public int getTime() {
        return clock.get();
    }

    /**
     * Sets the Lamport clock to a specific value.
     * Useful for restoring from persistent storage.
     *
     * @param value the new clock value
     */
    public void setTime(int value) {
        clock.set(value);
    }

    /**
     * Returns the clock value as a string (for logging/debugging).
     *
     * @return string representation of the timestamp
     */
    @Override
    public String toString() {
        return String.valueOf(clock.get());
    }
}
