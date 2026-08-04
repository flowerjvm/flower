package io.github.flowerjvm.flower.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares experiments that ran against the same dataset version. */
public final class EvaluationComparator {

    private EvaluationComparator() {
    }

    public static EvaluationComparison compare(
            EvaluationExperimentResult baseline,
            EvaluationExperimentResult candidate) {
        if (baseline == null || candidate == null) {
            throw new IllegalArgumentException("baseline and candidate must not be null");
        }
        if (!baseline.getDatasetId().equals(candidate.getDatasetId())
                || !baseline.getDatasetVersion().equals(candidate.getDatasetVersion())) {
            throw new IllegalArgumentException(
                    "experiments must use the same dataset id and version");
        }

        Map<String, EvaluationExampleResult> baselineCases = byExample(baseline.getCases());
        Map<String, EvaluationExampleResult> candidateCases = byExample(candidate.getCases());
        if (!baselineCases.keySet().equals(candidateCases.keySet())) {
            throw new IllegalArgumentException("experiments must contain the same example ids");
        }

        List<String> regressions = new ArrayList<String>();
        List<String> improvements = new ArrayList<String>();
        for (Map.Entry<String, EvaluationExampleResult> entry : baselineCases.entrySet()) {
            EvaluationCaseStatus before = entry.getValue().getStatus();
            EvaluationCaseStatus after = candidateCases.get(entry.getKey()).getStatus();
            if (before == EvaluationCaseStatus.PASS && after != EvaluationCaseStatus.PASS) {
                regressions.add(entry.getKey());
            } else if (before != EvaluationCaseStatus.PASS && after == EvaluationCaseStatus.PASS) {
                improvements.add(entry.getKey());
            }
        }

        Map<String, Double> baselineMeans = evaluatorMeans(baseline.getCases());
        Map<String, Double> candidateMeans = evaluatorMeans(candidate.getCases());
        Set<String> evaluatorIds = new LinkedHashSet<String>();
        evaluatorIds.addAll(baselineMeans.keySet());
        evaluatorIds.addAll(candidateMeans.keySet());
        List<EvaluationMetricDelta> deltas = new ArrayList<EvaluationMetricDelta>();
        for (String evaluatorId : evaluatorIds) {
            deltas.add(new EvaluationMetricDelta(
                    evaluatorId,
                    valueOrZero(baselineMeans, evaluatorId),
                    valueOrZero(candidateMeans, evaluatorId)));
        }

        return new EvaluationComparison(
                baseline.getExperimentId(),
                candidate.getExperimentId(),
                candidate.getSummary().getPassRate() - baseline.getSummary().getPassRate(),
                candidate.getSummary().getMeanScore() - baseline.getSummary().getMeanScore(),
                regressions,
                improvements,
                deltas);
    }

    private static Map<String, EvaluationExampleResult> byExample(
            List<EvaluationExampleResult> cases) {
        Map<String, EvaluationExampleResult> result =
                new LinkedHashMap<String, EvaluationExampleResult>();
        for (EvaluationExampleResult selected : cases) {
            if (result.put(selected.getExampleId(), selected) != null) {
                throw new IllegalArgumentException(
                        "duplicate example result: " + selected.getExampleId());
            }
        }
        return result;
    }

    private static Map<String, Double> evaluatorMeans(List<EvaluationExampleResult> cases) {
        Map<String, Double> sums = new LinkedHashMap<String, Double>();
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (EvaluationExampleResult selected : cases) {
            for (EvaluationScore score : selected.getScores()) {
                if (score.getVerdict() == EvaluationVerdict.ERROR) {
                    continue;
                }
                String id = score.getEvaluatorId();
                sums.put(id, valueOrZero(sums, id) + score.getValue());
                Integer count = counts.get(id);
                counts.put(id, count == null ? 1 : count + 1);
            }
        }
        Map<String, Double> means = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> entry : sums.entrySet()) {
            means.put(entry.getKey(), entry.getValue() / counts.get(entry.getKey()));
        }
        return means;
    }

    private static double valueOrZero(Map<String, Double> values, String key) {
        Double value = values.get(key);
        return value == null ? 0.0d : value;
    }
}
