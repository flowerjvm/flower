package io.github.parkkevinsb.flower.check.rule;

import io.github.parkkevinsb.flower.check.config.FlowerCheckConfig;
import io.github.parkkevinsb.flower.check.model.ProjectModel;

import java.util.Objects;

/**
 * Read-only context handed to every {@link Rule#apply}. Bundles the shared
 * pass-1 {@link ProjectModel} and the run {@link FlowerCheckConfig} so rules
 * never reach back into the engine or the filesystem.
 */
public final class RuleContext {

    private final ProjectModel projectModel;
    private final FlowerCheckConfig config;

    public RuleContext(ProjectModel projectModel, FlowerCheckConfig config) {
        this.projectModel = Objects.requireNonNull(projectModel, "projectModel");
        this.config = Objects.requireNonNull(config, "config");
    }

    public ProjectModel projectModel() {
        return projectModel;
    }

    public FlowerCheckConfig config() {
        return config;
    }
}
