---
doc_level: L1
status: active
authority: doc-governance
depends_on:
  - ../README.md
supersedes: []
last_reviewed: 2026-06-10
---

# 00. flower-check Doc Index & Governance

This is the **single authoritative entry point** for all `flower-check` design
docs. It defines the document levels, the authority order, the frontmatter
contract, and the rules an author (human or AI) must follow before writing code.

If you read only one file before working in this repo, read this one — then it
tells you which others, in which order.

> Why this exists: docs accumulate. Without explicit levels, every file — a
> fresh note, an old design, a temporary opinion — gets read with equal weight,
> and an AI agent will happily justify almost anything by quoting *some* doc.
> Levels and authority order remove that ambiguity.

---

## Document Levels

```text
L0  Constitution      Non-negotiable purpose and boundaries.
                      Wins every conflict, always.
L1  Authority Map     This index + governance. Defines order and source of truth.
L2  Architecture      Contracts for how flower-check itself is built.
L3  Rule / Feature    Design of specific rules or features.
L4  Progress Log      Implementation records. Reference only, never design authority.
```

## Authority Order (current docs)

Listed highest authority first. A lower-level doc can refine a higher one, but
**can never override it**.

```text
L0  ../README.md
      Purpose, scope, non-goals, enforcement intent. The constitution.

L1  docs/00-INDEX.md            <- you are here
      Doc levels, authority order, governance, frontmatter, read-before-coding.

L2  docs/01-architecture.md
      Checker pipeline, parser strategy, Rule SPI, findings, config.

L3  docs/02-rule-catalog.md
      The enforced rules (FLOWER-CHECK-001..015), each grounded and self-explaining.

L4  (none yet)
      Future: docs/19-progress.md or similar implementation logs.
```

## Upstream Source Of Truth

`flower-check` is downstream of Flower. The rules it enforces must conform to
the canonical Flower definition. These external sources outrank **every**
`flower-check` doc on questions of *what correct Flower usage is*:

```text
1. flower-core source   io.github.parkkevinsb.flower.core.*  (the real API)
2. flower/README.md     public Step Design Rules, Notes For AI Agents
3. flower-dev-notes     design rationale and intentional exclusions
4. flower-sample        known-good code = the zero-false-positive baseline
5. archdox docs         real agent/harness usage (Tier 2 agent rules)
```

If a `flower-check` doc and an upstream source disagree, the upstream source
wins and the `flower-check` doc (or rule) is the bug. Because of this,
`flower-check` lives **inside the `flower` repo**: when the Flower API changes,
the rules change in the same commit.

---

## Governance Rules

1. **A lower-level doc cannot win against a higher-level doc.** Resolve up the
   stack, not down.
2. **Rules must conform to the upstream source of truth.** A rule that
   contradicts `flower-core` or `flower/README.md` is wrong, not the API.
3. **Progress logs (L4) are records, not authority.** Never cite a progress log
   to justify a design decision.
4. **Declare placement before adding.** A new rule, module, or doc must state
   its `doc_level` and which authority it belongs under *before* implementation.
5. **Resolve doc conflicts in the docs first.** If two docs disagree, fix the
   conflict in writing before touching code.
6. **Default-on rules keep the sample baseline green.** Every rule enabled by
   default must produce zero findings on the `flower-sample` modules
   (see `02-rule-catalog.md` → False-Positive Baseline).
7. **One index, not many.** Governance lives here, not in a separate file, until
   the doc count makes splitting unavoidable. Do not create a parallel index.

---

## Frontmatter Contract

Every design doc (L0–L4) starts with this block. It makes level, status, and
dependencies machine-checkable and prevents stale docs from masquerading as
current authority.

```yaml
---
doc_level: L2                 # L0 | L1 | L2 | L3 | L4
status: active                # active | draft | superseded
authority: architecture-contract   # short tag for what this doc governs
depends_on:
  - 00-INDEX.md               # docs whose rules this one inherits
supersedes: []                # doc(s) this one replaces, if any
last_reviewed: 2026-06-10     # absolute date of last authority review
---
```

Rules for the fields:

- `status: superseded` docs are kept for history but carry **no** authority;
  they must name their replacement in `supersedes` of the new doc.
- `depends_on` points **upward** (to equal or higher authority), never downward.
- `last_reviewed` is an absolute date. A doc not reviewed since the last Flower
  API change is suspect until re-confirmed.

---

## Read Before Coding

Any author — human or AI — touching `flower-check` follows this order:

```text
1. Read ../README.md            (L0 — purpose & boundaries)
2. Read docs/00-INDEX.md        (L1 — this file)
3. Read docs/01-architecture.md (L2 — how the checker is built)
4. Read the relevant rule in docs/02-rule-catalog.md (L3)
5. Cross-check the upstream flower-core API for anything the rule touches
6. If any two docs conflict, STOP and resolve the doc conflict first
```

Only after step 6 is clean do you write or change code. A doc conflict is a
design bug; shipping code on top of it just buries the bug deeper.
