package io.github.parkkevinsb.flower.spring;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.engine.EngineState;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowerEngineLifecycleTest {

    private Engine engine() {
        return Engine.builder()
                .worker(Worker.builder("main").intervalMillis(100L).build())
                .build();
    }

    @Test
    void startStopFlipsRunningFlag() {
        Engine engine = engine();
        FlowerEngineLifecycle lifecycle = new FlowerEngineLifecycle(engine, true, 0);

        assertThat(lifecycle.isRunning()).isFalse();

        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();
        assertThat(engine.state()).isEqualTo(EngineState.RUNNING);

        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(engine.state()).isEqualTo(EngineState.STOPPED);
    }

    @Test
    void startIsIdempotent() {
        Engine engine = engine();
        FlowerEngineLifecycle lifecycle = new FlowerEngineLifecycle(engine, true, 0);

        lifecycle.start();
        lifecycle.start(); // second call should be a no-op

        assertThat(engine.state()).isEqualTo(EngineState.RUNNING);
        lifecycle.stop();
    }

    @Test
    void stopBeforeStartIsNoOp() {
        Engine engine = engine();
        FlowerEngineLifecycle lifecycle = new FlowerEngineLifecycle(engine, true, 0);

        lifecycle.stop();

        assertThat(engine.state()).isEqualTo(EngineState.CREATED);
    }

    @Test
    void exposesAutoStartAndPhase() {
        FlowerEngineLifecycle lifecycle = new FlowerEngineLifecycle(engine(), false, 42);

        assertThat(lifecycle.isAutoStartup()).isFalse();
        assertThat(lifecycle.getPhase()).isEqualTo(42);
    }

    @Test
    void rejectsNullEngine() {
        assertThatThrownBy(() -> new FlowerEngineLifecycle(null, true, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
