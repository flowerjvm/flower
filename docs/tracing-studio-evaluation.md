# Tracing, Studio, And Evaluation Architecture

Status: implementation roadmap. Runtime foundation plus event-loop and durable
recovery tracing are implemented for `0.1.2-SNAPSHOT`; later phases below are
not yet public runtime guarantees.

## Goal

Flower should provide observable execution semantics and the extension points
needed to build local or enterprise observability. Flower should not operate a
customer's database cluster, Kafka deployment, identity system, backup policy,
or SLA.

The top-level flow is:

```text
Flower Core execution facts
        + Agent / Harness / Action domain facts
        -> bounded non-blocking trace handoff
        -> local or host-provided storage
        -> Studio and evaluation consumers
```

Tracing, runtime safety, output validation, and evaluation are different
responsibilities:

- tracing records what happened;
- runtime policy prevents unsafe execution while it is happening;
- AI Harness validation decides whether a structured AI task result is valid;
- evaluation scores completed runs for quality, regression, cost, or policy
  conformance.

## Ownership

| Owner | Facts it owns |
| --- | --- |
| `flower-core` | Flow start/terminal state, Step attempts, effective transitions |
| `flower-eventloop` | explicit await, wake-up reason, timeout, resumed execution |
| Flower persistence modules | checkpoint save/delete/recovery facts |
| `flower-agent` | Agent run/turn, model call, Tool call, budget/completion decisions |
| `flower-ai-harness` | AI task attempts, validation, refinement, findings |
| `flower-action-runtime` | proposal, policy, approval, execution, durable audit |
| Host application | domain state and optional sanitized state snapshots |

Action Runtime's existing `audit.TraceSink` records `AuditEvent`. It is not a
Flow trace sink and must not replace Action audit truth. An adapter may mirror
Action audit events into an observation store while the original audit stream
remains authoritative.

## Core Runtime Trace

`flower-core` owns the observation point because it is the only layer that
knows the effective `StepResult`, the selected next Step, and the final Flow
state.

The first event set is:

```text
FLOW_STARTED
FLOW_RECOVERED
STEP_STARTED
STEP_SKIPPED
STEP_COMPLETED
STEP_FAILED
STEP_CANCELLED
FLOW_WAITING
FLOW_RESUMED
CHECKPOINT_SAVED
CHECKPOINT_FAILED
FLOW_SUSPENDED
FLOW_COMPLETED
FLOW_FAILED
FLOW_CANCELLED
```

`FlowerTraceEvent` is immutable and payload-light. Its stable correlation data
includes:

- `eventId`, per-runtime `sequence`, and `flower.flow.runtime.id`;
- `traceId`, `flowRunId`, `stepRunId`, and `parentRunId`;
- `FlowId`, Worker name, timestamp, and schema version;
- transition origin, outcome, selected target Step, and sanitized error type;
- selected `ExecutionContext` identity fields.

A repeated Step gets a new `stepRunId`. Its completion event retains the same
`stepRunId` as its corresponding start event. When `ExecutionContext.runId` or
`traceId` is absent, Flower creates an identifier. For a traced durable Flow,
the identifier is written into the existing checkpoint `ExecutionContext`, so
recovery keeps the same logical `flowRunId` and `traceId`. Each JVM activation
gets a distinct runtime id and restarts its local sequence, preventing event
and Step-run id collisions across recovery.

Fresh execution emits `FLOW_STARTED`. Durable recovery emits
`FLOW_RECOVERED`, not another `FLOW_STARTED`; this keeps recovery distinct from
restarting the business operation from the beginning. `FLOW_WAITING` and
`FLOW_RESUMED` can repeat inside one Step run. Event and signal payloads are not
captured: only event types, signal name/key pairs, deadlines, and wake-up
reasons are part of the core trace.

The trace contains no business state, prompt, model output, Tool input/output,
API key, or arbitrary Step object. Higher layers may capture selected content
only through an explicit sanitization and content-capture policy.

## Non-Blocking Delivery

`FlowerListener` callbacks run on the Worker execution thread. They must never
write a file, call a database, publish to Kafka, or invoke an HTTP collector
directly.

The supported handoff shape is:

```text
Worker
  -> FlowerTraceSinkListener
  -> AsyncFlowerTraceSink bounded queue
  -> blocking/custom FlowerTraceSink on a consumer thread
```

The bounded sink drops the newest event when full and exposes accepted,
published, dropped, and failed counters. A deployment must monitor drops and
select its capacity and downstream storage according to its load. Trace
backpressure must not halt business Flow execution.

## Implementation Phases

### Phase 1: Runtime Foundation

Implemented in `0.1.2-SNAPSHOT` for the ordinary tick-driven Worker:

- immutable `FlowerTraceEvent` and standard runtime event types;
- effective `StepTransition`, including Guard, lifecycle, and cancellation
  origins;
- sequence, Flow run identity, and distinct Step attempt identity;
- opt-in `FlowerTraceListener` alongside the unchanged lifecycle listener API;
- in-memory, composite, and bounded asynchronous sinks;
- deterministic tests for `GOTO`, `REPEAT`, Guard skip, failure, and cancel.

### Phase 2: Event Loop And Durability

Implemented in `0.1.2-SNAPSHOT`:

- `FLOW_WAITING` with payload-free event/signal/deadline descriptors;
- `FLOW_RESUMED` with event, signal, timeout, or recovery reason;
- checkpoint saved, checkpoint failed, Flow suspended, and Flow recovered
  events without confusing checkpoint/resume with replay;
- logical Flow correlation across durable recovery plus unique runtime
  segments and event ids;
- `OpenTelemetryFlowerTraceSink` mapping Flow/Step spans and the richer event
  stream to OpenTelemetry span events.

`CHECKPOINT_FAILED` represents a required active or terminal checkpoint save
failure. Best-effort terminal tombstone cleanup failures continue through the
Worker error callback because the logical Flow has already terminated.

### Phase 3: Storage And Security

- JSON Lines reference sink;
- JDBC sink in a separate module when schema and migration behavior stabilize;
- `TraceSanitizer`, content capture policy, artifact extraction, and sampling;
- fail-closed sanitization: sanitizer failure drops the event instead of
  forwarding unsanitized content;
- retention remains a storage concern, separate from sanitization.

Possible modules are added only when their dependency boundary is real:

```text
flower-core                    runtime facts and observer SPI
flower-eventloop               await/resume facts
flower-observability           basic sinks and OpenTelemetry adapters
flower-observability-jdbc      optional JDBC storage
flower-studio                  separate trace consumer application
flower-evaluation              later dataset/experiment/evaluator APIs
```

### Phase 4: Domain Adapters

Adapters correlate native events rather than moving their meaning into
Flower Core:

- `flower-agent-observability` for model and Tool events;
- AI Harness trace adapter for attempts, validation, and refinement;
- Action Runtime adapter for policy, approval, execution, and audit links.

There is no single giant Core event enum for all projects.

### Phase 5: Local Flower Studio

The first Studio is a read-only consumer with local JSON Lines or SQLite
storage. It should show:

- run list and terminal outcome;
- Step timeline, effective transition, and selected next Step;
- durations, failures, waits, wake-up reasons, checkpoints, and recovery;
- optional Agent model/Tool and Action approval/execution overlays;
- links to large artifacts rather than embedding them in every event.

Arbitrary state diff is opt-in through a host `StateSnapshotProvider`; Flower
cannot infer meaningful or safe diffs from arbitrary Java object graphs.

The local store is a reference implementation for development, tests, and
small deployments. Large or highly available deployments connect the same
event contracts to their own Kafka, OpenTelemetry, database, object storage,
security, and retention infrastructure.

### Phase 6: Evaluation

Evaluation is built after trace identity and storage stabilize. Its likely
public concepts are Dataset, Example, Experiment, Evaluator, Score, and
Feedback. It compares agent/prompt/model/tool-policy versions using completed
run artifacts and trace references.

Evaluation does not authorize an Action and does not make a bad run safe. It
measures behavior after, or alongside, runtime controls owned by the relevant
layer.

## Explicit Non-Goals

Flower does not own:

- enterprise Kafka or ClickHouse operation;
- database sharding, multi-region replication, backup, or disaster recovery;
- corporate SSO and organization authorization;
- customer-specific retention law or audit policy;
- a promise that local JSON/SQLite storage handles enterprise traffic;
- automatic capture of prompts, secrets, personal data, or Tool payloads.
