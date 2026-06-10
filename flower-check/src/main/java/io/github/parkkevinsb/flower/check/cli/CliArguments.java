package io.github.parkkevinsb.flower.check.cli;

import io.github.parkkevinsb.flower.check.rule.Severity;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed command-line arguments.
 *
 * <pre>
 * flower-check [--config &lt;file&gt;] [--fail-on error|warning|info] &lt;path&gt; [&lt;path&gt; ...]
 * </pre>
 *
 * Parsing is deliberately tiny — no args framework. Add flags here as the CLI
 * grows (e.g. {@code --format sarif}, {@code --list-rules}).
 */
public final class CliArguments {

    private final List<String> sourceRoots;
    private final Optional<Path> configPath;
    private final Optional<Severity> failOn;

    private CliArguments(List<String> sourceRoots, Optional<Path> configPath, Optional<Severity> failOn) {
        this.sourceRoots = sourceRoots;
        this.configPath = configPath;
        this.failOn = failOn;
    }

    public List<String> sourceRoots() {
        return sourceRoots;
    }

    public Optional<Path> configPath() {
        return configPath;
    }

    public Optional<Severity> failOn() {
        return failOn;
    }

    /** @throws IllegalArgumentException on malformed input (CLI maps to exit 2). */
    public static CliArguments parse(String[] args) {
        List<String> roots = new ArrayList<>();
        Optional<Path> configPath = Optional.empty();
        Optional<Severity> failOn = Optional.empty();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--config":
                    configPath = Optional.of(Paths.get(requireValue(args, ++i, "--config")));
                    break;
                case "--fail-on":
                    failOn = Optional.of(parseSeverity(requireValue(args, ++i, "--fail-on")));
                    break;
                default:
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("unknown option: " + arg);
                    }
                    roots.add(arg);
            }
        }

        if (roots.isEmpty()) {
            throw new IllegalArgumentException("at least one source path is required");
        }
        return new CliArguments(roots, configPath, failOn);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("missing value for " + option);
        }
        return args[index];
    }

    private static Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid severity: " + value + " (use error|warning|info)");
        }
    }

    public static String usage() {
        return "usage: flower-check [--config <file>] [--fail-on error|warning|info] <path> [<path> ...]";
    }
}
