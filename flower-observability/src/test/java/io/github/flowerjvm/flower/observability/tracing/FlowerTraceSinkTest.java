package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerTraceSinkTest {

    @Test
    void in_memory_sink_returns_an_immutable_snapshot() {
        InMemoryFlowerTraceSink sink = new InMemoryFlowerTraceSink();
        FlowerTraceEvent event = event(1);

        sink.publish(event);

        assertThat(sink.snapshot()).containsExactly(event);
        assertThat(sink.snapshot()).isUnmodifiable();
    }

    @Test
    void async_sink_publishes_on_its_consumer_thread() throws Exception {
        CountDownLatch published = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AsyncFlowerTraceSink sink = new AsyncFlowerTraceSink(event -> {
            threadName.set(Thread.currentThread().getName());
            published.countDown();
        }, 8, "trace-test-consumer");
        try {
            sink.publish(event(1));

            assertThat(published.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).isEqualTo("trace-test-consumer");
            sink.close();
            assertThat(sink.acceptedCount()).isEqualTo(1);
            assertThat(sink.publishedCount()).isEqualTo(1);
            assertThat(sink.droppedCount()).isZero();
        } finally {
            sink.close();
        }
    }

    @Test
    void async_sink_drops_the_newest_event_when_its_queue_is_full() throws Exception {
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        AsyncFlowerTraceSink sink = new AsyncFlowerTraceSink(event -> {
            consumerEntered.countDown();
            try {
                releaseConsumer.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, 1, "trace-overflow-test");
        try {
            sink.publish(event(1));
            assertThat(consumerEntered.await(2, TimeUnit.SECONDS)).isTrue();

            sink.publish(event(2));
            sink.publish(event(3));

            assertThat(sink.acceptedCount()).isEqualTo(2);
            assertThat(sink.droppedCount()).isEqualTo(1);
        } finally {
            releaseConsumer.countDown();
            sink.close();
        }
    }

    @Test
    void async_sink_close_with_zero_timeout_does_not_wait_for_consumer() throws Exception {
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        AsyncFlowerTraceSink sink = new AsyncFlowerTraceSink(event -> {
            consumerEntered.countDown();
            awaitUninterruptibly(releaseConsumer);
        }, 1, "trace-zero-close-test");
        Thread closer = new Thread(() -> {
            sink.close(0);
            closeReturned.countDown();
        }, "trace-zero-close-caller");

        boolean returnedWithoutWaiting;
        try {
            sink.publish(event(1));
            assertThat(consumerEntered.await(2, TimeUnit.SECONDS)).isTrue();
            closer.start();
            returnedWithoutWaiting = closeReturned.await(1, TimeUnit.SECONDS);
        } finally {
            releaseConsumer.countDown();
            closer.join(2_000L);
            sink.close();
        }

        assertThat(returnedWithoutWaiting).isTrue();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static FlowerTraceEvent event(long sequence) {
        return FlowerTraceEvent.builder(FlowerTraceEventType.FLOW_STARTED)
                .eventId("run-1:event:" + sequence)
                .traceId("trace-1")
                .flowRunId("run-1")
                .flowId(FlowId.of("test", "1"))
                .workerName("test-worker")
                .sequence(sequence)
                .occurredAt(Instant.ofEpochMilli(sequence))
                .build();
    }
}
