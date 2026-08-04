package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.studio.model.ObservationRecord;
import io.github.flowerjvm.flower.studio.store.StudioSnapshot;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds read-only Trace, Run, and Timeline projections from observation events. */
public final class StudioProjectionService {

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

    private final boolean artifactLinksEnabled;

    public StudioProjectionService(boolean artifactLinksEnabled) {
        this.artifactLinksEnabled = artifactLinksEnabled;
    }

    public TraceListView list(StudioSnapshot snapshot, StudioQuery query) {
        if (snapshot == null || query == null) {
            throw new IllegalArgumentException("snapshot and query must not be null");
        }
        List<TraceProjection> projections = sortedProjections(snapshot.events());

        Set<String> allSources = new LinkedHashSet<String>();
        List<TraceSummaryView> matched = new ArrayList<TraceSummaryView>();
        int totalMatched = 0;
        for (TraceProjection projection : projections) {
            allSources.addAll(projection.sources);
            if (!matches(projection, query)) {
                continue;
            }
            totalMatched++;
            if (matched.size() < query.limit()) {
                matched.add(projection.summary);
            }
        }
        List<String> sources = new ArrayList<String>(allSources);
        Collections.sort(sources);
        return new TraceListView(matched, sources, totalMatched, snapshot.diagnostics());
    }

    /** Returns every projected Trace summary, newest activity first. */
    public List<TraceSummaryView> summaries(StudioSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        List<TraceSummaryView> summaries = new ArrayList<TraceSummaryView>();
        for (TraceProjection projection : sortedProjections(snapshot.events())) {
            summaries.add(projection.summary);
        }
        return Collections.unmodifiableList(summaries);
    }

    public TraceDetailView detail(StudioSnapshot snapshot, String traceId) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (traceId == null || traceId.trim().isEmpty()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        for (TraceProjection projection : projections(snapshot.events())) {
            if (projection.traceId.equals(traceId)) {
                return new TraceDetailView(
                        projection.summary,
                        projection.runs,
                        projection.timeline,
                        snapshot.diagnostics());
            }
        }
        return null;
    }

    private List<TraceProjection> projections(List<ObservationRecord> events) {
        Map<String, List<ObservationRecord>> grouped =
                new LinkedHashMap<String, List<ObservationRecord>>();
        for (ObservationRecord event : events) {
            List<ObservationRecord> trace = grouped.get(event.traceId());
            if (trace == null) {
                trace = new ArrayList<ObservationRecord>();
                grouped.put(event.traceId(), trace);
            }
            trace.add(event);
        }
        List<TraceProjection> projections = new ArrayList<TraceProjection>(grouped.size());
        for (Map.Entry<String, List<ObservationRecord>> entry : grouped.entrySet()) {
            projections.add(project(entry.getKey(), entry.getValue()));
        }
        return projections;
    }

    private List<TraceProjection> sortedProjections(List<ObservationRecord> events) {
        List<TraceProjection> selected = projections(events);
        Collections.sort(selected, new Comparator<TraceProjection>() {
            @Override
            public int compare(TraceProjection left, TraceProjection right) {
                return right.updatedAt.compareTo(left.updatedAt);
            }
        });
        return selected;
    }

    private TraceProjection project(String traceId, List<ObservationRecord> sourceEvents) {
        List<ObservationRecord> events = new ArrayList<ObservationRecord>(sourceEvents);
        Collections.sort(events, EVENT_ORDER);
        Instant startedAt = events.get(0).occurredAt();
        Instant updatedAt = events.get(events.size() - 1).occurredAt();

        Map<String, RunAccumulator> runMap = new LinkedHashMap<String, RunAccumulator>();
        Set<String> sources = new LinkedHashSet<String>();
        int modelCalls = 0;
        int toolCalls = 0;
        int actions = 0;
        int waits = 0;
        int failures = 0;
        long inputTokens = 0L;
        long outputTokens = 0L;

        for (ObservationRecord event : events) {
            sources.add(event.source());
            String runKey = event.source() + "\u0000" + event.runId();
            RunAccumulator run = runMap.get(runKey);
            if (run == null) {
                run = new RunAccumulator(event);
                runMap.put(runKey, run);
            }
            run.accept(event);

            String type = event.eventType();
            if ("MODEL_CALL_SUBMITTED".equals(type)) {
                modelCalls++;
            } else if ("TOOL_CALL_STARTED".equals(type)) {
                toolCalls++;
            } else if ("ACTION_PROPOSED".equals(type)) {
                actions++;
            }
            if (isWait(type)) {
                waits++;
            }
            if (isFailureEvent(type)) {
                failures++;
            }
            if ("MODEL_CALL_COMPLETED".equals(type)) {
                inputTokens += firstLong(
                        event.attributes(), "ai.usage.input.tokens", "agent.inputTokens");
                outputTokens += firstLong(
                        event.attributes(), "ai.usage.output.tokens", "agent.outputTokens");
            }
        }

        Map<String, RunAccumulator> byRunId = new HashMap<String, RunAccumulator>();
        for (RunAccumulator run : runMap.values()) {
            if (!byRunId.containsKey(run.runId)) {
                byRunId.put(run.runId, run);
            }
        }
        List<RunAccumulator> orderedRuns = new ArrayList<RunAccumulator>(runMap.values());
        Collections.sort(orderedRuns, new Comparator<RunAccumulator>() {
            @Override
            public int compare(RunAccumulator left, RunAccumulator right) {
                int byStart = left.startedAt.compareTo(right.startedAt);
                if (byStart != 0) {
                    return byStart;
                }
                return left.source.compareTo(right.source);
            }
        });
        List<RunSummaryView> runs = new ArrayList<RunSummaryView>(runMap.size());
        for (RunAccumulator run : orderedRuns) {
            int depth = depth(run, byRunId, new HashSet<String>());
            runs.add(run.toView(depth));
        }

        RunSummaryView root = rootRun(runs);
        TraceStatus traceStatus = traceStatus(root, runs);
        List<TimelineEventView> timeline = timeline(events, startedAt);
        List<String> sourceList = new ArrayList<String>(sources);
        Collections.sort(sourceList);
        String displayName = root == null ? traceId : root.getLabel();
        String rootRunId = root == null ? null : root.getRunId();
        TraceSummaryView summary = new TraceSummaryView(
                traceId,
                displayName,
                traceStatus,
                startedAt.toString(),
                updatedAt.toString(),
                elapsed(startedAt, updatedAt),
                events.size(),
                runs.size(),
                modelCalls,
                toolCalls,
                actions,
                waits,
                failures,
                inputTokens,
                outputTokens,
                sourceList,
                rootRunId);
        return new TraceProjection(
                traceId,
                summary,
                runs,
                timeline,
                sources,
                updatedAt,
                searchable(traceId, runs, events));
    }

    private List<TimelineEventView> timeline(
            List<ObservationRecord> events,
            Instant traceStart) {
        Map<String, Instant> starts = new HashMap<String, Instant>();
        List<TimelineEventView> timeline = new ArrayList<TimelineEventView>(events.size());
        for (ObservationRecord event : events) {
            String family = operationFamily(event.eventType());
            String key = operationKey(event, family);
            if (key != null && isStartEvent(event.eventType())) {
                starts.put(key, event.occurredAt());
            }
            Long duration = null;
            if (key != null && isTerminalOperationEvent(event.eventType())) {
                Instant start = starts.remove(key);
                if (start != null) {
                    duration = elapsed(start, event.occurredAt());
                }
            }
            timeline.add(new TimelineEventView(
                    event.eventId(),
                    event.source(),
                    event.eventType(),
                    event.runId(),
                    event.parentRunId(),
                    event.operationId(),
                    event.operationName(),
                    event.sequence(),
                    event.occurredAt().toString(),
                    elapsed(traceStart, event.occurredAt()),
                    duration,
                    category(event),
                    tone(event.eventType()),
                    summary(event),
                    event.attributes(),
                    artifacts(event.attributes())));
        }
        return timeline;
    }

    private List<ArtifactLinkView> artifacts(Map<String, Object> attributes) {
        if (!artifactLinksEnabled) {
            return Collections.emptyList();
        }
        List<ArtifactLinkView> artifacts = new ArrayList<ArtifactLinkView>();
        scanArtifacts(attributes, "attributes", 0, artifacts);
        return artifacts;
    }

    private void scanArtifacts(
            Object value,
            String path,
            int depth,
            List<ArtifactLinkView> artifacts) {
        if (value == null || depth > 12) {
            return;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            String capture = objectText(map.get("capture"));
            String location = objectText(map.get("location"));
            if ("ARTIFACT".equals(capture) && safeArtifactLocation(location)) {
                artifacts.add(new ArtifactLinkView(
                        path,
                        objectText(map.get("artifactId")),
                        location,
                        objectText(map.get("mediaType")),
                        objectLong(map.get("sizeBytes")),
                        objectText(map.get("sha256")),
                        "/api/artifacts?location=" + urlEncode(location)));
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String) {
                    scanArtifacts(
                            entry.getValue(),
                            path + "." + entry.getKey(),
                            depth + 1,
                            artifacts);
                }
            }
        } else if (value instanceof Iterable<?>) {
            int index = 0;
            for (Object element : (Iterable<?>) value) {
                scanArtifacts(element, path + "[" + index + "]", depth + 1, artifacts);
                index++;
            }
        }
    }

    private static boolean matches(TraceProjection projection, StudioQuery query) {
        if (query.source() != null && !projection.sources.contains(query.source())) {
            return false;
        }
        if (query.status() != null && projection.summary.getStatus() != query.status()) {
            return false;
        }
        return query.text() == null
                || projection.searchText.contains(query.text().toLowerCase(Locale.ROOT));
    }

    private static int depth(
            RunAccumulator run,
            Map<String, RunAccumulator> byRunId,
            Set<String> visiting) {
        if (run.parentRunId == null) {
            return 0;
        }
        if (!visiting.add(run.runId)) {
            return 0;
        }
        RunAccumulator parent = byRunId.get(run.parentRunId);
        int selected = parent == null ? 0 : 1 + depth(parent, byRunId, visiting);
        visiting.remove(run.runId);
        return Math.min(selected, 12);
    }

    private static RunSummaryView rootRun(List<RunSummaryView> runs) {
        for (RunSummaryView run : runs) {
            if (run.getDepth() == 0) {
                return run;
            }
        }
        return runs.isEmpty() ? null : runs.get(0);
    }

    private static TraceStatus traceStatus(
            RunSummaryView root,
            List<RunSummaryView> runs) {
        if (root != null && isFinalTraceStatus(root.getStatus())) {
            return root.getStatus();
        }
        TraceStatus selected = TraceStatus.UNKNOWN;
        int priority = -1;
        for (RunSummaryView run : runs) {
            int candidate = statusPriority(run.getStatus());
            if (candidate > priority) {
                selected = run.getStatus();
                priority = candidate;
            }
        }
        return selected;
    }

    private static boolean isFinalTraceStatus(TraceStatus status) {
        return status == TraceStatus.COMPLETED
                || status == TraceStatus.FAILED
                || status == TraceStatus.CANCELLED
                || status == TraceStatus.INTERRUPTED;
    }

    private static int statusPriority(TraceStatus status) {
        switch (status) {
            case FAILED:
                return 60;
            case CANCELLED:
                return 50;
            case INTERRUPTED:
                return 40;
            case WAITING:
                return 30;
            case RUNNING:
                return 20;
            case COMPLETED:
                return 10;
            default:
                return 0;
        }
    }

    private static String searchable(
            String traceId,
            List<RunSummaryView> runs,
            List<ObservationRecord> events) {
        StringBuilder text = new StringBuilder(traceId);
        for (RunSummaryView run : runs) {
            text.append(' ').append(run.getRunId()).append(' ').append(run.getLabel());
        }
        for (ObservationRecord event : events) {
            if (event.operationName() != null) {
                text.append(' ').append(event.operationName());
            }
            text.append(' ').append(event.eventType()).append(' ').append(event.source());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static String summary(ObservationRecord event) {
        Map<String, Object> attributes = event.attributes();
        String step = firstText(attributes, "flower.step.id");
        String outcome = firstText(attributes, "flower.step.outcome");
        String target = firstText(attributes, "flower.step.target.id");
        if (step != null && outcome != null) {
            return target == null
                    ? step + " / " + outcome
                    : step + " / " + outcome + " -> " + target;
        }
        String resume = firstText(attributes, "flower.resume.reason");
        if (resume != null) {
            return "resume / " + resume;
        }
        String status = firstText(
                attributes,
                "status",
                "agent.status",
                "ai.run.status",
                "action.payload.status");
        if (event.operationName() != null && status != null) {
            return event.operationName() + " / " + status;
        }
        if (event.operationName() != null) {
            return event.operationName();
        }
        String error = firstText(attributes, "error.type", "agent.failureCode");
        if (error != null) {
            return error;
        }
        return status == null ? "" : status;
    }

    private static String category(ObservationRecord event) {
        String eventType = event.eventType();
        if (eventType.contains("CHECKPOINT") || "FLOW_RECOVERED".equals(eventType)) {
            return "CHECKPOINT";
        }
        if (eventType.contains("APPROVAL")) {
            return "APPROVAL";
        }
        if ("flower-action-runtime".equals(event.source())) {
            return "ACTION";
        }
        if (eventType.startsWith("MODEL_")) {
            return "MODEL";
        }
        if (eventType.startsWith("TOOL_")) {
            return "TOOL";
        }
        if (eventType.startsWith("ACTION_")) {
            return "ACTION";
        }
        if (eventType.startsWith("STEP_")) {
            return "STEP";
        }
        if (eventType.startsWith("FLOW_")) {
            return "FLOW";
        }
        if (eventType.startsWith("RUN_")) {
            return "RUN";
        }
        return "OTHER";
    }

    private static String tone(String eventType) {
        if (isFailureEvent(eventType)) {
            return "danger";
        }
        if (eventType.contains("CANCELLED")
                || eventType.contains("INTERRUPTED")
                || isWait(eventType)) {
            return "warning";
        }
        if (eventType.contains("COMPLETED")
                || eventType.contains("APPROVED")
                || eventType.contains("RESUMED")) {
            return "success";
        }
        if (eventType.contains("STARTED") || eventType.contains("SUBMITTED")) {
            return "info";
        }
        return "neutral";
    }

    private static boolean isFailureEvent(String type) {
        return type.endsWith("_FAILED")
                || type.contains("DENIED")
                || type.contains("REJECTED")
                || type.contains("EXPIRED")
                || type.contains("BUDGET_EXHAUSTED");
    }

    private static boolean isWait(String type) {
        return "FLOW_WAITING".equals(type)
                || "FLOW_SUSPENDED".equals(type)
                || "APPROVAL_REQUESTED".equals(type)
                || "ACTION_EXECUTION_DEFERRED".equals(type)
                || "RUN_INTERRUPTED".equals(type);
    }

    private static boolean isStartEvent(String type) {
        return "FLOW_STARTED".equals(type)
                || "FLOW_RECOVERED".equals(type)
                || "STEP_STARTED".equals(type)
                || "RUN_STARTED".equals(type)
                || "MODEL_CALL_SUBMITTED".equals(type)
                || "TOOL_CALL_STARTED".equals(type)
                || "ACTION_EXECUTION_STARTED".equals(type);
    }

    private static boolean isTerminalOperationEvent(String type) {
        return "FLOW_COMPLETED".equals(type)
                || "FLOW_FAILED".equals(type)
                || "FLOW_CANCELLED".equals(type)
                || "STEP_COMPLETED".equals(type)
                || "STEP_FAILED".equals(type)
                || "STEP_CANCELLED".equals(type)
                || "RUN_COMPLETED".equals(type)
                || "RUN_FAILED".equals(type)
                || "RUN_CANCELLED".equals(type)
                || "RUN_INTERRUPTED".equals(type)
                || "RUN_BUDGET_EXHAUSTED".equals(type)
                || "MODEL_CALL_COMPLETED".equals(type)
                || "MODEL_CALL_FAILED".equals(type)
                || "TOOL_CALL_COMPLETED".equals(type)
                || "TOOL_CALL_FAILED".equals(type)
                || "ACTION_EXECUTION_COMPLETED".equals(type)
                || "ACTION_EXECUTION_FAILED".equals(type)
                || "ACTION_EXECUTION_CANCELLED".equals(type)
                || "ACTION_EXECUTION_DEFERRED".equals(type);
    }

    private static String operationFamily(String type) {
        if (type.startsWith("FLOW_")) {
            return "FLOW";
        }
        if (type.startsWith("STEP_")) {
            return "STEP";
        }
        if (type.startsWith("RUN_")) {
            return "RUN";
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

    private static String operationKey(ObservationRecord event, String family) {
        if (family == null) {
            return null;
        }
        String operation = event.operationId();
        if (operation == null && ("FLOW".equals(family) || "RUN".equals(family))) {
            operation = event.runId();
        }
        if (operation == null) {
            operation = event.operationName();
        }
        return operation == null
                ? null
                : event.source() + "\u0000" + event.runId() + "\u0000" + family + "\u0000" + operation;
    }

    private static long elapsed(Instant start, Instant end) {
        return Math.max(0L, Duration.between(start, end).toMillis());
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

    private static String firstText(Map<String, Object> attributes, String... names) {
        for (String name : names) {
            String value = objectText(attributes.get(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String objectText(Object value) {
        if (value == null) {
            return null;
        }
        String selected = String.valueOf(value).trim();
        return selected.isEmpty() ? null : selected;
    }

    private static long objectLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static boolean safeArtifactLocation(String location) {
        if (location == null) {
            return false;
        }
        try {
            Path path = Paths.get(location).normalize();
            return !path.isAbsolute()
                    && path.getNameCount() > 0
                    && !path.startsWith("..");
        } catch (RuntimeException invalidPath) {
            return false;
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable", impossible);
        }
    }

    private static final class TraceProjection {
        private final String traceId;
        private final TraceSummaryView summary;
        private final List<RunSummaryView> runs;
        private final List<TimelineEventView> timeline;
        private final Set<String> sources;
        private final Instant updatedAt;
        private final String searchText;

        private TraceProjection(
                String traceId,
                TraceSummaryView summary,
                List<RunSummaryView> runs,
                List<TimelineEventView> timeline,
                Set<String> sources,
                Instant updatedAt,
                String searchText) {
            this.traceId = traceId;
            this.summary = summary;
            this.runs = runs;
            this.timeline = timeline;
            this.sources = sources;
            this.updatedAt = updatedAt;
            this.searchText = searchText;
        }
    }

    private static final class RunAccumulator {
        private final String runId;
        private final String source;
        private final String parentRunId;
        private final Instant startedAt;
        private Instant updatedAt;
        private int eventCount;
        private TraceStatus status = TraceStatus.UNKNOWN;
        private String label;

        private RunAccumulator(ObservationRecord first) {
            this.runId = first.runId();
            this.source = first.source();
            this.parentRunId = first.parentRunId();
            this.startedAt = first.occurredAt();
            this.updatedAt = first.occurredAt();
            this.label = runLabel(first);
        }

        private void accept(ObservationRecord event) {
            eventCount++;
            updatedAt = event.occurredAt();
            String selectedLabel = runLabel(event);
            if (label.equals(runId) && !selectedLabel.equals(runId)) {
                label = selectedLabel;
            }
            TraceStatus terminal = terminalStatus(event.eventType());
            if (terminal != null) {
                status = terminal;
            } else if (!isTerminal(status)) {
                if ("RUN_INTERRUPTED".equals(event.eventType())) {
                    status = TraceStatus.INTERRUPTED;
                } else if (isWait(event.eventType())) {
                    status = TraceStatus.WAITING;
                } else {
                    status = TraceStatus.RUNNING;
                }
            }
        }

        private RunSummaryView toView(int depth) {
            return new RunSummaryView(
                    runId,
                    source,
                    parentRunId,
                    label,
                    status,
                    startedAt.toString(),
                    updatedAt.toString(),
                    elapsed(startedAt, updatedAt),
                    eventCount,
                    depth);
        }

        private static String runLabel(ObservationRecord event) {
            Map<String, Object> attributes = event.attributes();
            if ("flower-core".equals(event.source())) {
                String type = firstText(attributes, "flower.flow.type");
                String key = firstText(attributes, "flower.flow.key");
                if (type != null) {
                    return key == null ? type : type + " / " + key;
                }
            } else if ("flower-agent".equals(event.source())) {
                String agent = firstText(attributes, "agent.id");
                String recipe = firstText(attributes, "agent.recipe.id");
                if (agent != null) {
                    return recipe == null ? agent : agent + " / " + recipe;
                }
            } else if ("flower-ai-harness".equals(event.source())) {
                String harness = firstText(attributes, "ai.harness.id");
                if (harness != null) {
                    return harness;
                }
            } else if ("flower-action-runtime".equals(event.source())) {
                String action = firstText(attributes, "action.id");
                if (action != null) {
                    return action;
                }
            }
            return event.operationName() == null ? event.runId() : event.operationName();
        }

        private static TraceStatus terminalStatus(String type) {
            if ("FLOW_COMPLETED".equals(type)
                    || "RUN_COMPLETED".equals(type)
                    || "ACTION_EXECUTION_COMPLETED".equals(type)
                    || "ACTION_DUPLICATE".equals(type)) {
                return TraceStatus.COMPLETED;
            }
            if ("FLOW_FAILED".equals(type)
                    || "RUN_FAILED".equals(type)
                    || "RUN_BUDGET_EXHAUSTED".equals(type)
                    || "ACTION_EXECUTION_FAILED".equals(type)
                    || "ACTION_RUNTIME_FAILED".equals(type)
                    || "ACTION_DENIED".equals(type)
                    || "APPROVAL_REJECTED".equals(type)
                    || "APPROVAL_EXPIRED".equals(type)) {
                return TraceStatus.FAILED;
            }
            if ("FLOW_CANCELLED".equals(type)
                    || "RUN_CANCELLED".equals(type)
                    || "ACTION_EXECUTION_CANCELLED".equals(type)) {
                return TraceStatus.CANCELLED;
            }
            if ("RUN_INTERRUPTED".equals(type)) {
                return TraceStatus.INTERRUPTED;
            }
            return null;
        }

        private static boolean isTerminal(TraceStatus status) {
            return status == TraceStatus.COMPLETED
                    || status == TraceStatus.FAILED
                    || status == TraceStatus.CANCELLED;
        }
    }
}
