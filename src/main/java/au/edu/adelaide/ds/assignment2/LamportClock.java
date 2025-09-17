package au.edu.adelaide.ds.assignment2;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * LamportClock implements a thread-safe logical clock using Lamport timestamps.
 * - Each thread/process has its own instance.
 * - Ensures logical ordering of distributed events.
 * - Internally uses AtomicInteger for safe concurrent access.
 */
public class LamportClock {

    private final AtomicInteger clock;  // Thread-safe logical clock counter

    /**
     * Constructs a LamportClock starting at 0.
     */
    public LamportClock() {
        this.clock = new AtomicInteger(0);
    }

    /**
     * Called on a local event or sending a message.
     * Increments the logical clock by 1.
     * return Updated timestamp after increment
     */
    public int tick() {
        return clock.incrementAndGet();
    }

    /**
     * Called on receiving a message from another process.
     * Updates this clock to: max(local, received) + 1.
     * Uses atomic compare-and-set loop to avoid race conditions.
     * receivedTimestamp The Lamport timestamp from the received message
     * return The updated local timestamp
     */
    public int update(int receivedTimestamp) {
        int current;
        int updated;
        do {
            current = clock.get();
            updated = Math.max(current, receivedTimestamp) + 1;
        } while (!clock.compareAndSet(current, updated));  // CAS ensures atomic update if no conflict
        return updated;
    }

    /**
     * Returns the current value of the Lamport clock.
     * return The current timestamp
     */
    public int getTime() {
        return clock.get();
    }

    /**
     * Converts the current clock value to a string.
     * Useful for logging/debugging.
     * return String representation of the timestamp
     */
    @Override
    public String toString() {
        return String.valueOf(clock.get());
    }
}
