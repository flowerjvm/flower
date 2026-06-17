# flower-eventloop

Event-driven Flower runtime. A reactor-pattern execution line where steps
declare **what should wake them** instead of being re-ticked.

```text
EventWorker
  -> EventFlow
      -> EventStep
```

This module does not replace tick-driven `flower-core`. It is a separate
runtime for workloads that are mostly *waits around external systems* — LLM
responses, agent approvals, MCP/tool callbacks, human-in-the-loop — where
"wake me when this event or deadline happens" expresses the intent more
directly than "tick me again and I'll check".

> Naming: this is the *reactor pattern* (as in Netty's `EventLoop`), **not**
> reactive streams. There is no `Mono`/`Flux`/`Publisher` API here, which is
> why the module is `flower-eventloop` and not `flower-reactor`.

## Relationship to flower-core

`flower-eventloop` depends on `flower-core` and **reuses its primitives** so
event-driven and tick-driven flows speak the same vocabulary and can share one
event bus and clock:

- `EventBus` / `Subscription` — inbound waits and outbound publishing
- `Clock` / `ManualClock` / `SystemClock` — deadlines and deterministic tests
- `FlowId`, `ExecutionContext`, `FlowState`

Like `flower-core`, this module only depends on the Flower `EventBus` SPI. It
does not depend on Bloom directly. A host can still use Bloom by adding
`flower-bloom-adapter` and passing `BloomEventBus.wrap(...)` as the worker's
`EventBus` implementation.

It does **not** reuse `Worker`, `Flow`, `Step`, or `StepResult`: those encode
the tick contract (`stay()` = "tick me again"). The event-driven model has its
own contract and its own classes.

## Runtime model

`EventWorker` owns one inbox queue and one deadline queue.

```text
external thread
  submit/cancel/event callback
    -> enqueue command
    -> wake worker

event worker thread
  take command, or sleep until nearest deadline
  run step callback
  apply transition
  register new awaits
```

If there is no pending command and no deadline, the background worker blocks on
the inbox. It does not scan active flows on a fixed interval.

All step callbacks and effects run on the event-worker thread. Keep them quick
and non-blocking. LLM, MCP, HTTP, database, tool, and human-approval work should
run outside the event loop and publish only the completion event back to the
worker's `EventBus`.

## Step contract

```java
abstract class EventStep {
    EventStepResult onEnter(ctx);              // start work, declare awaits
    EventStepResult onEvent(ctx, event);       // an awaited event arrived
    EventStepResult onTimeout(ctx);            // a declared deadline elapsed
    void            onExit(ctx);               // leaving the step
}
```

`onEnter` returns `await(...)` to wait or a transition result. `onEvent` /
`onTimeout` may return `null` to ignore the wake-up and keep waiting.

For request/response steps, prefer returning an await result with an attached
effect. The worker registers the await conditions first, then publishes the
request, so synchronous responses are not lost:

```java
return EventStepResult.await(AwaitCondition.event(LlmResponded.class))
        .thenPublish(new LlmRequested("hello"));
```

`EventStepResult` has no `stay()`. Instead:

- `await(AwaitCondition...)` — keep this step, wait for these conditions
- `next()` — go to the next declared step (or finish the flow)
- `goTo(stepId)` — jump to another step
- `finish()` — finish the flow successfully
- `fail(cause)` — fail the flow
- `thenPublish(event)` / `thenRun(effect)` — run effects after the result is
  accepted; for `await`, effects run after awaits are registered

`AwaitCondition` currently supports `event(Type.class)` and
`deadlineIn(millis)`. Event awaits can also include a predicate for correlation
keys such as request id or tool call id.

## Wake-up sources

The worker makes a flow progress only when something useful happens:

```text
flow submitted        -> enter first step
awaited event arrives -> deliver to current step (onEvent)
deadline reached      -> time out current step (onTimeout)
flow cancelled        -> exit current step, terminate
```

No fixed interval, no polling. An idle worker does no work.

## Example

```java
ManualClock clock = new ManualClock();
InMemoryEventBus bus = InMemoryEventBus.create();
EventWorker worker = new EventWorker("llm", clock, bus);

EventFlow flow = EventFlow.builder("llm-call", "req-1")
        .step("request", new EventStep() {
            protected EventStepResult onEnter(EventStepContext ctx) {
                return EventStepResult.await(
                        AwaitCondition.event(LlmResponded.class),
                        AwaitCondition.deadlineIn(30_000))
                        .thenPublish(new LlmRequested("hello"));
            }
            protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                return EventStepResult.next();      // got the response
            }
            protected EventStepResult onTimeout(EventStepContext ctx) {
                return EventStepResult.goTo("fallback");
            }
        })
        .step("complete", /* ... */)
        .step("fallback", /* ... */)
        .build();

worker.submit(flow);
worker.drain();                 // deterministic: process all runnable work now

bus.publish(new LlmResponded("hi"));
worker.drain();                 // flow reaches "complete" and finishes
```

## Drive modes

- **Manual / test**: `submit(flow)` then `drain()`. With `ManualClock`,
  advancing time and publishing events makes every transition deterministic —
  the event-loop equivalent of core's `tickOnce()`. Calling `drain()` after
  `start()` is rejected because manual and background modes must not share one
  worker.
- **Background**: `start()` runs one daemon thread that blocks on the wake-up
  queue and wakes for the nearest deadline. If no deadline exists, it blocks
  until the next command. Use with `SystemClock`. Do not mix the two modes on
  one worker.

## Durable Checkpoints

`EventFlow.builder(...).durable()` marks an event flow as durable. A durable
flow writes an `EventFlowCheckpoint` when it enters `await(...)`, and deletes
that checkpoint when the flow finishes, fails, or is cancelled.

The checkpoint records the current step, execution context, definition version,
await generation, and durable await descriptors:

```text
event type name
absolute deadline millis
```

Runtime objects such as subscriptions, event instances, and predicate lambdas
are not checkpointed. Durable flows may use `event(Type.class)` and
`deadlineIn(millis)`. Predicate-based awaits are still runtime-only and fail
fast if used by a durable flow.

To resume a durable flow, rebuild the flow definition, call
`recoverFrom(checkpoint)`, and submit it to a worker that uses the same
`EventFlowCheckpointStore`. During recovery the worker calls
`EventStep.onRecover(ctx, recovery)` instead of `onEnter(ctx)`, so steps can
re-register their durable awaits without replaying one-shot request effects.
The default `onRecover` fails fast to avoid silently resuming an unsafe step.

`EventFlowFactoryRegistry` and `EventFlowRecoveryService` provide the worker
startup path: load active checkpoints for a worker, rebuild each flow by
definition id, attach its checkpoint, then submit it.
`flower-eventloop-persistence-jdbc` provides `JdbcEventFlowCheckpointStore`
and dialect-specific schema SQL for the standard event-flow checkpoint table.

`EventWorker` can also be constructed with core `FlowerListener`s. The
event-loop runtime emits submitted, step-entered, step-exited, terminal,
listener-error, and worker-error callbacks using core `FlowSnapshot`, so the
existing observability listeners can be reused.

## Status

Experimental runtime. The contract (`EventStep` / `EventStepResult` /
`AwaitCondition` / `EventWorker`) is validated by `LlmEventFlowTest`, including
the synchronous response case, but the following are intentionally not
implemented yet:

- signal and external-callback await conditions
- specialized workers (`LlmEventWorker`, `AgentEventWorker`, `McpEventWorker`)
- coexistence under one shared `Engine` with tick-driven flows
- duplicate-submit policies

Do not depend on the API surface staying stable yet.
