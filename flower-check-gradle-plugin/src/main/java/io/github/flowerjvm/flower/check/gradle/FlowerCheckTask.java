package io.github.flowerjvm.flower.check.gradle;

import io.github.flowerjvm.flower.check.config.ConfigLoader;
import io.github.flowerjvm.flower.check.config.FlowerCheckConfig;
import io.github.flowerjvm.flower.check.engine.CheckResult;
import io.github.flowerjvm.flower.check.engine.FlowerCheckEngine;
import io.github.flowerjvm.flower.check.finding.BaselineWriter;
import io.github.flowerjvm.flower.check.finding.Finding;
import io.github.flowerjvm.flower.check.report.PlainTextReporter;
import io.github.flowerjvm.flower.check.report.ReportFormat;
import io.github.flowerjvm.flower.check.report.Reporter;
import io.github.flowerjvm.flower.check.report.SarifReporter;
import io.github.flowerjvm.flower.check.rule.Severity;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Gradle task that runs flower-check in-process.
 */
public class FlowerCheckTask extends DefaultTask {

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

    public FlowerCheckTask() {
        this.sourceRoots = getProject().files();
        this.testSourceRoots = getProject().files();
        this.extraSourceRoots = getProject().files();
        this.configFile = getProject().getObjects().fileProperty();
        this.outputFile = getProject().getObjects().fileProperty();
        this.writeBaseline = getProject().getObjects().fileProperty();
        this.skip = getProject().getObjects().property(Boolean.class);
        this.includeTests = getProject().getObjects().property(Boolean.class);
        this.failOn = getProject().getObjects().property(String.class);
        this.format = getProject().getObjects().property(String.class);

        setGroup("verification");
        setDescription("Runs flower-check against Flower source usage.");
    }

    @TaskAction
    public void runFlowerCheck() {
        if (Boolean.TRUE.equals(skip.getOrElse(false))) {
            getLogger().lifecycle("flower-check skipped by -Pflower.check.skip=true");
            return;
        }

        List<String> roots = collectSourceRoots();
        if (roots.isEmpty()) {
            getLogger().lifecycle("flower-check: no Java source roots to scan.");
            return;
        }

        FlowerCheckConfig config = loadConfig();
        CheckResult result = FlowerCheckEngine.create(config).run(roots);

        if (writeBaseline.isPresent()) {
            writeBaseline(result);
            return;
        }

        writeReport(result);
        if (result.failed()) {
            throw new GradleException("flower-check failed: "
                    + result.findings().size() + " active finding"
                    + (result.findings().size() == 1 ? "" : "s")
                    + " at or above " + config.failOn());
        }
    }

    @Internal
    public ConfigurableFileCollection getSourceRoots() {
        return sourceRoots;
    }

    @Internal
    public ConfigurableFileCollection getTestSourceRoots() {
        return testSourceRoots;
    }

    @Internal
    public ConfigurableFileCollection getExtraSourceRoots() {
        return extraSourceRoots;
    }

    @Internal
    public RegularFileProperty getConfigFile() {
        return configFile;
    }

    @Internal
    public RegularFileProperty getOutputFile() {
        return outputFile;
    }

    @Internal
    public RegularFileProperty getWriteBaseline() {
        return writeBaseline;
    }

    @Input
    public Property<Boolean> getSkip() {
        return skip;
    }

    @Input
    public Property<Boolean> getIncludeTests() {
        return includeTests;
    }

    @Internal
    public Property<String> getFailOn() {
        return failOn;
    }

    @Input
    public Property<String> getFormat() {
        return format;
    }

    private List<String> collectSourceRoots() {
        Set<String> roots = new LinkedHashSet<>();
        addExistingRoots(roots, sourceRoots);
        if (Boolean.TRUE.equals(includeTests.getOrElse(false))) {
            addExistingRoots(roots, testSourceRoots);
        }
        addExistingRoots(roots, extraSourceRoots);
        return new ArrayList<>(roots);
    }

    private static void addExistingRoots(Set<String> roots, ConfigurableFileCollection candidates) {
        for (File file : candidates.getFiles()) {
            if (file.exists()) {
                roots.add(file.getPath());
            }
        }
    }

    private FlowerCheckConfig loadConfig() {
        Optional<java.nio.file.Path> configPath = configPath();
        try {
            FlowerCheckConfig config = new ConfigLoader().load(configPath, writeBaseline.isPresent());
            if (failOn.isPresent() && !failOn.get().trim().isEmpty()) {
                config = config.toBuilder()
                        .failOn(parseSeverity(failOn.get()))
                        .build();
            }
            return config;
        } catch (RuntimeException e) {
            throw new GradleException("flower-check configuration error: " + e.getMessage(), e);
        }
    }

    private Optional<java.nio.file.Path> configPath() {
        if (configFile.isPresent()) {
            File file = configFile.get().getAsFile();
            if (file.isFile()) {
                return Optional.of(file.toPath());
            }
        }
        return Optional.empty();
    }

    private void writeBaseline(CheckResult result) {
        try {
            List<Finding> baselineFindings = new ArrayList<>();
            baselineFindings.addAll(result.acceptedFindings());
            baselineFindings.addAll(result.findings());
            File baseline = writeBaseline.get().getAsFile();
            int count = new BaselineWriter().write(baselineFindings, baseline.toPath());
            getLogger().lifecycle("flower-check: wrote " + count + " baseline "
                    + (count == 1 ? "finding" : "findings") + " to " + baseline);
        } catch (RuntimeException e) {
            throw new GradleException("flower-check baseline write error: " + e.getMessage(), e);
        }
    }

    private void writeReport(CheckResult result) {
        ReportFormat reportFormat = parseReportFormat();
        StringBuilder report = new StringBuilder();
        reporter(reportFormat).report(result.findings(), result.acceptedFindings(), report);

        if (outputFile.isPresent()) {
            File file = outputFile.get().getAsFile();
            writeFile(file, report.toString());
            getLogger().lifecycle("flower-check: wrote "
                    + reportFormat.name().toLowerCase(Locale.ROOT) + " report to " + file);
            return;
        }

        for (String line : report.toString().split("\\r?\\n", -1)) {
            if (!line.isEmpty()) {
                getLogger().lifecycle(line);
            }
        }
    }

    private ReportFormat parseReportFormat() {
        try {
            return ReportFormat.parse(format.getOrElse("plain"));
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    private static Reporter reporter(ReportFormat format) {
        if (format == ReportFormat.SARIF) {
            return new SarifReporter();
        }
        return new PlainTextReporter();
    }

    private static void writeFile(File file, String text) {
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new GradleException("could not write flower-check report: " + file, e);
        } catch (UncheckedIOException e) {
            throw new GradleException("could not write flower-check report: " + file, e);
        }
    }

    private static Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid severity: " + value + " (use error|warning|info)");
        }
    }
}
