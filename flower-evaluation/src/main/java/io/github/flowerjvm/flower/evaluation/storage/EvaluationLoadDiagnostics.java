package io.github.flowerjvm.flower.evaluation.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Diagnostics produced while tolerantly reading a local JSON Lines file. */
public final class EvaluationLoadDiagnostics {

    private final String file;
    private final boolean exists;
    private final long fileSize;
    private final long lineCount;
    private final int recordCount;
    private final long malformedCount;
    private final long ignoredCount;
    private final long duplicateCount;
    private final long truncatedCount;
    private final List<Long> malformedLines;
    private final String loadedAt;

    EvaluationLoadDiagnostics(
            Path file,
            boolean exists,
            long fileSize,
            long lineCount,
            int recordCount,
            long malformedCount,
            long ignoredCount,
            long duplicateCount,
            long truncatedCount,
            List<Long> malformedLines,
            Instant loadedAt) {
        this.file = file.toAbsolutePath().normalize().toString();
        this.exists = exists;
        this.fileSize = fileSize;
        this.lineCount = lineCount;
        this.recordCount = recordCount;
        this.malformedCount = malformedCount;
        this.ignoredCount = ignoredCount;
        this.duplicateCount = duplicateCount;
        this.truncatedCount = truncatedCount;
        this.malformedLines = Collections.unmodifiableList(
                new ArrayList<Long>(malformedLines));
        this.loadedAt = loadedAt.toString();
    }

    public String getFile() {
        return file;
    }

    public boolean isExists() {
        return exists;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getLineCount() {
        return lineCount;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public long getMalformedCount() {
        return malformedCount;
    }

    public long getIgnoredCount() {
        return ignoredCount;
    }

    public long getDuplicateCount() {
        return duplicateCount;
    }

    public long getTruncatedCount() {
        return truncatedCount;
    }

    public List<Long> getMalformedLines() {
        return malformedLines;
    }

    public String getLoadedAt() {
        return loadedAt;
    }
}
