package io.github.parkkevinsb.flower.check.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowerCheckMojoTest {

    @Test
    void failsBuildOnBadHostProjectSource(@TempDir Path root) throws Exception {
        Path sourceRoot = root.resolve("src/main/java");
        writeJava(sourceRoot, "demo/WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        FlowerCheckMojo mojo = mojo(root, sourceRoot);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("flower-check failed");
    }

    @Test
    void passesWhenProjectConfigDisablesExistingDebt(@TempDir Path root) throws Exception {
        Path sourceRoot = root.resolve("src/main/java");
        Files.write(root.resolve("flower-check.config"),
                "rules:\n  FLOWER-CHECK-001: off\n".getBytes(StandardCharsets.UTF_8));
        writeJava(sourceRoot, "demo/WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        FlowerCheckMojo mojo = mojo(root, sourceRoot);

        mojo.execute();
    }

    @Test
    void writeBaselineDoesNotFailBuild(@TempDir Path root) throws Exception {
        Path sourceRoot = root.resolve("src/main/java");
        Path baseline = root.resolve("flower-check-baseline.txt");
        writeJava(sourceRoot, "demo/WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        FlowerCheckMojo mojo = mojo(root, sourceRoot);
        set(mojo, "writeBaseline", baseline.toFile());

        mojo.execute();

        assertThat(new String(Files.readAllBytes(baseline), StandardCharsets.UTF_8))
                .isEqualTo("FLOWER-CHECK-001 demo/WaitStep.java:4\n");
    }

    private static FlowerCheckMojo mojo(Path root, Path sourceRoot) throws Exception {
        FlowerCheckMojo mojo = new FlowerCheckMojo();
        mojo.setLog(new TestLog());
        set(mojo, "basedir", root.toFile());
        set(mojo, "sourceRoots", Collections.singletonList(sourceRoot.toString()));
        set(mojo, "testSourceRoots", Collections.<String>emptyList());
        set(mojo, "extraSourceRoots", Collections.<String>emptyList());
        return mojo;
    }

    private static void set(FlowerCheckMojo mojo, String name, Object value) throws Exception {
        Field field = FlowerCheckMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }

    private static void writeJava(Path sourceRoot, String name, String... lines) throws IOException {
        Path file = sourceRoot.resolve(name.replace('/', File.separatorChar));
        Files.createDirectories(file.getParent());
        Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static final class TestLog implements Log {
        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(CharSequence content) {
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
        }

        @Override
        public void info(CharSequence content, Throwable error) {
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
        }

        @Override
        public void error(CharSequence content, Throwable error) {
        }

        @Override
        public void error(Throwable error) {
        }
    }
}
