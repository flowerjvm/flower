# Flower Evaluation

`flower-evaluation` measures completed AI or workflow behavior against a
versioned dataset. It provides small Java contracts for examples, candidates,
experiments, evaluators, scores, human feedback, baseline comparison, and a
local JSON Lines reference store.

It is independent of Flower Core. The target may run a Flower Flow, a Flower
Agent, an AI Harness task, an HTTP service, or plain Java code.

## Core Model

- `EvaluationDataset` is a fixed, versioned set of `EvaluationExample` values.
- `EvaluationCandidate` identifies the agent, prompt, model, Tool policy, or
  application version under test.
- `EvaluationTarget` executes one example and returns structured actual values,
  usage metrics, and optional Trace/Run ids.
- `Evaluator` scores one completed output. It may use deterministic code, an
  LLM supplied by the host, or an external service.
- `EvaluationRunner` isolates example and evaluator failures and produces one
  immutable `EvaluationExperimentResult`.
- `EvaluationComparator` reports regressions and improvements between results
  produced from the same dataset id and version.
- `EvaluationFeedback` records explicit human assessment without making Studio
  a write path.

## Example

```java
EvaluationDataset dataset = EvaluationDataset.builder("game-ops", "v1")
        .example(EvaluationExample.builder("healthy-server")
                .input("serverId", "server-beta")
                .expected("outcome", "NO_ACTION_NEEDED")
                .build())
        .example(EvaluationExample.builder("degraded-server")
                .input("serverId", "server-alpha")
                .expected("outcome", "RESOLVED")
                .build())
        .build();

EvaluationSuite suite = EvaluationSuite.builder()
        .evaluator(EvaluationEvaluators.expectedEquals("outcome"))
        .evaluator(EvaluationEvaluators.minimumActualCollectionSize("evidence", 1))
        .evaluator(EvaluationEvaluators.optional(
                EvaluationEvaluators.metricAtMost(EvaluationMetrics.TOOL_CALLS, 5)))
        .build();

EvaluationExperiment experiment = EvaluationExperiment.builder("game-ops-prompt-v2")
        .name("Game operations prompt v2")
        .dataset(dataset)
        .candidate(EvaluationCandidate.builder("game-ops-agent", "prompt-v2")
                .attribute("model", "local-model-v3")
                .attribute("toolPolicy", "evidence-first-v2")
                .build())
        .target(example -> runAgentAndMapOutput(example))
        .suite(suite)
        .baselineExperimentId("game-ops-prompt-v1")
        .build();

Path results = Paths.get("data/flower-evaluations.jsonl");
EvaluationExperimentResult result = new EvaluationRunner(
        Clock.systemUTC(),
        new JsonLinesEvaluationResultSink(results))
        .run(experiment);
```

`EvaluationOutput` supports conventional metrics such as input/output tokens,
Tool calls, model calls, turns, duration, and cost. Values remain structured;
the module does not parse model prose or inspect a Flow's arbitrary state.

## Custom And LLM Evaluators

Implement `Evaluator` for domain-specific rules or an LLM judge:

```java
Evaluator judge = new Evaluator() {
    @Override
    public String id() {
        return "grounded-summary";
    }

    @Override
    public EvaluationScore evaluate(EvaluationContext context) throws Exception {
        double score = companyJudge.score(
                context.example().expected(),
                context.output().actual());
        return EvaluationScore.scored(id(), score, 0.8, "Grounding threshold");
    }
};
```

Flower does not choose the judge model, prompt, fallback, or credentials. Keep
deterministic evaluators as the stable base and treat an LLM judge as another
fallible evaluator. Mark advisory checks with
`EvaluationEvaluators.optional(...)` when their failure must not fail a case.

## Storage And Studio

The module includes append-only JSON Lines sinks and tolerant, bounded sources
for results and feedback. They are reference implementations for local
development, tests, demonstrations, and small trusted deployments.

Flower Studio reads these files and displays experiment summaries, cases,
scores, baseline regressions, Trace references, and feedback. Studio remains a
read-only consumer. A host collects feedback and publishes it through
`EvaluationFeedbackSink` after applying its own authentication, authorization,
sanitization, and retention policy.

Large deployments should implement `EvaluationResultSink` and
`EvaluationFeedbackSink` against their own database, event stream, or analytics
platform.

## Execution Boundary

`EvaluationRunner` executes targets sequentially and targets may block. Run it
from tests, CI, a batch job, or a dedicated host executor. Never call it from a
Flower Worker tick.

Evaluation is post-run quality measurement. It does not:

- authorize an Action or replace Action Runtime policy and approval;
- validate and retry one structured AI task like Flower AI Harness;
- own the Flower Agent model/Tool loop;
- make an unsafe run safe after it happened;
- capture prompts, Tool payloads, secrets, or personal data automatically.

Persist only selected, sanitized facts. Human comments are opt-in content and
may require stricter handling than numeric scores.
