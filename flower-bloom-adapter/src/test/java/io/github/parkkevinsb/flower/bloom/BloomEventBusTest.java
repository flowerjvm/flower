package io.github.parkkevinsb.flower.bloom;

import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.EventHandler;
import io.github.parkkevinsb.flower.core.event.Subscription;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BloomEventBusTest {

    static final class Ping {
        final String value;
        Ping(String value) { this.value = value; }
    }

    static final class Pong { }

    @Test
    void wrapRejectsNullDelegate() {
        assertThatThrownBy(() -> BloomEventBus.wrap(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subscribeRejectsNullArguments() {
        EventBus bus = BloomEventBus.wrap(LocalEventBus.create());
        assertThatThrownBy(() -> bus.subscribe(null, e -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bus.subscribe(Ping.class, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishRejectsNull() {
        EventBus bus = BloomEventBus.wrap(LocalEventBus.create());
        assertThatThrownBy(() -> bus.publish(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishDispatchesToSubscribedHandler() {
        EventBus bus = BloomEventBus.wrap(LocalEventBus.create());
        List<String> received = new ArrayList<>();

        bus.subscribe(Ping.class, e -> received.add(e.value));

        bus.publish(new Ping("a"));
        bus.publish(new Ping("b"));

        assertThat(received).containsExactly("a", "b");
    }

    @Test
    void unsubscribeStopsDelivery() {
        EventBus bus = BloomEventBus.wrap(LocalEventBus.create());
        List<String> received = new ArrayList<>();

        Subscription sub = bus.subscribe(Ping.class, e -> received.add(e.value));
        bus.publish(new Ping("first"));
        sub.unsubscribe();
        bus.publish(new Ping("second"));

        assertThat(received).containsExactly("first");
    }

    @Test
    void unsubscribeIsIdempotent() {
        EventBus bus = BloomEventBus.wrap(LocalEventBus.create());
        Subscription sub = bus.subscribe(Ping.class, e -> {});

        sub.unsubscribe();
        sub.unsubscribe(); // must not throw
    }

    @Test
    void typeMatchingIsExact() {
        EventBus bus = BloomEventBus.wrap(LocalEventBus.create());
        List<Object> received = new ArrayList<>();

        bus.subscribe(Ping.class, received::add);

        bus.publish(new Pong());
        bus.publish(new Ping("only-this"));

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isInstanceOf(Ping.class);
    }

    @Test
    void delegateAccessorReturnsTheWrappedBus() {
        io.github.parkkevinsb.bloom.EventBus inner = LocalEventBus.create();
        BloomEventBus adapter = BloomEventBus.wrap(inner);
        assertThat(adapter.delegate()).isSameAs(inner);
    }

    @Test
    void publishForwardsToDelegateExactlyOnce() {
        RecordingDelegate delegate = new RecordingDelegate();
        EventBus bus = BloomEventBus.wrap(delegate);

        Ping event = new Ping("x");
        bus.publish(event);

        assertThat(delegate.publishedEvents).containsExactly(event);
    }

    @Test
    void subscribeForwardsToDelegateAndAdaptsHandler() {
        RecordingDelegate delegate = new RecordingDelegate();
        EventBus bus = BloomEventBus.wrap(delegate);
        List<Ping> received = new ArrayList<>();
        EventHandler<Ping> flowerHandler = received::add;

        bus.subscribe(Ping.class, flowerHandler);

        assertThat(delegate.subscribedTypes).containsExactly(Ping.class);
        assertThat(delegate.lastBloomHandler).isNotNull();

        Ping event = new Ping("y");
        @SuppressWarnings({"unchecked", "rawtypes"})
        io.github.parkkevinsb.bloom.EventHandler bloomHandler =
                (io.github.parkkevinsb.bloom.EventHandler) delegate.lastBloomHandler;
        bloomHandler.handle(event);

        assertThat(received).containsExactly(event);
    }

    private static final class RecordingDelegate implements io.github.parkkevinsb.bloom.EventBus {
        final List<Class<?>> subscribedTypes = new ArrayList<>();
        final List<Object> publishedEvents = new ArrayList<>();
        io.github.parkkevinsb.bloom.EventHandler<?> lastBloomHandler;

        @Override
        public <E> io.github.parkkevinsb.bloom.Subscription subscribe(
                Class<E> eventType, io.github.parkkevinsb.bloom.EventHandler<E> handler) {
            subscribedTypes.add(eventType);
            lastBloomHandler = handler;
            return new io.github.parkkevinsb.bloom.Subscription() {
                private boolean active = true;
                @Override public void close() { active = false; }
                @Override public boolean isActive() { return active; }
            };
        }

        @Override
        public void publish(Object event) {
            publishedEvents.add(event);
        }
    }
}
