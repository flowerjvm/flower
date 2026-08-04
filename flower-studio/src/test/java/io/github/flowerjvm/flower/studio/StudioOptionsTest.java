package io.github.flowerjvm.flower.studio;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudioOptionsTest {

    @Test
    void command_line_values_override_environment_and_can_disable_artifacts() {
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("FLOWER_STUDIO_HOST", "0.0.0.0");
        environment.put("FLOWER_STUDIO_PORT", "9000");
        environment.put("FLOWER_STUDIO_MAX_EVENTS", "500");

        StudioOptions options = StudioOptions.parse(new String[] {
                "--host=127.0.0.1",
                "--port=0",
                "--trace-file=demo.jsonl",
                "--artifact-root=none",
                "--max-events=25",
                "--evaluation-file=none",
                "--feedback-file=none",
                "--max-evaluations=50"
        }, environment);

        assertThat(options.host()).isEqualTo("127.0.0.1");
        assertThat(options.port()).isZero();
        assertThat(options.traceFile().getFileName().toString()).isEqualTo("demo.jsonl");
        assertThat(options.artifactRoot()).isNull();
        assertThat(options.maxEvents()).isEqualTo(25);
        assertThat(options.evaluationFile()).isNull();
        assertThat(options.feedbackFile()).isNull();
        assertThat(options.maxEvaluations()).isEqualTo(50);
    }

    @Test
    void rejects_unknown_and_invalid_options() {
        assertThatThrownBy(() -> StudioOptions.parse(
                new String[] {"--unknown=value"},
                Collections.<String, String>emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown option");
        assertThatThrownBy(() -> StudioOptions.parse(
                new String[] {"--port=70000"},
                Collections.<String, String>emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 65535");
        assertThatThrownBy(() -> StudioOptions.parse(
                new String[] {"--max-events=0"},
                Collections.<String, String>emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> StudioOptions.parse(
                new String[] {"--max-evaluations=0"},
                Collections.<String, String>emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> StudioOptions.parse(
                new String[] {"--evaluation-file=none"},
                Collections.<String, String>emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enabled or disabled together");
    }
}
