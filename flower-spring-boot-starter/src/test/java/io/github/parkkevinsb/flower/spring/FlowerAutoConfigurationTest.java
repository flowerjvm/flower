package io.github.parkkevinsb.flower.spring;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.engine.EngineState;
import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpoint;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpointStore;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import io.github.parkkevinsb.flower.persistence.jdbc.JdbcFlowCheckpointStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FlowerJdbcPersistenceAutoConfiguration.class,
                    FlowerAutoConfiguration.class));

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
    void usesNoopCheckpointStoreByDefault() {
        runner.run(ctx -> assertThat(ctx.getBean(Engine.class).checkpointStore())
                .isSameAs(FlowCheckpointStore.NOOP));
    }

    @Test
    void usesUserProvidedCheckpointStore() {
        FlowCheckpointStore store = new FlowCheckpointStore() {
            @Override
            public void save(FlowCheckpoint checkpoint) {
            }

            @Override
            public void delete(io.github.parkkevinsb.flower.core.flow.FlowId flowId) {
            }
        };

        runner.withBean("customCheckpointStore", FlowCheckpointStore.class, () -> store)
                .run(ctx -> assertThat(ctx.getBean(Engine.class).checkpointStore()).isSameAs(store));
    }

    @Test
    void jdbcPersistenceCreatesCheckpointStore() {
        runner.withPropertyValues(
                "flower.persistence.type=jdbc",
                "flower.persistence.jdbc.dialect=h2"
        ).withBean(DataSource.class, StubDataSource::new)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(FlowCheckpointStore.class);
                    assertThat(ctx.getBean(FlowCheckpointStore.class))
                            .isInstanceOf(JdbcFlowCheckpointStore.class);
                    assertThat(ctx.getBean(Engine.class).checkpointStore())
                            .isSameAs(ctx.getBean(FlowCheckpointStore.class));
                });
    }

    @Test
    void jdbcPersistenceRequiresDataSource() {
        runner.withPropertyValues(
                "flower.persistence.type=jdbc",
                "flower.persistence.jdbc.dialect=h2"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void jdbcPersistenceRequiresDialect() {
        runner.withPropertyValues("flower.persistence.type=jdbc")
                .withBean(DataSource.class, StubDataSource::new)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void customCheckpointStoreOverridesJdbcAutoConfiguration() {
        FlowCheckpointStore store = new FlowCheckpointStore() {
            @Override
            public void save(FlowCheckpoint checkpoint) {
            }

            @Override
            public void delete(io.github.parkkevinsb.flower.core.flow.FlowId flowId) {
            }
        };

        runner.withPropertyValues("flower.persistence.type=jdbc")
                .withBean("customCheckpointStore", FlowCheckpointStore.class, () -> store)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(FlowCheckpointStore.class);
                    assertThat(ctx.getBean(Engine.class).checkpointStore()).isSameAs(store);
                });
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

    private static final class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("test DataSource does not open connections");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("test DataSource does not open connections");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
