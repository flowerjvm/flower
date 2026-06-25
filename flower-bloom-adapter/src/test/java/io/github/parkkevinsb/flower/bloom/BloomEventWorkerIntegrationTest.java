package io.github.parkkevinsb.flower.bloom;

import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.SystemClock;
import io.github.parkkevinsb.flower.eventloop.flow.EventFlow;
import io.github.parkkevinsb.flower.eventloop.step.AwaitCondition;
import io.github.parkkevinsb.flower.eventloop.step.EventStep;
import io.github.parkkevinsb.flower.eventloop.step.EventStepContext;
import io.github.parkkevinsb.flower.eventloop.step.EventStepResult;
import io.github.parkkevinsb.flower.eventloop.worker.EventWorker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that the event-loop runtime stays Bloom-independent while a
 * host can still bridge a Bloom bus through {@link BloomEventBus}.
 */
class BloomEventWorkerIntegrationTest {

    static final class Request {
        final String id;

        Request(String id) {
            this.id = id;
        }
    }

    static final class Response {
        final String id;

        Response(String id) {
            this.id = id;
        }
    }

    @Test
    void rawBloomPublishWakesEventWorkerThroughAdapter() throws Exception {
        LocalEventBus bloom = LocalEventBus.create();
        EventWorker worker = EventWorker.builder("bloom-eventloop")
                .clock(SystemClock.INSTANCE)
                .eventBus(BloomEventBus.wrap(bloom))
                .build();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch awaiting = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        EventFlow flow = EventFlow.builder("bloom", "req-1")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        entered.countDown();
                        return EventStepResult.await(
                                AwaitCondition.event(Response.class, response -> "req-1".equals(response.id)))
                                .thenRun(context -> awaiting.countDown());
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        finished.countDown();
                        return EventStepResult.finish();
                    }
                })
                .build();

        try {
            worker.start();
            worker.submit(flow);

            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(awaiting.await(1, TimeUnit.SECONDS)).isTrue();

            bloom.publish(new Response("other"));
            assertThat(worker.stateOf(flow.flowId())).isEqualTo(FlowState.RUNNING);

            bloom.publish(new Response("req-1"));

            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
            awaitInactive(worker);
            assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        } finally {
            worker.stop();
        }
    }

    @Test
    void thenPublishCanReceiveSynchronousBloomResponse() {
        LocalEventBus bloom = LocalEventBus.create();
        EventWorker worker = EventWorker.builder("bloom-sync")
                .clock(SystemClock.INSTANCE)
                .eventBus(BloomEventBus.wrap(bloom))
                .build();
        CountDownLatch finished = new CountDownLatch(1);

        bloom.subscribe(Request.class, request -> bloom.publish(new Response(request.id)));

        EventFlow flow = EventFlow.builder("bloom", "sync")
                .step("request", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(
                                AwaitCondition.event(Response.class, response -> "sync".equals(response.id)))
                                .thenPublish(new Request("sync"));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        finished.countDown();
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(finished.getCount()).isZero();
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
    }

    private static void awaitInactive(EventWorker worker) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1_000L;
        while (worker.activeCount() != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(worker.activeCount()).isZero();
    }
}
