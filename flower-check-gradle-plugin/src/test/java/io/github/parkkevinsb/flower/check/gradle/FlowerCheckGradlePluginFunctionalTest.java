package io.github.parkkevinsb.flower.check.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerCheckGradlePluginFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void checkRunsFlowerCheckForCleanProject() throws IOException {
        writeProject(false);

        BuildResult result = gradle("check").build();

        assertThat(result.getOutput()).contains("flower-check: no findings.");
    }

    @Test
    void checkFailsOnFlowerFinding() throws IOException {
        writeProject(true);

        BuildResult result = gradle("check").buildAndFail();

        assertThat(result.getOutput()).contains("FLOWER-CHECK-001");
        assertThat(result.getOutput()).contains("Thread.sleep");
        assertThat(result.getOutput()).contains("flower-check failed");
    }

    @Test
    void skipPropertyBypassesFlowerCheck() throws IOException {
        writeProject(true);

        BuildResult result = gradle("check", "-Pflower.check.skip=true").build();

        assertThat(result.getOutput()).contains("flower-check skipped");
    }

    private GradleRunner gradle(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    private void writeProject(boolean blocking) throws IOException {
        Files.write(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'flower-check-gradle-plugin-test'\n".getBytes(StandardCharsets.UTF_8));
        Files.write(projectDir.resolve("build.gradle"),
                String.join("\n",
                        "plugins {",
                        "    id 'java'",
                        "    id 'io.github.parkkevinsb.flower.flower-check'",
                        "}",
                        "",
                        "flowerCheck {",
                        "    includeTests = false",
                        "}").getBytes(StandardCharsets.UTF_8));
        Path sourceRoot = projectDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.write(sourceRoot.resolve(blocking ? "WaitStep.java" : "GoodStep.java"),
                source(blocking).getBytes(StandardCharsets.UTF_8));
    }

    private static String source(boolean blocking) {
        String body = blocking
                ? "        Thread.sleep(1000);\n"
                : "";
        return "package demo;\n\n"
                + "class " + (blocking ? "WaitStep" : "GoodStep") + " extends Step {\n"
                + "    protected StepResult onTick(StepContext ctx) throws Exception {\n"
                + body
                + "        return StepResult.done();\n"
                + "    }\n"
                + "}\n\n"
                + "class Step {\n"
                + "}\n\n"
                + "class StepContext {\n"
                + "}\n\n"
                + "class StepResult {\n"
                + "    static StepResult done() {\n"
                + "        return new StepResult();\n"
                + "    }\n"
                + "}\n";
    }
}
