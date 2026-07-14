package io.github.flowerjvm.flower.core.time;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualClockTest {

    @Test
    void starts_at_zero_by_default() {
        ManualClock clock = new ManualClock();
        assertThat(clock.currentTimeMillis()).isZero();
    }

    @Test
    void advance_moves_forward() {
        ManualClock clock = new ManualClock(100);
        clock.advance(50);
        assertThat(clock.currentTimeMillis()).isEqualTo(150);
    }

    @Test
    void advance_with_negative_throws() {
        ManualClock clock = new ManualClock();
        assertThatThrownBy(() -> clock.advance(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
