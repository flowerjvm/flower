package io.github.parkkevinsb.flower.core.step;

/**
 * Optional gate checked before a Step is allowed to progress.
 *
 * <p>A Guard is the Flower replacement for the legacy PreCheckSeq pattern.
 * It is not a Step and does not own a lifecycle. It makes a quick decision
 * at the Worker tick boundary: pass, hold, redirect, or fail.
 */
@FunctionalInterface
public interface Guard {

    GuardResult check(StepContext ctx);
}
