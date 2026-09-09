# 🌸 Flower

**English** | [한국어](README.ko.md)

**One execution model. From Java business workflows to AI agents.**

Make application flows explicit with **Flow → Step → StepResult**.

Flower is a small in-JVM runtime for Java applications. It gives multi-phase
business workflows, event-driven coordination, and AI application flows the
same execution structure, while keeping your domain model and application
framework in place.

The same model gives people readable flows and coding agents structural
constraints, supported by Flower Skill, `flower-check`, and deterministic tests.

[![CI](https://github.com/flowerjvm/flower/actions/workflows/ci.yml/badge.svg)](https://github.com/flowerjvm/flower/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.flowerjvm/flower-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.flowerjvm/flower-core/0.1.3)

[Quick start](#quick-start) · [Execution model](#the-execution-contract) ·
[Coding agents](#one-execution-model-for-humans-and-coding-agents) ·
[Runtime reference](docs/runtime-reference.md) · [Modules](#modules)

```java
Flow flow = Flow.builder("order", orderId)
        .step("accept", new AcceptOrderStep(orderService))
        .step("payment", new WaitForPaymentStep(orders))
        .step("fulfill", new FulfillOrderStep(warehouseService))
        .build();

worker.submit(flow);
```

These Step classes belong to your application. Flower supplies the execution
model. A complete, self-contained example follows in [Quick start](#quick-start).

Start with `flower-core`: **Java 8+, one JVM, no separate Flower server, and no
database required for transient flows**. Spring Boot integration, checkpoints,
observability, and developer tooling are optional. The Spring Boot starter
requires Java 17 and Spring Boot 3.x.

Examples use `0.1.3`; `main` develops `0.1.4-SNAPSHOT`.

`flower-core` is built around an established execution contract:
`Worker → Flow → Step → StepResult`. Core development focuses on preserving
this model and compatibility with existing application code. Optional modules
and ecosystem projects evolve around it; MVP labels identify individual
components whose APIs are still being refined.

## The Flow Is Already There

Your app has flows. You just cannot see them yet.

An order waits for payment. A device waits for a response. A game waits for a
player. An AI application waits for a model, a tool, or an approval.

The flow exists, but its execution logic may be scattered across service
methods, scheduled jobs, event listeners, callbacks, status fields, and shared
flags. When something stops progressing, someone has to reconstruct the sequence:

> What is running? Which phase is it in? What is it waiting for?
> What makes it move forward?

Flower gives that sequence a place in the code: one Flow, small Steps, and
explicit transition results. Domain rules stay in your application; Steps
coordinate when the work can proceed.

<a id="flower-in-one-screen"></a>

## Different Workflows, One Model

| Application | Example phases, expressed as Steps |
| --- | --- |
| Business service | Accept order → wait for payment → fulfill order |
| Logistics or equipment coordination | Validate work → dispatch request → wait for completion → finalize |
| Game server | Prepare turn → wait for player input → wait for animation → complete turn |
| AI application | Prepare context → run model or agent → validate result → request approval → execute action |

The builder does not change for an AI application:

```java
Flow aiFlow = Flow.builder("assistant-task", taskId)
        .step("context", prepareContextStep)
        .step("model", waitForModelResultStep)
        .step("validate", validateOutputStep)
        .step("action", executeGovernedActionStep)
        .build();

worker.submit(aiFlow);
```

This is an application sketch: the Step variables represent application-created
instances, not built-in AI components. Each workflow uses the same three concepts:

| Concept | Responsibility |
| --- | --- |
| `Flow` | The declared execution stages for one instance of work, with a current position. |
| `Step` | A small, stateful unit that coordinates the current stage. |
| `StepResult` | An explicit decision: keep waiting, advance, repeat, jump, finish, or fail. |

The runtime manages the current Step and interprets its result. Your application
implements the work inside and around those stages. Model turns, transcripts,
tools, validation, and action authorization belong to application services or
optional higher-level runtimes.

You can change what the workflow does without changing how its execution is expressed.

<a id="structure-for-generated-code"></a>
<a id="notes-for-ai-agents"></a>
<a id="give-generated-java-code-an-explicit-execution-structure"></a>

## One Execution Model For Humans And Coding Agents

Flower gives coding agents a constrained execution model, not a blank Java
codebase. The agent expresses orchestration through `Flow → Step → StepResult`:
declare the stages, keep each Step small, and return an explicit transition.
Business rules remain in ordinary Java services and domain objects.

Humans get readable flows. Coding agents get structural constraints.
`flower-check` and deterministic tests close the feedback loop.

```text
GUIDE       Flower Skill
              ↓
CONSTRAIN   Flow / Step / StepResult
              ↓
VERIFY      flower-check + deterministic tests
```

[Flower Skill](https://github.com/flowerjvm/flower-agent-skills/blob/main/agent-skills/flower-app-guide/SKILL.md)
guides generation: how to compose Flows, write non-blocking Steps, model waits,
and keep domain responsibilities in application services. The execution model
gives the generated code a common structure that people and tools can inspect.

When wired into the host build, [flower-check](flower-check/README.md) enforces
supported usage rules and fails the build for violations at the configured
severity. Deterministic tests verify the expected transitions, waits, failures,
and recovery behavior. These checks give the agent concrete feedback to revise
its code, while giving reviewers a consistent structure to follow.

Use this development workflow for ordinary Java applications as well as AI
applications. The application itself does not need an LLM dependency.
See [Skill and build setup](#use-flower-with-chatgpt-and-codex).

<a id="before--after"></a>

## Before And After: Make The Sequence Visible

> **Before Flower, you read the code to reconstruct the flow.**
>
> **With Flower, you read the Flow to navigate the code.**

A workflow usually starts as ordinary application code. Waiting, events,
timeouts, and retries arrive one requirement at a time. Understanding the
execution sequence then means following the processor, event handlers, state
changes, and service calls together.

The excerpts below compare coordination around the same application services
and order-state view. Payment notifications update that view, and preparation
establishes the payment deadline. State queries and service calls here return
promptly; constructors and application types are omitted.

### Before: Follow The Processor

A scheduled processor tracks execution in an application-owned `stage` field:

```java
// Called by the application's scheduled processor.
void process(OrderRun run) {
    switch (run.stage) {
        case ACCEPT:
            orderService.prepare(run.orderId);
            run.stage = Stage.PAYMENT;
            break;
        case PAYMENT:
            if (orders.isPaymentApproved(run.orderId)) {
                run.stage = Stage.FULFILL;
            } else if (clock.currentTimeMillis()
                    >= orders.paymentDeadlineMillis(run.orderId)) {
                run.stage = Stage.FAILED;
            }
            break;
        case FULFILL:
            warehouseService.fulfill(run.orderId);
            run.stage = Stage.COMPLETE;
            break;
        default:
            break;
    }
}
```

The sequence is there, but you reconstruct it by following assignments to
`stage`, checking where payment state comes from, and finding how the deadline
is set. To investigate a payment wait, you first have to locate the relevant
branch within that coordination code.

### After: Start With The Flow

```java
Flow flow = Flow.builder("order", orderId)
        .step("accept", new AcceptOrderStep(orderService))
        .step("payment", new WaitForPaymentStep(orders))
        .step("fulfill", new FulfillOrderStep(warehouseService))
        .build();

worker.submit(flow);
```

The declaration shows `accept → payment → fulfill` before you open any Step.
For a payment wait, go directly to `WaitForPaymentStep`. It checks the same
payment facts and deadline, and returns the decision to the runtime:

```java
final class WaitForPaymentStep extends Step {
    private final OrderStateView orders;

    WaitForPaymentStep(OrderStateView orders) {
        this.orders = orders;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        String orderId = ctx.flowId().flowKey();
        if (orders.isPaymentApproved(orderId)) {
            return StepResult.done();
        }
        if (ctx.clock().currentTimeMillis()
                >= orders.paymentDeadlineMillis(orderId)) {
            return StepResult.fail(
                    new IllegalStateException("payment timeout"));
        }
        return StepResult.stay();
    }
}
```

`stay()` keeps the Flow at payment, `done()` advances to fulfillment, and
`fail(...)` terminates the Flow with an error. Flower tracks the execution
position and applies those transitions.

Your domain services still perform the business work and own its data.
The maintenance benefit is a clear starting point: read the Flow to understand
the sequence, then open the Step whose behavior you need to inspect or change.
Flower gives that execution flow one visible structure and one common runtime
contract.

These are application integration sketches. The
[quick start](#quick-start) below includes a complete runnable example of an
event subscription and an in-memory timeout. Restart recovery is a separate
choice; see [Execution boundaries](#execution-boundaries).

## More Than Splitting A Method Into Smaller Methods

Small methods organize code. Flower also supplies a common execution contract
for work that progresses over time.

| Need | Flower's contribution |
| --- | --- |
| See the execution structure | Declared Flow stages, stable Step IDs, and an inspectable current Step. |
| Express transitions consistently | `StepResult` instead of application-specific flags and return codes. |
| Wait for external work | Step-owned subscriptions, signals, and timeout helpers. |
| Test without real scheduling | `engine.attach()`, `worker.tickOnce()`, and a controllable clock. |
| Inspect a running application | `Engine.dump()`, lifecycle listeners, and optional tracing and console views. |
| Resume selected work after restart | Opt-in checkpoint/resume with an explicit per-Step recovery policy. |

Building these facilities one requirement at a time can turn application code
into a runtime of its own. Flower supplies that shared machinery. Keep domain
behavior in services and domain objects, and keep Steps focused on coordination.

## Quick Start

<a id="install-from-maven-central"></a>

### 1. Add Core

For a plain Java application, add only `flower-core`.

Maven:

```xml
<dependency>
    <groupId>io.github.flowerjvm</groupId>
    <artifactId>flower-core</artifactId>
    <version>0.1.3</version>
</dependency>
```

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.flowerjvm:flower-core:0.1.3")
}
```

### 2. Declare A Flow, Wait For An Event, And Advance It

Run the following `FlowerQuickStart` class with Core on the classpath.
It defines all application classes it uses and advances the Worker manually,
without Spring, a database, a background scheduler, or `Thread.sleep`.

`PrintStep` represents demo work. The event is deliberately published after
the waiting Step has subscribed.

This quick start uses transient signals and timeouts. Events published before
subscription are not retained for the waiting Step, and its signals and timeout
are not restored after a restart. See [Execution boundaries](#execution-boundaries)
for durable state and recovery.

```java
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.SystemClock;
import io.github.flowerjvm.flower.core.worker.Worker;

public final class FlowerQuickStart {
    public static void main(String[] args) throws Exception {
        Worker worker = Worker.builder("orders").build();
        Engine engine = Engine.builder()
                .clock(SystemClock.INSTANCE)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();

        Flow flow = Flow.builder("order", "ORD-1")
                .step("accept", new PrintStep("accepted"))
                .step("payment", new WaitForPaymentStep())
                .step("fulfill", new PrintStep("fulfilled"))
                .build();

        engine.attach();
        try {
            worker.submit(flow);
            worker.tickOnce(); // Complete accept; payment is next.
            worker.tickOnce(); // Enter payment, subscribe, and stay.
            System.out.println("waiting at " + flow.currentStepId());

            engine.eventBus().publish(new PaymentApproved("ORD-1"));

            worker.tickOnce(); // Complete payment; fulfill is next.
            worker.tickOnce(); // Complete fulfill and finish the Flow.
            System.out.println("flow " + flow.state());
        } finally {
            engine.stop();
        }
    }

    static final class PrintStep extends Step {
        private final String message;

        PrintStep(String message) {
            this.message = message;
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            System.out.println(message + " " + ctx.flowId().flowKey());
            return StepResult.done();
        }
    }

    static final class WaitForPaymentStep extends Step {
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
                return StepResult.fail(
                        new IllegalStateException("payment timeout"));
            }
            return StepResult.stay();
        }
    }

    static final class PaymentApproved {
        private final String orderId;

        PaymentApproved(String orderId) {
            this.orderId = orderId;
        }

        String orderId() {
            return orderId;
        }
    }
}
```

Output:

```text
accepted ORD-1
waiting at payment
fulfilled ORD-1
flow FINISHED
```

Each Worker tick calls at most one `onTick` per active Flow. Completing
`accept` selects `payment`; its `onEnter` runs on the following tick.
Once the final Step returns `done()`, the Flow finishes and the Worker removes
it from its active set.

For scheduled execution, use `engine.start()` and stop the Engine with the
application's lifecycle. Let the scheduler drive the Worker; do not call
`tickOnce()` in scheduled mode. Spring Boot users can let the starter manage
that lifecycle. To test elapsed time deterministically, use `ManualClock`;
see [Testing](#testing).

<a id="mental-model"></a>

## The Execution Contract

Application code is organized as `Flow → Step → StepResult`. The runtime hosts it as:

```text
Engine
  └─ Worker
      └─ Flow
          └─ Step
              └─ StepResult
```

`Engine` owns runtime services such as the clock, event bus, Workers, and
listeners. In scheduled mode, each Core Worker ticks its active Flows on one
scheduler thread. `FlowId(flowType, flowKey)` identifies a Flow and is unique
across Workers within the same Engine.

### Step Lifecycle

| Callback | Purpose |
| --- | --- |
| `onEnter(ctx)` | Run when a Step enters; initiate work or subscribe for completion. |
| `onTick(ctx)` | Make a short, repeatable decision and return a `StepResult`. |
| `onExit(ctx)` | Clean up when the current Step exits, including cancellation. |
| `onReset(ctx)` | Reset for `repeat()`, before the Step re-enters. |

Entry occurs again on re-entry. Durable recovery uses the Step's declared
recovery policy; see [Checkpoint / Resume](docs/runtime-reference.md#checkpoint--resume).

### Explicit Results

| Result | Meaning |
| --- | --- |
| `stay()` | Keep the current Step and tick again later. |
| `done()` | Advance to the next declared Step, or finish if this is the last. |
| `repeat()` | Reset the current Step and run it again from the beginning. |
| `goTo("stepId")` | Move to a declared Step by its stable ID. |
| `finish()` | Finish successfully without running later Steps. |
| `fail(cause)` | Fail the Flow. |

These are `StepResult` factory methods. `repeat()` means reset and re-enter;
it does not supply a business-safe retry or backoff policy by itself.

<a id="step-design-rules"></a>
<a id="step-ids-stepno-and-shared-state"></a>
<a id="event-driven-steps"></a>

Keep Steps small and non-blocking. Have application services own external work,
and observe completion through signals, events, or domain state. A blocking
network call or sleep in `onEnter` or `onTick` stalls other Flows on that Worker.
Dispatch slow work through an appropriate asynchronous service or executor.

Create fresh Step instances when building each Flow and pass service
dependencies through constructors. Use `stepNo` only for a small local cursor,
and keep recoverable business state in domain storage. See
[Step design](docs/runtime-reference.md#step-design-rules) and
[events and waits](docs/runtime-reference.md#event-driven-steps).

<a id="typical-use-with-kafka"></a>
<a id="production-notes-for-kafka"></a>
<a id="flow-submission"></a>
<a id="execution-context"></a>
<a id="event-bus-choices"></a>
<a id="bloom-event-example"></a>

## Fits Inside Your Application

Flower is an execution layer. Your domain model and dependency-injection
container keep their existing responsibilities.

```text
REST / Kafka input
        ↓
Application workflow: Flow + Step classes
        ↓
Domain services, repositories, SDK adapters
```

In a multi-module Spring application, the workflow module can depend on Flower
while the domain module keeps its own objects, rules, and services.

For Kafka-backed work, preserve the distinction:

```text
Kafka event  = something happened
Flower Step  = decide whether execution can advance
Database     = remember the business fact
```

Persist the domain fact, publish an in-JVM notification, and let the Step decide
whether to advance. Submit a Flow after commit when it depends on newly stored
state. The host application handles duplicate events, inbox/outbox patterns
where needed, and startup recovery of active work.

See [Kafka integration](docs/runtime-reference.md#typical-use-with-kafka),
[Flow submission](docs/runtime-reference.md#flow-submission),
[Bloom integration](docs/runtime-reference.md#event-bus-choices), and
[Execution context](docs/runtime-reference.md#execution-context).
`ExecutionContext` carries execution identity; its `tenantId` does not change
the `FlowId` used for duplicate detection.

## Spring Boot

Use `flower-spring-boot-starter` for Java 17 / Spring Boot 3.x applications:

```kotlin
dependencies {
    implementation("io.github.flowerjvm:flower-spring-boot-starter:0.1.3")
}
```

```yaml
flower:
  enabled: true
  auto-start: true
  workers:
    - name: orders
      interval-ms: 100
```

The starter configures the Engine and its lifecycle, with clock and event-bus
defaults. JDBC checkpoints remain an explicit opt-in. Java 8/11 applications
can use the compatible Core artifacts but cannot load this starter.

See [Spring Boot configuration](docs/runtime-reference.md#spring-boot).

<a id="testing-with-flower-testkit"></a>

## Testing

Core supports manual execution through `engine.attach()` and `worker.tickOnce()`.
`ManualClock` lets tests control time. The optional MVP `flower-testkit` bundles
common test setup and assertions.

With the testkit dependency added, this example reuses the nested classes from
`FlowerQuickStart` above:

```java
try (FlowTestHarness harness = FlowTestHarness.create()) {
    Flow flow = Flow.builder("order", "ORD-1")
            .step("accept", new FlowerQuickStart.PrintStep("accepted"))
            .step("payment", new FlowerQuickStart.WaitForPaymentStep())
            .build();

    harness.submit(flow)
            .tick() // Complete accept and select payment.
            .tick() // Enter payment and subscribe before publishing.
            .assertFlow("order", "ORD-1")
            .isRunning()
            .currentStepIs("payment");

    harness.publish(new FlowerQuickStart.PaymentApproved("ORD-1"))
            .tick()
            .assertFlow("order", "ORD-1")
            .isFinished();
}
```

Import `io.github.flowerjvm.flower.testkit.FlowTestHarness` and `Flow`.
This uses a two-Step Flow: `accept → payment`, with payment as the final Step.
The three-Step quick-start Flow has an additional fulfillment stage.

See [Testkit setup and recovery tests](docs/runtime-reference.md#testing-with-flower-testkit).

<a id="observability"></a>
<a id="spring-boot-dump-endpoint"></a>
<a id="spring-boot-console"></a>

## Inspect The Same Model At Runtime

The Flow you declare is also the structure you inspect when the application
is running.

`Engine.dump()` exposes active Flows, their current Step, declared Step order,
and execution context. Lifecycle listeners observe submission, entry, exit,
completion, cancellation, and failure. Optional trace listeners and sinks add
execution history and correlated run information.

The Spring Boot starter can expose an opt-in internal console in the
application's existing web server:

![Flower console showing Workers, active Flows, current Steps, and execution context](assets/flower-console-runtime.png)

[Flower Studio](https://github.com/flowerjvm/flower-studio) is a separate
read-only local trace consumer. It explores execution paths, waits, recovery,
evaluation results, and optional Agent, Harness, and Action overlays.
Production metrics and alerts remain the host observability platform's responsibility.

Admin endpoints are disabled by default. Protect them with application
authentication and appropriate network controls; they can expose execution
identifiers and operational state.

See [Observability, tracing, and console configuration](docs/runtime-reference.md#observability).

<a id="ai-automation-and-ai-assisted-development"></a>

## AI Is A Use Case, Not A Dependency

The same execution model used by people and coding agents can also coordinate
AI work inside an application.

### Run AI Application Workflows

Express the surrounding application phases using the same Core model:

```text
Prepare context
  → run model or agent
  → wait for results
  → validate output
  → request approval when required
  → execute a governed action
  → observe the outcome
```

These phases remain Flows and Steps. Their AI-specific responsibilities stay
outside Core:

| Project | Responsibility |
| --- | --- |
| Flower Core | Application Flow execution, current Step, and explicit waits and transitions. |
| [Flower AI Harness](https://github.com/flowerjvm/flower-ai-harness) | Final structured-output validation and whole-task refinement or retry. |
| [Flower Action Runtime](https://github.com/flowerjvm/flower-action-runtime) | Mutating actions with policy, approval, idempotency, and audit. |

A model call belongs in an application service or adapter, dispatched without
blocking the Worker. Making "approval" a Step does not itself implement
authorization; the application or Action Runtime must enforce it.

<a id="use-flower-with-chatgpt-and-codex"></a>

### Set Up Coding-Agent Guidance And Checks

The [Flower plugin for ChatGPT and Codex](https://chatgpt.com/plugins/plugins_6a6b70b4903081918ec3eb37651cf01f)
provides the Flower skills. The guidance is also available in the
[Flower Skills repository](https://github.com/flowerjvm/flower-agent-skills).

Add the [Maven](flower-check-maven-plugin/README.md) or
[Gradle](flower-check-gradle-plugin/README.md) checker to the host build, and
use [deterministic tests](#testing) to exercise the application's behavior.
`flower-check` detects supported structural and usage violations, such as
blocking Worker ticks; tests check the application-specific outcomes.

<a id="what-flower-is-not"></a>
<a id="operational-boundaries"></a>
<a id="checkpoint--resume"></a>

## Execution Boundaries

Flower's small footprint comes with an explicit scope.

| Boundary | What to expect |
| --- | --- |
| One JVM | Core is not a distributed scheduler, multi-node coordinator, BPMN engine, or durable saga engine. |
| Tick-based Core | In scheduled mode, a Worker ticks active Flows on one scheduler thread. `stay()` does not turn Core into a wake-only event loop. |
| In-memory notifications | Signals and subscriptions are not a durable event log. Recheck durable business facts when recovery matters. |
| Opt-in checkpoint/resume | Rebuild a fresh Flow and resume from a saved position and execution identity. Step objects, signals, and arbitrary business state are not serialized. This is not execution replay. |
| External side effects | Checkpoints do not make database writes, messages, or API calls exactly-once. Use application-level idempotency and durable intent/outbox records where needed. |
| Durable deadlines | Core `startTimeout(...)` is runtime-only and rejected in durable Flows. Store recoverable deadlines in domain state, or use the separate event-loop runtime's await deadlines. |
| Storage and ownership | Checkpoint writes are synchronous. Multi-process recovery requires host-managed locking, leases, or leader election. |
| Scale | Core targets small-to-medium in-process workloads. Very large idle-Flow populations may need sharding or another scheduling strategy. |

The MVP [flower-eventloop](flower-eventloop/README.md) module is a separate
execution line. It has its own API and recovery behavior; these Core examples
describe the tick-driven Worker / Flow / Step contract.

For checkpoint policies, event-loop crash windows, and recovery ownership,
read [Checkpoint / Resume](docs/runtime-reference.md#checkpoint--resume),
[Operational boundaries](docs/runtime-reference.md#operational-boundaries),
and [Persistence](docs/persistence.md).

<a id="why-and-when-to-use-it"></a>

## When To Use Flower

Use Flower when work has meaningful execution phases, especially when it waits
for events, needs timeouts, revisits stages, or needs an inspectable current position.

For a short `validate → save → return` method, ordinary Java methods may be
enough. For a small state machine, an enum and a switch may be enough. Flower
is useful when the surrounding execution machinery becomes a concern of its own.

It is domain-independent, not a requirement to rewrite every kind of Java code as a Flow.

<a id="modules-and-maturity"></a>

## Modules

**Start with Core. Add only what you need.**

| Module | Adds | Status |
| --- | --- | --- |
| `flower-core` | Engine, Worker, Flow, Step, event bus, clock, and listener APIs. | Established execution model |
| `flower-spring-boot-starter` | Spring Boot configuration and lifecycle integration. | Optional |
| `flower-persistence-jdbc` | JDBC checkpoints with explicit schema setup. | Optional |
| `flower-observability` | Logging, tracing, metrics integration, and trace sinks. | Optional |
| `flower-testkit` | Deterministic test helpers. | MVP |
| [flower-check and build plugins](flower-check/README.md) | Build-time checks for known Flower anti-patterns. | MVP |
| [flower-evaluation](flower-evaluation/README.md) | Offline datasets, evaluators, comparisons, and feedback. | MVP |
| [flower-eventloop](flower-eventloop/README.md) and `flower-eventloop-persistence-jdbc` | Separate event-driven execution and checkpoints. | MVP |

MVP labels describe the individual optional modules above. Their APIs are being
refined around Core's established execution model.

JDBC stores include schema SQL for PostgreSQL, MySQL, Oracle, H2, and SQLite.
The host supplies its driver and applies the schema explicitly.

These modules do not all need to be installed together. Bloom's separately
published adapter connects an existing Bloom event bus; Flower Core already
includes `InMemoryEventBus`.

<a id="java-compatibility"></a>

See [Module details and maturity](docs/runtime-reference.md#modules-and-maturity)
and [Java compatibility](docs/runtime-reference.md#java-compatibility).

## Where It Comes From

Flower's `Worker → Flow → Step → StepResult` execution model was shaped by
practical experience gained while developing industrial equipment control
systems and business applications.

It generalizes recurring patterns observed in long-running, stage-based
processes into a reusable workflow runtime for Java applications: explicit
execution stages, result-driven transitions, waits, timeouts, retries, human
intervention, and inspectable execution traces.

The underlying discipline is simple: make the current state visible, make
transitions explicit, keep each unit of work small, and leave a trace that a
human can inspect.

<a id="where-flower-is-being-hardened"></a>

Flower is being exercised in architecture-office SaaS document workflows, a
Terminal Operating System execution layer, game-server coordination, and
controlled AI automation. These projects help harden the design; they are
not a claim of broad independent adoption.

## Documentation And Project Status

The [Runtime reference](docs/runtime-reference.md) covers detailed API guidance,
Kafka and Bloom integration, Spring configuration, checkpoints, execution
context, observability, tests, and module maturity. The reference is currently
in English; this introduction is available in both English and Korean.

For deeper topics, see [Persistence](docs/persistence.md),
[Tracing, Studio, and Evaluation](docs/tracing-studio-evaluation.md),
[Trace Storage and Security](docs/tracing-storage-security.md), and
[Domain Observation Adapters](docs/domain-observation-adapters.md).

Project process: [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) ·
[Roadmap](ROADMAP.md) · [Releasing](docs/RELEASING.md).

<a id="build"></a>

To build the repository:

```bash
mvn -B verify
```

The full repository build requires JDK 17. Applications should normally consume
released Maven Central artifacts. Contributors working on the separately
built Gradle checker should follow [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Flower is licensed under the [Apache License 2.0](LICENSE).

**Different domains. The same execution model.**

Make the flow explicit. Keep the domain yours.
