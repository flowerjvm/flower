package io.github.parkkevinsb.flower.check.rule;

import io.github.parkkevinsb.flower.check.config.FlowerCheckConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers {@link Rule} implementations via {@link ServiceLoader} and applies
 * config filtering (disabled rules). Rules are returned sorted by id so output
 * order is deterministic.
 *
 * <p>Because discovery is service-based, adding a rule never touches the engine:
 * implement {@link Rule} and list it in
 * {@code META-INF/services/io.github.parkkevinsb.flower.check.rule.Rule}.
 */
public final class RuleRegistry {

    private final List<Rule> allRules;

    private RuleRegistry(List<Rule> allRules) {
        this.allRules = allRules;
    }

    /** Load every registered rule from the current classpath. */
    public static RuleRegistry fromServiceLoader() {
        List<Rule> rules = new ArrayList<>();
        for (Rule rule : ServiceLoader.load(Rule.class)) {
            rules.add(rule);
        }
        rules.sort(Comparator.comparing(Rule::id));
        return new RuleRegistry(rules);
    }

    /** Explicit list, mainly for tests. */
    public static RuleRegistry of(List<Rule> rules) {
        List<Rule> copy = new ArrayList<>(rules);
        copy.sort(Comparator.comparing(Rule::id));
        return new RuleRegistry(copy);
    }

    public List<Rule> all() {
        return new ArrayList<>(allRules);
    }

    /** Rules that are enabled under the given config. */
    public List<Rule> enabled(FlowerCheckConfig config) {
        List<Rule> enabled = new ArrayList<>();
        for (Rule rule : allRules) {
            if (!config.isDisabled(rule.id()) && isOptedIn(rule, config)) {
                enabled.add(rule);
            }
        }
        return enabled;
    }

    private boolean isOptedIn(Rule rule, FlowerCheckConfig config) {
        return !isAgentRule(rule) || config.agentRulesEnabled();
    }

    private boolean isAgentRule(Rule rule) {
        String id = rule.id();
        return "FLOWER-CHECK-006".equals(id)
                || "FLOWER-CHECK-007".equals(id)
                || "FLOWER-CHECK-008".equals(id)
                || rule.getClass().getName().contains(".rule.agent.");
    }
}
