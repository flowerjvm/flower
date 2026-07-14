package io.github.flowerjvm.flower.core.worker;

/**
 * Fluent builder for {@link Worker}.
 */
public final class WorkerBuilder {

    private final String name;
    private long intervalMillis = 100L;

    WorkerBuilder(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("worker name must not be null or empty");
        }
        this.name = name;
    }

    /**
     * Tick interval used by the scheduler started by {@code Engine.start()}.
     * Has no effect if the Worker is driven manually via {@link Worker#tickOnce()}.
     * Default: 100 ms.
     */
    public WorkerBuilder intervalMillis(long intervalMillis) {
        if (intervalMillis <= 0L) {
            throw new IllegalArgumentException("intervalMillis must be > 0: " + intervalMillis);
        }
        this.intervalMillis = intervalMillis;
        return this;
    }

    public Worker build() {
        return new Worker(name, intervalMillis);
    }
}
