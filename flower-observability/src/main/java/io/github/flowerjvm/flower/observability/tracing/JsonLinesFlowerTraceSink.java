package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only JSON Lines reference sink for development and small deployments.
 *
 * <p>Each successful publish flushes one complete line. File rotation,
 * retention, backup, and multi-process coordination are intentionally outside
 * this sink. File I/O blocks, so use it behind {@link AsyncFlowerTraceSink}.
 */
public final class JsonLinesFlowerTraceSink implements FlowerTraceSink, AutoCloseable {

    public static final int DEFAULT_MAX_EVENT_BYTES = 1_048_576;

    private final Path file;
    private final int maxEventBytes;
    private final BufferedWriter writer;
    private boolean closed;

    public JsonLinesFlowerTraceSink(Path file) {
        this(file, DEFAULT_MAX_EVENT_BYTES);
    }

    public JsonLinesFlowerTraceSink(Path file, int maxEventBytes) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (maxEventBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxEventBytes must be positive: " + maxEventBytes);
        }
        this.file = file.toAbsolutePath().normalize();
        this.maxEventBytes = maxEventBytes;
        try {
            Path parent = this.file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.writer = Files.newBufferedWriter(
                    this.file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot open trace file: " + this.file, failure);
        }
    }

    @Override
    public synchronized void publish(FlowerTraceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        ensureOpen();
        String json = FlowerTraceEventJson.toJson(event);
        int eventBytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (eventBytes > maxEventBytes) {
            throw new IllegalArgumentException(
                    "trace event exceeds maxEventBytes: " + eventBytes + " > " + maxEventBytes);
        }
        try {
            writer.write(json);
            writer.newLine();
            writer.flush();
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot append trace event to: " + file, failure);
        }
    }

    public Path file() {
        return file;
    }

    public int maxEventBytes() {
        return maxEventBytes;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.close();
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot close trace file: " + file, failure);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("trace sink is closed: " + file);
        }
    }
}
