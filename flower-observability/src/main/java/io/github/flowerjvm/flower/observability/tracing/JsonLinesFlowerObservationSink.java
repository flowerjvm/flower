package io.github.flowerjvm.flower.observability.tracing;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only JSON Lines reference sink for common observation events.
 * Blocking file I/O belongs behind {@link AsyncFlowerObservationSink}.
 */
public final class JsonLinesFlowerObservationSink
        implements FlowerObservationSink, AutoCloseable {

    public static final int DEFAULT_MAX_EVENT_BYTES = 1_048_576;

    private final Path file;
    private final int maxEventBytes;
    private final BufferedWriter writer;
    private boolean closed;

    public JsonLinesFlowerObservationSink(Path file) {
        this(file, DEFAULT_MAX_EVENT_BYTES);
    }

    public JsonLinesFlowerObservationSink(Path file, int maxEventBytes) {
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
            throw new UncheckedIOException("cannot open observation file: " + this.file, failure);
        }
    }

    @Override
    public synchronized void publish(FlowerObservationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        ensureOpen();
        String json = FlowerObservationEventJson.toJson(event);
        int eventBytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (eventBytes > maxEventBytes) {
            throw new IllegalArgumentException(
                    "observation event exceeds maxEventBytes: "
                            + eventBytes + " > " + maxEventBytes);
        }
        try {
            writer.write(json);
            writer.newLine();
            writer.flush();
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot append observation event to: " + file, failure);
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
            throw new UncheckedIOException("cannot close observation file: " + file, failure);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("observation sink is closed: " + file);
        }
    }
}
