# Flower Studio

Flower Studio is a small, read-only local application for inspecting correlated
Flower observation and evaluation data. It reads the common JSON Lines stream produced by
Flower Core, Flower Agent, Flower AI Harness, and Flower Action Runtime
observation adapters, and also accepts the legacy Flower Core trace JSON Lines
shape from the Phase 3 sink.

Studio can also read `flower-evaluation` result and feedback JSON Lines. The
evaluation view shows candidate quality, individual cases and evaluator scores,
baseline regressions, Trace references, and explicit human feedback.

It shows:

- trace list, outcome, source, duration, failures, waits, and token totals;
- correlated Flow, Agent, Harness, and Action run hierarchy;
- chronological events with Step transitions and operation durations;
- model, Tool, approval, Action, checkpoint, and recovery overlays;
- safe links to explicitly captured local artifacts.
- evaluation experiment summaries, baseline comparison, cases, scores, and
  feedback.

## Run The Included Demo

Build the runnable jar from the Flower repository root:

```powershell
mvn -pl flower-studio -am package
```

Then start Studio with the included game-server operations trace:

```powershell
java -jar flower-studio/target/flower-studio-0.1.2-SNAPSHOT-all.jar `
  --trace-file=flower-studio/examples/game-server-ops-observations.jsonl `
  --artifact-root=flower-studio/examples/artifacts `
  --evaluation-file=flower-studio/examples/game-server-ops-evaluations.jsonl `
  --feedback-file=flower-studio/examples/game-server-ops-evaluation-feedback.jsonl
```

Open [http://127.0.0.1:8077](http://127.0.0.1:8077). Studio reloads the file
when its size or modification time changes, so an append-only sink can keep
writing while the page is open.

## Connect An Application

Write all runtime observations to one shared, sanitized JSON Lines sink. Keep
the blocking file sink behind the bounded asynchronous handoff:

```java
JsonLinesFlowerObservationSink json =
        new JsonLinesFlowerObservationSink(
                Paths.get("data/flower-observations.jsonl"));
AsyncFlowerObservationSink async =
        new AsyncFlowerObservationSink(json, 4096);
FlowerObservationSink observations = new SanitizingFlowerObservationSink(
        async,
        FlowerObservationSanitizers.removeAttributes(
                "authorization", "api.key", "user.email"));

FlowerTraceSink coreTrace = new FlowerTraceObservationSink(observations);
Engine engine = Engine.builder()
        .listener(new FlowerTraceSinkListener(coreTrace))
        // workers, clock, and event bus
        .build();
```

Pass the same `observations` destination to the Agent, AI Harness, and Action
Runtime observation adapters. The Host supplies one outer `traceId` and the
meaningful `parentRunId` relationships so Studio can display the combined run
hierarchy. See [Domain Observation Adapters](../docs/domain-observation-adapters.md)
for complete adapter examples and the correlation rule.

## Options

| Option | Environment variable | Default |
| --- | --- | --- |
| `--host` | `FLOWER_STUDIO_HOST` | `127.0.0.1` |
| `--port` | `FLOWER_STUDIO_PORT` | `8077` |
| `--trace-file` | `FLOWER_STUDIO_TRACE_FILE` | `data/flower-observations.jsonl` |
| `--artifact-root` | `FLOWER_STUDIO_ARTIFACT_ROOT` | `data/flower-artifacts` |
| `--max-events` | `FLOWER_STUDIO_MAX_EVENTS` | `100000` |
| `--evaluation-file` | `FLOWER_STUDIO_EVALUATION_FILE` | `data/flower-evaluations.jsonl` |
| `--feedback-file` | `FLOWER_STUDIO_FEEDBACK_FILE` | `data/flower-evaluation-feedback.jsonl` |
| `--max-evaluations` | `FLOWER_STUDIO_MAX_EVALUATIONS` | `10000` |

Use `--artifact-root=none` to disable artifact downloads. The server permits
only `GET` requests for APIs, binds to loopback by default, and resolves
artifact locations beneath the configured root.
Use both `--evaluation-file=none` and `--feedback-file=none` to disable the
evaluation API.

## Scope And Operating Boundary

This first Studio is a JSON Lines reference implementation for local
development, tests, demonstrations, and small trusted deployments. It is not
an enterprise trace platform and does not provide authentication,
organization authorization, high availability, long-term retention, backup,
or a distributed collector. Do not expose it directly to the public internet.
Studio does not write evaluation feedback; the Host owns authenticated feedback
collection and publishes sanitized records through `EvaluationFeedbackSink`.

Large deployments should send the same common observation contract to their
own Kafka, OpenTelemetry, database, object storage, security, and retention
infrastructure. SQLite/JDBC query storage and automatic state diff are not part
of this first implementation. Hosts may emit explicitly selected, sanitized
state facts or artifacts; Flower does not inspect arbitrary Java object graphs.
