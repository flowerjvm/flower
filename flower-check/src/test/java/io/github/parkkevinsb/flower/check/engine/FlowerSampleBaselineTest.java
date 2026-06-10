package io.github.parkkevinsb.flower.check.engine;

import io.github.parkkevinsb.flower.check.config.FlowerCheckConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * False-positive baseline required by docs/02-rule-catalog.md.
 *
 * <p>The sample project lives as a sibling checkout in this workspace, not
 * inside the flower repo. Environments without that checkout skip this test;
 * local development with the sibling repo present must keep all three modules
 * at zero findings.
 */
class FlowerSampleBaselineTest {

    private static final List<String> BASELINE_MODULES = Arrays.asList(
            "cafe-order",
            "durable-order",
            "flower-basic-samples");

    @Test
    void knownGoodFlowerSampleModulesHaveNoFindings() {
        Optional<Path> sampleRoot = findSampleRoot();
        assumeTrue(sampleRoot.isPresent(),
                "flower-sample checkout not found; set -Dflower.check.sampleRoot=<path> to enable baseline");

        List<String> roots = new ArrayList<>();
        for (String module : BASELINE_MODULES) {
            Path moduleRoot = sampleRoot.get().resolve(module);
            assumeTrue(Files.isDirectory(moduleRoot), "flower-sample module not found: " + moduleRoot);
            roots.add(moduleRoot.toString());
        }

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults()).run(roots);

        assertThat(result.findings())
                .as("flower-sample modules must remain a zero-finding false-positive baseline")
                .isEmpty();
        assertThat(result.failed()).isFalse();
    }

    private static Optional<Path> findSampleRoot() {
        String configured = System.getProperty("flower.check.sampleRoot");
        if (configured != null && !configured.trim().isEmpty()) {
            Path path = Paths.get(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return Optional.of(path);
            }
        }

        for (String candidate : Arrays.asList(
                "../flower-sample",
                "../../flower-sample",
                "flower-sample")) {
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }
}
