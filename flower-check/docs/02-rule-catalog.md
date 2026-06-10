---
doc_level: L3
status: active
authority: rule-design
depends_on:
  - 00-INDEX.md
  - 01-architecture.md
supersedes: []
last_reviewed: 2026-06-10
---

# 02. flower-check Rule Catalog

> Authority: L3. Each rule must conform to the upstream source of truth listed
> in `00-INDEX.md`. If a rule contradicts `flower-core` or `flower/README.md`,
> the rule is the bug.

This is the enforced contract. Each rule is grounded in the real `flower-core`
API and the Flower execution model, so a check maps to a concrete, defensible
violation — not a style opinion.

Every rule states: **what** is detected, **why** it is risky, and the **fix**.
A rule that cannot fill all three does not belong here.

## Development Notes Grounding

Rules must be grounded in `flower-core`, the public `flower/README.md`, and the
private rationale in `../../flower-dev-notes` when that workspace is available.
Use this map before adding or tightening a rule:

```text
01-scope-and-principles.md
  - core stays small and Java 8 compatible
  - Step instances are stateful and owned by one Flow
  - event callbacks record signal/payload only; onTick decides progress

02-core-architecture.md
  - Engine -> Worker -> Flow -> Step ownership
  - Worker ticks Flows sequentially
  - Step ids are unique within a Flow
  - Step creation/dependency wiring belongs to user factories, not core magic

03-step-stepno-and-flow-control.md
  - Flow-level stepId is different from Step-internal stepNo
  - goTo targets a declared Flow-level step id
  - time/old tick waits move to StepContext timeout patterns

04-events-bloom-and-threading.md
  - Flower core does not import Bloom
  - ctx.subscribe is the framework-managed Step subscription path
  - event handlers run off the Worker boundary and must not decide StepResult

08-current-implementation-update.md
  - durable support is checkpoint/resume, not replay
  - ExecutionContext is identity only, never business state
  - tenantId is not part of FlowId; runId survives recovery

09-flower-testkit.md
  - testkit is outside core and depends on core only
  - deterministic Flow tests should use harness-style tick/assert helpers

User scheduler approval requirement
  - recurring Spring/Java schedulers must not be introduced as an AI shortcut
  - every recurring scheduler must carry an explicit user-approval annotation
  - one-shot delayed simulation remains allowed; repeated/cron work is gated
```

If a rule cannot point to one of these anchors or to equivalent upstream source
code, do not enable it by default.

## Core API Baseline

This catalog was reconfirmed against `flower-core` on 2026-06-10. Rule
implementations must use these current API facts, even when older design notes
show earlier names:

```text
Step lifecycle:
  Step.onEnter(ctx)
  Step.onTick(ctx)
  Step.onExit(ctx)
  Step.onReset(ctx)
  DurableStep.onResume(ctx)

StepResult factories:
  stay()
  done()
  repeat()
  goTo(String stepId)
  finish()
  fail(Throwable cause)

FlowBuilder durable API:
  durable()
  step(String, Step)
  step(String, Step, Guard)
  durableStep(String, Step, RecoveryPolicy)
  durableStep(String, Step, Guard, RecoveryPolicy)
```

There is no current public `StepResult.advance()`, `StepResult.defer()`,
`StepResult.goTo(..., mode)`, or `durableStep(...)` overload without a
`RecoveryPolicy`. A rule should not search for APIs that cannot compile in the
current core unless it is explicitly a migration rule and is opt-in.

## Scope Of A "Step Lifecycle Method"

Several rules apply *inside Step lifecycle methods*. That means the bodies of
methods that override, in a class extending `Step` or `DurableStep`:

```text
onEnter(StepContext)
onTick(StepContext)
onExit(StepContext)
onReset(StepContext)
onResume(StepContext)   // DurableStep
```

and any private helper reached only from those methods within the same class.
It does **not** mean the whole class — constructors and unrelated helpers are
out of scope, because Flower's threading contract is about what runs *on the
Worker tick*, not about the class as a whole.

Grounding: `flower-core` runs every Flow on a single Worker thread, one tick at
a time (`02-core-architecture.md`, `04-events-bloom-and-threading.md`). The
Worker calls these methods. Anything slow or control-stealing here corrupts
every other Flow on that Worker.

---

## Tier 1 — Step / Flow Usage (FLOWER-CHECK-001..005, 009..016)

### FLOWER-CHECK-001 — No blocking on the Worker thread

**Severity:** ERROR

**What:** Inside a Step lifecycle method, a blocking call:

```text
Thread.sleep(...)
Object.wait(...) / join()
Future.get() / CompletableFuture.get()  without a timeout argument
blocking JDBC / socket / HTTP read
while/for poll loop that spins until a condition (busy-wait)
```

**Why:** One Worker ticks every one of its Flows on one thread
(`Worker tick -> for each Flow: flow.tick`). A blocking call freezes *all*
other Flows on that Worker until it returns. Flower's whole composability comes
from quick, repeatable ticks (`flower/README.md` "Do not block a worker tick").

**Fix:** Start the wait in `onEnter` and observe completion across ticks. Return
`StepResult.stay()` while waiting; resolve on a signal or `ctx.timedOut()`.

```java
// bad
protected StepResult onTick(StepContext ctx) {
    Thread.sleep(5_000);                 // freezes the Worker
    return StepResult.done();
}

// good
protected void onEnter(StepContext ctx) { ctx.startTimeout(5_000); }
protected StepResult onTick(StepContext ctx) {
    return ctx.timedOut() ? StepResult.done() : StepResult.stay();
}
```

Detection: AST scan of in-scope method bodies for the calls above. `Future.get`
with an explicit timeout and a `try/catch(TimeoutException)` is allowed.

---

### FLOWER-CHECK-002 — No direct LLM / provider SDK calls in a Step

**Severity:** ERROR

**What:** A Step lifecycle method directly calls an AI/provider SDK (Anthropic,
OpenAI, etc.) or an obvious blocking model client.

**Why:** Model calls are slow, failure-prone, and need quality validation,
retry, and refine handling. The Flower README requires Step work to stay
asynchronous in shape: start external work, return `stay()`, and observe
completion on later ticks. Agent/action state also belongs in a higher-level
module, not in `flower-core` or a raw Step. A direct SDK call re-introduces the
FLOWER-CHECK-001 blocking problem.

**Fix:** Submit the model call to an executor / harness service in `onEnter` (or
`stepNo 0`), then observe the result over ticks. Route model access through the
project's AI-harness layer, not the Step.

Detection: configured set of provider client type/package names invoked inside
in-scope method bodies. The provider list is configuration so it adapts per
project.

---

### FLOWER-CHECK-003 — A Flow must not directly drive another Flow

**Severity:** ERROR

**What:** Inside Step / FlowFactory code:

```text
worker.tick() / worker.tickOnce()
engine.start() / engine.stop() / engine.attach()
calling another Step's onTick/onEnter directly
holding another Flow and mutating its step/state
```

**Why:** Tick ownership belongs to the Worker. Driving another Flow by hand
breaks the deterministic single-threaded tick boundary that makes Flower
predictable (`04-events-bloom-and-threading.md`: only the Worker thread decides
Flow/Step progress). A Step that needs to start child work should submit a
child Flow to a Worker and wait through state/event checks.

**Fix:** `worker.submit(childFlow)` and then wait on a signal, an event, or
durable domain state. Never tick or mutate another Flow inline.

Detection: calls to `tick/tickOnce/attach/start/stop` on a `Worker`/`Engine`
reference, and direct `onTick/onEnter` invocations, inside Step/FlowFactory
sources.

---

### FLOWER-CHECK-004 — A waiting Step must have a timeout or cancellation

**Severity:** WARNING

**What:** A Step that can return `StepResult.stay()` while waiting on an
external signal/event, but never calls `ctx.startTimeout(...)` (and exposes no
cancellation path / max-tick bound).

**Why:** Without a bound, a missed event strands the Flow forever and the Worker
keeps ticking a Flow that will never progress. Real Flower steps pair a wait
with a timeout (`flower/README.md` `WaitForPaymentStep`; sample `PaymentStep`
uses `startTimeout` + a `timedOut()` fail branch).

**Fix:** Call `ctx.startTimeout(ms)` when entering the wait, and handle
`ctx.timedOut()` with `fail(...)`, `repeat()`, or a `goTo` recovery step.

Detection: a Step whose `onTick` contains a `subscribe`/`hasSignal`/
`consumeSignal` wait and a `stay()` return, but no `startTimeout` /
`timedOut` / `elapsedMillis` anywhere in the class. WARNING because some
indefinite waiters are legitimate (e.g. a long-lived monitor loop) — those use
a suppression comment with a reason.

---

### FLOWER-CHECK-005 — Durable Flow steps must declare a recovery policy

**Severity:** ERROR

**What:** A `Flow.builder(...).durable()` chain that adds a step whose recovery
policy cannot be resolved:

```text
.durable() ... .step("x", new XStep())          // XStep is not DurableStep
```

`durableStep(...)` without a `RecoveryPolicy` is not a current core API and
should not be a detection target. A plain `.step(...)` is valid only when the
step type is known to extend `DurableStep`, because `StepDefinition` resolves
that step's built-in policy.

**Why:** Durable mode is checkpoint/resume: after a restart the host rebuilds a
fresh Flow and resumes from the saved position. Every step must declare how it
behaves on re-entry, or recovery is undefined (`flower/README.md`
Checkpoint/Resume: *durable flows require every step to declare a recovery
policy*; `08-current-implementation-update.md`).

**Fix:** Use `.durableStep("x", new XStep(), RecoveryPolicy.REENTER_IDEMPOTENT)`
when re-running `onEnter` is safe. When entry and recovery must differ, extend
`DurableStep` with `RecoveryPolicy.RESUME_ONLY` and implement `onResume(ctx)`;
the step may then be recoverable through its own `DurableStep` policy.

Detection: in a builder chain where `.durable()` is present, flag `.step(...)`
calls whose step argument is known not to extend `DurableStep`, or whose type
cannot be proven recoverable from the analyzed source/configuration. Do not flag
`.durableStep(...)` calls with a `RecoveryPolicy`; the missing-policy overload
does not exist in current core.

---

### FLOWER-CHECK-009 — `goTo` target must be a declared step id

**Severity:** ERROR

**What:** `StepResult.goTo("cleanup")` whose literal id is not declared by any
`.step(...)` / `.durableStep(...)` in the flow(s) that contain the step.

**Why:** Flower navigates by string step id, not by Java class
(`03-step-stepno-and-flow-control.md`; `flower/README.md` "goTo targets that
id, not the Java class name"). A typo'd or stale id is a runtime failure, not a
compile error — exactly the silent breakage `flower-check` exists to catch.

**Fix:** Make the `goTo` literal match a declared step id, or add the missing
step to the builder. Treat step ids as a small closed vocabulary per flow type.

Detection: pass-1 `stepIdGraph` resolves, per flow type, declared ids vs.
referenced `goTo` literals. Non-literal targets (a variable/constant) are
reported as INFO ("cannot verify goTo target statically") unless the constant
resolves within the source set.

---

### FLOWER-CHECK-010 — Event callbacks may only record, not decide

**Severity:** ERROR

**What:** Inside a `ctx.subscribe(Type.class, e -> { ... })` handler (or a
method used as one), code that does more than record:

```text
returns / produces a StepResult
calls ctx.setStepNo(...) to drive control flow
calls domain services / blocking work
mutates flow or other-step state
```

Allowed in a handler: `ctx.signal(name)`, `ctx.signal(name, payload)`,
enqueue-style recording, and a cheap guard `if`.

**Why:** Event handlers run on the *publisher's* thread, not the Worker thread.
The Flower contract is that handlers only set a signal / enqueue a payload; the
real `StepResult` is decided later in `onTick` on the Worker tick
(`04-events-bloom-and-threading.md` Threading 원칙; `06`/`README` canonical
PaymentStep does exactly `equals(...) -> ctx.signal(...)`). Deciding inside the
handler reintroduces the cross-thread races Flower was built to remove.

**Fix:** In the handler, only `ctx.signal("name")` (optionally with payload).
Read it in `onTick` and convert to a `StepResult` there.

Detection: AST scan of lambda/method-reference bodies passed to
`ctx.subscribe(...)`; flag service calls, `setStepNo`, and any `StepResult`
construction inside them.

---

### FLOWER-CHECK-011 — A Step must not own Engine/Worker lifecycle

**Severity:** ERROR

**What:** A Step (or its constructor) creates or controls runtime
infrastructure: `Engine.builder()`, `Worker.builder()`, `engine.start/stop`,
or stores an `Engine`/`Worker` to drive it.

**Why:** Layering is `Engine -> Worker -> Flow -> Step`
(`02-core-architecture.md`). A Step is the smallest orchestration unit; it
decides *within* a tick. Owning the runtime inverts the hierarchy and makes the
Flow non-deterministic and untestable with `tickOnce()`.

**Fix:** Build `Engine`/`Worker` in application wiring (or the Spring starter).
Pass only domain services into Steps via constructors.

Detection: `Engine.builder`/`Worker.builder`/engine lifecycle calls inside
classes extending `Step`/`DurableStep`.

---

### FLOWER-CHECK-012 — Prefer framework-managed subscriptions in a Step

**Severity:** WARNING

**What:** A Step subscribes through the raw bus —
`ctx.eventBus().subscribe(...)` — instead of `ctx.subscribe(...)`, without
storing the returned `Subscription` and unsubscribing in `onExit`/`onReset`.

**Why:** `ctx.subscribe(...)` subscriptions are auto-released when the Step
exits, resets, or the Flow terminates; raw `eventBus()` subscriptions are
**not** framework-managed (`StepContext` Javadoc; `flower/README.md`). An
un-released listener leaks and can fire for a Step that is no longer current —
the old `unregisterAllListeners()` bug Flower removed.

**Fix:** Use `ctx.subscribe(Type.class, handler)`. If `eventBus()` access is
truly needed, keep each `Subscription` and `unsubscribe()` it in
`onExit`/`onReset`.

Detection: `ctx.eventBus().subscribe(` inside a Step where the result is not
assigned to a field that is unsubscribed in `onExit`/`onReset`.

---

### FLOWER-CHECK-013 — Step ids must be unique within a Flow

**Severity:** ERROR

**What:** Two `.step(...)`/`.durableStep(...)` calls in one builder chain use
the same id literal.

**Why:** Step id must be unique within a Flow (`02-core-architecture.md`
StepDefinition). Flower resolves `goTo` and current-step by id; a duplicate id
makes navigation and the dump/console ambiguous.

**Fix:** Give each step a distinct, meaningful id.

Detection: pass-1 duplicate-id check per flow builder.

---

### FLOWER-CHECK-014 — `ExecutionContext` is not a business context

**Severity:** WARNING

**What:** Code stuffs business state into `ExecutionContext`, or treats it as a
general bag — e.g. building it from a `Map<String,Object>`, or reading
role/permission/approval/agentId/actionId out of it.

**Why:** `ExecutionContext` is a small execution *id card*
(tenant/user/session/run/trace/correlation only). Business state, roles,
policy, approval, and agent/action ids are explicitly excluded; a metadata map
is called out as "a back door for business state"
(`08-current-implementation-update.md`; `flower/README.md` Execution Context).

**Fix:** Keep identity in `ExecutionContext`. Put roles/policy/approval/domain
state in domain services and (for agents) a higher-level
`flower-agent-runtime` layer.

Detection: `ExecutionContext` construction from a map, or accessor names
outside the allowed field set used on an `executionContext()` result.

---

### FLOWER-CHECK-015 — Do not share a Step instance across Flows

**Severity:** WARNING

**What:** The same `Step` instance is reused — e.g. a `static`/singleton Step
field placed into more than one `Flow.builder`, or a FlowFactory that returns
flows built from a shared Step instance.

**Why:** A Step is stateful and owned by one Flow (`01-scope-and-principles.md`
Stateful: "같은 Step class라도 Flow instance마다 별도 Step instance";
`flower/README.md` "Create fresh step instances when building a new flow").
Sharing leaks `stepNo`/signals/timeout state between Flows.

**Fix:** Construct fresh Step instances per Flow inside the FlowFactory.

Detection: a Step-typed field/local that is `static` or reused across multiple
`.step(...)` arguments; best-effort, hence WARNING.

---

### FLOWER-CHECK-016 — Recurring schedulers require explicit user approval

**Severity:** ERROR

**What:** Code introduces recurring scheduler behavior without an approval
annotation on the same method or class:

```text
@Scheduled(...)
@EnableScheduling
scheduler.scheduleAtFixedRate(...)
scheduler.scheduleWithFixedDelay(...)
taskRegistrar.addCronTask(...) / addFixedRateTask(...) / addFixedDelayTask(...)
timer.scheduleAtFixedRate(...)
```

The default approval annotation names are:

```text
@FlowerSchedulerApproved
@UserApprovedScheduler
@SchedulerApproved
@ApprovedScheduler
```

Projects may add more names through `schedulerApprovalAnnotations` in
`flower-check.config`.

**Why:** Recurring schedulers are a common escape hatch when an agent does not
want to model waiting, retry, or approval through Flower's explicit Step/Flow
boundary. Hidden cron/poll loops can duplicate Flow execution, bypass user
approval, or keep doing work after the Flow should be parked.

**Fix:** Prefer an explicit Flower wait pattern: start the external request,
return `StepResult.stay()`, and resume from an event/signal/timeout. If a real
recurring scheduler is still required, get user approval and mark the method or
class with an approved annotation.

Detection: AST scan for Spring scheduling annotations and recurring Java/Spring
scheduler APIs. One-shot delayed calls such as `TaskScheduler.schedule(...,
Instant)` are not flagged; they are not periodic work.

---

## Tier 2 — Agent Runtime (FLOWER-CHECK-006..008)

These target the future agent/harness layer described as outside `flower-core`
in `flower/README.md` and `flower-dev-notes/08-current-implementation-update.md`.
They are **off by default** until a project opts in (`config: rule.agent
enabled`), because they need project-specific type names (`ActionRegistry`,
`PolicyGate`, audit/approval services). They encode the boundary that agent and
approval state belongs above core, not in `ExecutionContext` or raw Step side
effects.

### FLOWER-CHECK-006 — Agent write actions must not bypass ActionRegistry / PolicyGate

**Severity:** ERROR

**What:** An agent action performs an external write (DB write, command
dispatch, HTTP mutation, file/storage write) without going through the
registered action / policy gate.

**Why:** Agent actions must be registered and policy-checked, not free-form side
effects. Current Flower docs deliberately keep agent/action/approval state out
of `flower-core` and reserve it for a higher-level `flower-agent-runtime`
boundary.

**Fix:** Register the action and dispatch through `ActionRegistry`/`PolicyGate`
so the write is gated, observable, and revocable.

### FLOWER-CHECK-007 — Business write actions must emit or require an audit event

**Severity:** WARNING

**What:** An agent/business write action with no corresponding audit/operation
event.

**Why:** Important state changes must leave an operation/audit trail so
operators can see cost, failures, and stuck/blocked actions. An unaudited write
is invisible to admin/console and to recovery reasoning.

**Fix:** Emit an audit/operation event (or require one via the action contract)
alongside the write.

### FLOWER-CHECK-008 — Approval-required actions must not execute directly

**Severity:** ERROR

**What:** An action marked approval-required is executed inline instead of being
parked for an approval decision.

**Why:** Approval-gated work must wait for a decision, the same way a Flower
wait step waits for a signal — not run eagerly on the tick. Executing directly
defeats the gate.

**Fix:** Submit the action through the approval path; resume only after the
approval signal/state, then dispatch through the gated registry
(FLOWER-CHECK-006).

Detection (Tier 2, all): heuristic, driven by configured type/annotation names
for actions, the registry/gate, the audit emitter, and the approval marker.
Because these are heuristic, they default to opt-in and lean toward WARNING
unless the project supplies precise names.

---

## Rule Index

```text
FLOWER-CHECK-001  ERROR    No blocking on the Worker thread
FLOWER-CHECK-002  ERROR    No direct LLM/provider SDK calls in a Step
FLOWER-CHECK-003  ERROR    A Flow must not directly drive another Flow
FLOWER-CHECK-004  WARNING  A waiting Step must have timeout/cancellation
FLOWER-CHECK-005  ERROR    Durable Flow steps must declare a recovery policy
FLOWER-CHECK-006  ERROR    Agent write must not bypass ActionRegistry/PolicyGate
FLOWER-CHECK-007  WARNING  Business write must emit/require an audit event
FLOWER-CHECK-008  ERROR    Approval-required action must not execute directly
FLOWER-CHECK-009  ERROR    goTo target must be a declared step id
FLOWER-CHECK-010  ERROR    Event callbacks may only record, not decide
FLOWER-CHECK-011  ERROR    A Step must not own Engine/Worker lifecycle
FLOWER-CHECK-012  WARNING  Prefer framework-managed subscriptions in a Step
FLOWER-CHECK-013  ERROR    Step ids must be unique within a Flow
FLOWER-CHECK-014  WARNING  ExecutionContext is not a business context
FLOWER-CHECK-015  WARNING  Do not share a Step instance across Flows
FLOWER-CHECK-016  ERROR    Recurring schedulers require user approval
```

## False-Positive Baseline

Every rule must pass these known-good modules with zero findings before it is
enabled by default:

```text
flower-sample/cafe-order
flower-sample/durable-order
flower-sample/flower-basic-samples
```

If a rule fires on canonical sample code, the rule is wrong, not the sample.
Tighten the detection or downgrade to opt-in.
