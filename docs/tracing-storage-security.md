# Trace Storage And Security

Status: Phase 3 reference implementation for `0.1.2`.

## Boundary

Flower provides a standard runtime event, composable sink APIs, and local
reference storage. The host owns production storage topology, access control,
retention, backup, encryption, high availability, and incident response.

Flower Core emits orchestration metadata only. It does not automatically
capture business objects, model prompts or responses, Tool input or output,
Action payloads, personal data, or credentials.

## Recommended Pipeline

Listener callbacks run on the Worker thread. Only quick, non-blocking work may
run before the asynchronous handoff:

```text
Worker
  -> FlowerTraceSinkListener
  -> SanitizingFlowerTraceSink
  -> SamplingFlowerTraceSink
  -> AsyncFlowerTraceSink (bounded queue)
  -> ContentCaptureFlowerTraceSink
  -> JsonLinesFlowerTraceSink or host storage
```

This order gives each layer one job:

| Layer | Responsibility |
| --- | --- |
| sanitizer | remove or replace sensitive attributes; failure drops the event |
| sampler | keep or drop the complete logical trace by `traceId` |
| async sink | protect the Worker from blocking storage and backpressure |
| content capture | drop, inline, or externalize explicitly marked content |
| storage | persist already selected and sanitized events |

Sampling and sanitizer failures are fail-closed. Queue overflow drops the
newest event and increments `AsyncFlowerTraceSink.droppedCount()`. Deployments
must monitor all exposed drop and failure counters.

## Reference Configuration

```java
Path traceFile = Paths.get("var/flower/traces.jsonl");
Path artifactRoot = Paths.get("var/flower/artifacts");

JsonLinesFlowerTraceSink json = new JsonLinesFlowerTraceSink(traceFile);
FileTraceArtifactStore artifacts = new FileTraceArtifactStore(artifactRoot);

FlowerTraceSink blockingStorage = new ContentCaptureFlowerTraceSink(
        json,
        TraceContentPolicies.inlineOrArtifact(16 * 1024),
        artifacts);

AsyncFlowerTraceSink async = new AsyncFlowerTraceSink(
        blockingStorage,
        4096,
        "flower-trace-storage");

FlowerTraceSink sampled = new SamplingFlowerTraceSink(
        async,
        TraceSamplers.probability(0.10));

TraceSanitizer sanitizer = TraceSanitizers.compose(
        TraceSanitizers.removeAttributes("authorization", "api.key"),
        TraceSanitizers.redactAttributes("[REDACTED]", "user.email"));

FlowerTraceSink ingress = new SanitizingFlowerTraceSink(sampled, sanitizer);

Engine engine = Engine.builder()
        .worker(worker)
        .listener(new FlowerTraceSinkListener(ingress))
        .build();
```

On shutdown, stop event production, close `async` so its accepted queue drains,
then close `json`. The reference JSON sink flushes each successful event and
appends on reopen. It does not rotate files or coordinate multiple processes.

## Content Capture

Higher-layer or custom instrumentation must wrap textual content explicitly:

```java
builder.attribute(
        "agent.model.response",
        TraceContent.text("application/json", modelResponse));
```

`ContentCaptureFlowerTraceSink` recognizes `TraceContent` only in top-level
attributes. A `TraceContentPolicy` chooses:

| Decision | Stored event value |
| --- | --- |
| `DROP` | attribute omitted |
| `INLINE` | media type, byte size, and text embedded as JSON-safe metadata |
| `ARTIFACT` | content stored separately and replaced by location and SHA-256 |

Raw `TraceContent` is never forwarded when policy evaluation or artifact
storage fails. The whole event is dropped. `TraceContent.toString()` also omits
the content to reduce accidental logging, but that is not a substitute for a
sanitizer.

The local artifact store writes UTF-8 content atomically under a configured
root and deduplicates equal bytes by SHA-256. Binary output should be placed in
host object storage directly and represented by a sanitized reference rather
than encoded into trace events.

## JSON Lines Contract

Each line contains one event with these stable top-level fields:

```text
schemaVersion, source, eventId, eventType,
traceId, flowRunId, stepRunId, parentRunId,
flowType, flowKey, workerName, sequence,
occurredAt, attributes
```

The sink accepts JSON-safe primitives, maps with string keys, iterables, arrays,
enums, and `Instant`. Cyclic, unsupported, non-finite, over-nested, or oversized
values are rejected before a line is written. The default maximum event size is
1 MiB and can be overridden in the constructor.

## Production Boundary

The JSON Lines and file artifact implementations are for development, tests,
reference integrations, and small deployments. They do not provide rotation,
cross-process locking, indexed queries, tenant authorization, retention jobs,
replication, or backup.

Production systems can implement `FlowerTraceSink` and `TraceArtifactStore` for
Kafka, a database, or object storage, or use the OpenTelemetry adapter. A JDBC
reference module should be added only after Studio query patterns, schema
migration, and retention semantics are stable.
