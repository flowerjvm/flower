# Flower

Flower is a lightweight Java orchestration toolkit for event-driven,
tick-driven workflows inside one JVM.

It helps you model a long-running piece of application behavior as a `Flow`
made of small `Step` objects. A `Worker` ticks active flows, each step decides
whether to stay, move to the next step, repeat, jump, finish, or fail, and an `Engine` owns
the shared clock, event bus, workers, and lifecycle listeners.

Flower is intentionally smaller than a workflow platform. It is not BPMN,
Temporal, Camunda, a distributed scheduler, or a durable saga engine. It is a
plain Java toolkit for applications that need controlled internal
orchestration without surrendering their domain model to a large runtime.

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
- `flower-bloom-adapter`: adapts Bloom's event bus to Flower's `EventBus` SPI.
- `flower-spring-boot-starter`: Spring Boot auto-configuration for an `Engine`.
- `flower-observability`: listeners and helpers for logging, dumps, metrics,
  tracing, and awaiting flow completion.

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
- an `Engine` bean
- a lifecycle bean that starts and stops the engine with the application context

Example configuration:

```yaml
flower:
  enabled: true
  auto-start: true
  workers:
    - name: orders
      interval-ms: 100
    - name: alerts
      interval-ms: 250
```

Provide your own `Engine`, `EventBus`, `Clock`, or `FlowerListener` beans when
you need more control. The auto-configuration backs off where appropriate.

## Observability

Attach `FlowerListener` implementations to observe flow submission,
step entry/exit, flow completion, cancellation, failure, listener errors, and
worker errors. `Engine.dump()` gives a snapshot of the current engine and worker
state, including active flows and their current steps.

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
