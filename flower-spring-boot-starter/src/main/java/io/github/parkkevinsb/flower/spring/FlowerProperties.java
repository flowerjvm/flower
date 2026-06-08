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
     * Optional admin endpoints. Disabled by default because dump output can
     * expose flow keys, execution context, and operational state.
     */
    private Admin admin = new Admin();

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

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin != null ? admin : new Admin();
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
     * Admin/ops endpoint configuration.
     */
    public static class Admin {

        /** Engine dump endpoint configuration. */
        private Dump dump = new Dump();

        /** Built-in read-only console page configuration. */
        private Console console = new Console();

        public Dump getDump() {
            return dump;
        }

        public void setDump(Dump dump) {
            this.dump = dump != null ? dump : new Dump();
        }

        public Console getConsole() {
            return console;
        }

        public void setConsole(Console console) {
            this.console = console != null ? console : new Console();
        }
    }

    /**
     * Read-only Engine dump endpoint configuration.
     */
    public static class Dump {

        /**
         * Enables the dump endpoint when this application is a Spring MVC web
         * application. Default false.
         */
        private boolean enabled = false;

        /**
         * Request path for the dump endpoint.
         */
        private String path = "/internal/flower/dump";

        /**
         * Pretty-print JSON by default. A request may override this with the
         * {@code pretty} query parameter.
         */
        private boolean pretty = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path != null && !path.isEmpty()
                    ? path
                    : "/internal/flower/dump";
        }

        public boolean isPretty() {
            return pretty;
        }

        public void setPretty(boolean pretty) {
            this.pretty = pretty;
        }
    }

    /**
     * Built-in read-only Flower console page configuration.
     */
    public static class Console {

        /**
         * Enables the console page when this application is a Spring MVC web
         * application. Default false.
         */
        private boolean enabled = false;

        /**
         * Request path for the HTML console page.
         */
        private String path = "/internal/flower/console";

        /**
         * Same-origin JSON endpoint used by the console page.
         */
        private String apiPath = "/internal/flower/console/dump";

        /**
         * Initial polling interval shown in the console UI.
         */
        private long pollIntervalMs = 3000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path != null && !path.isEmpty()
                    ? path
                    : "/internal/flower/console";
        }

        public String getApiPath() {
            return apiPath;
        }

        public void setApiPath(String apiPath) {
            this.apiPath = apiPath != null && !apiPath.isEmpty()
                    ? apiPath
                    : "/internal/flower/console/dump";
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs > 0L ? pollIntervalMs : 3000L;
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
