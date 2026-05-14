package io.github.parkkevinsb.flower.core.time;

/**
 * Time source SPI used by Flower core for timeout calculation.
 *
 * <p>Production code uses {@link SystemClock}. Tests use {@link ManualClock}
 * to make timeout-driven flows deterministic.
 */
public interface Clock {

    /**
     * Returns the current time in milliseconds since the epoch.
     */
    long currentTimeMillis();
}
