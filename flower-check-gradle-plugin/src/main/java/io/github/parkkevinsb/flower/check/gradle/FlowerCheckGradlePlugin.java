package io.github.parkkevinsb.flower.check.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Registers the flowerCheck verification task and wires it into Gradle check.
 */
public final class FlowerCheckGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        FlowerCheckExtension extension = project.getExtensions()
                .create("flowerCheck", FlowerCheckExtension.class, project);

        extension.getSkip().convention(booleanProperty(project, "flower.check.skip", false));
        extension.getIncludeTests().convention(booleanProperty(project, "flower.check.includeTests", false));
        extension.getFormat().convention(project.getProviders()
                .gradleProperty("flower.check.format")
                .orElse("plain"));
        extension.getFailOn().convention(project.getProviders().gradleProperty("flower.check.failOn"));
        extension.getConfigFile().convention(project.getProviders()
                .gradleProperty("flower.check.config")
                .map(value -> project.getLayout().getProjectDirectory().file(value))
                .orElse(project.getLayout().getProjectDirectory().file("flower-check.config")));
        extension.getOutputFile().set(project.getProviders()
                .gradleProperty("flower.check.outputFile")
                .map(value -> project.getLayout().getProjectDirectory().file(value)));
        extension.getWriteBaseline().set(project.getProviders()
                .gradleProperty("flower.check.writeBaseline")
                .map(value -> project.getLayout().getProjectDirectory().file(value)));

        extension.getSourceRoots().from(project.getLayout().getProjectDirectory().dir("src/main/java"));
        extension.getTestSourceRoots().from(project.getLayout().getProjectDirectory().dir("src/test/java"));

        org.gradle.api.tasks.TaskProvider<FlowerCheckTask> flowerCheckTask = project.getTasks()
                .register("flowerCheck", FlowerCheckTask.class, task -> {
                    task.getSkip().set(extension.getSkip());
                    task.getIncludeTests().set(extension.getIncludeTests());
                    task.getFormat().set(extension.getFormat());
                    task.getFailOn().set(extension.getFailOn());
                    task.getConfigFile().set(extension.getConfigFile());
                    task.getOutputFile().set(extension.getOutputFile());
                    task.getWriteBaseline().set(extension.getWriteBaseline());
                    task.getSourceRoots().from(extension.getSourceRoots());
                    task.getTestSourceRoots().from(extension.getTestSourceRoots());
                    task.getExtraSourceRoots().from(extension.getExtraSourceRoots());
                });

        project.getPlugins().withId("java", ignored -> configureJavaSourceSets(project, extension));
        project.getTasks().matching(task -> "check".equals(task.getName()))
                .configureEach(task -> task.dependsOn(flowerCheckTask));
    }

    private static void configureJavaSourceSets(Project project, FlowerCheckExtension extension) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (main != null) {
            extension.getSourceRoots().from(main.getAllJava().getSrcDirs());
        }
        SourceSet test = sourceSets.findByName(SourceSet.TEST_SOURCE_SET_NAME);
        if (test != null) {
            extension.getTestSourceRoots().from(test.getAllJava().getSrcDirs());
        }
    }

    private static Provider<Boolean> booleanProperty(Project project, String name, boolean defaultValue) {
        return project.getProviders()
                .gradleProperty(name)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }
}
