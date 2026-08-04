package io.github.flowerjvm.flower.studio.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read diagnostics exposed by the local Studio API. */
public final class StudioDiagnostics {

    private final String traceFile;
    private final boolean traceFileExists;
    private final long fileSizeBytes;
    private final long lineCount;
    private final long eventCount;
    private final long malformedLineCount;
    private final long ignoredLineCount;
    private final long duplicateEventCount;
    private final long truncatedEventCount;
    private final List<Long> malformedLineNumbers;
    private final Instant loadedAt;

    public StudioDiagnostics(
            String traceFile,
            boolean traceFileExists,
            long fileSizeBytes,
            long lineCount,
            long eventCount,
            long malformedLineCount,
            long ignoredLineCount,
            long duplicateEventCount,
            long truncatedEventCount,
            List<Long> malformedLineNumbers,
            Instant loadedAt) {
        this.traceFile = traceFile;
        this.traceFileExists = traceFileExists;
        this.fileSizeBytes = fileSizeBytes;
        this.lineCount = lineCount;
        this.eventCount = eventCount;
        this.malformedLineCount = malformedLineCount;
        this.ignoredLineCount = ignoredLineCount;
        this.duplicateEventCount = duplicateEventCount;
        this.truncatedEventCount = truncatedEventCount;
        this.malformedLineNumbers = Collections.unmodifiableList(
                new ArrayList<Long>(malformedLineNumbers));
        this.loadedAt = loadedAt;
    }

    public String getTraceFile() {
        return traceFile;
    }

    public boolean isTraceFileExists() {
        return traceFileExists;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public long getLineCount() {
        return lineCount;
    }

    public long getEventCount() {
        return eventCount;
    }

    public long getMalformedLineCount() {
        return malformedLineCount;
    }

    public long getIgnoredLineCount() {
        return ignoredLineCount;
    }

    public long getDuplicateEventCount() {
        return duplicateEventCount;
    }

    public long getTruncatedEventCount() {
        return truncatedEventCount;
    }

    public List<Long> getMalformedLineNumbers() {
        return malformedLineNumbers;
    }

    public String getLoadedAt() {
        return loadedAt.toString();
    }
}
