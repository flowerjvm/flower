package io.github.flowerjvm.flower.core.flow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowIdTest {

    @Test
    void equality_uses_type_and_key() {
        assertThat(FlowId.of("x", "1")).isEqualTo(FlowId.of("x", "1"));
        assertThat(FlowId.of("x", "1")).isNotEqualTo(FlowId.of("x", "2"));
        assertThat(FlowId.of("x", "1")).isNotEqualTo(FlowId.of("y", "1"));
    }

    @Test
    void rejects_empty_components() {
        assertThatThrownBy(() -> FlowId.of("", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlowId.of("x", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlowId.of(null, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_is_human_readable() {
        assertThat(FlowId.of("quay-work", "WO-42").toString()).isEqualTo("quay-work/WO-42");
    }
}
