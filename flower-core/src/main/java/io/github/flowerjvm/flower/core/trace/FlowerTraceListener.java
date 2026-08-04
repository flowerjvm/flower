package io.github.flowerjvm.flower.core.trace;

import io.github.flowerjvm.flower.core.listener.FlowerListener;

/**
 * Opt-in listener for the standard Flower runtime trace stream.
 *
 * <p>The separate type keeps trace allocation out of applications that only
 * use the historical lifecycle callbacks on {@link FlowerListener}. Trace
 * callbacks run on the Worker thread and must return quickly.
 */
@FunctionalInterface
public interface FlowerTraceListener extends FlowerListener {

    void onTraceEvent(FlowerTraceEvent event);
}
