package au.edu.adelaide.ds.assignment2;

import java.util.concurrent.atomic.AtomicInteger;

public class LamportClock {

    private final AtomicInteger clock;

    public LamportClock() {
        this.clock = new AtomicInteger(0);
    }

    // Increment for local events (e.g., send or internal event)
    public int tick() {
        return clock.incrementAndGet();
    }

    // Update on receiving a message (remote event)
    public int update(int receivedTimestamp) {
        int current;
        int updated;
        do {
            current = clock.get();
            updated = Math.max(current, receivedTimestamp) + 1;
        } while (!clock.compareAndSet(current, updated));
        return updated;
    }

    public int getTime() {
        return clock.get();
    }

    @Override
    public String toString() {
        return String.valueOf(clock.get());
    }
}
