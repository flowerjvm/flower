package io.github.flowerjvm.flower.check.config;

import io.github.flowerjvm.flower.check.finding.BaselineEntry;
import io.github.flowerjvm.flower.check.rule.Severity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime configuration for a check run: what fails the build, per-rule severity
 * overrides, disabled rules, project-specific Step base classes, and opt-in
 * switches. Defaults are strict (see {@code docs/00-INDEX.md} governance).
 *
 * <p>Immutable value built through {@link Builder}. {@link ConfigLoader}
 * populates it from the small {@code flower-check.config} format.
 */
public final class FlowerCheckConfig {

    private static final List<String> DEFAULT_SCHEDULER_APPROVAL_ANNOTATIONS = Arrays.asList(
            "FlowerSchedulerApproved",
            "UserApprovedScheduler",
            "SchedulerApproved",
            "ApprovedScheduler");

    private final Severity failOn;
    private final Map<String, Severity> severityOverrides;
    private final Set<String> disabledRules;
    private final List<String> stepBaseClasses;
    private final List<String> providerClientNames;
    private final List<String> schedulerApprovalAnnotations;
    private final List<BaselineEntry> baselineEntries;
    private final boolean agentRulesEnabled;

    private FlowerCheckConfig(Builder b) {
        this.failOn = b.failOn;
        this.severityOverrides = Collections.unmodifiableMap(new HashMap<>(b.severityOverrides));
        this.disabledRules = Collections.unmodifiableSet(new LinkedHashSet<>(b.disabledRules));
        this.stepBaseClasses = Collections.unmodifiableList(new ArrayList<>(b.stepBaseClasses));
        this.providerClientNames = Collections.unmodifiableList(new ArrayList<>(b.providerClientNames));
        this.schedulerApprovalAnnotations =
                Collections.unmodifiableList(new ArrayList<>(b.schedulerApprovalAnnotations));
        this.baselineEntries = Collections.unmodifiableList(new ArrayList<>(b.baselineEntries));
        this.agentRulesEnabled = b.agentRulesEnabled;
    }

    /** Strict defaults: fail on ERROR, all non-agent rules enabled. */
    public static FlowerCheckConfig defaults() {
        return builder().build();
    }

    public Severity failOn() {
        return failOn;
    }

    public boolean isDisabled(String ruleId) {
        return disabledRules.contains(ruleId);
    }

    /** Effective severity for a rule: an override if present, else the default. */
    public Severity effectiveSeverity(String ruleId, Severity defaultSeverity) {
        Severity override = severityOverrides.get(ruleId);
        return override != null ? override : defaultSeverity;
    }

    /** Extra fully-qualified or simple names treated as Step base classes. */
    public List<String> stepBaseClasses() {
        return stepBaseClasses;
    }

    /** Provider client type/package names that FLOWER-CHECK-002 treats as LLM SDKs. */
    public List<String> providerClientNames() {
        return providerClientNames;
    }

    /** Annotation names that mark a recurring scheduler as explicitly user-approved. */
    public List<String> schedulerApprovalAnnotations() {
        return schedulerApprovalAnnotations;
    }

    /** Findings accepted as existing debt for this run. New findings still fail. */
    public List<BaselineEntry> baselineEntries() {
        return baselineEntries;
    }

    public boolean agentRulesEnabled() {
        return agentRulesEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder copy = builder()
                .failOn(failOn)
                .agentRulesEnabled(agentRulesEnabled);
        for (Map.Entry<String, Severity> e : severityOverrides.entrySet()) {
            copy.overrideSeverity(e.getKey(), e.getValue());
        }
        for (String ruleId : disabledRules) {
            copy.disableRule(ruleId);
        }
        for (String name : stepBaseClasses) {
            copy.addStepBaseClass(name);
        }
        for (String name : providerClientNames) {
            copy.addProviderClientName(name);
        }
        for (String name : schedulerApprovalAnnotations) {
            copy.addSchedulerApprovalAnnotation(name);
        }
        for (BaselineEntry entry : baselineEntries) {
            copy.addBaselineEntry(entry);
        }
        return copy;
    }

    public static final class Builder {
        private Severity failOn = Severity.ERROR;
        private final Map<String, Severity> severityOverrides = new HashMap<>();
        private final Set<String> disabledRules = new LinkedHashSet<>();
        private final List<String> stepBaseClasses = new ArrayList<>();
        private final List<String> providerClientNames = new ArrayList<>();
        private final List<String> schedulerApprovalAnnotations =
                new ArrayList<>(DEFAULT_SCHEDULER_APPROVAL_ANNOTATIONS);
        private final List<BaselineEntry> baselineEntries = new ArrayList<>();
        private boolean agentRulesEnabled = false; // Tier 2 is opt-in

        public Builder failOn(Severity failOn) {
            this.failOn = failOn;
            return this;
        }

        public Builder overrideSeverity(String ruleId, Severity severity) {
            this.severityOverrides.put(ruleId, severity);
            return this;
        }

        public Builder disableRule(String ruleId) {
            this.disabledRules.add(ruleId);
            return this;
        }

        public Builder addStepBaseClass(String name) {
            this.stepBaseClasses.add(name);
            return this;
        }

        public Builder addProviderClientName(String name) {
            this.providerClientNames.add(name);
            return this;
        }

        public Builder addSchedulerApprovalAnnotation(String name) {
            if (!this.schedulerApprovalAnnotations.contains(name)) {
                this.schedulerApprovalAnnotations.add(name);
            }
            return this;
        }

        public Builder addBaselineEntry(BaselineEntry entry) {
            this.baselineEntries.add(entry);
            return this;
        }

        public Builder addBaselineEntries(List<BaselineEntry> entries) {
            this.baselineEntries.addAll(entries);
            return this;
        }

        public Builder agentRulesEnabled(boolean enabled) {
            this.agentRulesEnabled = enabled;
            return this;
        }

        public FlowerCheckConfig build() {
            return new FlowerCheckConfig(this);
        }
    }
}
