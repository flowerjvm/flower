package io.github.parkkevinsb.flower.check.rule;

import io.github.parkkevinsb.flower.check.finding.Finding;
import io.github.parkkevinsb.flower.check.model.AnalysisFact;
import io.github.parkkevinsb.flower.check.parse.SourceUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for rules backed by parser-neutral {@link AnalysisFact}s.
 */
public abstract class AbstractFactRule extends AbstractRule {

    private final String factKind;

    protected AbstractFactRule(String id, Severity defaultSeverity, String title, String factKind) {
        super(id, defaultSeverity, title);
        this.factKind = factKind;
    }

    @Override
    public final List<Finding> apply(SourceUnit unit, RuleContext context) {
        List<Finding> findings = new ArrayList<>();
        for (AnalysisFact fact : context.projectModel().facts(factKind)) {
            if (!unit.file().relativePath().equals(fact.file())) {
                continue;
            }
            findings.add(finding(context, fact.file(), fact.line())
                    .column(fact.column())
                    .what(what(fact))
                    .why(why(fact))
                    .fix(fix(fact))
                    .build());
        }
        return findings;
    }

    protected abstract String what(AnalysisFact fact);

    protected abstract String why(AnalysisFact fact);

    protected abstract String fix(AnalysisFact fact);
}
