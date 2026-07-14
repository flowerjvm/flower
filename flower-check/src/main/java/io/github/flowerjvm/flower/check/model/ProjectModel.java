package io.github.flowerjvm.flower.check.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared, read-only facts gathered in pass 1 and consumed by rules in pass 2.
 *
 * <p>Rules read this instead of re-deriving cross-file facts (is this a Step?
 * is this goTo target declared? is this flow durable?). See
 * {@code docs/01-architecture.md} ProjectModel.
 *
 * <p>Skeleton: holds the collections and basic lookups. Population lives in
 * {@link ProjectModelBuilder}.
 */
public final class ProjectModel {

    private final List<StepType> stepTypes;
    private final List<FlowBuilderSite> flowBuilders;
    private final List<AnalysisFact> facts;

    public ProjectModel(List<StepType> stepTypes, List<FlowBuilderSite> flowBuilders) {
        this(stepTypes, flowBuilders, Collections.<AnalysisFact>emptyList());
    }

    public ProjectModel(List<StepType> stepTypes, List<FlowBuilderSite> flowBuilders, List<AnalysisFact> facts) {
        this.stepTypes = Collections.unmodifiableList(new ArrayList<>(stepTypes));
        this.flowBuilders = Collections.unmodifiableList(new ArrayList<>(flowBuilders));
        this.facts = Collections.unmodifiableList(new ArrayList<>(facts));
    }

    public static ProjectModel empty() {
        return new ProjectModel(
                Collections.<StepType>emptyList(),
                Collections.<FlowBuilderSite>emptyList(),
                Collections.<AnalysisFact>emptyList());
    }

    public List<StepType> stepTypes() {
        return stepTypes;
    }

    public List<FlowBuilderSite> flowBuilders() {
        return flowBuilders;
    }

    public List<AnalysisFact> facts() {
        return facts;
    }

    public List<AnalysisFact> facts(String kind) {
        List<AnalysisFact> out = new ArrayList<>();
        for (AnalysisFact fact : facts) {
            if (fact.kind().equals(kind)) {
                out.add(fact);
            }
        }
        return out;
    }

    /** True when a class with the given simple name was identified as a Step. */
    public boolean isStepType(String simpleName) {
        for (StepType s : stepTypes) {
            if (s.simpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }
}
