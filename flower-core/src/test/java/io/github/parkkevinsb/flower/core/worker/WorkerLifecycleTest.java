package io.github.parkkevinsb.flower.core.worker;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.step.RecoveryPolicy;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerLifecycleTest {

    @Test
    void attach_transitions_worker_and_rejects_second_engine_attach() {
        Worker worker = Worker.builder("main").build();
        Engine first = engineFor(worker);

        first.attach();

        assertThat(worker.state()).isEqualTo(WorkerState.ATTACHED);
        assertThat(worker.driveMode()).isEqualTo(DriveMode.NONE);

        Engine second = engineFor(worker);
        assertThatThrownBy(second::attach)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already attached");
    }

    @Test
    void first_manual_tick_selects_manual_drive_mode_and_prevents_scheduled_start() {
        Worker worker = Worker.builder("manual").build();
        engineFor(worker).attach();

        worker.tickOnce();

        assertThat(worker.state()).isEqualTo(WorkerState.ATTACHED);
        assertThat(worker.driveMode()).isEqualTo(DriveMode.MANUAL);
        assertThatThrownBy(worker::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manual drive mode");
    }

    @Test
    void start_from_paused_requires_resume_instead_of_creating_another_scheduler() {
        Worker worker = Worker.builder("scheduled").intervalMillis(1_000L).build();
        engineFor(worker).attach();

        try {
            worker.start();
            worker.pause();

            assertThat(worker.state()).isEqualTo(WorkerState.PAUSED);
            assertThat(worker.driveMode()).isEqualTo(DriveMode.SCHEDULED);
            assertThatThrownBy(worker::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("resume");

            worker.resume();
            assertThat(worker.state()).isEqualTo(WorkerState.RUNNING);
        } finally {
            worker.stop();
        }
    }

    @Test
    void public_tick_once_is_rejected_while_scheduled_mode_owns_the_worker() {
        Worker worker = Worker.builder("scheduled").intervalMillis(1_000L).build();
        engineFor(worker).attach();

        try {
            worker.start();

            assertThat(worker.state()).isEqualTo(WorkerState.RUNNING);
            assertThat(worker.driveMode()).isEqualTo(DriveMode.SCHEDULED);
            assertThatThrownBy(worker::tickOnce)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("scheduled mode");
        } finally {
            worker.stop();
        }
    }

    @Test
    void durable_flow_requires_durable_checkpoint_store() {
        Worker worker = Worker.builder("no-store").build();
        engineFor(worker).attach();
        Flow flow = Flow.builder("durable", "no-store")
                .durable()
                .durableStep("only", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        return StepResult.stay();
                    }
                }, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        assertThatThrownBy(() -> worker.submit(flow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Durable Flow requires a durable FlowCheckpointStore");
        assertThat(flow.state()).isEqualTo(FlowState.CREATED);
    }

    private static Engine engineFor(Worker worker) {
        return Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();
    }
}
