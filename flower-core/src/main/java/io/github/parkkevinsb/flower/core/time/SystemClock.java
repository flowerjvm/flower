package io.github.parkkevinsb.flower.core.time;

/**
 * {@link Clock} backed by {@link System#currentTimeMillis()}.
 */
public final class SystemClock implements Clock {

    public static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
