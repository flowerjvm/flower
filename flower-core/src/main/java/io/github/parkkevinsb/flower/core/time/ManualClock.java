package io.github.parkkevinsb.flower.core.time;

/**
 * Test-friendly {@link Clock} whose time only changes via {@link #advance(long)}
 * or {@link #setTime(long)}.
 *
 * <p>Not thread-safe. Tests should drive the clock from a single thread, the
 * same one that ticks the worker.
 */
public final class ManualClock implements Clock {

    private long now;

    public ManualClock() {
        this(0L);
    }

    public ManualClock(long startMillis) {
        this.now = startMillis;
    }

    @Override
    public long currentTimeMillis() {
        return now;
    }

    public void advance(long millis) {
        if (millis < 0L) {
            throw new IllegalArgumentException("ManualClock cannot move backwards: " + millis);
        }
        this.now += millis;
    }

    public void setTime(long millis) {
        this.now = millis;
    }
}
