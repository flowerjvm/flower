package io.github.flowerjvm.flower.spring;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.engine.EngineBuilder;
import io.github.flowerjvm.flower.core.event.EventBus;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.time.Clock;
import io.github.flowerjvm.flower.core.time.SystemClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring Boot auto-configuration for Flower.
 *
 * <p>Registers an {@link Engine} bean built from {@link FlowerProperties} and a
 * {@link FlowerEngineLifecycle} that ties {@code Engine.start/stop} to the
 * Spring application context.
 *
 * <p>Each bean is conditional on the absence of a user-provided override:
 * <ul>
 *   <li>Provide your own {@link Engine} to bypass the entire builder path.</li>
 *   <li>Provide an {@link EventBus} (e.g. a Bloom-backed one) and the auto
 *       Engine will pick it up.</li>
 *   <li>Provide a {@link Clock} (such as a manual clock for tests).</li>
 *   <li>Provide a {@link FlowCheckpointStore} for durable Flow checkpoints, or
 *       set {@code flower.persistence.type=jdbc} with the JDBC module present.</li>
 *   <li>{@link FlowerListener} beans declared in the context are collected
 *       and attached to the Engine.</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(Engine.class)
@ConditionalOnProperty(prefix = "flower", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FlowerProperties.class)
public class FlowerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock flowerClock() {
        return SystemClock.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public EventBus flowerEventBus() {
        return InMemoryEventBus.create();
    }

    @Bean
    @ConditionalOnMissingBean
    public Engine flowerEngine(
            FlowerProperties properties,
            Clock clock,
            EventBus eventBus,
            ObjectProvider<FlowCheckpointStore> checkpointStore,
            ObjectProvider<FlowerListener> listeners
    ) {
        FlowCheckpointStore store = checkpointStore.getIfAvailable();
        if (store == null && properties.getPersistence().getType() == FlowerProperties.PersistenceType.JDBC) {
            throw new IllegalStateException(
                    "flower.persistence.type=jdbc requires a FlowCheckpointStore bean. "
                            + "Add flower-persistence-jdbc with a DataSource, or provide your own FlowCheckpointStore.");
        }

        EngineBuilder builder = Engine.builder()
                .clock(clock)
                .eventBus(eventBus)
                .checkpointStore(store != null ? store : FlowCheckpointStore.NOOP);

        listeners.orderedStream().forEach(builder::listener);

        List<FlowerProperties.Worker> configured = properties.getWorkers();
        if (configured == null || configured.isEmpty()) {
            builder.worker(Worker.builder("main").intervalMillis(100L).build());
        } else {
            Set<String> seen = new HashSet<>();
            for (FlowerProperties.Worker w : configured) {
                String name = w.getName();
                if (name == null || name.isEmpty()) {
                    throw new IllegalStateException(
                            "flower.workers[*].name must not be empty");
                }
                if (!seen.add(name)) {
                    throw new IllegalStateException(
                            "duplicate flower.workers[*].name: " + name);
                }
                builder.worker(Worker.builder(name)
                        .intervalMillis(w.getIntervalMs())
                        .build());
            }
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowerEngineLifecycle flowerEngineLifecycle(Engine engine, FlowerProperties properties) {
        return new FlowerEngineLifecycle(engine, properties.isAutoStart(), properties.getPhase());
    }
}
