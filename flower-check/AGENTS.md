# AGENTS — flower-check

This module is built so that the design docs are the authority and code follows
them — not the other way around. Read this before writing any code here.

## Read Before Coding (mandatory order)

```text
1. ../README.md            L0  purpose, scope, non-goals (constitution)
2. docs/00-INDEX.md        L1  doc levels, authority order, governance
3. docs/01-architecture.md L2  the pipeline, SPI, package layout you must match
4. docs/02-rule-catalog.md L3  the rule you are implementing (FLOWER-CHECK-0NN)
5. The upstream flower-core source for any API the rule reasons about
6. If two docs conflict, STOP and fix the doc conflict first
```

A lower-level doc can never override a higher-level one. A rule that contradicts
`flower-core` or `flower/README.md` is the bug, not the API.

## Where Things Go (matches docs/01-architecture.md)

```text
cli      entry point, args, exit codes        — wiring only, no rule logic
source   load .java files                      — io only
parse    Parser SPI + AST/text impls           — no rule logic, no findings
model    ProjectModel (pass 1 facts)           — shared read-only facts
rule     Rule SPI, registry, context, severity — the extension surface
rule.core   FLOWER-CHECK-001..005, 009..015
rule.agent  FLOWER-CHECK-006..008 (opt-in)
finding  Finding (what/why/fix), suppression   — no parser/AST types here
report   Reporter SPI + PlainText/SARIF
config   FlowerCheckConfig, loading
engine   FlowerCheckEngine — orchestrates the pipeline
```

## How To Add A Rule (the only common task)

1. Confirm the rule exists in `docs/02-rule-catalog.md`. If not, add the design
   there first (L3), grounded in upstream sources, with what/why/fix.
2. Create the class under `rule.core` (or `rule.agent`), extend `AbstractRule`.
3. Register it in
   `src/main/resources/META-INF/services/io.github.parkkevinsb.flower.check.rule.Rule`.
   The engine discovers rules by `ServiceLoader`; do not hard-wire rules into
   the engine.
4. Add a test, and add/confirm a known-good `flower-sample` file stays at zero
   findings (False-Positive Baseline in the catalog).

## Hard Rules

- Build-time tool only. Never import or depend on it from `flower-core`.
- No parser/AST type may appear in `rule.*`, `finding.*`, or `report.*` public
  signatures. Rules see the `ProjectModel` and `SourceUnit` abstractions.
- Every `Finding` must carry `what`, `why`, and `fix`.
- Prefer a missed violation over a false positive.
- Default-on rules keep every `flower-sample` module green.
