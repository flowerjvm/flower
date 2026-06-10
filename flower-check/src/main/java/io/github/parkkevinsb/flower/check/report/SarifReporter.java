package io.github.parkkevinsb.flower.check.report;

import io.github.parkkevinsb.flower.check.finding.Finding;
import io.github.parkkevinsb.flower.check.rule.Severity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SARIF 2.1.0 reporter for CI annotations.
 */
public final class SarifReporter implements Reporter {

    @Override
    public void report(List<Finding> findings, Appendable out) {
        report(findings, java.util.Collections.<Finding>emptyList(), out);
    }

    @Override
    public void report(List<Finding> findings, List<Finding> acceptedFindings, Appendable out) {
        try {
            List<Finding> all = new ArrayList<>();
            all.addAll(findings);
            all.addAll(acceptedFindings);

            out.append("{\n");
            field(out, 1, "version", "2.1.0", true);
            field(out, 1, "$schema", "https://json.schemastore.org/sarif-2.1.0.json", true);
            indent(out, 1).append("\"runs\": [\n");
            indent(out, 2).append("{\n");
            renderTool(out, all);
            indent(out, 3).append("\"results\": [\n");
            renderResults(out, findings, acceptedFindings);
            indent(out, 3).append("]\n");
            indent(out, 2).append("}\n");
            indent(out, 1).append("]\n");
            out.append("}\n");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write SARIF report", e);
        }
    }

    private static void renderTool(Appendable out, List<Finding> findings) throws IOException {
        indent(out, 3).append("\"tool\": {\n");
        indent(out, 4).append("\"driver\": {\n");
        field(out, 5, "name", "flower-check", true);
        field(out, 5, "informationUri", "https://github.com/parkKevinSB/flower", true);
        indent(out, 5).append("\"rules\": [\n");

        List<String> ruleIds = uniqueRuleIds(findings);
        for (int i = 0; i < ruleIds.size(); i++) {
            String ruleId = ruleIds.get(i);
            indent(out, 6).append("{\n");
            field(out, 7, "id", ruleId, true);
            field(out, 7, "name", ruleId, true);
            indent(out, 7).append("\"shortDescription\": {\"text\": ")
                    .append(json(ruleId)).append("}\n");
            indent(out, 6).append("}");
            out.append(i + 1 == ruleIds.size() ? "\n" : ",\n");
        }

        indent(out, 5).append("]\n");
        indent(out, 4).append("}\n");
        indent(out, 3).append("},\n");
    }

    private static void renderResults(Appendable out,
                                      List<Finding> findings,
                                      List<Finding> acceptedFindings) throws IOException {
        int total = findings.size() + acceptedFindings.size();
        int index = 0;
        for (Finding finding : findings) {
            renderResult(out, finding, "new", ++index == total);
        }
        for (Finding finding : acceptedFindings) {
            renderResult(out, finding, "unchanged", ++index == total);
        }
    }

    private static void renderResult(Appendable out,
                                     Finding finding,
                                     String baselineState,
                                     boolean last) throws IOException {
        indent(out, 4).append("{\n");
        field(out, 5, "ruleId", finding.ruleId(), true);
        field(out, 5, "level", level(finding.severity()), true);
        field(out, 5, "baselineState", baselineState, true);
        indent(out, 5).append("\"message\": {\"text\": ")
                .append(json(message(finding))).append("},\n");
        indent(out, 5).append("\"locations\": [\n");
        indent(out, 6).append("{\"physicalLocation\": {\"artifactLocation\": {\"uri\": ")
                .append(json(finding.file()))
                .append("}, \"region\": {\"startLine\": ")
                .append(Integer.toString(finding.line()))
                .append(", \"startColumn\": ")
                .append(Integer.toString(finding.column() <= 0 ? 1 : finding.column()))
                .append("}}}\n");
        indent(out, 5).append("]\n");
        indent(out, 4).append("}");
        out.append(last ? "\n" : ",\n");
    }

    private static List<String> uniqueRuleIds(List<Finding> findings) {
        Set<String> ids = new LinkedHashSet<>();
        for (Finding finding : findings) {
            ids.add(finding.ruleId());
        }
        return new ArrayList<>(ids);
    }

    private static String message(Finding finding) {
        return "what: " + finding.what()
                + "\nwhy: " + finding.why()
                + "\nfix: " + finding.fix();
    }

    private static String level(Severity severity) {
        if (severity == Severity.ERROR) {
            return "error";
        }
        if (severity == Severity.WARNING) {
            return "warning";
        }
        return "note";
    }

    private static void field(Appendable out, int depth, String name, String value, boolean comma)
            throws IOException {
        indent(out, depth)
                .append(json(name))
                .append(": ")
                .append(json(value))
                .append(comma ? ",\n" : "\n");
    }

    private static Appendable indent(Appendable out, int depth) throws IOException {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        return out;
    }

    private static String json(String value) {
        StringBuilder b = new StringBuilder(value.length() + 2);
        b.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        b.append("\\u");
                        for (int j = hex.length(); j < 4; j++) {
                            b.append('0');
                        }
                        b.append(hex);
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
        return b.toString();
    }
}
