package io.github.flowerjvm.flower.check.engine;

import io.github.flowerjvm.flower.check.config.FlowerCheckConfig;
import io.github.flowerjvm.flower.check.finding.Finding;
import io.github.flowerjvm.flower.check.finding.FindingCollector;
import io.github.flowerjvm.flower.check.finding.Suppression;
import io.github.flowerjvm.flower.check.finding.SuppressionScanner;
import io.github.flowerjvm.flower.check.model.ProjectModel;
import io.github.flowerjvm.flower.check.model.ProjectModelBuilder;
import io.github.flowerjvm.flower.check.parse.JavaParserParser;
import io.github.flowerjvm.flower.check.parse.Parser;
import io.github.flowerjvm.flower.check.parse.SourceUnit;
import io.github.flowerjvm.flower.check.rule.Rule;
import io.github.flowerjvm.flower.check.rule.RuleContext;
import io.github.flowerjvm.flower.check.rule.RuleRegistry;
import io.github.flowerjvm.flower.check.rule.Severity;
import io.github.flowerjvm.flower.check.source.SourceFile;
import io.github.flowerjvm.flower.check.source.SourceLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the two-pass pipeline described in {@code docs/01-architecture.md}:
 *
 * <pre>
 * load sources -> parse -> build ProjectModel (pass 1) -> run rules (pass 2)
 *   -> collect findings -> decide failure against config.failOn
 * </pre>
 *
 * The engine owns no rule logic. Rules come from the {@link RuleRegistry};
 * parsing comes from a {@link Parser}. Swap either without touching this class.
 */
public final class FlowerCheckEngine {

    /** Integrity diagnostic emitted when a source file receives only fallback analysis. */
    public static final String PARSE_DIAGNOSTIC_ID = "FLOWER-CHECK-PARSE";

    private final Parser parser;
    private final RuleRegistry registry;
    private final FlowerCheckConfig config;
    private final SourceLoader sourceLoader = new SourceLoader();
    private final SuppressionScanner suppressionScanner = new SuppressionScanner();

    public FlowerCheckEngine(Parser parser, RuleRegistry registry, FlowerCheckConfig config) {
        this.parser = parser;
        this.registry = registry;
        this.config = config;
    }

    /** Default wiring: JavaParser with text fallback + service-loaded rules. */
    public static FlowerCheckEngine create(FlowerCheckConfig config) {
        return new FlowerCheckEngine(
                new JavaParserParser(),
                RuleRegistry.fromServiceLoader(),
                config);
    }

    public CheckResult run(List<String> roots) {
        // Load + parse.
        List<SourceFile> files = sourceLoader.load(roots);
        List<SourceUnit> units = new ArrayList<>(files.size());
        List<Finding> parseDiagnostics = new ArrayList<>();
        List<Suppression> suppressions = new ArrayList<>();
        for (SourceFile file : files) {
            SourceUnit unit = parser.parse(file);
            units.add(unit);
            if (!unit.parsed()) {
                parseDiagnostics.add(parseDiagnostic(unit));
            }
            suppressions.addAll(suppressionScanner.scan(file));
        }

        // Pass 1: shared facts.
        ProjectModel model = new ProjectModelBuilder(config).build(units);
        RuleContext ruleContext = new RuleContext(model, config);

        // Pass 2: run each enabled rule over each unit.
        List<Rule> rules = registry.enabled(config);
        FindingCollector collector = new FindingCollector();
        for (SourceUnit unit : units) {
            for (Rule rule : rules) {
                List<Finding> produced = rule.apply(unit, ruleContext);
                if (produced != null && !produced.isEmpty()) {
                    collector.addAll(produced);
                }
            }
        }
        collector.suppressAll(suppressions);
        List<Finding> acceptedFindings = collector.acceptBaseline(config.baselineEntries());
        // Parser integrity diagnostics are deliberately added after suppression
        // and baseline handling. Incomplete analysis cannot be waived as debt.
        collector.addAll(parseDiagnostics);

        // Decide failure.
        List<Finding> findings = collector.findings();
        Severity worst = collector.worstSeverity();
        boolean failed = worst != null && worst.atLeast(config.failOn());
        return new CheckResult(findings, acceptedFindings, worst, failed);
    }

    private Finding parseDiagnostic(SourceUnit unit) {
        String detail = unit.parseFailure().orElse("parser did not provide failure details")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        return Finding.builder()
                .ruleId(PARSE_DIAGNOSTIC_ID)
                .severity(config.strictParsing() ? Severity.ERROR : Severity.WARNING)
                .file(unit.file().relativePath())
                .line(1)
                .column(1)
                .what("Java source could not be parsed; only conservative fallback checks ran. " + detail)
                .why("Structural Flower rules require an AST, so this file was not analyzed completely.")
                .fix("Fix the source syntax or parser compatibility; use strictParsing in CI to fail closed.")
                .build();
    }
}
