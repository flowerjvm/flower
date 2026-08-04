package io.github.flowerjvm.flower.studio.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.studio.model.ObservationRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cached, bounded reader for common observation and legacy Core trace JSONL.
 * Malformed line contents are never copied into diagnostics.
 */
public final class JsonLinesObservationRepository implements ObservationRepository {

    public static final int DEFAULT_MAX_EVENTS = 100_000;
    private static final int MAX_REPORTED_MALFORMED_LINES = 20;
    private static final TypeReference<LinkedHashMap<String, Object>> ATTRIBUTE_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() { };

    private final Path traceFile;
    private final int maxEvents;
    private final ObjectMapper mapper;
    private FileStamp cachedStamp;
    private StudioSnapshot cachedSnapshot;

    public JsonLinesObservationRepository(Path traceFile) {
        this(traceFile, DEFAULT_MAX_EVENTS, new ObjectMapper());
    }

    public JsonLinesObservationRepository(Path traceFile, int maxEvents) {
        this(traceFile, maxEvents, new ObjectMapper());
    }

    JsonLinesObservationRepository(
            Path traceFile,
            int maxEvents,
            ObjectMapper mapper) {
        if (traceFile == null) {
            throw new IllegalArgumentException("traceFile must not be null");
        }
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        this.traceFile = traceFile.toAbsolutePath().normalize();
        this.maxEvents = maxEvents;
        this.mapper = mapper;
    }

    @Override
    public synchronized StudioSnapshot load() throws IOException {
        FileStamp stamp = FileStamp.read(traceFile);
        if (stamp.equals(cachedStamp) && cachedSnapshot != null) {
            return cachedSnapshot;
        }
        cachedSnapshot = stamp.exists ? readExisting(stamp) : empty(stamp);
        cachedStamp = stamp;
        return cachedSnapshot;
    }

    public Path traceFile() {
        return traceFile;
    }

    public int maxEvents() {
        return maxEvents;
    }

    private StudioSnapshot readExisting(FileStamp stamp) throws IOException {
        LinkedHashMap<String, ObservationRecord> retained = new LinkedHashMap<String, ObservationRecord>();
        List<Long> malformedLines = new ArrayList<Long>();
        long lineCount = 0L;
        long malformed = 0L;
        long ignored = 0L;
        long duplicates = 0L;
        long truncated = 0L;

        try (BufferedReader reader = Files.newBufferedReader(traceFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.trim().isEmpty()) {
                    ignored++;
                    continue;
                }
                ObservationRecord event;
                try {
                    event = parse(line);
                } catch (RuntimeException failure) {
                    malformed++;
                    if (malformedLines.size() < MAX_REPORTED_MALFORMED_LINES) {
                        malformedLines.add(lineCount);
                    }
                    continue;
                }
                String identity = event.source() + "\u0000" + event.eventId();
                if (retained.containsKey(identity)) {
                    duplicates++;
                    continue;
                }
                if (retained.size() == maxEvents) {
                    Iterator<String> oldest = retained.keySet().iterator();
                    oldest.next();
                    oldest.remove();
                    truncated++;
                }
                retained.put(identity, event);
            }
        }

        List<ObservationRecord> events = new ArrayList<ObservationRecord>(retained.values());
        StudioDiagnostics diagnostics = new StudioDiagnostics(
                traceFile.toString(),
                true,
                stamp.size,
                lineCount,
                events.size(),
                malformed,
                ignored,
                duplicates,
                truncated,
                malformedLines,
                Instant.now());
        return new StudioSnapshot(events, diagnostics);
    }

    private StudioSnapshot empty(FileStamp stamp) {
        StudioDiagnostics diagnostics = new StudioDiagnostics(
                traceFile.toString(),
                false,
                stamp.size,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                Collections.<Long>emptyList(),
                Instant.now());
        return new StudioSnapshot(Collections.<ObservationRecord>emptyList(), diagnostics);
    }

    private ObservationRecord parse(String line) {
        try {
            JsonNode root = mapper.readTree(line);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("observation line must be a JSON object");
            }
            boolean legacyCore = text(root, "runId") == null && text(root, "flowRunId") != null;
            String runId = legacyCore ? text(root, "flowRunId") : text(root, "runId");
            String operationId = legacyCore ? text(root, "stepRunId") : text(root, "operationId");
            Map<String, Object> attributes = attributes(root.get("attributes"));
            if (legacyCore) {
                copyRootText(root, attributes, "flowType", "flower.flow.type");
                copyRootText(root, attributes, "flowKey", "flower.flow.key");
                copyRootText(root, attributes, "workerName", "flower.worker.name");
            }
            String operationName = legacyCore
                    ? asText(attributes.get("flower.step.id"))
                    : text(root, "operationName");
            return new ObservationRecord(
                    positiveInt(root, "schemaVersion", 1),
                    requiredText(root, "source"),
                    requiredText(root, "eventId"),
                    requiredText(root, "eventType"),
                    requiredText(root, "traceId"),
                    requireText("runId", runId),
                    text(root, "parentRunId"),
                    operationId,
                    operationName,
                    nonNegativeLong(root, "sequence", 0L),
                    Instant.parse(requiredText(root, "occurredAt")),
                    attributes);
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid JSON", failure);
        }
    }

    private Map<String, Object> attributes(JsonNode node) {
        if (node == null || node.isNull()) {
            return new LinkedHashMap<String, Object>();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("attributes must be a JSON object");
        }
        return mapper.convertValue(node, ATTRIBUTE_TYPE);
    }

    private static void copyRootText(
            JsonNode root,
            Map<String, Object> attributes,
            String rootName,
            String attributeName) {
        String value = text(root, rootName);
        if (value != null && !attributes.containsKey(attributeName)) {
            attributes.put(attributeName, value);
        }
    }

    private static int positiveInt(JsonNode root, String name, int fallback) {
        JsonNode value = root.get(name);
        int selected = value == null || value.isNull() ? fallback : value.asInt(-1);
        if (selected <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return selected;
    }

    private static long nonNegativeLong(JsonNode root, String name, long fallback) {
        JsonNode value = root.get(name);
        long selected = value == null || value.isNull() ? fallback : value.asLong(-1L);
        if (selected < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return selected;
    }

    private static String requiredText(JsonNode root, String name) {
        return requireText(name, text(root, name));
    }

    private static String requireText(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String text(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String selected = value.asText().trim();
        return selected.isEmpty() ? null : selected;
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String selected = String.valueOf(value).trim();
        return selected.isEmpty() ? null : selected;
    }

    private static final class FileStamp {
        private final boolean exists;
        private final long size;
        private final long modifiedMillis;

        private FileStamp(boolean exists, long size, long modifiedMillis) {
            this.exists = exists;
            this.size = size;
            this.modifiedMillis = modifiedMillis;
        }

        private static FileStamp read(Path file) throws IOException {
            if (!Files.isRegularFile(file)) {
                return new FileStamp(false, 0L, 0L);
            }
            return new FileStamp(
                    true,
                    Files.size(file),
                    Files.getLastModifiedTime(file).toMillis());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FileStamp)) {
                return false;
            }
            FileStamp that = (FileStamp) other;
            return exists == that.exists
                    && size == that.size
                    && modifiedMillis == that.modifiedMillis;
        }

        @Override
        public int hashCode() {
            int result = exists ? 1 : 0;
            result = 31 * result + (int) (size ^ (size >>> 32));
            result = 31 * result + (int) (modifiedMillis ^ (modifiedMillis >>> 32));
            return result;
        }
    }
}
