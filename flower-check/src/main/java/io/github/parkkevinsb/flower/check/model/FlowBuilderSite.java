package io.github.parkkevinsb.flower.check.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One {@code Flow.builder(...)} call chain found in source.
 *
 * <p>Captures the facts several rules need: the flow type literal (when
 * present), whether the chain is {@code durable()}, the declared step ids, the
 * step ids referenced by {@code goTo(...)}, and whether durable steps declared a
 * recovery policy. Skeleton: a data holder populated by {@link ProjectModelBuilder}.
 */
public final class FlowBuilderSite {

    private final String file;
    private final int line;
    private final String flowType;        // null when not a string literal
    private final boolean durable;
    private final List<String> declaredStepIds;
    private final Set<String> goToTargets;
    private final boolean allDurableStepsHaveRecoveryPolicy;

    public FlowBuilderSite(String file,
                           int line,
                           String flowType,
                           boolean durable,
                           List<String> declaredStepIds,
                           Set<String> goToTargets,
                           boolean allDurableStepsHaveRecoveryPolicy) {
        this.file = Objects.requireNonNull(file, "file");
        this.line = line;
        this.flowType = flowType;
        this.durable = durable;
        this.declaredStepIds = Collections.unmodifiableList(declaredStepIds);
        this.goToTargets = Collections.unmodifiableSet(new LinkedHashSet<>(goToTargets));
        this.allDurableStepsHaveRecoveryPolicy = allDurableStepsHaveRecoveryPolicy;
    }

    public String file() {
        return file;
    }

    public int line() {
        return line;
    }

    public String flowType() {
        return flowType;
    }

    public boolean durable() {
        return durable;
    }

    public List<String> declaredStepIds() {
        return declaredStepIds;
    }

    public Set<String> goToTargets() {
        return goToTargets;
    }

    public boolean allDurableStepsHaveRecoveryPolicy() {
        return allDurableStepsHaveRecoveryPolicy;
    }
}
