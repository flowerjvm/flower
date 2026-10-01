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

Use a complete JDK 17 (the development baseline) or a supported newer JDK and
Maven 3.9.x for the full repository build. This build JDK does not change the
modules' Java 8 and Java 17 compatibility targets. The current samples require
JDK 21 and Gradle 8.10.2; inspect their build files when preparing the environment.

For Flower code changes, run `mvn -B verify`. This runs the reactor tests, Maven
plugin integration fixtures, and the strict `flower-check` self-check. Complete
the sample application validation below as well. Prepare the required tools,
sibling checkouts, and dependency caches, then execute the checks; do not stop
at suggesting commands. Documentation-only changes need relevant document checks.

For Gradle plugin changes, also run its separate build:

```bash
mvn -B -pl flower-check,flower-check-annotations -am install -DskipTests
gradle -p flower-check-gradle-plugin --no-daemon check
```

Prefer manual execution and `ManualClock` for runtime tests. Report checks run,
skipped, or unavailable, including external sample validation.

### Sample False-Positive Baseline

Keep `flower-sample` as a sibling checkout at `../flower-sample`
(`/workspace/flower-sample` in this cloud workspace). After changing
`flower-check` rules or analysis logic, run `FlowerSampleBaselineTest`:

```bash
mvn -B -pl flower-check \
  -Dtest=FlowerSampleBaselineTest \
  -Dflower.check.sampleRoot="$(cd ../flower-sample && pwd)" test
```

This checks Java sources in `cafe-order`, `durable-order`, and
`flower-basic-samples` with the default checker rules and requires zero findings.
It protects against false positives; sample application tests are a separate check.

Report baseline success only when this execution's Surefire report for
`FlowerSampleBaselineTest` shows `tests=1`, `failures=0`, `errors=0`, and
`skipped=0`. A fresh reactor run with those results also satisfies this check.
If the checkout or a required module is missing and the test skips, report the
baseline as unverified and name the missing path. `BUILD SUCCESS`, the presence
of a checkout, and an old report are insufficient evidence.

### Sample Application Validation

After the reactor verification passes, install the current Flower artifacts
locally and run all sample modules' tests:

```bash
mvn -B -DskipTests -Dinvoker.skip=true install
```

The install command only prepares artifacts; its skipped tests are not
verification evidence. Check the samples' resolved Flower dependency versions.
Samples may pin a released Flower version, so `mvn install` alone does not make
them use the current checkout. For regression validation of the current code,
align `io.github.flowerjvm:flower-*` dependencies with the parent POM version
using an external Gradle init script or another supported override, and verify
the resolved versions before reporting success. Preserve unrelated dependency
versions and tracked sample build files. State the tested Flower version.

For an external init script that reads `flower.validation.version`, set
`FLOWER_DEV_VERSION` to the current parent POM version and
`FLOWER_SAMPLE_INIT_SCRIPT` to the script's absolute path. Apply that same
override when running all sample tests, and generate fresh results:

```bash
gradle -p ../flower-sample --no-daemon \
  --init-script "$FLOWER_SAMPLE_INIT_SCRIPT" \
  -Dflower.validation.version="$FLOWER_DEV_VERSION" \
  test --rerun-tasks --no-build-cache
```

Run the relevant sample entry point or integration smoke check when changing
behavior that unit tests do not exercise. Report sample tests, baseline checks,
and smoke checks separately; a skipped or unavailable check remains unverified.

Keep cloud tool installation and Maven proxy/mirror settings outside the
repository. Preserve the environment's supplied proxies and CA trust.
