---
doc_level: L1
status: active
authority: pointer
depends_on:
  - 00-INDEX.md
supersedes: []
last_reviewed: 2026-06-10
---

# flower-check Design Docs

**Start at [`00-INDEX.md`](00-INDEX.md).** It is the single authoritative entry
point: document levels, authority order, governance rules, and the
read-before-coding order.

This file is only a pointer so that GitHub's default `docs/` view leads here.
Do not maintain a second table of contents in this file — that is exactly the
doc-sprawl `00-INDEX.md` exists to prevent.

```text
L0  ../README.md             purpose, scope, non-goals (constitution)
L1  00-INDEX.md              doc levels + governance + source of truth
L2  01-architecture.md       how the checker is built
L3  02-rule-catalog.md       the enforced rules (FLOWER-CHECK-001..015)
```
