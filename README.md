# 🌸 Flower

Flower is a lightweight Java workflow framework for explicit, testable,
human-operable orchestration inside one JVM.

It helps you model long-running application behavior as a `Flow` made of small
`Step` objects. A `Worker` ticks active flows, each step returns an explicit
transition, and an `Engine` owns the shared clock, event bus, workers, and
lifecycle listeners.

Flower is intentionally smaller than a workflow platform. It is not BPMN,
Temporal, Camunda, a distributed scheduler, or a durable saga engine. It is a
plain Java toolkit for applications that need controlled internal
orchestration without surrendering their domain model to a large runtime.

## AI-Era Positioning

Flower core is not an AI framework, and it does not depend on an LLM. Its value
in the AI coding era is structure.

AI can generate more application code than humans can comfortably review when
that code becomes scattered callbacks, service methods, scheduled jobs, and
background threads. Flower gives business behavior a small execution shape:

```text
Engine
  -> Worker
      -> Flow
          -> Step
              -> StepResult
```

That shape makes generated and hand-written orchestration easier to inspect,
test, recover, observe, and change. A flow has a current step. A step returns
an explicit result. Waiting is modeled through events, signals, timeouts, or
durable domain state instead of hidden sleeps and ad-hoc polling loops.

The intended core positioning is:

```text
Flower is AI-era friendly, but not AI-specific.
```

The broader Flower ecosystem is meant to bound AI in two places:

```text
1. When AI writes code
   Flower gives generated code an explicit Flow / Step structure.
   flower-check can reject known bad patterns.
   A future flower-dev-mcp can guide coding agents before code is written.

2. When AI runs inside an application
   Higher-level modules can keep AI actions behind harnesses, policies,
   approvals, audits, state machines, and MCP/tool boundaries.
```

In other words, Flower does not try to make AI smarter. It tries to make
AI-generated and AI-driven behavior explicit, bounded, testable, and operable.

The broader ecosystem may include AI-facing support around the core:

```text
flower-check
  build-time checks that detect known Flower anti-patterns

future flower-dev-mcp
  developer MCP tools that teach AI coding agents Flower concepts, templates,
  and design rules

future flower-ai-harness / flower-agent-runtime
  higher-level AI execution and controlled agent/action runtime layers

future flower-mcp-proxy
  secure tool/MCP gateway for controlled business actions
```

Those layers should guide and govern AI usage without making `flower-core`
itself a model framework.

## Why Flower Is Useful

Use Flower when application work has multiple phases and should progress over
time or in response to events:

- order processing that waits for payment, inventory, or fulfillment signals
- game turns where a flow waits for player input and animation completion
- logistics or device workflows where each unit of work moves through zones
- retryable background coordination that should remain testable
- demos and simulations that need deterministic manual ticks

Flower makes this kind of logic easier to reason about because every flow has a
current step, every step returns an explicit result, and time/event waiting is
represented by `StepContext` instead of ad-hoc threads and sleeps.

## Mental Model

```text
Engine
  -> Worker
      -> Flow
          -> Step
              -> stepNo
```

- `Engine`: top-level runtime. Owns `Clock`, `EventBus`, `Worker`s, and
  listeners.
- `Worker`: single-threaded tick loop. Owns active flows and ticks each
  non-terminal flow once per worker tick.
- `Flow`: one ordered sequence of steps for one domain instance, identified by
  `FlowId(flowType, flowKey)`.
- `Step`: a small stateful orchestration unit. It receives a `StepContext` and
  returns `StepResult`.
- `stepNo`: optional step-local cursor for sub-state inside one step.

## Modules

- `flower-core`: core Engine, Worker, Flow, Step, event bus, clock, and listener
  APIs.
- `flower-persistence-jdbc`: JDBC `FlowCheckpointStore` implementation plus
  schema SQL for PostgreSQL, MySQL, Oracle, and H2.
- `flower-eventloop-persistence-jdbc`: JDBC `EventFlowCheckpointStore`
  implementation plus event-loop schema SQL for PostgreSQL, MySQL, Oracle,
  and H2.
- `flower-bloom-adapter`: adapts Bloom's event bus to Flower's `EventBus` SPI.
- `flower-spring-boot-starter`: Spring Boot auto-configuration for an `Engine`
  and optional checkpoint store wiring.
- `flower-observability`: listeners and helpers for logging, dumps, metrics,
  tracing, and awaiting flow completion.
- `flower-testkit`: optional test helpers for deterministic Flow tests.
- `flower-check`: build-time Flower usage checker for host applications.
- `flower-check-annotations`: SOURCE-retained approval markers consumed by
  `flower-check`.
- `flower-check-maven-plugin`: Maven `verify` integration for `flower-check`.
- `flower-check-gradle-plugin`: Gradle `check` integration for `flower-check`.

## Build

Until the artifacts are published, install locally:

```bash
mvn test
mvn install
```

Then use the modules you need from your application.

## Quick Start

```java
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.time.SystemClock;
import io.github.parkkevinsb.flower.core.worker.Worker;

public final class FlowerQuickStart {

    static final class PrepareOrderStep extends Step {
        @Override
        protected StepResult onTick(StepContext ctx) {
            System.out.println("prepare " + ctx.flowId());
            return StepResult.done();
        }
    }

    static final class CompleteOrderStep extends Step {
        @Override
        protected StepResult onTick(StepContext ctx) {
            System.out.println("complete " + ctx.flowId());
            return StepResult.done();
        }
    }

    public static void main(String[] args) throws Exception {
        Worker worker = Worker.builder("orders")
                .intervalMillis(100)
                .build();

        Engine engine = Engine.builder()
                .clock(SystemClock.INSTANCE)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();

        Flow flow = Flow.builder("order", "order-1")
                .step("prepare", new PrepareOrderStep())
                .step("complete", new CompleteOrderStep())
                .build();

        engine.start();
        worker.submit(flow);

        Thread.sleep(500);
        engine.stop();
    }
}
```

For deterministic tests, use `engine.attach()` and `worker.tickOnce()` instead
of starting the scheduler.

```java
Worker worker = Worker.builder("test").build();
Engine engine = Engine.builder()
        .eventBus(InMemoryEventBus.create())
        .worker(worker)
        .build();

engine.attach();
worker.submit(flow);

worker.tickOnce();
worker.tickOnce();
```

## Testing With Flower Testkit

`flower-testkit` keeps testing helpers outside `flower-core`. It does not
change the runtime model; it only bundles the setup most tests repeat:

```text
Engine + Worker + ManualClock + InMemoryEventBus
+ RecordingFlowerListener + FakeCheckpointStore
```

Add it as a test dependency:

```xml
<dependency>
    <groupId>io.github.parkkevinsb.flower</groupId>
    <artifactId>flower-testkit</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

Example:

```java
FlowTestHarness harness = FlowTestHarness.create();

Flow flow = Flow.builder("order", "ORD-1")
        .executionContext(TestExecutionContexts.tenantRun("office-a", "run-1"))
        .step("accept", new AcceptOrderStep(orderService))
        .step("payment", new WaitForPaymentStep())
        .build();

harness.submit(flow)
        .tick()
        .assertFlow("order", "ORD-1")
        .isRunning()
        .currentStepIs("payment")
        .tenantIdIs("office-a")
        .runIdIs("run-1");

harness.publish(new PaymentApproved("ORD-1"))
        .tick()
        .assertFlow("order", "ORD-1")
        .isFinished();
```

For durable Flow recovery tests, reuse the same fake checkpoint store through
`restart()` and recover with a `FlowFactoryRegistry`:

```java
FlowTestHarness restarted = harness.restart();

int recovered = restarted.recoverActiveCount(registry);

restarted.tick()
        .assertFlow("order", "ORD-1")
        .currentStepIs("payment")
        .runIdIs("run-1");
```

The first version intentionally avoids a large assertion DSL or a JUnit-only
API. Failed `FlowAssertions` checks throw `AssertionError`, so the helpers work
with JUnit, AssertJ, or plain test code.

## Step Lifecycle

```text
onEnter(ctx)       called once when the step becomes current
onTick(ctx)        called once per worker tick while the step is current
onExit(ctx)        called when the step leaves by done, goTo, finish, or fail
onReset(ctx)       called for StepResult.repeat(), then the step re-enters
```

`onTick` returns one of:

- `StepResult.stay()`: keep this step and tick again later.
- `StepResult.done()`: finish this step; the flow moves to the next declared step, or finishes if this was the last step.
- `StepResult.repeat()`: reset this step and run it from the beginning.
- `StepResult.goTo("stepId")`: jump to another flow-level step id.
- `StepResult.finish()`: finish the flow successfully without running later steps.
- `StepResult.fail(Throwable)`: fail the flow.

## Event-Driven Steps

Steps should be asynchronous in shape. Do not block a worker thread while
waiting for outside work. Start or subscribe in `onEnter`, return `stay()` while
waiting, and return `done()` when a signal or timeout says the work is ready.

```java
final class WaitForPaymentStep extends Step {

    @Override
    protected void onEnter(StepContext ctx) {
        ctx.startTimeout(30_000);
        ctx.subscribe(PaymentApproved.class, event -> {
            if (event.orderId().equals(ctx.flowId().flowKey())) {
                ctx.signal("paid");
            }
        });
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (ctx.hasSignal("paid")) {
            return StepResult.done();
        }
        if (ctx.timedOut()) {
            return StepResult.fail(new IllegalStateException("payment timeout"));
        }
        return StepResult.stay();
    }
}
```

If `onTick` needs the event data, attach it to the signal. Flower keeps only
the latest payload for each signal name, which is usually what a waiting step
needs:

```java
ctx.subscribe(PaymentApproved.class, event -> ctx.signal("paid", event));

PaymentApproved approved = ctx.consumeSignal("paid", PaymentApproved.class);
if (approved != null) {
    return StepResult.done();
}
```

Subscriptions made through `StepContext.subscribe(...)` are cleaned up
automatically when the step exits, resets, or the flow terminates.

You may also unsubscribe a specific event while the step is still running.
Keep each returned `Subscription` separately when the step listens to multiple
event types:

```java
final class WaitDockStep extends Step {
    private volatile StepContext current;
    private Subscription ackSub;
    private Subscription arrivalSub;

    @Override
    protected void onEnter(StepContext ctx) {
        current = ctx;
        ackSub = ctx.subscribe(AckEvent.class, this::onAck);
        arrivalSub = ctx.subscribe(ArrivalEvent.class, this::onArrival);
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (ctx.stepNo() == 0 && shouldStopArrival(ctx)) {
            arrivalSub.unsubscribe(); // ack subscription remains active
            arrivalSub = null;
            ctx.setStepNo(10);
            return StepResult.stay();
        }

        if (ctx.stepNo() == 10 && shouldListenArrivalAgain(ctx)) {
            arrivalSub = ctx.subscribe(ArrivalEvent.class, this::onArrival);
            ctx.setStepNo(20);
            return StepResult.stay();
        }

        return ctx.hasSignal("arrived") ? StepResult.done() : StepResult.stay();
    }

    private void onAck(AckEvent event) {
        current.signal("ack");
    }

    private void onArrival(ArrivalEvent event) {
        current.signal("arrived");
    }

    @Override
    protected void onExit(StepContext ctx) {
        ackSub = null;
        arrivalSub = null;
        current = null;
    }

    @Override
    protected void onReset(StepContext ctx) {
        ackSub = null;
        arrivalSub = null;
        current = null;
    }
}
```

## Step Design Rules

- Keep `onTick` short and non-blocking. No `sleep`, long polling, network waits,
  or database loops inside the worker tick.
- Start external work in `onEnter`, then observe completion through events,
  signals, stored domain state, or timeouts.
- Use `StepContext.subscribe(...)` for step-owned event subscriptions so Flower
  can release them automatically.
- Use `StepContext.eventBus().publish(...)` when a step needs to emit an event.
- Use `stepNo` for small internal cursors, not for large hidden state machines.
- Put heavy domain logic in services. A step should orchestrate, not become the
  domain model.
- Pass dependencies through step constructors. Flower does not instantiate steps
  by reflection and does not provide a DI container in core.
- Give every step a stable, meaningful flow-level id. `goTo(...)` targets that
  id, not the Java class name.
- Prefer immutable events and exact event classes. The default event buses match
  by exact runtime type.
- Treat a `Step` instance as owned by one `Flow`. Create fresh step instances
  when building a new flow.
- Use `Guard` for "do not enter this step yet" rules. Use `StepResult.stay()`
  for "I entered and am waiting" rules.
- Make terminal outcomes explicit. Return `done()` for success and
  `fail(cause)` for failure.

## Future Worker Scheduling Direction

Today, each `Worker` is driven by a `ScheduledExecutorService` and wakes up at
its configured `intervalMillis` to run one short tick. This is not a busy loop:
an idle worker does not spin in a manual `while` loop, and user code must still
avoid blocking inside `onTick`.

This model is intentionally simple and deterministic. It is a good default for
small and medium in-JVM orchestration because it keeps Worker behavior easy to
test with `tickOnce()` and `ManualClock`.

For deployments with many mostly-idle Workers, Flower may later add an
event-driven Worker scheduler. The goal would be similar to a selector-style
runtime, but not based on Java NIO `Selector` directly because Flower is not
waiting on socket file descriptors. Instead, a future scheduler could wake a
Worker only when useful work is possible:

```text
submit/cancel queued
event or signal delivered
timeout/deadline reached
runnable flow available
```

That direction should preserve the current programming model:

```text
onEnter starts or subscribes
onTick checks state quickly
stay means "not ready yet"
done/goTo/fail drive the Flow transition
```

An event-driven scheduler is therefore a future optimization, not a change in
the Step contract. It should be considered only after measuring that idle
Worker wakeups are a real cost. Until then, prefer tuning `intervalMillis` per
Worker and keeping each tick cheap.

## Flow Submission

```java
Flow flow = Flow.builder("order", orderId)
        .step("accept", new AcceptOrderStep(orderService))
        .step("payment", new WaitForPaymentStep())
        .step("fulfill", new FulfillOrderStep(warehouseService))
        .build();

worker.submit(flow);
```

`flowType` and `flowKey` form the `FlowId`. Submitting a duplicate flow defaults
to `DuplicatePolicy.REJECT`. You can also use `IGNORE` or `REPLACE`.

```java
worker.submit(flow, DuplicatePolicy.REPLACE);
```

## Execution Context

A `FlowId(flowType, flowKey)` answers "which domain instance is this flow for?"
`ExecutionContext` answers "whose execution is this?"

Use it when logs, dumps, checkpoints, admin views, or future audit/eval tooling
need to connect one flow run to a tenant, user, session, run id, trace id, or
correlation id.

Existing code does not need to change. Flows default to
`ExecutionContext.empty()`.

```java
import io.github.parkkevinsb.flower.core.context.ExecutionContext;

ExecutionContext execution = ExecutionContext.builder()
        .tenantId("office-a")
        .userId("user-1")
        .sessionId("session-1")
        .runId("run-123")
        .traceId("trace-abc")
        .correlationId("request-789")
        .build();

Flow flow = Flow.builder("order", "ORD-1")
        .executionContext(execution)
        .step("accept", new AcceptOrderStep(orderService))
        .step("payment", new WaitForPaymentStep())
        .build();
```

Steps can read it when they need execution identity:

```java
String tenantId = ctx.executionContext().tenantId().orElse("default");
String runId = ctx.executionContext().runId().orElse("unknown");
```

Keep this context small. It is an execution id card, not a business context.
Do not put roles, permissions, approval state, domain objects, agent ids,
action ids, or policy decisions in Flower core context. Agent/action state
belongs in higher-level modules such as a future `flower-agent-runtime`.

`ExecutionContext` is attached to the `Flow`, not to a `ThreadLocal`. That keeps
the same identity visible from steps, listeners, dumps, checkpoints, and
recovery even when events or callbacks happen on other threads.

Important: `tenantId` does not change Flower's duplicate-flow identity.
`Worker` still uses only `FlowId(flowType, flowKey)` to detect duplicates. If
two tenants can have the same domain key, make the `flowKey` globally unique in
the host application, for example `office-a:DOC-1`.

## Event Bus Choices

`flower-core` includes `InMemoryEventBus` for simple setups and deterministic
tests. To share events with Bloom:

```java
io.github.parkkevinsb.bloom.EventBus bloom =
        io.github.parkkevinsb.bloom.LocalEventBus.create();

Engine engine = Engine.builder()
        .eventBus(io.github.parkkevinsb.flower.bloom.BloomEventBus.wrap(bloom))
        .worker(Worker.builder("main").build())
        .build();
```

The adapter preserves the dispatch semantics of the wrapped Bloom bus.

## Spring Boot

`flower-spring-boot-starter` auto-configures:

- a `Clock` bean, defaulting to `SystemClock.INSTANCE`
- an `EventBus` bean, defaulting to `InMemoryEventBus`
- a `FlowCheckpointStore`, when JDBC persistence is explicitly enabled
- an `Engine` bean
- a lifecycle bean that starts and stops the engine with the application context

Example configuration:

```yaml
flower:
  enabled: true
  auto-start: true
  persistence:
    type: none
  workers:
    - name: orders
      interval-ms: 100
    - name: alerts
      interval-ms: 250
```

Provide your own `Engine`, `EventBus`, `Clock`, or `FlowerListener` beans when
you need more control. The auto-configuration backs off where appropriate.

For durable flows with JDBC checkpoints, add `flower-persistence-jdbc`, create
the table using the packaged schema SQL, and enable the store explicitly:

```yaml
flower:
  persistence:
    type: jdbc
    jdbc:
      dialect: postgresql
      initialize-schema: never
```

Supported dialects are `postgresql`, `mysql`, `oracle`, and `h2`. The starter
does not create tables automatically; `initialize-schema` is reserved and
currently only supports `never`. If you need a custom backend, provide a
`FlowCheckpointStore` bean and the auto-configured `Engine` will use it.

### Spring Boot Dump Endpoint

`flower-spring-boot-starter` can expose a read-only Engine dump endpoint when
the application is already a Spring MVC web application. It is disabled by
default because dump output can include flow keys, execution context, and
operational state.

```yaml
flower:
  admin:
    dump:
      enabled: true
      path: /internal/flower/dump
      pretty: false
```

With the default path, the endpoint is:

```text
GET /internal/flower/dump
GET /internal/flower/dump?pretty=true
```

The endpoint uses the host application's web server. Flower does not start a
separate console server. In production, keep this endpoint behind application
authentication, a private network, VPN, or an admin gateway.

### Spring Boot Console

For a small built-in web view, enable the console endpoint:

```yaml
flower:
  admin:
    console:
      enabled: true
      path: /internal/flower/console
      api-path: /internal/flower/console/dump
      poll-interval-ms: 3000
```

Then open:

```text
GET /internal/flower/console
```

The console is served by the same Spring Boot application and polls the
same-origin `api-path`. It shows Engine, Worker, Flow, current Step, stepNo,
declared Step order, and execution context. The UI has Start, Stop, Refresh,
and polling interval controls.

This is an internal/admin surface, not a public endpoint. Do not expose it
directly to the internet.

## Observability

Attach `FlowerListener` implementations to observe flow submission,
step entry/exit, flow completion, cancellation, failure, listener errors, and
worker errors. `Engine.dump()` gives a snapshot of the current engine and worker
state, including active flows, current step id, current step index, current
stepNo, and the declared step list for admin/console views.

Lifecycle listener snapshots stay lightweight. The declared step list is only
materialized for dump/admin views so observability does not add work to every
listener callback.

## Checkpoint / Resume

Flower's durable mode is checkpoint/resume, not durable execution replay.
It stores only the current Flow position so an application can rebuild a fresh
Flow and resume ticking from that position.

```java
Flow flow = Flow.builder("order", orderId)
        .durable()
        .durableStep("payment", new WaitPaymentStep(orderService),
                RecoveryPolicy.REENTER_IDEMPOTENT)
        .durableStep("fulfill", new FulfillOrderStep(warehouseService),
                RecoveryPolicy.REENTER_IDEMPOTENT)
        .build();
```

Durable flows require every step to declare a recovery policy. A regular
`Step` may opt in through `durableStep(...)` with
`RecoveryPolicy.REENTER_IDEMPOTENT` when re-running `onEnter` is safe. If
initial entry and recovery setup must be different, extend `DurableStep` with
`RecoveryPolicy.RESUME_ONLY` and implement `onResume(ctx)`.

```java
Flow recovered = Flow.builder("order", orderId)
        .durable()
        .durableStep("payment", new WaitPaymentStep(orderService),
                RecoveryPolicy.REENTER_IDEMPOTENT)
        .durableStep("fulfill", new FulfillOrderStep(warehouseService),
                RecoveryPolicy.REENTER_IDEMPOTENT)
        .build()
        .recoverFrom(checkpoint);
```

Applications that want a small startup helper can register factories by
`flowType` and recover the checkpoints they choose:

```java
FlowFactoryRegistry registry = FlowFactoryRegistry.builder()
        .register("order", id -> buildOrderFlow(id.flowKey()))
        .build();

FlowRecoveryService recovery = FlowRecoveryService.create(store, registry);
recovery.recoverActiveForWorker(engine.worker("orders"));
```

The helper only rebuilds fresh Flows and submits them to the chosen Worker. It
does not start Workers, create schema, lock rows, delete failed checkpoints, or
turn Flower into an event replay engine.

Core exposes `FlowCheckpointStore` as the storage boundary. The default store
is no-op, so existing transient flows are unaffected. Core does not create DB
tables. JDBC, Redis, JPA, or file-backed checkpoint stores should live in
optional modules or in the host application, and schema initialization should
be explicit and opt-in.

Durable checkpoints keep the `ExecutionContext` with the saved flow position.
After recovery, the same logical run keeps the same `runId`, `traceId`, tenant,
and user identifiers. Flower does not regenerate a new run id during recovery.

`flower-persistence-jdbc` provides a JDBC implementation:

```java
FlowCheckpointStore store = JdbcFlowCheckpointStore.create(
        dataSource,
        JdbcCheckpointDialects.postgresql());

Engine engine = Engine.builder()
        .eventBus(InMemoryEventBus.create())
        .worker(Worker.builder("orders").build())
        .checkpointStore(store)
        .build();
```

`flower-eventloop-persistence-jdbc` provides a separate JDBC implementation for
event-loop checkpoints:

```java
EventFlowCheckpointStore eventStore = JdbcEventFlowCheckpointStore.create(
        dataSource,
        JdbcEventFlowCheckpointDialects.postgresql());

EventWorker worker = new EventWorker(
        "agents",
        SystemClock.INSTANCE,
        InMemoryEventBus.create(),
        eventStore);
```

Schema SQL is packaged under:

```text
flower/persistence/jdbc/postgresql/schema.sql
flower/persistence/jdbc/mysql/schema.sql
flower/persistence/jdbc/oracle/schema.sql
flower/persistence/jdbc/h2/schema.sql
```

Event-loop checkpoint schema SQL is packaged separately under:

```text
flower/eventloop/persistence/jdbc/postgresql/schema.sql
flower/eventloop/persistence/jdbc/mysql/schema.sql
flower/eventloop/persistence/jdbc/oracle/schema.sql
flower/eventloop/persistence/jdbc/h2/schema.sql
```

Apply the SQL yourself, or copy it into Flyway/Liquibase. The JDBC stores do
not create tables automatically. The standard checkpoint schemas include
nullable execution-context columns:

```text
tenant_id
user_id
session_id
run_id
trace_id
correlation_id
```

If you already created the checkpoint table from an older schema, add those
nullable columns before upgrading the JDBC store. Migration SQL is packaged
next to each dialect schema:

```text
flower/persistence/jdbc/postgresql/execution-context-migration.sql
flower/persistence/jdbc/mysql/execution-context-migration.sql
flower/persistence/jdbc/oracle/execution-context-migration.sql
flower/persistence/jdbc/h2/execution-context-migration.sql
```

Signals are still in-memory wake-up hints. Durable step decisions should be
based on domain state that can be checked again after restart, not on signal
payloads alone.

## Notes For AI Agents

When generating Flower code, prefer this pattern:

- Model business phases as explicit steps with stable ids.
- Keep each step small: start work, check state, emit result.
- Use `stay()` for asynchronous waits and `done()` only when the condition is
  definitely satisfied.
- Do not create background threads inside steps unless the application service
  owns them.
- Do not block a worker tick. Flower gets its composability from quick,
  repeatable ticks.
- Use constructor-injected services and plain Java objects.
- Use events for cross-step or external completion signals.
- Write tests with `engine.attach()` and `worker.tickOnce()` so behavior is
  deterministic.
- Prefer readable flow builders over hidden reflection or annotation magic in
  core code.

That shape keeps Flower flows easy for humans and AI tools to inspect: the
current step is visible, transitions are explicit, and waiting behavior is
encoded as small repeatable decisions.

AI coding agents should treat Flower as a structure provider, not just another
library call. The goal is not to generate more code faster. The goal is to make
generated orchestration small enough that a human can read it, test it, and
repair it later.

`flower-check` is the first enforcement tool for this direction. It can fail a
build when generated code uses known bad patterns, such as blocking a worker
tick or hiding orchestration outside Flower's Flow/Step boundary.

A future developer MCP can make the same rules available before code is
written:

```text
AI coding agent
-> asks Flower developer MCP for the right pattern
-> generates Flow / Step code
-> flower-check verifies the result
-> tests prove the behavior
```

This is the AI-era framework loop Flower should aim for: guidance before
generation, explicit structure in code, checks during build, and deterministic
tests for behavior.
