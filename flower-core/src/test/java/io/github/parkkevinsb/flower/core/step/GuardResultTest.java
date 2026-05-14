package io.github.parkkevinsb.flower.core.step;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardResultTest {

    @Test
    void factories_create_expected_types() {
        assertThat(GuardResult.pass().type()).isEqualTo(GuardResult.Type.PASS);
        assertThat(GuardResult.hold().type()).isEqualTo(GuardResult.Type.HOLD);
        assertThat(GuardResult.goTo("cleanup").type()).isEqualTo(GuardResult.Type.GOTO);
        assertThat(GuardResult.goTo("cleanup").targetStepId()).isEqualTo("cleanup");

        RuntimeException cause = new RuntimeException("boom");
        GuardResult fail = GuardResult.fail(cause);
        assertThat(fail.type()).isEqualTo(GuardResult.Type.FAIL);
        assertThat(fail.cause()).isSameAs(cause);
    }

    @Test
    void goTo_rejects_empty_target() {
        assertThatThrownBy(() -> GuardResult.goTo(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuardResult.goTo(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fail_rejects_null_cause() {
        assertThatThrownBy(() -> GuardResult.fail(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
