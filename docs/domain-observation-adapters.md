# Domain Observation Adapters

## Purpose

Flower Core knows how a Flow and its Steps execute. It does not own the meaning
of an Agent model call, an AI Harness validation attempt, or an Action Runtime
policy decision.

Phase 4 connects those facts without merging the projects into one runtime:

```text
FlowerTraceEvent ---- FlowerTraceObservationSink --------+
AgentEvent ---------- AgentObservationSinkAdapter -------+
Harness callbacks --- AiHarnessObservationTraceListener -+-> FlowerObservationSink
AuditEvent ----------- ActionRuntimeObservationTraceSink -+
```

Each adapter produces `FlowerObservationEvent`. The envelope standardizes:

- `source`, `eventId`, and the source-owned `eventType`;
- `traceId`, `runId`, and optional `parentRunId`;
- optional operation id and name for a Step, model call, Tool call, or action;
- source sequence, timestamp, and sanitized attributes.

The source still owns event semantics. Flower Core does not gain Agent,
Harness, or Action event enums.

## Shared Pipeline

All four sources can publish to one pipeline:

```java
JsonLinesFlowerObservationSink json =
        new JsonLinesFlowerObservationSink(Paths.get("data/flower-observations.jsonl"));
AsyncFlowerObservationSink async =
        new AsyncFlowerObservationSink(json, 4096);
FlowerObservationSink sampled = new SamplingFlowerObservationSink(
        async, FlowerObservationSamplers.probability(0.10));
FlowerObservationSink observations = new SanitizingFlowerObservationSink(
        sampled,
        FlowerObservationSanitizers.removeAttributes(
                "authorization", "api.key", "user.email"));
```

Sanitize and sample before the bounded asynchronous handoff. File, database,
HTTP, OpenTelemetry, and message-broker work must remain behind that handoff.
Monitor published, dropped, and failure counters.

The Core adapter is a regular `FlowerTraceSink`:

```java
FlowerTraceSink coreTrace = new FlowerTraceObservationSink(observations);
```

## Agent

`flower-agent-observability` adapts the Agent core's sequenced `AgentEvent`:

```java
AgentEventSink agentEvents = new AgentObservationSinkAdapter(
        observations,
        event -> new AgentObservationCorrelation(outerTraceId, parentRunId));

AgentRecipe recipe = AgentFlows.react(agentSpec)
        .modelGateway(modelGateway)
        .tools(toolRegistry)
        .transcripts(transcriptStore)
        .events(agentEvents)
        .build();
```

The default retains run/recipe/agent/thread/turn identities, model usage,
status, retry delay, Tool operation identity, and error classification. It
does not retain prompts, messages, failure text, or Tool input/output.

## AI Harness

`flower-ai-harness-observability` is registered through the existing Harness
listener API:

```java
var harnessTrace = new AiHarnessObservationTraceListener(
        observations,
        context -> new AiHarnessObservationCorrelation(outerTraceId, agentRunId));

AiHarnessSpec<?, ?> spec = AiHarnessSpec.builder(/* existing configuration */)
        .addTraceListener(harnessTrace)
        .build();
```

The adapter keeps attempts, model id, timing, token usage, validation counts,
refinement, finding counts, and terminal status. It omits rendered prompts,
raw responses, provider trace payloads, exception messages, validation text,
and finding bodies.

## Action Runtime

`flower-action-runtime-observability` implements the existing Action Runtime
`TraceSink` and mirrors `AuditEvent` without replacing `AuditSink`:

```java
TraceSink actionTrace = new ActionRuntimeObservationTraceSink(
        observations,
        event -> new ActionRuntimeObservationCorrelation(
                outerTraceId, event.runId(), agentRunId));
```

Supply `actionTrace` through the runtime's existing `TraceSink` constructor
slot. Keep the business `AuditSink` configured independently; it remains the
authoritative action record.

The default Action payload allowlist retains policy/approval/status/code facts
but excludes reasons, messages, metadata, outputs, principals, tenants, and
users. Adapter failures are counted and isolated from Action execution.

## Correlation Rule

Standalone adapters use their own run id as `traceId`. That is useful for a
single component but cannot infer an outer business task.

The Host owns cross-runtime correlation. Create one outer `traceId`, then pass
it through each adapter's correlation resolver. Use `parentRunId` to express
the nesting that matters to the application, for example:

```text
outer Harness run
  -> Agent run
      -> Action run
```

This makes independent events queryable as one trace without coupling their
lifecycle, retry, persistence, or authority boundaries.

## Security Boundary

Default adapters are payload-light, not a substitute for deployment policy.
Before storage, a Host should still:

- remove or hash identifiers that its policy treats as sensitive;
- apply an explicit allowlist for any custom attributes;
- keep prompt, Tool, and Action payload capture opt-in;
- place large approved content in an artifact store instead of every event;
- implement retention, access control, and deletion in its chosen backend.
