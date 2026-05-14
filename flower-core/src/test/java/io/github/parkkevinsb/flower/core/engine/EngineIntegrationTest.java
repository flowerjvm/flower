package io.github.parkkevinsb.flower.core.engine;

import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.time.SystemClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EngineIntegrationTest {

    @Test
    void real_scheduler_runs_flow_to_completion() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<FlowSnapshot> finishedFlow = new AtomicReference<>();

        FlowerListener l = new FlowerListener() {
            @Override
            public void onFlowFinished(FlowSnapshot f) {
                finishedFlow.set(f);
                finished.countDown();
            }
        };

        Worker worker = Worker.builder("main").intervalMillis(10).build();
        Engine engine = Engine.builder()
                .clock(SystemClock.INSTANCE)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(l)
                .build();

        Step done = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("t", "1").step("a", done).build();

        try {
            engine.start();
            assertThat(engine.state()).isEqualTo(EngineState.RUNNING);
            worker.submit(flow);

            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(finishedFlow.get().flowId().toString()).isEqualTo("t/1");
            assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        } finally {
            engine.stop();
        }

        assertThat(engine.state()).isEqualTo(EngineState.STOPPED);
    }

    @Test
    void dump_reports_engine_and_worker_state() {
        Worker w1 = Worker.builder("w1").intervalMillis(50).build();
        Worker w2 = Worker.builder("w2").intervalMillis(50).build();
        Engine engine = Engine.builder()
                .clock(SystemClock.INSTANCE)
                .eventBus(InMemoryEventBus.create())
                .worker(w1)
                .worker(w2)
                .build();
        engine.attach();

        EngineDump dump = engine.dump();
        assertThat(dump.workers()).hasSize(2);
        assertThat(dump.workers()).extracting(EngineDump.WorkerDump::name)
                .containsExactly("w1", "w2");
        assertThat(dump.toText()).contains("Worker 'w1'").contains("Worker 'w2'");
    }
}
