package io.github.flowerjvm.flower.evaluation.storage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tolerant, bounded reader for local evaluation result JSON Lines files. */
public final class JsonLinesEvaluationResultSource {

    private static final int MAX_REPORTED_MALFORMED_LINES = 20;

    private final Path file;
    private final int maxRecords;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JsonLinesEvaluationResultSource(Path file, int maxRecords) {
        this(file, maxRecords, new ObjectMapper(), Clock.systemUTC());
    }

    JsonLinesEvaluationResultSource(
            Path file,
            int maxRecords,
            ObjectMapper objectMapper,
            Clock clock) {
        if (file == null || objectMapper == null || clock == null) {
            throw new IllegalArgumentException("file, objectMapper, and clock must not be null");
        }
        if (maxRecords <= 0) {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
        this.file = file.toAbsolutePath().normalize();
        this.maxRecords = maxRecords;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.clock = clock;
    }

    public EvaluationResultSnapshot load() throws IOException {
        if (!Files.exists(file)) {
            return new EvaluationResultSnapshot(
                    Collections.<EvaluationExperimentResult>emptyList(),
                    diagnostics(false, 0L, 0L, 0, 0L, 0L, 0L, 0L,
                            Collections.<Long>emptyList()));
        }

        Map<String, EvaluationExperimentResult> records =
                new LinkedHashMap<String, EvaluationExperimentResult>();
        long lineCount = 0L;
        long malformed = 0L;
        long ignored = 0L;
        long duplicates = 0L;
        List<Long> malformedLines = new ArrayList<Long>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.trim().isEmpty()) {
                    ignored++;
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(line);
                    if (!EvaluationExperimentResult.RECORD_TYPE.equals(
                            node.path("recordType").asText())) {
                        ignored++;
                        continue;
                    }
                    EvaluationExperimentResult result = objectMapper.treeToValue(
                            node, EvaluationExperimentResult.class);
                    if (records.remove(result.getExperimentId()) != null) {
                        duplicates++;
                    }
                    records.put(result.getExperimentId(), result);
                } catch (RuntimeException | IOException failure) {
                    malformed++;
                    if (malformedLines.size() < MAX_REPORTED_MALFORMED_LINES) {
                        malformedLines.add(lineCount);
                    }
                }
            }
        }

        long truncated = trimOldest(records, maxRecords);
        List<EvaluationExperimentResult> selected =
                new ArrayList<EvaluationExperimentResult>(records.values());
        Collections.reverse(selected);
        return new EvaluationResultSnapshot(
                selected,
                diagnostics(
                        true,
                        Files.size(file),
                        lineCount,
                        selected.size(),
                        malformed,
                        ignored,
                        duplicates,
                        truncated,
                        malformedLines));
    }

    public Path file() {
        return file;
    }

    private EvaluationLoadDiagnostics diagnostics(
            boolean exists,
            long fileSize,
            long lineCount,
            int recordCount,
            long malformed,
            long ignored,
            long duplicates,
            long truncated,
            List<Long> malformedLines) {
        return new EvaluationLoadDiagnostics(
                file,
                exists,
                fileSize,
                lineCount,
                recordCount,
                malformed,
                ignored,
                duplicates,
                truncated,
                malformedLines,
                clock.instant());
    }

    private static long trimOldest(Map<String, ?> records, int maximum) {
        long removed = 0L;
        Iterator<String> iterator = records.keySet().iterator();
        while (records.size() > maximum && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
            removed++;
        }
        return removed;
    }
}
