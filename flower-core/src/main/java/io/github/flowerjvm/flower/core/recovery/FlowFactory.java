package io.github.flowerjvm.flower.core.recovery;

import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;

/**
 * Rebuilds a fresh Flow definition for one durable Flow id.
 *
 * <p>The returned Flow should be newly built and not yet submitted to a Worker.
 * Recovery itself is applied by {@link FlowFactoryRegistry} or
 * {@link FlowRecoveryService}.
 */
@FunctionalInterface
public interface FlowFactory {

    Flow create(FlowId flowId);
}
