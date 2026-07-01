# 🌸 Flower

Flower -- the one that flows.

Flower is not a workflow platform. It is a tiny in-JVM runtime for
long-running Spring application flows.

It gives application code an explicit, testable, human-operable execution shape
inside one JVM.

It helps you model long-running application behavior as a `Flow` made of small
`Step` objects. A `Worker` ticks active flows, each step returns an explicit
transition, and an `Engine` owns the shared clock, event bus, workers, and
lifecycle listeners.

Flower fits naturally in Spring Boot applications: Spring provides the
application framework, and Flower gives long-running business flows an explicit
runtime shape without replacing the application's framework or domain model.

It is not BPMN, Temporal, Camunda, a distributed scheduler, or a durable saga
engine. It is a plain Java toolkit for applications that need controlled
internal orchestration without surrendering their domain model to a large
runtime.

## The Shape, In One Screen

The reason Flower exists is one small execution shape:

```text
Engine
  -> Worker
      -> Flow
          -> Step
              -> StepResult
```

A flow has a current step. A step returns an explicit result. Waiting is
modeled through events, signals, timeouts, or durable domain state instead of
hidden sleeps and ad-hoc polling loops. That is the core idea.

## Before / After

Most orchestration inside real applications does not start as a workflow
engine. It starts as a status column, a scheduled poller, an event listener,
and a few shared flags that drift apart over time.

Before: orchestration scattered across status strings, scheduled scans, and
side channels:

```java
@Entity
class Order {
    String id;
    String status; // "NEW", "WAITING_PAYMENT", "PAID", "FULFILLED", "FAILED"
    Instant paymentDeadline;
}

@Component
class OrderPoller {
    @Scheduled(fixedDelay = 1000)
    void tick() {
        for (Order order : orders.findActive()) {
            switch (order.status) {
                case "NEW" -> {
                    prepare(order);
                    order.status = "WAITING_PAYMENT";
                    order.paymentDeadline = now().plusSeconds(30);
                }
                case "WAITING_PAYMENT" -> {
                    if (paidFlags.contains(order.id)) {
                        order.status = "PAID";
                    } else if (now().isAfter(order.paymentDeadline)) {
                        order.status = "FAILED";
                    }
                }
                case "PAID" -> {
                    fulfill(order);
                    order.status = "FULFILLED";
                }
            }
            orders.save(order);
        }
    }
}

@EventListener
void onPaid(PaymentApproved event) {
    paidFlags.add(event.orderId());
}
```

The state machine is implicit in strings and a switch. Waiting is hidden in a
shared flag. Recovery means "whatever the status column happened to be."

After: the same behavior as an explicit Flow of Steps:

```java
Flow flow = Flow.builder("order", orderId)
        .step("accept", new AcceptOrderStep(orderService))
        .step("payment", new WaitForPaymentStep())
        .step("fulfill", new FulfillOrderStep(warehouseService))
        .build();

worker.submit(flow);
```

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

Now the current step is visible. The transition is the return value. The wait
is a subscription plus a timeout that Flower cleans up for you. The same flow
can be tested with a manual clock and `tickOnce()`, without starting a
scheduler or database.

## Where It Comes From

Flower's execution model is inspired by proven patterns from industrial and
equipment-control software, where long-running work is modeled as explicit
states, guarded transitions, timeouts, retries, operator intervention, and
observable execution logs.

Flower brings that discipline to ordinary Java application code: make the
current state visible, make transitions explicit, keep each unit small, and
leave a trace a human can inspect.

## Why And When To Use It

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

Flower is not the right tool when you need cross-service distributed
transactions, durable execution replay, a BPMN designer, or a multi-node
scheduler. Reach for Temporal, Camunda, or a saga framework there. Flower
stays in one JVM on purpose.

## Why Not Just an Enum or Spring StateMachine?

For small flows, an enum and a switch are genuinely enough. Use them.
Spring StateMachine is a good fit when your main problem is modeling
formal states, events, transitions, and guards.

Flower is different: it is an execution runtime for internal application flows.
It focuses on running steps, waiting for events, handling timeouts,
checkpointing, resuming, inspecting, and testing flows inside one JVM.

State machines model state.
Flower runs flows.

The cost of "just build it yourself" appears when the flow starts needing the
things below. None of them is hard alone. Together, they become a runtime you
did not mean to write.

| What the flow eventually needs | Hand-rolled around an enum | With Flower |
| --- | --- | --- |
| Wait for an event, then clean up the subscription | Register/deregister listeners by hand; leaks are easy to miss. | `ctx.subscribe(...)` in `onEnter`, released automatically on exit/reset/finish. |
| Timeout on a wait | Deadline field plus a scheduler that checks it. | `ctx.startTimeout(30_000)` and `ctx.timedOut()`. |
| Retry or explicit failure transition | Extra state, counters, and branches in the switch. | `StepResult.repeat()` / `StepResult.fail(cause)`. |
| Checkpoint and resume after restart | Serialize position, persist it, rebuild, and resume. | `durable()` plus a `FlowCheckpointStore`. |
| Deterministic tests | Abstract the clock, bypass the scheduler, and fake the bus yourself. | `ManualClock` plus `worker.tickOnce()`. |
| Inspect what is running right now | Build your own dump/admin view. | `Engine.dump()` plus optional console. |

You can build every row yourself, but then you are slowly rebuilding a runtime.
Or you can adopt a formal state machine framework when the state model itself is
the center of the problem.

Flower is for the other case: your domain model stays in your Spring Boot
application, but a long-running internal flow needs a small runtime to execute
it.

## Typical Use With Kafka

Flower works well when Kafka tells a Spring Boot service that something
happened and the service needs to advance an internal flow.

Kafka tells the application that something happened. Flower decides whether the
current step can move forward. The database remembers the business fact.

This example keeps Kafka concerns such as duplicate handling, inbox/outbox, and
startup recovery out of the main flow. Those belong in production code, not in
the first shape.

```java
@Component
final class OrderKafkaListener {
    private final Engine engine;
    private final OrderRepository orders;
    private final OrderFlowFactory flows;

    @KafkaListener(topics = "order-created")
    void onOrderCreated(OrderCreated event) {
        orders.markCreated(event.orderId());

        engine.worker("orders").submit(
                flows.createOrderFlow(event.orderId()),
                DuplicatePolicy.IGNORE);
    }

    @KafkaListener(topics = "payment-approved")
    void onPaymentApproved(PaymentApproved event) {
        orders.markPaymentApproved(event.orderId());
        engine.eventBus().publish(event);
    }
}

final class OrderFlowFactory {
    private final OrderRepository orders;

    OrderFlowFactory(OrderRepository orders) {
        this.orders = orders;
    }

    Flow createOrderFlow(String orderId) {
        return Flow.builder("order", orderId)
                .step("accept", new AcceptOrderStep())
                .step("payment", new WaitPaymentStep(orders))
                .step("complete", new CompleteOrderStep())
                .build();
    }
}

final class WaitPaymentStep extends Step {
    private final OrderRepository orders;

    WaitPaymentStep(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    protected void onEnter(StepContext ctx) {
        ctx.startTimeout(30_000);
        ctx.subscribe(PaymentApproved.class, event -> {
            if (event.orderId().equals(ctx.flowId().flowKey())) {
                ctx.signal("paid"); // event arrived; check the DB on the next tick
            }
        });
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        String orderId = ctx.flowId().flowKey();
        if (orders.isPaymentApproved(orderId)) {
            return StepResult.done();
        }
        if (ctx.timedOut()) {
            return StepResult.fail(new IllegalStateException("payment timeout"));
        }
        return StepResult.stay();
    }
}
```

The event handler does not complete the Step directly. It records a signal, and
the next `onTick` completes the Step by returning `StepResult.done()` after the
database says the payment is approved.

The split is simple:

```text
Kafka event  = something happened
Flower Step  = decide stay, done, or fail
Database     = remember the business fact
```

In a Spring multi-module application, Flower usually belongs in the workflow
module rather than the domain model itself:

```text
order-api       REST/Kafka input
order-domain    Order, OrderStatus, repository, domain service
order-workflow  Flower FlowFactory and Step classes
order-events    Kafka event DTOs, publisher, listener
order-infra     DB, Kafka, Flower engine config
```

The Kafka listener stays thin: persist the domain fact, publish the event to
Flower's in-JVM event bus, and let the Step decide whether the flow can advance.

### Production Notes For Kafka

Keep the boundaries boring on purpose:

- Kafka carries domain events.
- Flower keeps the internal execution position.
- The DB keeps business facts and recovery state.
- Flower signals are hints, not business facts.
- Use an inbox or event id check for duplicate Kafka events when needed.
- Use an outbox for external events or commands that must be published
  reliably.
- On startup, recover or submit flows for DB records that are still active but
  not currently running.

## Operational Boundaries

Flower core is deliberately small, so its runtime contract is also explicit:

- Concurrency: a Worker ticks its Flows on one scheduler thread. Submit/cancel
  requests are queued. Event callbacks may call `ctx.signal(...)`; do not mutate
  Step fields directly from callback threads.
- Recovery: durable Flows checkpoint the current step id, `stepNo`, execution
  context, and definition version. Recovery rebuilds a fresh Flow and resumes
  from that checkpoint. It is not deterministic replay or exactly-once side
  effect execution, so external writes and API calls should be idempotent.
- Scale: the default Worker is tick-based and simple to test. It is a good fit
  for small to medium in-process workloads. Very large numbers of idle Flows may
  need application-level sharding or a future event-loop scheduler.

## Mental Model

```text
Engine
  -> Worker
      -> Flow
          -> Step
              -> StepResult
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
- `StepResult`: the explicit transition returned by a Step.
- `stepId`: a stable flow-level string id used by `goTo`, dumps, checkpoints,
  and admin views.
- `stepNo`: optional step-local cursor for tiny sub-state inside one step.

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

## AI-Era Positioning

Flower core is not an AI framework, and it does not depend on an LLM. Its
relevance in the AI coding era is structure.

AI can generate more orchestration code than humans can comfortably review
when that code becomes scattered callbacks, service methods, scheduled jobs,
and background threads. Flower's contribution is to force that behavior into a
small, inspectable shape:

```text
Engine -> Worker -> Flow -> Step -> StepResult
```

Generated and hand-written orchestration both become easier to inspect, test,
recover, observe, and change. A step starts work, checks state, and returns an
explicit result, so a reviewer, tool, or coding agent can follow it.

> Flower is AI-era friendly, but not AI-specific.

The broader Flower ecosystem is meant to bound AI in two places:

- When AI writes code, Flower gives generated code an explicit Flow / Step
  structure. `flower-check` can reject known bad patterns, and a future
  developer MCP can guide coding agents before code is written.
- When AI runs inside an application, higher-level modules can keep AI actions
  behind harnesses, policies, approvals, audits, state machines, and MCP/tool
  boundaries.

Available now:

- `flower-core`: the stable center. Explicit Flow / Step structure for
  generated and hand-written orchestration.
- `flower-check` (MVP): build-time checks that reject known Flower
  anti-patterns, such as blocking a worker tick or hiding orchestration outside
  the Flow / Step boundary.
- `flower-ai-harness`: a separate higher-level AI execution harness, early but
  already being tested with real application usage.

Taking shape / in validation:

- `flower-agent-runtime`: a controlled agent/action execution layer. The shape
  exists and is being validated against real usage; APIs may still move.

Roadmap:

- `flower-dev-mcp`: developer MCP tools that teach coding agents Flower
  concepts, templates, and design rules before code is written.
- `flower-mcp-proxy`: a secure MCP/tool gateway for controlled business
  actions.

None of these layers turn `flower-core` itself into a model framework. The
separation is deliberate.

## Step Lifecycle

```text
onEnter(ctx)       called once when the step becomes current
onTick(ctx)        called once per worker tick while the step is current
onExit(ctx)        called when the step leaves by done, goTo, finish, or fail
onReset(ctx)       called for StepResult.repeat(), then the step re-enters
```

`onTick` returns one of:

| Result | Meaning |
| --- | --- |
| `StepResult.stay()` | Keep this step and tick again later. |
| `StepResult.done()` | Finish this step and move to the next declared step, or finish the flow if this was the last step. |
| `StepResult.repeat()` | Reset this step and run it from the beginning. |
| `StepResult.goTo("stepId")` | Jump to another flow-level step id. |
| `StepResult.finish()` | Finish the flow successfully without running later steps. |
| `StepResult.fail(Throwable)` | Fail the flow. |

## Step IDs, stepNo, And Shared State

Flower already has step ids. They are flow-level string ids:

```java
Flow flow = Flow.builder("order", orderId)
        .step("accept", new AcceptOrderStep(orderService))
        .step("payment", new WaitForPaymentStep())
        .step("fulfill", new FulfillOrderStep(warehouseService))
        .build();

return StepResult.goTo("payment");
```

The core keeps step ids as strings because the same ids must be readable in
logs, dumps, checkpoints, admin screens, and external configuration. If you
want type safety in application code, wrap them with an enum:

```java
enum OrderStep {
    ACCEPT("accept"),
    PAYMENT("payment"),
    FULFILL("fulfill");

    private final String id;

    OrderStep(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }
}
```

```java
Flow flow = Flow.builder("order", orderId)
        .step(OrderStep.ACCEPT.id(), new AcceptOrderStep(orderService))
        .step(OrderStep.PAYMENT.id(), new WaitForPaymentStep())
        .step(OrderStep.FULFILL.id(),
                new FulfillOrderStep(warehouseService))
        .build();
```

Use `stepNo` only as a small cursor inside one Step. If it starts representing
business states such as `WAITING_PAYMENT`, `RETRYING`, `FULFILLING`, or
`FAILED`, split the behavior into explicit Steps.

When multiple Steps need shared values, do not hide them in `stepNo` or
step-local signals. Use domain state, or pass a small run context object while
building the Flow:

```java
final class OrderFlowRun {
    final String orderId;
    PaymentResult paymentResult;
    FulfillmentPlan fulfillmentPlan;

    OrderFlowRun(String orderId) {
        this.orderId = orderId;
    }
}
```

```java
OrderFlowRun run = new OrderFlowRun(orderId);

Flow flow = Flow.builder("order", orderId)
        .step(OrderStep.ACCEPT.id(), new AcceptOrderStep(run, orderService))
        .step(OrderStep.PAYMENT.id(), new WaitForPaymentStep(run, paymentService))
        .step(OrderStep.FULFILL.id(),
                new FulfillOrderStep(run, warehouseService))
        .build();
```

For durable flows, keep recoverable business state in your domain storage. A
run context object is convenient for transient coordination, but it is not a
durable source of truth after process restart.

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

You may also unsubscribe a specific event while a step is still running. Keep
that pattern small. If the step starts to need its own large internal state
machine, split the behavior into multiple explicit Steps instead.

## Step Design Rules

- Keep `onTick` short and non-blocking. No `sleep`, long polling, network waits,
  or database loops inside the worker tick.
- Start external work in `onEnter`, then observe completion through events,
  signals, stored domain state, or timeouts.
- Use `StepContext.subscribe(...)` for step-owned event subscriptions so Flower
  can release them automatically.
- Use `StepContext.eventBus().publish(...)` when a step needs to emit an event.
- Use `stepNo` for small internal cursors, not for large hidden state machines.
  If the cursor turns into business state, split the Step.
- Put heavy domain logic in services. A step should orchestrate, not become the
  domain model.
- Pass dependencies through step constructors. Flower does not instantiate steps
  by reflection and does not provide a DI container in core.
- Give every step a stable, meaningful flow-level id. `goTo(...)` targets that
  id, not the Java class name. Wrap ids in an enum when application code needs
  type safety.
- Keep shared values in domain state or an explicit run context object. Durable
  flows must be recoverable from domain state and checkpoints, not from
  transient Step fields alone.
- Prefer immutable events and exact event classes. The default event buses match
  by exact runtime type.
- Treat a `Step` instance as owned by one `Flow`. Create fresh step instances
  when building a new flow.
- Use `Guard` for "do not enter this step yet" rules. Use `StepResult.stay()`
  for "I entered and am waiting" rules.
- Make terminal outcomes explicit. Return `done()` for success and
  `fail(cause)` for failure.

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
belongs in higher-level runtime modules such as `flower-agent-runtime`.

`ExecutionContext` is attached to the `Flow`, not to a `ThreadLocal`. That keeps
the same identity visible from steps, listeners, dumps, checkpoints, and
recovery even when events or callbacks happen on other threads.

Important: `tenantId` does not change Flower's duplicate-flow identity.
`Worker` still uses only `FlowId(flowType, flowKey)` to detect duplicates. If
two tenants can have the same domain key, make the `flowKey` globally unique in
the host application, for example `office-a:DOC-1`.

## Event Bus Choices

`flower-core` includes `InMemoryEventBus` for simple setups and deterministic
tests. Bloom is the small in-memory event bus provided in the Flower ecosystem.
To share events with Bloom, add `flower-bloom-adapter`:

```java
EventBus bloom = LocalEventBus.create();

Engine engine = Engine.builder()
        .eventBus(BloomEventBus.wrap(bloom))
        .worker(Worker.builder("main").build())
        .build();
```

The adapter preserves the dispatch semantics of the wrapped Bloom bus.

### Bloom Event Example

When Flower is backed by Bloom, application code can publish to Bloom directly.
Flower steps subscribed through `ctx.subscribe(...)` will receive the same
events.

```java
EventBus bloom = LocalEventBus.create();

Engine engine = Engine.builder()
        .eventBus(BloomEventBus.wrap(bloom))
        .worker(Worker.builder("orders").intervalMillis(100).build())
        .build();
```

Application code publishes an ordinary Bloom event:

```java
final class PaymentService {
    private final EventBus bloom;
    private final OrderRepository orders;

    PaymentService(EventBus bloom, OrderRepository orders) {
        this.bloom = bloom;
        this.orders = orders;
    }

    void approvePayment(String orderId) {
        orders.markPaymentApproved(orderId); // business fact
        bloom.publish(new PaymentApproved(orderId)); // wake waiting steps
    }
}
```

The waiting Flower step receives that Bloom event through the adapter:

```java
final class WaitPaymentStep extends Step {
    private final OrderRepository orders;

    WaitPaymentStep(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    protected void onEnter(StepContext ctx) {
        ctx.startTimeout(30_000);
        ctx.subscribe(PaymentApproved.class, event -> {
            if (event.orderId().equals(ctx.flowId().flowKey())) {
                ctx.signal("payment-approved");
            }
        });
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (orders.isPaymentApproved(ctx.flowId().flowKey())) {
            return StepResult.done();
        }
        if (ctx.timedOut()) {
            return StepResult.fail(new IllegalStateException("payment timeout"));
        }
        return StepResult.stay();
    }
}
```

In this setup Bloom remains the application event bus, while Flower uses the
same events to advance the internal flow. The signal is only a wake-up hint; the
database remains the source of truth.

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

Core `StepContext.startTimeout(...)` is a runtime-only helper and is not stored
in durable checkpoints. Durable Flows reject it so a restart cannot silently
reset or lose a deadline. For durable waits, store `dueAtMillis` or equivalent
deadline data in domain state, or use the event-loop runtime's await deadlines.

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
event-loop checkpoints. Schema SQL is packaged for PostgreSQL, MySQL, Oracle,
and H2. Apply the SQL yourself, or copy it into Flyway/Liquibase. The JDBC
stores do not create tables automatically.

For dialect paths, execution-context columns, and migration notes, see
[Persistence](docs/persistence.md).

Signals are still in-memory wake-up hints. Durable step decisions should be
based on domain state that can be checked again after restart, not on signal
payloads alone.

Operational boundaries to remember:

- Flow ownership is enforced inside one `Engine`, not across JVMs. If multiple
  processes recover from the same checkpoint store, the application must
  coordinate recovery with its own lock, lease, or leader election.
- Checkpoint `save(...)` and `delete(...)` run synchronously on the Worker tick
  path or EventWorker loop path. Slow storage slows Flow progress.
- Terminal durable Flows save a terminal tombstone before cleanup delete, so
  normal completion may perform both a save and a delete.
- `definitionVersion` is checked only when both the Flow and checkpoint have a
  non-null version.

## Observability

Attach `FlowerListener` implementations to observe flow submission, step
entry/exit, flow completion, cancellation, failure, listener errors, and worker
errors. `Engine.dump()` gives a snapshot of the current engine and worker
state, including active flows, current step id, current step index, current
stepNo, and the declared step list for admin/console views.

Lifecycle listener snapshots stay lightweight. The declared step list is only
materialized for dump/admin views so observability does not add work to every
listener callback.

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

## Modules And Maturity

The main stable center is `flower-core`. Everything else orbits it.

Core:

- `flower-core`: stable center. Engine, Worker, Flow, Step, event bus, clock,
  and listener APIs.

Persistence / integration:

- `flower-persistence-jdbc`: JDBC `FlowCheckpointStore` plus schema SQL for
  PostgreSQL, MySQL, Oracle, and H2.
- `flower-spring-boot-starter`: Spring Boot auto-configuration for an `Engine`
  and optional checkpoint store wiring.
- `flower-bloom-adapter`: adapts Bloom's event bus to Flower's `EventBus` SPI.

Observability / testing:

- `flower-observability`: listeners and helpers for logging, dumps, metrics,
  tracing, and awaiting flow completion.
- `flower-testkit` (MVP): deterministic Flow test helpers.

Developer tooling:

- `flower-check` (MVP): build-time Flower usage checker for host applications.
- `flower-check-annotations` (MVP): SOURCE-retained approval markers consumed
  by `flower-check`.
- `flower-check-maven-plugin` (MVP): Maven `verify` integration for
  `flower-check`.
- `flower-check-gradle-plugin` (MVP): companion Gradle plugin project for
  running `flower-check` from Gradle `check`.

Early execution line:

- `flower-eventloop` (MVP): separate event-driven runtime for explicit waits
  such as callbacks, signals, approvals, LLM/tool responses, and deadlines.
- `flower-eventloop-persistence-jdbc` (MVP): JDBC `EventFlowCheckpointStore`
  plus event-loop schema SQL.

Read the MVP labels literally. These modules are useful enough to try, but
their APIs may move more than `flower-core`. The event loop is a separate
execution line, not a replacement for the tick-driven Worker / Flow / Step
model.

## Future Worker Scheduling Direction

Today, each `Worker` is driven by a `ScheduledExecutorService` and wakes up at
its configured `intervalMillis` to run one short tick. This is not a busy loop:
an idle worker does not spin in a manual `while` loop, and user code must still
avoid blocking inside `onTick`.

This model is intentionally simple and deterministic. It is a good default for
small and medium in-JVM orchestration because it keeps Worker behavior easy to
test with `tickOnce()` and `ManualClock`.

For deployments with many mostly-idle Workers, Flower may later add an
event-driven Worker scheduler. A future scheduler could wake a Worker only
when useful work is possible:

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

## Where Flower Is Being Hardened

Flower is being dogfooded against real application code, not only toy
examples. It is currently being run and hardened in:

- architecture-office SaaS document workflows
- a Terminal Operating System execution layer
- game server workflow and turn/state coordination
- AI harness runtime experiments for controlled AI execution

These projects keep Flower honest about practical needs: explicit flow
structure, small steps, recoverable execution, observable state, and code that
stays understandable as systems grow. This is not a claim of broad external
adoption; it is a statement about the real projects driving Flower's design.

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

## Build

Until the artifacts are published, install locally:

```bash
mvn test
mvn install
```

Then use the modules you need from your application.

## License

Flower is licensed under the Apache License 2.0.
