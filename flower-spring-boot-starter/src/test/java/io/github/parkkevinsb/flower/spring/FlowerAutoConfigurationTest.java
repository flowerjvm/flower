package io.github.parkkevinsb.flower.spring;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.engine.EngineState;
import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlowerAutoConfiguration.class));

    @Test
    void registersEngineWithDefaultWorkerWhenNoPropertiesGiven() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(Engine.class);
            Engine engine = ctx.getBean(Engine.class);
            assertThat(engine.workers()).hasSize(1);
            assertThat(engine.worker("main")).isNotNull();
            assertThat(engine.worker("main").intervalMillis()).isEqualTo(100L);
        });
    }

    @Test
    void registersWorkersFromProperties() {
        runner.withPropertyValues(
                "flower.workers[0].name=foo",
                "flower.workers[0].interval-ms=250",
                "flower.workers[1].name=bar",
                "flower.workers[1].interval-ms=500"
        ).run(ctx -> {
            Engine engine = ctx.getBean(Engine.class);
            assertThat(engine.workers()).hasSize(2);
            assertThat(engine.worker("foo").intervalMillis()).isEqualTo(250L);
            assertThat(engine.worker("bar").intervalMillis()).isEqualTo(500L);
        });
    }

    @Test
    void rejectsDuplicateWorkerNames() {
        runner.withPropertyValues(
                "flower.workers[0].name=dup",
                "flower.workers[1].name=dup"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void backsOffWhenUserProvidesEngine() {
        Engine custom = Engine.builder()
                .worker(Worker.builder("custom").build())
                .build();
        runner.withBean("customEngine", Engine.class, () -> custom)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Engine.class);
                    assertThat(ctx.getBean(Engine.class)).isSameAs(custom);
                });
    }

    @Test
    void usesUserProvidedEventBus() {
        EventBus bus = InMemoryEventBus.create();
        runner.withBean("customBus", EventBus.class, () -> bus)
                .run(ctx -> assertThat(ctx.getBean(Engine.class).eventBus()).isSameAs(bus));
    }

    @Test
    void usesUserProvidedClock() {
        ManualClock clock = new ManualClock(0L);
        runner.withBean("customClock", Clock.class, () -> clock)
                .run(ctx -> assertThat(ctx.getBean(Engine.class).clock()).isSameAs(clock));
    }

    @Test
    void collectsListenerBeans() {
        FlowerListener a = new FlowerListener() { };
        FlowerListener b = new FlowerListener() { };
        runner.withBean("a", FlowerListener.class, () -> a)
                .withBean("b", FlowerListener.class, () -> b)
                .run(ctx -> assertThat(ctx.getBean(Engine.class).listeners())
                        .containsExactlyInAnyOrder(a, b));
    }

    @Test
    void disabledByProperty() {
        runner.withPropertyValues("flower.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(Engine.class));
    }

    @Test
    void smartLifecycleStartsEngineOnContextRefresh() {
        runner.run(ctx -> {
            Engine engine = ctx.getBean(Engine.class);
            assertThat(engine.state()).isEqualTo(EngineState.RUNNING);
        });
    }

    @Test
    void autoStartFalseLeavesEngineCreated() {
        runner.withPropertyValues("flower.auto-start=false").run(ctx -> {
            Engine engine = ctx.getBean(Engine.class);
            assertThat(engine.state()).isEqualTo(EngineState.CREATED);
        });
    }
}
