package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, non-blocking handoff from a Flower Worker to a potentially blocking
 * trace sink.
 *
 * <p>{@link #publish(FlowerTraceEvent)} only attempts {@code queue.offer}. When
 * the queue is full, the newest event is dropped and {@link #droppedCount()}
 * increases. Storage and network work runs on one daemon consumer thread.
 */
public final class AsyncFlowerTraceSink implements FlowerTraceSink, AutoCloseable {

    private static final long POLL_MILLIS = 100L;
    private static final long DEFAULT_CLOSE_TIMEOUT_MILLIS = 5_000L;

    private final FlowerTraceSink delegate;
    private final ArrayBlockingQueue<FlowerTraceEvent> queue;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final Thread consumer;
    private volatile boolean running = true;

    public AsyncFlowerTraceSink(FlowerTraceSink delegate, int capacity) {
        this(delegate, capacity, "flower-trace-sink");
    }

    public AsyncFlowerTraceSink(FlowerTraceSink delegate, int capacity, String threadName) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        if (threadName == null || threadName.trim().isEmpty()) {
            throw new IllegalArgumentException("threadName must not be blank");
        }
        this.delegate = delegate;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.consumer = new Thread(this::consume, threadName.trim());
        this.consumer.setDaemon(true);
        this.consumer.start();
    }

    @Override
    public void publish(FlowerTraceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (!running || !queue.offer(event)) {
            dropped.incrementAndGet();
            return;
        }
        accepted.incrementAndGet();
    }

    public long acceptedCount() {
        return accepted.get();
    }

    public long publishedCount() {
        return published.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long failureCount() {
        return failures.get();
    }

    public int queuedCount() {
        return queue.size();
    }

    @Override
    public void close() {
        close(DEFAULT_CLOSE_TIMEOUT_MILLIS);
    }

    public void close(long timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeoutMillis must not be negative: " + timeoutMillis);
        }
        running = false;
        consumer.interrupt();
        try {
            consumer.join(timeoutMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void consume() {
        while (running || !queue.isEmpty()) {
            FlowerTraceEvent event;
            try {
                event = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                continue;
            }
            if (event == null) {
                continue;
            }
            try {
                delegate.publish(event);
                published.incrementAndGet();
            } catch (Throwable failure) {
                failures.incrementAndGet();
            }
        }
    }
}
