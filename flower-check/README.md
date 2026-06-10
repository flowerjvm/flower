# flower-check

`flower-check` is a Flower build-time developer tooling module.

It is not part of `flower-core` runtime execution. Its job is to inspect a
host application's source code and fail the build when generated or handwritten
Flower code violates known Flower patterns.

## Why This Exists

In the AI coding-agent era, users may ask an AI agent to implement Flower
flows, steps, AI harnesses, or agent-runtime actions.

Documentation and MCP guidance can help, but guidance is not enforcement.

`flower-check` is the enforcement layer:

```text
AI agent writes code
-> flower-check scans source
-> forbidden Flower patterns are found
-> build fails
-> bad workflow code cannot be merged or released
```

## Placement

`flower-check` lives inside the main `flower` repository.

Reason:

```text
flower-check depends on Flower API rules.
When Flower APIs change, checks should evolve in the same repository.
```

It may later publish as:

```text
flower-check-cli
flower-check-gradle-plugin
flower-check-maven-plugin
```

A compiling implementation now exists, so `flower-check` is registered in the
Maven reactor and runs during the module's `verify` phase. The design and
rules live in [`docs/`](docs) - start at
[`docs/00-INDEX.md`](docs/00-INDEX.md). Contributors (human or AI) must follow
[`AGENTS.md`](AGENTS.md) before writing code.

## First Scope

The first version should scan Java source and report violations with file and
line references.

Initial findings:

```text
FLOWER-CHECK-001
  Step must not call Thread.sleep or block the Worker thread.

FLOWER-CHECK-002
  Step must not call LLM/provider SDKs directly.
  Use flower-ai-harness for AI model calls.

FLOWER-CHECK-003
  Flow code must not directly tick another Flow.
  Submit child flows through a Worker and wait through state/event checks.

FLOWER-CHECK-004
  Await/wait-style Step should have timeout or cancellation behavior.

FLOWER-CHECK-005
  Durable Flow Step should declare recovery policy.

FLOWER-CHECK-006
  Agent write action should not bypass ActionRegistry / PolicyGate.

FLOWER-CHECK-007
  Business write action should emit or require an audit event.

FLOWER-CHECK-008
  Approval-required action must not execute directly.
```

## Enforcement Path

Local:

```bash
flower-check src/main/java
flower-check --write-baseline flower-check-baseline.txt src/main/java
flower-check --list-rules
```

Maven:

```bash
mvn verify
mvn -Dflower.check.skip=true verify
```

CI:

```text
pull request
-> mvn verify
-> flower-check
-> tests
-> build
-> merge blocked on failure
```

## Relationship To Other Flower Tools

```text
flower-dev-mcp
  = guides AI coding agents toward correct Flower designs.

flower-check
  = rejects known bad Flower code patterns.

flower-testkit
  = verifies Flow behavior deterministically.

CI
  = runs flower-check and tests automatically.

AI Reviewer
  = summarizes intent, risk, and check/test results for humans.
```

## Non-Goals

Do not make the first version:

```text
a general Java linter
a security scanner
a replacement for tests
a full static analyzer for all business logic
a dashboard
a hosted service
```

The first useful version should be small, boring, and strict.

## Implementation Notes

Current implementation:

```text
1. CLI scans Java source files.
2. JavaParser is primary; conservative text fallback remains.
3. Rules are discovered through ServiceLoader.
4. Plain text and SARIF reporters are available.
5. Existing findings can be written to a baseline file for controlled adoption.
6. Maven verify runs flower-check over the Flower reactor source roots.
```

Use a parser when rules need structure, such as identifying classes extending
`Step` or methods overriding `onTick`.

Avoid brittle rules that generate too many false positives. Each rule should
explain:

```text
what was found
why it is risky
what to do instead
```
