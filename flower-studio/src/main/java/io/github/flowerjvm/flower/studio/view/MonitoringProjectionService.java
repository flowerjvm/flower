package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.evaluation.EvaluationComparator;
import io.github.flowerjvm.flower.evaluation.EvaluationComparison;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationSummary;
import io.github.flowerjvm.flower.evaluation.storage.EvaluationLoadDiagnostics;
import io.github.flowerjvm.flower.studio.model.ObservationRecord;
import io.github.flowerjvm.flower.studio.store.StudioSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds bounded monitoring aggregates from the observation and evaluation snapshots. */
public final class MonitoringProjectionService {

    private static final int MAX_OPERATION_ROWS = 50;
    private static final long[] BUCKET_WIDTHS_MILLIS = {
        60_000L,
        5L * 60_000L,
        15L * 60_000L,
        60L * 60_000L,
        6L * 60L * 60_000L,
        24L * 60L * 60_000L,
        7L * 24L * 60L * 60_000L,
        30L * 24L * 60L * 60_000L
    };

    private static final Comparator<ObservationRecord> EVENT_ORDER =
            new Comparator<ObservationRecord>() {
                @Override
                public int compare(ObservationRecord left, ObservationRecord right) {
                    int byTime = left.occurredAt().compareTo(right.occurredAt());
                    if (byTime != 0) {
                        return byTime;
                    }
                    int bySource = left.source().compareTo(right.source());
                    if (bySource != 0) {
                        return bySource;
                    }
                    int bySequence = Long.compare(left.sequence(), right.sequence());
                    return bySequence != 0
                            ? bySequence : left.eventId().compareTo(right.eventId());
                }
            };

    private final StudioProjectionService traces;

    public MonitoringProjectionService() {
        this.traces = new StudioProjectionService(false);
    }

    public MonitoringDashboardView project(
            StudioSnapshot snapshot,
            List<EvaluationExperimentResult> experiments,
            EvaluationLoadDiagnostics evaluationDiagnostics) {
        if (snapshot == null || experiments == null) {
            throw new IllegalArgumentException("snapshot and experiments must not be null");
        }

        List<ObservationRecord> events = new ArrayList<ObservationRecord>(snapshot.events());
        Collections.sort(events, EVENT_ORDER);
        List<TraceSummaryView> summaries = traces.summaries(snapshot);
        Map<TraceStatus, Integer> statusCounts = statusCounts(summaries);

        return new MonitoringDashboardView(
                overview(events, summaries, statusCounts),
                statuses(summaries.size(), statusCounts),
                activity(events, summaries),
                operations(events),
                transitions(events),
                sources(events),
                evaluation(experiments),
                snapshot.diagnostics(),
                evaluationDiagnostics);
    }

    private static MonitoringOverviewView overview(
            List<ObservationRecord> events,
            List<TraceSummaryView> summaries,
            Map<TraceStatus, Integer> statusCounts) {
        long terminalDuration = 0L;
        int terminalCount = 0;
        int toolFailures = 0;
        int approvals = 0;
        int timeouts = 0;
        for (TraceSummaryView summary : summaries) {
            if (isTerminal(summary.getStatus())) {
                terminalDuration += summary.getDurationMillis();
                terminalCount++;
            }
        }
        for (ObservationRecord event : events) {
            if ("TOOL_CALL_FAILED".equals(event.eventType())) {
                toolFailures++;
            }
            if ("APPROVAL_REQUESTED".equals(event.eventType())) {
                approvals++;
            }
            if (isTimeout(event)) {
                timeouts++;
            }
        }

        int modelCalls = 0;
        int toolCalls = 0;
        int actions = 0;
        int waits = 0;
        long inputTokens = 0L;
        long outputTokens = 0L;
        for (TraceSummaryView summary : summaries) {
            modelCalls += summary.getModelCalls();
            toolCalls += summary.getToolCalls();
            actions += summary.getActions();
            waits += summary.getWaits();
            inputTokens += summary.getInputTokens();
            outputTokens += summary.getOutputTokens();
        }

        String windowStart = events.isEmpty() ? null : events.get(0).occurredAt().toString();
        String windowEnd = events.isEmpty()
                ? null : events.get(events.size() - 1).occurredAt().toString();
        return new MonitoringOverviewView(
                windowStart,
                windowEnd,
                summaries.size(),
                events.size(),
                count(statusCounts, TraceStatus.COMPLETED),
                count(statusCounts, TraceStatus.FAILED),
                count(statusCounts, TraceStatus.WAITING),
                count(statusCounts, TraceStatus.RUNNING),
                count(statusCounts, TraceStatus.INTERRUPTED),
                count(statusCounts, TraceStatus.CANCELLED),
                count(statusCounts, TraceStatus.UNKNOWN),
                terminalCount == 0 ? 0L : terminalDuration / terminalCount,
                modelCalls,
                toolCalls,
                toolFailures,
                actions,
                approvals,
                waits,
                timeouts,
                inputTokens,
                outputTokens);
    }

    private static Map<TraceStatus, Integer> statusCounts(List<TraceSummaryView> summaries) {
        Map<TraceStatus, Integer> counts = new EnumMap<TraceStatus, Integer>(TraceStatus.class);
        for (TraceStatus status : TraceStatus.values()) {
            counts.put(status, 0);
        }
        for (TraceSummaryView summary : summaries) {
            TraceStatus status = summary.getStatus();
            counts.put(status, counts.get(status) + 1);
        }
        return counts;
    }

    private static List<MonitoringStatusView> statuses(
            int total,
            Map<TraceStatus, Integer> counts) {
        List<MonitoringStatusView> statuses = new ArrayList<MonitoringStatusView>();
        for (TraceStatus status : TraceStatus.values()) {
            statuses.add(new MonitoringStatusView(status, count(counts, status), total));
        }
        return statuses;
    }

    private static List<MonitoringSourceView> sources(List<ObservationRecord> events) {
        Map<String, SourceAccumulator> accumulators =
                new LinkedHashMap<String, SourceAccumulator>();
        for (ObservationRecord event : events) {
            SourceAccumulator accumulator = accumulators.get(event.source());
            if (accumulator == null) {
                accumulator = new SourceAccumulator(event.source());
                accumulators.put(event.source(), accumulator);
            }
            accumulator.eventCount++;
            accumulator.traceIds.add(event.traceId());
        }
        List<SourceAccumulator> ordered = new ArrayList<SourceAccumulator>(accumulators.values());
        Collections.sort(ordered, new Comparator<SourceAccumulator>() {
            @Override
            public int compare(SourceAccumulator left, SourceAccumulator right) {
                int byEvents = Integer.compare(right.eventCount, left.eventCount);
                return byEvents != 0 ? byEvents : left.source.compareTo(right.source);
            }
        });
        List<MonitoringSourceView> result = new ArrayList<MonitoringSourceView>();
        for (SourceAccumulator accumulator : ordered) {
            result.add(new MonitoringSourceView(
                    accumulator.source,
                    accumulator.eventCount,
                    accumulator.traceIds.size()));
        }
        return result;
    }

    private static List<MonitoringOperationView> operations(List<ObservationRecord> events) {
        Map<String, OperationAccumulator> accumulators =
                new LinkedHashMap<String, OperationAccumulator>();
        Map<String, Deque<OperationStart>> active =
                new HashMap<String, Deque<OperationStart>>();
        for (ObservationRecord event : events) {
            String category = operationCategory(event.eventType());
            if (category == null) {
                continue;
            }
            if (!isOperationStart(event.eventType())
                    && !isOperationTerminal(event.eventType())) {
                continue;
            }
            String name = operationName(event, category);
            String aggregateKey = category + "\u0000" + name;
            OperationAccumulator accumulator = accumulators.get(aggregateKey);
            if (accumulator == null) {
                accumulator = new OperationAccumulator(category, name);
                accumulators.put(aggregateKey, accumulator);
            }
            String operationKey = operationKey(event, category, name);
            if (isOperationStart(event.eventType())) {
                accumulator.count++;
                Deque<OperationStart> starts = active.get(operationKey);
                if (starts == null) {
                    starts = new ArrayDeque<OperationStart>();
                    active.put(operationKey, starts);
                }
                starts.addLast(new OperationStart(event.occurredAt(), accumulator));
            } else if (isOperationTerminal(event.eventType())) {
                Deque<OperationStart> starts = active.get(operationKey);
                OperationStart start = starts == null ? null : starts.pollFirst();
                OperationAccumulator selected = start == null ? accumulator : start.accumulator;
                if (start == null) {
                    selected.count++;
                }
                selected.completed++;
                if (isFailure(event.eventType())) {
                    selected.failures++;
                }
                if (start != null) {
                    selected.durationTotal += elapsed(start.startedAt, event.occurredAt());
                    selected.durationCount++;
                }
                if (starts != null && starts.isEmpty()) {
                    active.remove(operationKey);
                }
            }
        }

        List<OperationAccumulator> ordered =
                new ArrayList<OperationAccumulator>(accumulators.values());
        Collections.sort(ordered, new Comparator<OperationAccumulator>() {
            @Override
            public int compare(OperationAccumulator left, OperationAccumulator right) {
                int byFailures = Integer.compare(right.failures, left.failures);
                if (byFailures != 0) {
                    return byFailures;
                }
                int byCount = Integer.compare(right.count, left.count);
                if (byCount != 0) {
                    return byCount;
                }
                int byCategory = left.category.compareTo(right.category);
                return byCategory != 0 ? byCategory : left.name.compareTo(right.name);
            }
        });
        List<MonitoringOperationView> result = new ArrayList<MonitoringOperationView>();
        int limit = Math.min(MAX_OPERATION_ROWS, ordered.size());
        for (int index = 0; index < limit; index++) {
            OperationAccumulator selected = ordered.get(index);
            result.add(new MonitoringOperationView(
                    selected.category,
                    selected.name,
                    selected.count,
                    selected.completed,
                    selected.failures,
                    selected.durationTotal,
                    selected.durationCount));
        }
        return result;
    }

    private static List<MonitoringTransitionView> transitions(List<ObservationRecord> events) {
        Map<String, TransitionAccumulator> accumulators =
                new LinkedHashMap<String, TransitionAccumulator>();
        Map<String, Integer> totals = new HashMap<String, Integer>();
        for (ObservationRecord event : events) {
            if (!isStepTransition(event.eventType())) {
                continue;
            }
            String step = firstText(event.attributes(), "flower.step.id");
            if (step == null) {
                step = event.operationName() == null ? "unknown-step" : event.operationName();
            }
            String outcome = firstText(event.attributes(), "flower.step.outcome");
            if (outcome == null) {
                outcome = transitionOutcome(event.eventType());
            }
            String target = firstText(event.attributes(), "flower.step.target.id");
            if (target == null) {
                target = "STEP_COMPLETED".equals(event.eventType()) ? "END" : outcome;
            }
            String key = step + "\u0000" + outcome + "\u0000" + target;
            TransitionAccumulator accumulator = accumulators.get(key);
            if (accumulator == null) {
                accumulator = new TransitionAccumulator(step, outcome, target);
                accumulators.put(key, accumulator);
            }
            accumulator.count++;
            Integer total = totals.get(step);
            totals.put(step, total == null ? 1 : total + 1);
        }
        List<TransitionAccumulator> ordered =
                new ArrayList<TransitionAccumulator>(accumulators.values());
        Collections.sort(ordered, new Comparator<TransitionAccumulator>() {
            @Override
            public int compare(TransitionAccumulator left, TransitionAccumulator right) {
                int byCount = Integer.compare(right.count, left.count);
                if (byCount != 0) {
                    return byCount;
                }
                int byStep = left.stepId.compareTo(right.stepId);
                if (byStep != 0) {
                    return byStep;
                }
                int byOutcome = left.outcome.compareTo(right.outcome);
                return byOutcome != 0
                        ? byOutcome : left.targetStepId.compareTo(right.targetStepId);
            }
        });
        List<MonitoringTransitionView> result = new ArrayList<MonitoringTransitionView>();
        for (TransitionAccumulator selected : ordered) {
            result.add(new MonitoringTransitionView(
                    selected.stepId,
                    selected.outcome,
                    selected.targetStepId,
                    selected.count,
                    totals.get(selected.stepId)));
        }
        return result;
    }

    private static List<MonitoringTimeBucketView> activity(
            List<ObservationRecord> events,
            List<TraceSummaryView> summaries) {
        if (events.isEmpty()) {
            return Collections.emptyList();
        }
        long first = events.get(0).occurredAt().toEpochMilli();
        long last = events.get(events.size() - 1).occurredAt().toEpochMilli();
        long width = bucketWidth(last - first);
        long start = Math.floorDiv(first, width) * width;
        long end = Math.floorDiv(last, width) * width;
        Map<Long, BucketAccumulator> buckets = new LinkedHashMap<Long, BucketAccumulator>();
        for (long cursor = start; cursor <= end; cursor += width) {
            buckets.put(cursor, new BucketAccumulator(cursor));
        }
        for (TraceSummaryView summary : summaries) {
            long timestamp = Instant.parse(summary.getStartedAt()).toEpochMilli();
            BucketAccumulator bucket = bucket(buckets, timestamp, start, width);
            bucket.traces++;
            if (summary.getStatus() == TraceStatus.FAILED) {
                bucket.failures++;
            }
        }
        for (ObservationRecord event : events) {
            BucketAccumulator bucket = bucket(
                    buckets, event.occurredAt().toEpochMilli(), start, width);
            if ("MODEL_CALL_SUBMITTED".equals(event.eventType())) {
                bucket.modelCalls++;
            } else if ("TOOL_CALL_STARTED".equals(event.eventType())) {
                bucket.toolCalls++;
            } else if ("MODEL_CALL_COMPLETED".equals(event.eventType())) {
                bucket.inputTokens += firstLong(
                        event.attributes(), "ai.usage.input.tokens", "agent.inputTokens");
                bucket.outputTokens += firstLong(
                        event.attributes(), "ai.usage.output.tokens", "agent.outputTokens");
            }
        }
        List<MonitoringTimeBucketView> result = new ArrayList<MonitoringTimeBucketView>();
        for (BucketAccumulator selected : buckets.values()) {
            result.add(new MonitoringTimeBucketView(
                    Instant.ofEpochMilli(selected.startedAt).toString(),
                    Instant.ofEpochMilli(selected.startedAt + width).toString(),
                    selected.traces,
                    selected.failures,
                    selected.modelCalls,
                    selected.toolCalls,
                    selected.inputTokens,
                    selected.outputTokens));
        }
        return result;
    }

    private static MonitoringEvaluationView evaluation(
            List<EvaluationExperimentResult> experiments) {
        int cases = 0;
        int passed = 0;
        int failed = 0;
        int errors = 0;
        EvaluationExperimentResult latest = null;
        Map<String, EvaluationExperimentResult> byId =
                new HashMap<String, EvaluationExperimentResult>();
        for (EvaluationExperimentResult experiment : experiments) {
            byId.put(experiment.getExperimentId(), experiment);
            EvaluationSummary summary = experiment.getSummary();
            cases += summary.getTotal();
            passed += summary.getPassed();
            failed += summary.getFailed();
            errors += summary.getErrors();
            if (latest == null || Instant.parse(experiment.getCompletedAt()).isAfter(
                    Instant.parse(latest.getCompletedAt()))) {
                latest = experiment;
            }
        }
        int regressions = 0;
        int improvements = 0;
        if (latest != null && latest.getBaselineExperimentId() != null) {
            EvaluationExperimentResult baseline = byId.get(latest.getBaselineExperimentId());
            if (baseline != null) {
                try {
                    EvaluationComparison comparison = EvaluationComparator.compare(baseline, latest);
                    regressions = comparison.getRegressedExampleIds().size();
                    improvements = comparison.getImprovedExampleIds().size();
                } catch (IllegalArgumentException incompatible) {
                    // The dashboard remains available when local experiment files do not align.
                }
            }
        }
        return new MonitoringEvaluationView(
                experiments.size(),
                cases,
                passed,
                failed,
                errors,
                latest == null ? null : latest.getExperimentId(),
                latest == null ? null : latest.getCandidateId(),
                latest == null ? null : latest.getCandidateVersion(),
                latest == null ? 0.0d : latest.getSummary().getPassRate(),
                regressions,
                improvements);
    }

    private static int count(Map<TraceStatus, Integer> counts, TraceStatus status) {
        Integer count = counts.get(status);
        return count == null ? 0 : count;
    }

    private static boolean isTerminal(TraceStatus status) {
        return status == TraceStatus.COMPLETED
                || status == TraceStatus.FAILED
                || status == TraceStatus.CANCELLED
                || status == TraceStatus.INTERRUPTED;
    }

    private static boolean isTimeout(ObservationRecord event) {
        if (event.eventType().contains("TIMEOUT")) {
            return true;
        }
        String reason = firstText(event.attributes(), "flower.resume.reason");
        return reason != null && reason.toUpperCase(java.util.Locale.ROOT).contains("TIMEOUT");
    }

    private static String operationCategory(String type) {
        if (type.startsWith("STEP_")) {
            return "STEP";
        }
        if (type.startsWith("MODEL_CALL_")) {
            return "MODEL";
        }
        if (type.startsWith("TOOL_CALL_")) {
            return "TOOL";
        }
        if (type.startsWith("ACTION_EXECUTION_")) {
            return "ACTION";
        }
        return null;
    }

    private static String operationName(ObservationRecord event, String category) {
        String name;
        if ("STEP".equals(category)) {
            name = firstText(event.attributes(), "flower.step.id");
        } else if ("ACTION".equals(category)) {
            name = firstText(event.attributes(), "action.id");
        } else {
            name = null;
        }
        if (name == null) {
            name = event.operationName();
        }
        return name == null ? category.toLowerCase(java.util.Locale.ROOT) : name;
    }

    private static String operationKey(
            ObservationRecord event,
            String category,
            String name) {
        String operation = event.operationId() == null ? name : event.operationId();
        return event.source() + "\u0000" + event.traceId() + "\u0000"
                + event.runId() + "\u0000"
                + category + "\u0000" + operation;
    }

    private static boolean isOperationStart(String type) {
        return "STEP_STARTED".equals(type)
                || "MODEL_CALL_SUBMITTED".equals(type)
                || "TOOL_CALL_STARTED".equals(type)
                || "ACTION_EXECUTION_STARTED".equals(type);
    }

    private static boolean isOperationTerminal(String type) {
        return "STEP_COMPLETED".equals(type)
                || "STEP_SKIPPED".equals(type)
                || "STEP_FAILED".equals(type)
                || "STEP_CANCELLED".equals(type)
                || "MODEL_CALL_COMPLETED".equals(type)
                || "MODEL_CALL_FAILED".equals(type)
                || "TOOL_CALL_COMPLETED".equals(type)
                || "TOOL_CALL_FAILED".equals(type)
                || "ACTION_EXECUTION_COMPLETED".equals(type)
                || "ACTION_EXECUTION_FAILED".equals(type)
                || "ACTION_EXECUTION_CANCELLED".equals(type)
                || "ACTION_EXECUTION_DEFERRED".equals(type);
    }

    private static boolean isFailure(String type) {
        return type.endsWith("_FAILED")
                || type.contains("DENIED")
                || type.contains("REJECTED")
                || type.contains("EXPIRED")
                || type.contains("BUDGET_EXHAUSTED");
    }

    private static boolean isStepTransition(String type) {
        return "STEP_COMPLETED".equals(type)
                || "STEP_SKIPPED".equals(type)
                || "STEP_FAILED".equals(type)
                || "STEP_CANCELLED".equals(type);
    }

    private static String transitionOutcome(String type) {
        return type.substring("STEP_".length());
    }

    private static long elapsed(Instant start, Instant end) {
        return Math.max(0L, Duration.between(start, end).toMillis());
    }

    private static long bucketWidth(long span) {
        for (long width : BUCKET_WIDTHS_MILLIS) {
            if (span / width < 72L) {
                return width;
            }
        }
        long minimum = Math.max(1L, span / 71L);
        long day = 24L * 60L * 60_000L;
        return ((minimum + day - 1L) / day) * day;
    }

    private static BucketAccumulator bucket(
            Map<Long, BucketAccumulator> buckets,
            long timestamp,
            long start,
            long width) {
        return buckets.get(start + Math.floorDiv(timestamp - start, width) * width);
    }

    private static String firstText(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        String selected = String.valueOf(value).trim();
        return selected.isEmpty() ? null : selected;
    }

    private static long firstLong(Map<String, Object> attributes, String... names) {
        for (String name : names) {
            Object value = attributes.get(name);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException ignored) {
                    // Try the next compatible attribute name.
                }
            }
        }
        return 0L;
    }

    private static final class SourceAccumulator {
        private final String source;
        private final Set<String> traceIds = new LinkedHashSet<String>();
        private int eventCount;

        private SourceAccumulator(String source) {
            this.source = source;
        }
    }

    private static final class OperationAccumulator {
        private final String category;
        private final String name;
        private int count;
        private int completed;
        private int failures;
        private long durationTotal;
        private int durationCount;

        private OperationAccumulator(String category, String name) {
            this.category = category;
            this.name = name;
        }
    }

    private static final class OperationStart {
        private final Instant startedAt;
        private final OperationAccumulator accumulator;

        private OperationStart(Instant startedAt, OperationAccumulator accumulator) {
            this.startedAt = startedAt;
            this.accumulator = accumulator;
        }
    }

    private static final class TransitionAccumulator {
        private final String stepId;
        private final String outcome;
        private final String targetStepId;
        private int count;

        private TransitionAccumulator(String stepId, String outcome, String targetStepId) {
            this.stepId = stepId;
            this.outcome = outcome;
            this.targetStepId = targetStepId;
        }
    }

    private static final class BucketAccumulator {
        private final long startedAt;
        private int traces;
        private int failures;
        private int modelCalls;
        private int toolCalls;
        private long inputTokens;
        private long outputTokens;

        private BucketAccumulator(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
