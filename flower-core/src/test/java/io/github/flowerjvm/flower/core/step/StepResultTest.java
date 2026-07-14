package io.github.flowerjvm.flower.core.step;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepResultTest {

    @Test
    void factory_methods_set_correct_type() {
        assertThat(StepResult.stay().type()).isEqualTo(StepResult.Type.STAY);
        assertThat(StepResult.done().type()).isEqualTo(StepResult.Type.DONE);
        assertThat(StepResult.repeat().type()).isEqualTo(StepResult.Type.REPEAT);
        assertThat(StepResult.finish().type()).isEqualTo(StepResult.Type.FINISH);
    }

    @Test
    void goTo_carries_target_and_default_mode() {
        StepResult r = StepResult.goTo("cleanup");
        assertThat(r.type()).isEqualTo(StepResult.Type.GOTO);
        assertThat(r.targetStepId()).isEqualTo("cleanup");
        assertThat(r.goToMode()).isEqualTo(GoToMode.COMPLETE_CURRENT);
    }

    @Test
    void goTo_rejects_empty_id() {
        assertThatThrownBy(() -> StepResult.goTo("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StepResult.goTo(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fail_carries_cause() {
        Throwable cause = new RuntimeException("boom");
        StepResult r = StepResult.fail(cause);
        assertThat(r.type()).isEqualTo(StepResult.Type.FAIL);
        assertThat(r.cause()).isSameAs(cause);
    }

    @Test
    void fail_rejects_null_cause() {
        assertThatThrownBy(() -> StepResult.fail(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
