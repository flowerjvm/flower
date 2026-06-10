package io.github.parkkevinsb.flower.check.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.parkkevinsb.flower.check.finding.BaselineLoader;
import io.github.parkkevinsb.flower.check.rule.Severity;

/**
 * Loads a {@link FlowerCheckConfig} from a config file.
 *
 * <p>The format is a small YAML-like subset, deliberately kept dependency-free:
 *
 * <pre>
 * rules:
 *   FLOWER-CHECK-004: warning
 *   FLOWER-CHECK-007: off
 * failOn: error
 * stepBaseClasses:
 *   - com.acme.flow.AbstractDomainStep
 * providerClientNames: OpenAIClient, com.acme.llm.Client
 * schedulerApprovalAnnotations:
 *   - ProjectSchedulerApproved
 * baselineFile: flower-check-baseline.txt
 * agentRulesEnabled: true
 * </pre>
 */
public final class ConfigLoader {

    public FlowerCheckConfig load(Optional<Path> configPath) {
        if (!configPath.isPresent()) {
            return FlowerCheckConfig.defaults();
        }
        Path path = configPath.get();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("config file does not exist: " + path);
        }
        try {
            return new Parser(path, Files.readAllLines(path, StandardCharsets.UTF_8)).parse();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read config file: " + path, e);
        }
    }

    private static final class Parser {
        private final Path path;
        private final List<String> lines;
        private final FlowerCheckConfig.Builder builder = FlowerCheckConfig.builder();
        private String section;

        Parser(Path path, List<String> lines) {
            this.path = path;
            this.lines = lines;
        }

        FlowerCheckConfig parse() {
            for (int i = 0; i < lines.size(); i++) {
                parseLine(i + 1, lines.get(i));
            }
            return builder.build();
        }

        private void parseLine(int lineNo, String raw) {
            String line = stripComment(raw);
            if (line.trim().isEmpty()) {
                return;
            }

            String trimmed = line.trim();
            int indent = leadingSpaces(line);
            if (trimmed.startsWith("-")) {
                parseListItem(lineNo, trimmed.substring(1).trim());
                return;
            }

            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                throw error(lineNo, "expected key: value");
            }

            String key = cleaned(trimmed.substring(0, colon));
            String value = cleaned(trimmed.substring(colon + 1));
            if (key.isEmpty()) {
                throw error(lineNo, "empty config key");
            }

            if (indent > 0 && ("rules".equals(section) || "severity".equals(section))) {
                applyRuleSetting(lineNo, key, value);
                return;
            }

            section = null;
            parseTopLevel(lineNo, key, value);
        }

        private void parseTopLevel(int lineNo, String key, String value) {
            String normalized = normalize(key);
            if (value.isEmpty()) {
                if (isSection(normalized)) {
                    section = normalized;
                    return;
                }
                throw error(lineNo, "missing value for " + key);
            }

            if ("failon".equals(normalized)) {
                builder.failOn(parseSeverity(lineNo, value));
            } else if ("agentrulesenabled".equals(normalized) || "agentrules".equals(normalized)) {
                builder.agentRulesEnabled(parseBoolean(lineNo, value));
            } else if ("stepbaseclasses".equals(normalized)) {
                addListValues(lineNo, "stepbaseclasses", value);
            } else if ("providerclientnames".equals(normalized)) {
                addListValues(lineNo, "providerclientnames", value);
            } else if ("schedulerapprovalannotations".equals(normalized)) {
                addListValues(lineNo, "schedulerapprovalannotations", value);
            } else if ("baselinefile".equals(normalized) || "baseline".equals(normalized)) {
                builder.addBaselineEntries(new BaselineLoader().load(resolvePath(value)));
            } else if ("disabledrules".equals(normalized)) {
                addListValues(lineNo, "disabledrules", value);
            } else if ("rules".equals(normalized) || "severity".equals(normalized)) {
                parseInlineRuleSettings(lineNo, value);
            } else if (normalized.startsWith("rules.") || normalized.startsWith("severity.")) {
                applyRuleSetting(lineNo, key.substring(key.indexOf('.') + 1), value);
            } else if (normalized.startsWith("rule.") && normalized.endsWith(".severity")) {
                String ruleId = key.substring("rule.".length(), key.length() - ".severity".length());
                applyRuleSetting(lineNo, ruleId, value);
            } else {
                throw error(lineNo, "unknown config key: " + key);
            }
        }

        private void parseListItem(int lineNo, String value) {
            if (section == null) {
                throw error(lineNo, "list item is not inside a config section");
            }
            if ("rules".equals(section) || "severity".equals(section)) {
                int split = splitRuleSetting(value);
                if (split < 0) {
                    throw error(lineNo, "expected ruleId: severity in " + section);
                }
                applyRuleSetting(lineNo, value.substring(0, split), value.substring(split + 1));
                return;
            }
            addListValues(lineNo, section, value);
        }

        private void parseInlineRuleSettings(int lineNo, String value) {
            for (String entry : splitList(value)) {
                int split = splitRuleSetting(entry);
                if (split < 0) {
                    throw error(lineNo, "expected ruleId: severity in rules");
                }
                applyRuleSetting(lineNo, entry.substring(0, split), entry.substring(split + 1));
            }
        }

        private void applyRuleSetting(int lineNo, String ruleId, String value) {
            String id = cleaned(ruleId);
            String setting = cleaned(value);
            if (id.isEmpty() || setting.isEmpty()) {
                throw error(lineNo, "rule settings require rule id and severity");
            }
            if ("off".equalsIgnoreCase(setting) || "disabled".equalsIgnoreCase(setting)) {
                builder.disableRule(id);
                return;
            }
            builder.overrideSeverity(id, parseSeverity(lineNo, setting));
        }

        private void addListValues(int lineNo, String sectionName, String value) {
            for (String item : splitList(value)) {
                String cleaned = cleaned(item);
                if (cleaned.isEmpty()) {
                    continue;
                }
                if ("stepbaseclasses".equals(sectionName)) {
                    builder.addStepBaseClass(cleaned);
                } else if ("providerclientnames".equals(sectionName)) {
                    builder.addProviderClientName(cleaned);
                } else if ("schedulerapprovalannotations".equals(sectionName)) {
                    builder.addSchedulerApprovalAnnotation(cleaned);
                } else if ("disabledrules".equals(sectionName)) {
                    builder.disableRule(cleaned);
                } else {
                    throw error(lineNo, "unsupported list section: " + sectionName);
                }
            }
        }

        private Severity parseSeverity(int lineNo, String value) {
            try {
                return Severity.valueOf(cleaned(value).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw error(lineNo, "invalid severity: " + value + " (use error|warning|info|off for rules)");
            }
        }

        private boolean parseBoolean(int lineNo, String value) {
            String normalized = cleaned(value).toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
                return false;
            }
            throw error(lineNo, "invalid boolean: " + value + " (use true|false)");
        }

        private boolean isSection(String key) {
            return "rules".equals(key)
                    || "severity".equals(key)
                    || "stepbaseclasses".equals(key)
                    || "providerclientnames".equals(key)
                    || "schedulerapprovalannotations".equals(key)
                    || "disabledrules".equals(key);
        }

        private Path resolvePath(String value) {
            Path candidate = path.getFileSystem().getPath(cleaned(value));
            if (candidate.isAbsolute()) {
                return candidate.normalize();
            }
            Path parent = path.getParent();
            return (parent == null ? candidate : parent.resolve(candidate)).normalize();
        }

        private IllegalArgumentException error(int lineNo, String message) {
            return new IllegalArgumentException(path + ":" + lineNo + ": " + message);
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String normalize(String key) {
        return cleaned(key).toLowerCase(Locale.ROOT).replace("-", "");
    }

    private static String cleaned(String value) {
        String cleaned = value.trim();
        if (!cleaned.isEmpty() && cleaned.charAt(0) == '\uFEFF') {
            cleaned = cleaned.substring(1).trim();
        }
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            return cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private static String[] splitList(String value) {
        String body = cleaned(value);
        if (body.startsWith("[") && body.endsWith("]")) {
            body = body.substring(1, body.length() - 1);
        }
        return body.split(",");
    }

    private static int splitRuleSetting(String value) {
        int colon = value.indexOf(':');
        int equals = value.indexOf('=');
        if (colon < 0) {
            return equals;
        }
        if (equals < 0) {
            return colon;
        }
        return Math.min(colon, equals);
    }
}
