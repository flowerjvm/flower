package io.github.parkkevinsb.flower.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for {@code flower.*}.
 *
 * <p>Drives the auto-configured {@code Engine} and its lifecycle binding.
 * Users that need full control should provide their own {@code Engine} bean
 * instead — the auto-configuration backs off when one is present.
 *
 * <pre>
 * flower:
 *   enabled: true
 *   auto-start: true
 *   phase: 0
 *   persistence:
 *     type: none
 *   workers:
 *     - name: main
 *       interval-ms: 100
 *     - name: alerts
 *       interval-ms: 250
 * </pre>
 */
@ConfigurationProperties(prefix = "flower")
public class FlowerProperties {

    /** Master switch. When false, the auto-configuration registers nothing. */
    private boolean enabled = true;

    /**
     * If true (default), the SmartLifecycle bean starts the Engine when the
     * Spring context becomes ready and stops it on context close. When false,
     * the application is responsible for calling {@code Engine.start()}.
     */
    private boolean autoStart = true;

    /**
     * SmartLifecycle phase for the Engine lifecycle bean. Higher values start
     * later and stop earlier. Default 0 mirrors most user beans; raise it if
     * the Engine should outlive other lifecycle-managed components on shutdown.
     */
    private int phase = 0;

    /**
     * Checkpoint persistence settings. Defaults to no durable store unless a
     * {@code FlowCheckpointStore} bean is provided by the application.
     */
    private Persistence persistence = new Persistence();

    /**
     * Workers to register. If empty, a single Worker named {@code "main"} with
     * a 100 ms tick interval is created.
     */
    private List<Worker> workers = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public void setPersistence(Persistence persistence) {
        this.persistence = persistence != null ? persistence : new Persistence();
    }

    public List<Worker> getWorkers() {
        return workers;
    }

    public void setWorkers(List<Worker> workers) {
        this.workers = workers != null ? workers : new ArrayList<>();
    }

    public enum PersistenceType {
        NONE,
        JDBC
    }

    public enum JdbcDialect {
        POSTGRESQL,
        MYSQL,
        ORACLE,
        H2
    }

    public enum SchemaInitialization {
        NEVER
    }

    /**
     * Persistence configuration block.
     */
    public static class Persistence {

        /**
         * Persistence backend. {@code NONE} keeps Flower's default in-memory
         * behavior unless a custom {@code FlowCheckpointStore} bean exists.
         */
        private PersistenceType type = PersistenceType.NONE;

        /** JDBC-specific settings used when {@code type=jdbc}. */
        private Jdbc jdbc = new Jdbc();

        public PersistenceType getType() {
            return type;
        }

        public void setType(PersistenceType type) {
            this.type = type != null ? type : PersistenceType.NONE;
        }

        public Jdbc getJdbc() {
            return jdbc;
        }

        public void setJdbc(Jdbc jdbc) {
            this.jdbc = jdbc != null ? jdbc : new Jdbc();
        }
    }

    /**
     * JDBC checkpoint store configuration.
     */
    public static class Jdbc {

        /**
         * SQL dialect for the standard Flower checkpoint table. Required when
         * {@code flower.persistence.type=jdbc}.
         */
        private JdbcDialect dialect;

        /**
         * Reserved for future schema initialization support. For now Flower
         * never creates tables automatically.
         */
        private SchemaInitialization initializeSchema = SchemaInitialization.NEVER;

        public JdbcDialect getDialect() {
            return dialect;
        }

        public void setDialect(JdbcDialect dialect) {
            this.dialect = dialect;
        }

        public SchemaInitialization getInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(SchemaInitialization initializeSchema) {
            this.initializeSchema = initializeSchema != null
                    ? initializeSchema
                    : SchemaInitialization.NEVER;
        }
    }

    /**
     * Per-worker configuration block. Mirrors {@code Worker.builder(name).intervalMillis(...)}.
     */
    public static class Worker {

        /** Worker name. Must be unique within an Engine. */
        private String name;

        /** Tick interval in milliseconds. Must be {@code > 0}. */
        private long intervalMs = 100L;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }
}
