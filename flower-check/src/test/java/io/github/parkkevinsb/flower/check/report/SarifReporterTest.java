package io.github.parkkevinsb.flower.check.report;

import io.github.parkkevinsb.flower.check.finding.Finding;
import io.github.parkkevinsb.flower.check.rule.Severity;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class SarifReporterTest {

    @Test
    void rendersActiveAndBaselineFindingsAsSarifResults() {
        Finding active = finding("FLOWER-CHECK-001", Severity.ERROR, "src/WaitStep.java", 4);
        Finding accepted = finding("FLOWER-CHECK-004", Severity.WARNING, "src/WaitStep.java", 8);

        StringBuilder out = new StringBuilder();
        new SarifReporter().report(Collections.singletonList(active),
                Collections.singletonList(accepted),
                out);

        String sarif = out.toString();
        assertThat(sarif).contains("\"version\": \"2.1.0\"");
        assertThat(sarif).contains("\"ruleId\": \"FLOWER-CHECK-001\"");
        assertThat(sarif).contains("\"level\": \"error\"");
        assertThat(sarif).contains("\"baselineState\": \"new\"");
        assertThat(sarif).contains("\"ruleId\": \"FLOWER-CHECK-004\"");
        assertThat(sarif).contains("\"level\": \"warning\"");
        assertThat(sarif).contains("\"baselineState\": \"unchanged\"");
        assertThat(sarif).contains("\"uri\": \"src/WaitStep.java\"");
        assertThat(sarif).contains("\"startLine\": 4");
    }

    @Test
    void escapesJsonMessageText() {
        Finding finding = Finding.builder()
                .ruleId("FLOWER-CHECK-001")
                .severity(Severity.ERROR)
                .file("src/QuoteStep.java")
                .line(1)
                .column(2)
                .what("quoted \"text\"")
                .why("line\nbreak")
                .fix("use \\ escape")
                .build();

        StringBuilder out = new StringBuilder();
        new SarifReporter().report(Collections.singletonList(finding), out);

        assertThat(out.toString()).contains("quoted \\\"text\\\"");
        assertThat(out.toString()).contains("line\\nbreak");
        assertThat(out.toString()).contains("use \\\\ escape");
    }

    private static Finding finding(String ruleId, Severity severity, String file, int line) {
        return Finding.builder()
                .ruleId(ruleId)
                .severity(severity)
                .file(file)
                .line(line)
                .column(1)
                .what("what")
                .why("why")
                .fix("fix")
                .build();
    }
}
