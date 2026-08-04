package io.github.flowerjvm.flower.evaluation.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.evaluation.EvaluationFeedback;
import io.github.flowerjvm.flower.evaluation.EvaluationFeedbackSink;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Append-only local JSON Lines feedback sink intended as a reference implementation. */
public final class JsonLinesEvaluationFeedbackSink implements EvaluationFeedbackSink {

    private final Path file;
    private final ObjectMapper objectMapper;

    public JsonLinesEvaluationFeedbackSink(Path file) {
        this(file, new ObjectMapper());
    }

    public JsonLinesEvaluationFeedbackSink(Path file, ObjectMapper objectMapper) {
        if (file == null || objectMapper == null) {
            throw new IllegalArgumentException("file and objectMapper must not be null");
        }
        this.file = file.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void publish(EvaluationFeedback feedback) throws IOException {
        if (feedback == null) {
            throw new IllegalArgumentException("feedback must not be null");
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
            writer.write(objectMapper.writeValueAsString(feedback));
            writer.newLine();
        }
    }

    public Path file() {
        return file;
    }
}
