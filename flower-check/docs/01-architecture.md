---
doc_level: L2
status: active
authority: architecture-contract
depends_on:
  - 00-INDEX.md
  - ../README.md
supersedes: []
last_reviewed: 2026-06-10
---

# 01. flower-check Architecture

> Authority: L2. Governed by `00-INDEX.md` (L1) and `../README.md` (L0). On any
> question of correct Flower usage, the upstream `flower-core` source wins.

This document defines the internal design of `flower-check`: how source code
becomes a set of findings, and how that pipeline stays small, deterministic,
and low on false positives.

The driving constraint is the one from the top-level README: the first useful
version should be *small, boring, and strict*. Everything below is shaped to
keep it that way.

## High-Level Pipeline

```text
source paths
  -> SourceLoader        (find .java files, read text)
  -> Parser              (JavaParser AST, or conservative text fallback)
  -> ProjectModel build  (pass 1: type hierarchy, flow builders, step ids)
  -> Rule execution      (pass 2: each Rule inspects model + AST)
  -> Findings            (id, severity, file, line, message, why, fix)
  -> Reporter            (plain text or SARIF)
  -> exit code           (non-zero when any finding at/above fail threshold)
```

The pipeline is two passes on purpose. Most meaningful Flower rules are not
local to a single line — they need to know "is this class a `Step`?", "is this
`goTo` target a declared step id?", "is this flow durable?". Pass 1 builds that
context once; pass 2 rules read it instead of re-deriving it.

## Module Shape

`flower-check` starts as a single CLI module and only splits when integration
demands it.

```text
flower-check            CLI + engine + rules (start here)
flower-check-gradle     Gradle plugin wrapper (later)
flower-check-maven      Maven plugin wrapper (later)
```

Do not add `flower-check` to the Maven reactor until there is real code, per
the top-level README. The Gradle/Maven wrappers are thin: they collect source
roots and configuration, then call the same engine the CLI calls.

Internal package layout:

```text
io.github.parkkevinsb.flower.check
  cli         entry point, argument parsing, exit codes
  source      SourceLoader, SourceFile, SourceSet
  parse       Parser abstraction over JavaParser + text fallback
  model       ProjectModel, StepType, FlowBuilderSite, StepIdGraph
  rule        Rule SPI, RuleContext, RuleRegistry, Severity
  rule.core   FLOWER-CHECK-001..005 (Step/Flow usage rules)
  rule.agent  FLOWER-CHECK-006..008 (agent-runtime rules)
  finding     Finding, FindingCollector, suppression
  report      Reporter, PlainTextReporter, SarifReporter
  config      FlowerCheckConfig, baseline loading
```

`flower-check` itself targets Java 8+ where practical so it can run in the same
old toolchains Flower targets, but the checker is a build tool — if the parser
library forces a higher baseline that is acceptable, because the checker never
ships inside `flower-core`.

## Parsing Strategy

Rules need structure (which class extends `Step`, which method overrides
`onTick`, what the `goTo` string literal is). A regex linter cannot answer
those reliably, so the primary parser is an AST.

```text
Primary:   JavaParser (com.github.javaparser)
           - full AST, line numbers, type names by simple name
           - no full symbol solver required for v1 rules

Fallback:  conservative text scan
           - used only when a file fails to parse
           - emits a low-severity "could not analyze" note, never a hard rule
             violation, so broken/generated files do not fail the build on a
             parse error alone
```

v1 deliberately avoids a full type-resolution symbol solver. The rules are
designed to work from *simple names and syntactic shape* (e.g. a class whose
`extends` clause names `Step` or `DurableStep`). This trades a small amount of
precision for far less setup and far fewer environment-dependent failures. If a
later rule genuinely needs resolved types, the symbol solver is added behind
`RuleContext`, not sprinkled through rules.

## ProjectModel (Pass 1)

Pass 1 walks every parsed file once and records the facts rules share. It does
not produce findings.

```text
ProjectModel
  stepTypes        : classes extending Step / DurableStep
                     (name, file, onEnter/onTick/onExit/onReset methods)
  flowBuilders     : Flow.builder(...) call sites
                     (flowType literal if present, durable? flag,
                      declared step ids, guard presence, recovery policies)
  stepIdGraph      : per flow builder, the set of declared step ids and the
                     set of goTo("...") / setNextSeq targets referenced
  agentActions     : classes/methods that look like agent write actions
                     (best-effort; see agent rules for the heuristic)
```

Key model facts and how they are gathered:

- **Is this class a Step?** `extends Step` or `extends DurableStep` by simple
  name. Transitive subclassing (a project base class that extends `Step`) is
  resolved within the analyzed source set; a Step base class from a dependency
  is matched by configured names.
- **Step bodies.** The overrides of `onEnter`, `onTick`, `onExit`, `onReset`
  are recorded so "in-Step" rules only scan inside those methods, not the whole
  class.
- **Flow shape.** A `Flow.builder(type, key)` chain is followed through
  `.step(...)`, `.step(..., guard)`, `.durable()`, `.durableStep(...)`,
  `.executionContext(...)`, `.build()`. This yields the declared step-id set
  and whether the flow is durable — both needed by several rules.

## Rule SPI (Pass 2)

A rule is small, single-purpose, and explains itself.

```java
public interface Rule {

    /** Stable id, e.g. "FLOWER-CHECK-001". */
    String id();

    /** Default severity; user config may override. */
    Severity defaultSeverity();

    /** Inspect one source unit against shared model; return findings. */
    List<Finding> apply(SourceUnit unit, RuleContext context);
}
```

```text
SourceUnit
  file, compilation-unit AST, the Step/Flow facts for that file

RuleContext
  read-only ProjectModel, FlowerCheckConfig, line resolver
```

```text
Severity
  ERROR    fails the build by default
  WARNING  reported, does not fail by default
  INFO     advisory (e.g. parse-fallback notes)
```

Rules never read files, parse, or call `System.exit`. They receive a parsed
unit and shared context and return findings. That keeps each rule a pure,
testable function and lets the engine run, order, and report them uniformly.

## Findings

```java
public final class Finding {
    String ruleId;        // FLOWER-CHECK-001
    Severity severity;
    String file;          // relative path
    int line;             // 1-based
    int column;           // best-effort
    String what;          // what was found
    String why;           // why it is risky
    String fix;           // what to do instead
}
```

Every finding carries `what / why / fix`. This is a hard requirement, not a
nicety: it is the difference between an enforcement tool people trust and a
linter people silence. Plain-text output renders all three.

```text
FLOWER-CHECK-001  ERROR  src/.../WaitTruckStep.java:42
  what: Thread.sleep(...) inside Step.onTick blocks the Worker thread.
  why : One Worker ticks every Flow on one thread. A blocking call freezes all
        other Flows on that Worker until it returns.
  fix : Start the wait in onEnter (timeout/event/subscription) and return
        StepResult.stay() until a signal or ctx.timedOut() resolves it.
```

## Suppression And Baseline

Strictness only survives if there is a controlled escape hatch; otherwise teams
disable the whole tool.

- **Inline suppression**: a line/element comment
  `// flower-check:ignore FLOWER-CHECK-004 reason: ...` suppresses one rule at
  one site. A reason is required.
- **Baseline file**: `flower-check-baseline.txt` records findings that exist
  today so a team can adopt the tool without fixing everything at once. New
  violations still fail; baselined ones are reported as accepted debt.
  Baseline lines are intentionally copy/paste-friendly:
  `FLOWER-CHECK-001 src/main/java/demo/WaitStep.java:42` (a severity token after
  the rule id is also accepted).
- **Config disable**: a rule can be turned off or down-graded in config, but
  the default posture is strict.

```text
flower-check.config (or flower-check section in build config)
  rules:
    FLOWER-CHECK-004: warning      # downgrade
    FLOWER-CHECK-007: off          # disable (must be justified in review)
  failOn: error                    # build fails at this severity and above
  stepBaseClasses:                 # extra project-specific Step base classes
    - com.acme.flow.AbstractDomainStep
  baselineFile: flower-check-baseline.txt
```

## CLI And Exit Codes

```bash
flower-check src/main/java
flower-check --config flower-check.config src/main/java another/src
flower-check --format sarif src/main/java > flower-check.sarif
flower-check --write-baseline flower-check-baseline.txt src/main/java
flower-check --list-rules
mvn verify
mvn -Dflower.check.skip=true verify
```

```text
exit 0   no findings at or above failOn
exit 1   findings at or above failOn (build should fail)
exit 2   usage error (bad arguments / unreadable path)
```

`--write-baseline <file>` is an adoption/update mode. It writes the current
findings in baseline format and exits 0 when writing succeeds, because the
purpose is to record accepted debt before enabling the normal failing check.

Maven `verify` runs the same engine from the `flower-check` module and scans
the Flower reactor source roots. `-Dflower.check.skip=true` is available for
explicit local bypass only. Gradle and dedicated Maven wrappers can later map
the same engine onto `flowerCheck` / `flower-check:check` goals.

CI must run `mvn verify` for pull requests so generated or handwritten Flower
code is checked before merge.

## Why This Shape

- **Two passes** because the rules that matter are cross-file (step-id
  resolution, durable-flow shape), not single-line greps.
- **AST primary, text fallback** because Flower rules are structural, but a
  single un-parseable generated file must not take down the whole build.
- **Pure rules + shared model** so rules are independently testable and the
  `flower-sample` modules can be used as a zero-finding regression baseline.
- **what/why/fix on every finding** because the product is enforcement people
  accept, and an unexplained failure gets suppressed, not fixed.
- **Strict default, controlled suppression** so the tool stays strict without
  being un-adoptable.
