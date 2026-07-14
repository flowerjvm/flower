package io.github.flowerjvm.flower.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventBusTest {

    static final class Foo {
        final int v;
        Foo(int v) { this.v = v; }
    }

    static final class Bar { }

    @Test
    void delivers_events_to_matching_type_only() {
        InMemoryEventBus bus = InMemoryEventBus.create();
        List<Foo> received = new ArrayList<>();
        bus.subscribe(Foo.class, received::add);

        bus.publish(new Foo(1));
        bus.publish(new Bar());
        bus.publish(new Foo(2));

        assertThat(received).hasSize(2);
        assertThat(received).extracting(f -> f.v).containsExactly(1, 2);
    }

    @Test
    void unsubscribe_removes_handler() {
        InMemoryEventBus bus = InMemoryEventBus.create();
        AtomicInteger count = new AtomicInteger();
        Subscription sub = bus.subscribe(Foo.class, e -> count.incrementAndGet());

        bus.publish(new Foo(0));
        sub.unsubscribe();
        bus.publish(new Foo(0));

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void listener_exception_is_isolated() {
        InMemoryEventBus bus = InMemoryEventBus.create();
        List<Throwable> errors = new ArrayList<>();
        bus.onListenerError((event, handler, cause) -> errors.add(cause));

        bus.subscribe(Foo.class, e -> { throw new RuntimeException("boom"); });
        AtomicInteger reached = new AtomicInteger();
        bus.subscribe(Foo.class, e -> reached.incrementAndGet());

        bus.publish(new Foo(0));

        assertThat(reached.get()).isEqualTo(1);
        assertThat(errors).hasSize(1);
    }

    @Test
    void unsubscribe_twice_is_safe() {
        InMemoryEventBus bus = InMemoryEventBus.create();
        Subscription sub = bus.subscribe(Foo.class, e -> {});
        sub.unsubscribe();
        sub.unsubscribe();
    }
}
