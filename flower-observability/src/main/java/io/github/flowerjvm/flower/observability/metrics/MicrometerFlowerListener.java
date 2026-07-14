package io.github.flowerjvm.flower.observability.metrics;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * {@link FlowerListener} that publishes Flow / Step metrics to a Micrometer
 * {@link MeterRegistry}.
 *
 * <p>Counters:
 * <ul>
 *   <li>{@code flower.flow.submitted}, tagged by {@code flowType}</li>
 *   <li>{@code flower.flow.finished} / {@code .failed} / {@code .cancelled}</li>
 *   <li>{@code flower.step.entered}, tagged by {@code flowType} + {@code stepId}</li>
 * </ul>
 *
 * <p>Timers:
 * <ul>
 *   <li>{@code flower.flow.duration} - submitted -&gt; terminal, tagged with the
 *       terminal {@code outcome}</li>
 *   <li>{@code flower.step.duration} - entered -&gt; exited per step</li>
 * </ul>
 *
 * <p>Start times are tracked in concurrent maps keyed by
 * {@link FlowId} (for flows) and {@code FlowId + stepId} (for steps). If a
 * Step is re-entered after a {@code repeat}, the previous start time is
 * overwritten - the timer measures the most recent activation only. Listener
 * fanout from {@code Worker} is best-effort, so an entry with no matching
 * exit (e.g. listener registered mid-flight) is silently skipped.
 *
 * <p>Names live in {@link FlowerMetricNames} so dashboards can pin them.
 */
public final class MicrometerFlowerListener implements FlowerListener {

    private final MeterRegistry registry;
    private final TimeUnit timerBaseUnit;

    private final ConcurrentMap<FlowId, Long> flowStarts = new ConcurrentHashMap<>();
    private final ConcurrentMap<StepKey, Long> stepStarts = new ConcurrentHashMap<>();

    public MicrometerFlowerListener(MeterRegistry registry) {
        this(registry, TimeUnit.NANOSECONDS);
    }

    public MicrometerFlowerListener(MeterRegistry registry, TimeUnit timerBaseUnit) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        if (timerBaseUnit == null) {
            throw new IllegalArgumentException("timerBaseUnit must not be null");
        }
        this.registry = registry;
        this.timerBaseUnit = timerBaseUnit;
    }

    @Override
    public void onFlowSubmitted(FlowSnapshot flow) {
        FlowId id = flow.flowId();
        flowStarts.put(id, System.nanoTime());
        registry.counter(FlowerMetricNames.FLOW_SUBMITTED, flowTypeTags(id)).increment();
    }

    @Override
    public void onStepEntered(FlowSnapshot flow, String stepId) {
        StepKey key = new StepKey(flow.flowId(), stepId);
        stepStarts.put(key, System.nanoTime());
        registry.counter(FlowerMetricNames.STEP_ENTERED, stepTags(flow.flowId(), stepId)).increment();
    }

    @Override
    public void onStepExited(FlowSnapshot flow, String stepId) {
        StepKey key = new StepKey(flow.flowId(), stepId);
        Long start = stepStarts.remove(key);
        if (start == null) return;
        long elapsed = elapsedSince(start);
        Timer.builder(FlowerMetricNames.STEP_DURATION)
                .tags(stepTags(flow.flowId(), stepId))
                .register(registry)
                .record(elapsed, timerBaseUnit);
    }

    @Override
    public void onFlowFinished(FlowSnapshot flow) {
        recordFlowTerminal(flow.flowId(), FlowerMetricNames.FLOW_FINISHED, FlowerMetricNames.OUTCOME_FINISHED);
    }

    @Override
    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
        recordFlowTerminal(flow.flowId(), FlowerMetricNames.FLOW_FAILED, FlowerMetricNames.OUTCOME_FAILED);
    }

    @Override
    public void onFlowCancelled(FlowSnapshot flow) {
        recordFlowTerminal(flow.flowId(), FlowerMetricNames.FLOW_CANCELLED, FlowerMetricNames.OUTCOME_CANCELLED);
    }

    /**
     * Time unit used when recording duration values into Micrometer timers.
     */
    public TimeUnit timerBaseUnit() {
        return timerBaseUnit;
    }

    private void recordFlowTerminal(FlowId id, String counterName, String outcome) {
        registry.counter(counterName, flowTypeTags(id)).increment();
        Long start = flowStarts.remove(id);
        if (start == null) return;
        long elapsed = elapsedSince(start);
        Tags tags = Tags.of(
                Tag.of(FlowerMetricNames.TAG_FLOW_TYPE, id.flowType()),
                Tag.of(FlowerMetricNames.TAG_OUTCOME, outcome)
        );
        Timer.builder(FlowerMetricNames.FLOW_DURATION)
                .tags(tags)
                .register(registry)
                .record(elapsed, timerBaseUnit);
    }

    private long elapsedSince(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        return timerBaseUnit.convert(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private static Tags flowTypeTags(FlowId id) {
        return Tags.of(Tag.of(FlowerMetricNames.TAG_FLOW_TYPE, id.flowType()));
    }

    private static Tags stepTags(FlowId id, String stepId) {
        return Tags.of(
                Tag.of(FlowerMetricNames.TAG_FLOW_TYPE, id.flowType()),
                Tag.of(FlowerMetricNames.TAG_STEP_ID, stepId == null ? "" : stepId)
        );
    }

    private static final class StepKey {
        private final FlowId flowId;
        private final String stepId;

        StepKey(FlowId flowId, String stepId) {
            this.flowId = flowId;
            this.stepId = stepId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StepKey)) return false;
            StepKey that = (StepKey) o;
            if (!flowId.equals(that.flowId)) return false;
            return stepId == null ? that.stepId == null : stepId.equals(that.stepId);
        }

        @Override
        public int hashCode() {
            int h = flowId.hashCode();
            h = 31 * h + (stepId == null ? 0 : stepId.hashCode());
            return h;
        }
    }
}
