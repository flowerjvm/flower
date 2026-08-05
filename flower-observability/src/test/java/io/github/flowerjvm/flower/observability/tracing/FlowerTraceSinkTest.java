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
