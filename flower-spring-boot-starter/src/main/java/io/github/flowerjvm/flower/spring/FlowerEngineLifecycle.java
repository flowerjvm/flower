package io.github.flowerjvm.flower.spring;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.engine.EngineState;
import org.springframework.context.SmartLifecycle;

/**
 * Binds {@link Engine#start()} / {@link Engine#stop()} to the Spring application
 * context lifecycle.
 *
 * <p>{@code start()} is invoked by Spring after all beans are initialized;
 * {@code stop()} is invoked on context close so Worker schedulers shut down
 * cleanly before other beans are torn down.
 */
public class FlowerEngineLifecycle implements SmartLifecycle {

    private final Engine engine;
    private final boolean autoStart;
    private final int phase;
    private volatile boolean running = false;

    public FlowerEngineLifecycle(Engine engine, boolean autoStart, int phase) {
        if (engine == null) {
            throw new IllegalArgumentException("engine must not be null");
        }
        this.engine = engine;
        this.autoStart = autoStart;
        this.phase = phase;
    }

    @Override
    public void start() {
        if (running) return;
        engine.start();
        running = true;
    }

    @Override
    public void stop() {
        if (!running) return;
        try {
            engine.stop();
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running && engine.state() == EngineState.RUNNING;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStart;
    }

    @Override
    public int getPhase() {
        return phase;
    }
}
