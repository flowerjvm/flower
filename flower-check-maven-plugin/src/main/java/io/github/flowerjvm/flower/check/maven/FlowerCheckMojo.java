package io.github.flowerjvm.flower.check.maven;

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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Runs flower-check against a host Maven project's source roots.
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true, requiresProject = true)
public final class FlowerCheckMojo extends AbstractMojo {

    /**
     * Skip the check. Intended as an explicit local escape hatch only.
     */
    @Parameter(property = "flower.check.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Main Java source roots from the host Maven project.
     */
    @Parameter(defaultValue = "${project.compileSourceRoots}", readonly = true)
    private List<String> sourceRoots;

    /**
     * Test Java source roots from the host Maven project. Disabled by default.
     */
    @Parameter(defaultValue = "${project.testCompileSourceRoots}", readonly = true)
    private List<String> testSourceRoots;

    /**
     * Include test source roots in the scan.
     */
    @Parameter(property = "flower.check.includeTests", defaultValue = "false")
    private boolean includeTests;

    /**
     * Extra source roots to scan.
     */
    @Parameter
    private List<String> extraSourceRoots;

    /**
     * Host project base directory. Used to discover flower-check.config by default.
     */
    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File basedir;

    /**
     * Optional flower-check config file. When omitted, flower-check.config in the
     * project base directory is used if it exists.
     */
    @Parameter(property = "flower.check.config")
    private File configFile;

    /**
     * Override the config fail threshold: error, warning, or info.
     */
    @Parameter(property = "flower.check.failOn")
    private String failOn;

    /**
     * Report format: plain or sarif.
     */
    @Parameter(property = "flower.check.format", defaultValue = "plain")
    private String format = "plain";

    /**
     * Optional report output file. Without this, the report is written to Maven logs.
     */
    @Parameter(property = "flower.check.outputFile")
    private File outputFile;

    /**
     * Write the current findings to this baseline file and do not fail the build.
     */
    @Parameter(property = "flower.check.writeBaseline")
    private File writeBaseline;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("flower-check skipped by -Dflower.check.skip=true");
            return;
        }

        List<String> roots = collectSourceRoots();
        if (roots.isEmpty()) {
            getLog().info("flower-check: no Java source roots to scan.");
            return;
        }

        FlowerCheckConfig config = loadConfig();
        CheckResult result = runEngine(config, roots);

        if (writeBaseline != null) {
            writeBaseline(result);
            return;
        }

        writeReport(result);
        if (result.failed()) {
            throw new MojoFailureException("flower-check failed: "
                    + result.findings().size() + " active finding"
                    + (result.findings().size() == 1 ? "" : "s")
                    + " at or above " + config.failOn());
        }
    }

    private List<String> collectSourceRoots() {
        List<String> roots = new ArrayList<>();
        addExistingRoots(roots, sourceRoots);
        if (includeTests) {
            addExistingRoots(roots, testSourceRoots);
        }
        addExistingRoots(roots, extraSourceRoots);
        return roots;
    }

    private void addExistingRoots(List<String> roots, List<String> candidates) {
        if (candidates == null) {
            return;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            File file = new File(candidate);
            if (file.exists()) {
                roots.add(file.getPath());
            }
        }
    }

    private FlowerCheckConfig loadConfig() throws MojoExecutionException {
        Optional<java.nio.file.Path> configPath = configPath();
        try {
            FlowerCheckConfig config = new ConfigLoader().load(configPath, writeBaseline != null);
            if (failOn != null && !failOn.trim().isEmpty()) {
                config = config.toBuilder()
                        .failOn(parseSeverity(failOn))
                        .build();
            }
            return config;
        } catch (RuntimeException e) {
            throw new MojoExecutionException("flower-check configuration error: " + e.getMessage(), e);
        }
    }

    private Optional<java.nio.file.Path> configPath() {
        if (configFile != null) {
            return Optional.of(configFile.toPath());
        }
        if (basedir != null) {
            File discovered = new File(basedir, "flower-check.config");
            if (discovered.isFile()) {
                return Optional.of(discovered.toPath());
            }
        }
        return Optional.empty();
    }

    private CheckResult runEngine(FlowerCheckConfig config, List<String> roots) throws MojoExecutionException {
        try {
            return FlowerCheckEngine.create(config).run(roots);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("flower-check execution error: " + e.getMessage(), e);
        }
    }

    private void writeBaseline(CheckResult result) throws MojoExecutionException {
        try {
            List<Finding> baselineFindings = new ArrayList<>();
            baselineFindings.addAll(result.acceptedFindings());
            baselineFindings.addAll(result.findings());
            int count = new BaselineWriter().write(baselineFindings, writeBaseline.toPath());
            getLog().info("flower-check: wrote " + count + " baseline "
                    + (count == 1 ? "finding" : "findings") + " to " + writeBaseline);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("flower-check baseline write error: " + e.getMessage(), e);
        }
    }

    private void writeReport(CheckResult result) throws MojoExecutionException {
        ReportFormat reportFormat = parseReportFormat();
        StringBuilder report = new StringBuilder();
        reporter(reportFormat).report(result.findings(), result.acceptedFindings(), report);

        if (outputFile != null) {
            writeFile(outputFile, report.toString());
            getLog().info("flower-check: wrote " + reportFormat.name().toLowerCase(Locale.ROOT)
                    + " report to " + outputFile);
            return;
        }

        for (String line : report.toString().split("\\r?\\n", -1)) {
            if (!line.isEmpty()) {
                getLog().info(line);
            }
        }
    }

    private ReportFormat parseReportFormat() throws MojoExecutionException {
        try {
            return ReportFormat.parse(format);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private static Reporter reporter(ReportFormat format) {
        if (format == ReportFormat.SARIF) {
            return new SarifReporter();
        }
        return new PlainTextReporter();
    }

    private void writeFile(File file, String text) throws MojoExecutionException {
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MojoExecutionException("could not write flower-check report: " + file, e);
        } catch (UncheckedIOException e) {
            throw new MojoExecutionException("could not write flower-check report: " + file, e);
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
