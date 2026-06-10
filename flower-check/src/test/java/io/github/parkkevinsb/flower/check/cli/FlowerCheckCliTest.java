package io.github.parkkevinsb.flower.check.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerCheckCliTest {

    @Test
    void failOnOptionPreservesLoadedConfig(@TempDir Path root) throws IOException {
        Path config = root.resolve("flower-check.config");
        Files.write(config, String.join("\n",
                "rules:",
                "  FLOWER-CHECK-001: off",
                "failOn: error").getBytes(StandardCharsets.UTF_8));
        writeJava(root, "WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--config", config.toString(),
                "--fail-on", "warning",
                root.toString()
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).doesNotContain("FLOWER-CHECK-001");
        assertThat(err.toString()).isEmpty();
    }

    private static void writeJava(Path root, String name, String... lines) throws IOException {
        Path file = root.resolve(name);
        Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }
}
