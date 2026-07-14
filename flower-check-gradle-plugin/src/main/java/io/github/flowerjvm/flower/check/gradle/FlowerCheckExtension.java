package io.github.flowerjvm.flower.check.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * Gradle DSL surface for flower-check.
 */
public class FlowerCheckExtension {

    private final ConfigurableFileCollection sourceRoots;
    private final ConfigurableFileCollection testSourceRoots;
    private final ConfigurableFileCollection extraSourceRoots;
    private final RegularFileProperty configFile;
    private final RegularFileProperty outputFile;
    private final RegularFileProperty writeBaseline;
    private final Property<Boolean> skip;
    private final Property<Boolean> includeTests;
    private final Property<String> failOn;
    private final Property<String> format;

    public FlowerCheckExtension(Project project) {
        ObjectFactory objects = project.getObjects();
        this.sourceRoots = project.files();
        this.testSourceRoots = project.files();
        this.extraSourceRoots = project.files();
        this.configFile = objects.fileProperty();
        this.outputFile = objects.fileProperty();
        this.writeBaseline = objects.fileProperty();
        this.skip = objects.property(Boolean.class);
        this.includeTests = objects.property(Boolean.class);
        this.failOn = objects.property(String.class);
        this.format = objects.property(String.class);
    }

    public ConfigurableFileCollection getSourceRoots() {
        return sourceRoots;
    }

    public ConfigurableFileCollection getTestSourceRoots() {
        return testSourceRoots;
    }

    public ConfigurableFileCollection getExtraSourceRoots() {
        return extraSourceRoots;
    }

    public RegularFileProperty getConfigFile() {
        return configFile;
    }

    public RegularFileProperty getOutputFile() {
        return outputFile;
    }

    public RegularFileProperty getWriteBaseline() {
        return writeBaseline;
    }

    public Property<Boolean> getSkip() {
        return skip;
    }

    public Property<Boolean> getIncludeTests() {
        return includeTests;
    }

    public Property<String> getFailOn() {
        return failOn;
    }

    public Property<String> getFormat() {
        return format;
    }
}
