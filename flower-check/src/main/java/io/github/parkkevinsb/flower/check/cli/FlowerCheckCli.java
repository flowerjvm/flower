package io.github.parkkevinsb.flower.check.cli;

import io.github.parkkevinsb.flower.check.config.ConfigLoader;
import io.github.parkkevinsb.flower.check.config.FlowerCheckConfig;
import io.github.parkkevinsb.flower.check.engine.CheckResult;
import io.github.parkkevinsb.flower.check.engine.FlowerCheckEngine;
import io.github.parkkevinsb.flower.check.report.PlainTextReporter;
import io.github.parkkevinsb.flower.check.report.ReportFormat;
import io.github.parkkevinsb.flower.check.report.Reporter;
import io.github.parkkevinsb.flower.check.report.RuleListReporter;
import io.github.parkkevinsb.flower.check.report.SarifReporter;
import io.github.parkkevinsb.flower.check.rule.RuleRegistry;

/**
 * Command-line entry point. Wiring only: argument parsing, engine run,
 * reporting, exit code. No rule logic lives here.
 *
 * <pre>
 * flower-check src/main/java
 * flower-check --config flower-check.config src/main/java another/src
 * flower-check --list-rules
 * </pre>
 */
public final class FlowerCheckCli {

    private final ConfigLoader configLoader = new ConfigLoader();

    public static void main(String[] args) {
        int code = new FlowerCheckCli().execute(args, System.out, System.err);
        System.exit(code);
    }

    /** Testable entry: returns the exit code instead of calling System.exit. */
    public int execute(String[] args, Appendable out, Appendable err) {
        CliArguments parsed;
        try {
            parsed = CliArguments.parse(args);
        } catch (IllegalArgumentException e) {
            appendLine(err, "flower-check: " + e.getMessage());
            appendLine(err, CliArguments.usage());
            return ExitCode.USAGE;
        }

        FlowerCheckConfig config;
        try {
            config = configLoader.load(parsed.configPath());
            if (parsed.failOn().isPresent()) {
                config = config.toBuilder()
                        .failOn(parsed.failOn().get())
                        .build();
            }
        } catch (RuntimeException e) {
            appendLine(err, "flower-check: " + e.getMessage());
            return ExitCode.USAGE;
        }

        if (parsed.listRules()) {
            RuleRegistry registry = RuleRegistry.fromServiceLoader();
            new RuleListReporter().report(registry.all(), registry.enabled(config), config, out);
            return ExitCode.OK;
        }

        CheckResult result;
        try {
            result = FlowerCheckEngine.create(config).run(parsed.sourceRoots());
        } catch (RuntimeException e) {
            appendLine(err, "flower-check: " + e.getMessage());
            return ExitCode.USAGE;
        }

        reporter(parsed.reportFormat()).report(result.findings(), result.acceptedFindings(), out);
        return result.failed() ? ExitCode.FINDINGS : ExitCode.OK;
    }

    private static Reporter reporter(ReportFormat format) {
        if (format == ReportFormat.SARIF) {
            return new SarifReporter();
        }
        return new PlainTextReporter();
    }

    private static void appendLine(Appendable sink, String text) {
        try {
            sink.append(text).append("\n");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
