package io.github.parkkevinsb.flower.check.model;

import io.github.parkkevinsb.flower.check.config.FlowerCheckConfig;
import io.github.parkkevinsb.flower.check.parse.SourceUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Pass 1: walk every parsed unit once and build the {@link ProjectModel}.
 * Produces no findings.
 *
 * <p>Skeleton: returns an empty model. The real implementation visits each
 * {@link SourceUnit#ast()} to populate {@link StepType}s and
 * {@link FlowBuilderSite}s. It must respect {@link FlowerCheckConfig#stepBaseClasses()}
 * so project-specific Step base classes are recognized.
 */
public final class ProjectModelBuilder {

    private final FlowerCheckConfig config;

    public ProjectModelBuilder(FlowerCheckConfig config) {
        this.config = config;
    }

    public ProjectModel build(List<SourceUnit> units) {
        List<StepType> stepTypes = new ArrayList<>();
        List<FlowBuilderSite> flowBuilders = new ArrayList<>();

        for (SourceUnit unit : units) {
            if (!unit.parsed()) {
                continue; // text-only fallback: no structural facts available yet
            }
            // TODO(codex): visit unit.ast() and populate stepTypes / flowBuilders.
            //   - stepTypes:    classes extending Step/DurableStep or a configured
            //                   base class (config.stepBaseClasses()); record
            //                   overridden lifecycle methods.
            //   - flowBuilders: Flow.builder(...) chains; record flowType literal,
            //                   durable() flag, declared step ids, goTo targets,
            //                   and whether durable steps declared a RecoveryPolicy.
        }

        return new ProjectModel(stepTypes, flowBuilders);
    }
}
