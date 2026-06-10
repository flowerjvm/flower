package io.github.parkkevinsb.flower.check.model;

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

    public ProjectModel(List<StepType> stepTypes, List<FlowBuilderSite> flowBuilders) {
        this.stepTypes = Collections.unmodifiableList(new ArrayList<>(stepTypes));
        this.flowBuilders = Collections.unmodifiableList(new ArrayList<>(flowBuilders));
    }

    public static ProjectModel empty() {
        return new ProjectModel(Collections.<StepType>emptyList(), Collections.<FlowBuilderSite>emptyList());
    }

    public List<StepType> stepTypes() {
        return stepTypes;
    }

    public List<FlowBuilderSite> flowBuilders() {
        return flowBuilders;
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
