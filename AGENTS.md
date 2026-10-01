# Flower Repository Guidance

Read `README.md` and `CONTRIBUTING.md`, then the source and tests relevant to
the change. Follow nested `AGENTS.md` files in the area you work on.

## Task References

- Runtime and lifecycle: `docs/runtime-reference.md`; EventLoop:
  `flower-eventloop/README.md`.
- Checkpoints and JDBC: `docs/persistence.md`.
- Tracing: `docs/tracing-storage-security.md`; evaluation:
  `flower-evaluation/README.md`.
- Any `flower-check*` work: `flower-check/README.md` and
  `flower-check/docs/00-INDEX.md`.
- For design changes, consult relevant `../flower-dev-notes`; for Bloom
  integration, also consult `../bloom-dev-notes`. Cross-check the notes
  against current source and public contracts.
- Before adding or tightening checker rules, review the relevant Flower notes.
  If required notes are unavailable, report the missing files and unverified
  design rationale, and leave the required review incomplete.
- Apply available Flower skills within their documented scope and version;
  application guidance must stay consistent with the framework's contracts.

## Preserve Contracts

- Keep `flower-core` free of runtime dependencies. Put integrations in their
  optional modules. Preserve Java 8 compatibility except for the Java 17
  Spring Boot starter.
- Keep Worker callbacks short and non-blocking. Preserve lifecycle and thread
  ownership; do not mix manual and scheduled execution or introduce recursive
  ticks. Core and EventLoop have separate execution contracts.
- Durable execution is checkpoint/resume. Preserve execution identity and
  recovery semantics; review supported dialects, schemas, and migrations when
  changing persistence.
- For behavior changes, add focused regression tests and update affected public
  docs and checker rules. Check POM versions; released examples may differ from
  the development checkout.

## Verification

Use a complete JDK 17 and Maven 3.9.x for the full repository build. This build
JDK does not change the modules' Java 8 and Java 17 compatibility targets.
For code changes, run `mvn -B verify`.
For Gradle plugin changes, also run its separate build:

```bash
mvn -B -pl flower-check,flower-check-annotations -am install -DskipTests
gradle -p flower-check-gradle-plugin --no-daemon check
```

Prefer manual execution and `ManualClock` for runtime tests. Report checks run,
skipped, or unavailable, including external sample validation.

Keep cloud tool installation and Maven proxy/mirror settings outside the
repository. Preserve the environment's supplied proxies and CA trust.
