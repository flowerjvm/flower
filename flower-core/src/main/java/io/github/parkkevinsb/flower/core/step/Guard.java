package io.github.parkkevinsb.flower.core.step;

/**
 * Optional pre-step decision checked before a Step enters or ticks.
 *
 * <p>A Guard is not a Step and does not own workflow progression. It should
 * read current readiness or blocking state and make a quick, non-blocking
 * decision at the Worker tick boundary: pass, hold, redirect, or fail.
 *
 * <p>State changes, user-visible handling, retry, timeout, and long-running
 * work should remain in Steps or application services. A Guard should stay
 * focused on deciding whether the guarded Step may run on this tick.
 */
@FunctionalInterface
public interface Guard {

    GuardResult check(StepContext ctx);
}
