package io.github.flowerjvm.flower.evaluation.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationResultSink;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Append-only local JSON Lines result sink intended for development and small deployments. */
public final class JsonLinesEvaluationResultSink implements EvaluationResultSink {

    private final Path file;
    private final ObjectMapper objectMapper;

    public JsonLinesEvaluationResultSink(Path file) {
        this(file, new ObjectMapper());
    }

    public JsonLinesEvaluationResultSink(Path file, ObjectMapper objectMapper) {
        if (file == null || objectMapper == null) {
            throw new IllegalArgumentException("file and objectMapper must not be null");
        }
        this.file = file.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void publish(EvaluationExperimentResult result) throws IOException {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.write(objectMapper.writeValueAsString(result));
            writer.newLine();
        }
    }

    public Path file() {
        return file;
    }
}
