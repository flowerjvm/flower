package io.github.parkkevinsb.flower.check.config;

import io.github.parkkevinsb.flower.check.rule.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    @Test
    void loadsDocumentShapeConfig(@TempDir Path root) throws IOException {
        Path configFile = writeConfig(root,
                "rules:",
                "  FLOWER-CHECK-004: warning",
                "  FLOWER-CHECK-007: off",
                "failOn: warning",
                "stepBaseClasses:",
                "  - com.acme.flow.AbstractDomainStep",
                "providerClientNames: OpenAIClient, com.acme.llm.ProviderClient",
                "schedulerApprovalAnnotations:",
                "  - ProjectSchedulerApproved",
                "agentRulesEnabled: true");

        FlowerCheckConfig config = new ConfigLoader().load(Optional.of(configFile));

        assertThat(config.failOn()).isEqualTo(Severity.WARNING);
        assertThat(config.effectiveSeverity("FLOWER-CHECK-004", Severity.ERROR)).isEqualTo(Severity.WARNING);
        assertThat(config.isDisabled("FLOWER-CHECK-007")).isTrue();
        assertThat(config.stepBaseClasses()).containsExactly("com.acme.flow.AbstractDomainStep");
        assertThat(config.providerClientNames()).containsExactly("OpenAIClient", "com.acme.llm.ProviderClient");
        assertThat(config.schedulerApprovalAnnotations()).contains("ProjectSchedulerApproved");
        assertThat(config.agentRulesEnabled()).isTrue();
    }

    @Test
    void loadsInlineAliases(@TempDir Path root) throws IOException {
        Path configFile = writeConfig(root,
                "disabledRules: [FLOWER-CHECK-001, FLOWER-CHECK-002]",
                "severity.FLOWER-CHECK-003: info",
                "agentRules: yes");

        FlowerCheckConfig config = new ConfigLoader().load(Optional.of(configFile));

        assertThat(config.isDisabled("FLOWER-CHECK-001")).isTrue();
        assertThat(config.isDisabled("FLOWER-CHECK-002")).isTrue();
        assertThat(config.effectiveSeverity("FLOWER-CHECK-003", Severity.ERROR)).isEqualTo(Severity.INFO);
        assertThat(config.agentRulesEnabled()).isTrue();
    }

    @Test
    void loadsHostProjectTemplateConfig() {
        Path template = templatePath("flower-check.config");

        FlowerCheckConfig config = new ConfigLoader().load(Optional.of(template));

        assertThat(config.failOn()).isEqualTo(Severity.ERROR);
        assertThat(config.agentRulesEnabled()).isFalse();
        assertThat(config.schedulerApprovalAnnotations()).contains("FlowerSchedulerApproved");
        assertThat(config.baselineEntries()).isEmpty();
    }

    @Test
    void loadsBaselineFileRelativeToConfig(@TempDir Path root) throws IOException {
        Files.write(root.resolve("flower-check-baseline.txt"),
                "FLOWER-CHECK-001 ERROR WaitStep.java:4\n".getBytes(StandardCharsets.UTF_8));
        Path configFile = writeConfig(root, "baselineFile: flower-check-baseline.txt");

        FlowerCheckConfig config = new ConfigLoader().load(Optional.of(configFile));

        assertThat(config.baselineEntries()).hasSize(1);
        assertThat(config.baselineEntries().get(0).matches(io.github.parkkevinsb.flower.check.finding.Finding.builder()
                .ruleId("FLOWER-CHECK-001")
                .severity(Severity.ERROR)
                .file("WaitStep.java")
                .line(4)
                .what("what")
                .why("why")
                .fix("fix")
                .build())).isTrue();
    }

    @Test
    void rejectsMissingBaselineFileByDefault(@TempDir Path root) throws IOException {
        Path configFile = writeConfig(root, "baselineFile: flower-check-baseline.txt");

        assertThatThrownBy(() -> new ConfigLoader().load(Optional.of(configFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseline file does not exist");
    }

    @Test
    void allowsMissingBaselineFileWhenWritingBaseline(@TempDir Path root) throws IOException {
        Path configFile = writeConfig(root, "baselineFile: flower-check-baseline.txt");

        FlowerCheckConfig config = new ConfigLoader().load(Optional.of(configFile), true);

        assertThat(config.baselineEntries()).isEmpty();
    }

    @Test
    void rejectsUnknownKeys(@TempDir Path root) throws IOException {
        Path configFile = writeConfig(root, "surprise: true");

        assertThatThrownBy(() -> new ConfigLoader().load(Optional.of(configFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown config key");
    }

    @Test
    void acceptsUtf8BomOnFirstKey(@TempDir Path root) throws IOException {
        Path configFile = writeConfig(root, "\uFEFFrules:", "  FLOWER-CHECK-007: off");

        FlowerCheckConfig config = new ConfigLoader().load(Optional.of(configFile));

        assertThat(config.isDisabled("FLOWER-CHECK-007")).isTrue();
    }

    private static Path writeConfig(Path root, String... lines) throws IOException {
        Path file = root.resolve("flower-check.config");
        Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static Path templatePath(String name) {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path fromModule = userDir.resolve("templates").resolve(name);
        if (Files.isRegularFile(fromModule)) {
            return fromModule;
        }
        return userDir.resolve("flower-check").resolve("templates").resolve(name);
    }
}
