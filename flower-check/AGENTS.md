# AGENTS - flower-check

This module is built so that the design docs are the authority and code follows
them, not the other way around. Read this before writing any code here.

## Read Before Coding (mandatory order)

```text
1. ../README.md                              L0  public purpose, scope, non-goals
2. docs/00-INDEX.md                          L1  authority order and governance
3. ../../flower-dev-notes/README.md          upstream rationale index, if present
4. ../../flower-dev-notes/01-scope-and-principles.md
5. ../../flower-dev-notes/02-core-architecture.md
6. ../../flower-dev-notes/03-step-stepno-and-flow-control.md
7. ../../flower-dev-notes/04-events-bloom-and-threading.md
8. ../../flower-dev-notes/08-current-implementation-update.md
9. docs/01-architecture.md                   L2  checker pipeline and SPI
10. docs/02-rule-catalog.md                  L3  rule being implemented
11. The upstream flower-core source for any API the rule reasons about
12. If two docs conflict, STOP and fix the doc conflict first
```

A lower-level doc can never override a higher-level one. A rule that contradicts
`flower-core`, `flower/README.md`, or the applicable `flower-dev-notes` design
rationale is the bug, not the API.

## Where Things Go (matches docs/01-architecture.md)

```text
cli      entry point, args, exit codes        - wiring only, no rule logic
source   load .java files                     - IO only
parse    Parser SPI + AST/text impls          - no rule logic, no findings
model    ProjectModel (pass 1 facts)          - shared read-only facts
rule     Rule SPI, registry, context, severity - the extension surface
rule.core   FLOWER-CHECK-001..005, 009..019
rule.agent  FLOWER-CHECK-006..008 (opt-in)
finding  Finding (what/why/fix), suppression  - no parser/AST types here
report   Reporter SPI + PlainText/SARIF
config   FlowerCheckConfig, loading
engine   FlowerCheckEngine - orchestrates the pipeline
```

## How To Add A Rule (the only common task)

1. Confirm the rule exists in `docs/02-rule-catalog.md`. If not, add the design
   there first (L3), grounded in upstream sources, with what/why/fix.
2. Cross-check the matching `flower-dev-notes` section and cite it in the rule
   design. Use at least one concrete upstream anchor: core source, README,
   dev-notes, sample, or archdox for agent rules.
3. Create the class under `rule.core` (or `rule.agent`), extend `AbstractRule`.
4. Register it in
   `src/main/resources/META-INF/services/io.github.flowerjvm.flower.check.rule.Rule`.
   The engine discovers rules by `ServiceLoader`; do not hard-wire rules into
   the engine.
5. Add a test, and add/confirm a known-good `flower-sample` file stays at zero
   findings (False-Positive Baseline in the catalog).

## Hard Rules

- Build-time tool only. Never import or depend on it from `flower-core`.
- Treat `flower-dev-notes` as required rationale, not optional background, when
  tightening Flower development rules.
- No parser/AST type may appear in `rule.*`, `finding.*`, or `report.*` public
  signatures. Rules see the `ProjectModel` and `SourceUnit` abstractions.
- Every `Finding` must carry `what`, `why`, and `fix`.
- Prefer a missed violation over a false positive.
- Default-on rules keep every `flower-sample` module green.
