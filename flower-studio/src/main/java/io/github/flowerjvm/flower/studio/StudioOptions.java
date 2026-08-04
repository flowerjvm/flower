package io.github.flowerjvm.flower.studio;

import io.github.flowerjvm.flower.studio.store.JsonLinesObservationRepository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/** Command-line and environment configuration for the local Studio app. */
public final class StudioOptions {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8077;

    private final String host;
    private final int port;
    private final Path traceFile;
    private final Path artifactRoot;
    private final int maxEvents;
    private final boolean help;

    private StudioOptions(
            String host,
            int port,
            Path traceFile,
            Path artifactRoot,
            int maxEvents,
            boolean help) {
        this.host = host;
        this.port = port;
        this.traceFile = traceFile;
        this.artifactRoot = artifactRoot;
        this.maxEvents = maxEvents;
        this.help = help;
    }

    public static StudioOptions parse(String[] args) {
        return parse(args, System.getenv());
    }

    static StudioOptions parse(String[] args, Map<String, String> environment) {
        String host = value(environment, "FLOWER_STUDIO_HOST", DEFAULT_HOST);
        int port = integer(value(environment, "FLOWER_STUDIO_PORT", null), DEFAULT_PORT, "port");
        String trace = value(
                environment,
                "FLOWER_STUDIO_TRACE_FILE",
                "data/flower-observations.jsonl");
        String artifacts = value(
                environment,
                "FLOWER_STUDIO_ARTIFACT_ROOT",
                "data/flower-artifacts");
        int maxEvents = integer(
                value(environment, "FLOWER_STUDIO_MAX_EVENTS", null),
                JsonLinesObservationRepository.DEFAULT_MAX_EVENTS,
                "max-events");
        boolean help = false;

        if (args != null) {
            for (String argument : args) {
                if ("--help".equals(argument) || "-h".equals(argument)) {
                    help = true;
                    continue;
                }
                if (argument == null || !argument.startsWith("--") || !argument.contains("=")) {
                    throw new IllegalArgumentException(
                            "arguments must use --name=value; use --help for options");
                }
                int separator = argument.indexOf('=');
                String name = argument.substring(2, separator);
                String selected = argument.substring(separator + 1);
                if ("host".equals(name)) {
                    host = requireText(name, selected);
                } else if ("port".equals(name)) {
                    port = integer(selected, DEFAULT_PORT, name);
                } else if ("trace-file".equals(name)) {
                    trace = requireText(name, selected);
                } else if ("artifact-root".equals(name)) {
                    artifacts = selected;
                } else if ("max-events".equals(name)) {
                    maxEvents = integer(selected, maxEvents, name);
                } else {
                    throw new IllegalArgumentException("unknown option: --" + name);
                }
            }
        }

        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("max-events must be positive");
        }
        Path artifactRoot = artifacts == null
                || artifacts.trim().isEmpty()
                || "none".equalsIgnoreCase(artifacts.trim())
                ? null : Paths.get(artifacts).toAbsolutePath().normalize();
        return new StudioOptions(
                requireText("host", host),
                port,
                Paths.get(trace).toAbsolutePath().normalize(),
                artifactRoot,
                maxEvents,
                help);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public Path traceFile() {
        return traceFile;
    }

    public Path artifactRoot() {
        return artifactRoot;
    }

    public int maxEvents() {
        return maxEvents;
    }

    public boolean help() {
        return help;
    }

    public static String helpText() {
        return "Flower Studio options:\n"
                + "  --host=127.0.0.1             HTTP bind address\n"
                + "  --port=8077                  HTTP port; 0 selects a free port\n"
                + "  --trace-file=<path>          Observation JSON Lines file\n"
                + "  --artifact-root=<path|none>  Safe root for artifact downloads\n"
                + "  --max-events=100000          Newest events retained in memory\n"
                + "  --help                       Show this help\n";
    }

    private static String value(
            Map<String, String> environment,
            String name,
            String fallback) {
        String selected = environment == null ? null : environment.get(name);
        return selected == null || selected.trim().isEmpty() ? fallback : selected.trim();
    }

    private static int integer(String value, int fallback, String name) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }

    private static String requireText(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
