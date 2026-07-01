# Persistence

Flower core exposes `FlowCheckpointStore` as the storage boundary. The default
store is no-op, so transient flows are unaffected unless the host application
explicitly enables a store.

Core does not create database tables. JDBC, Redis, JPA, or file-backed stores
should live in optional modules or in the host application, and schema
initialization should be explicit and opt-in.

## Flow Checkpoints

`flower-persistence-jdbc` provides a JDBC `FlowCheckpointStore`.

```java
FlowCheckpointStore store = JdbcFlowCheckpointStore.create(
        dataSource,
        JdbcCheckpointDialects.postgresql());

Engine engine = Engine.builder()
        .eventBus(InMemoryEventBus.create())
        .worker(Worker.builder("orders").build())
        .checkpointStore(store)
        .build();
```

Schema SQL is packaged under:

```text
flower/persistence/jdbc/postgresql/schema.sql
flower/persistence/jdbc/mysql/schema.sql
flower/persistence/jdbc/oracle/schema.sql
flower/persistence/jdbc/h2/schema.sql
```

Apply the SQL yourself, or copy it into Flyway/Liquibase. The JDBC store does
not create tables automatically.

## Event-Loop Checkpoints

`flower-eventloop-persistence-jdbc` provides a separate JDBC implementation for
event-loop checkpoints.

```java
EventFlowCheckpointStore eventStore = JdbcEventFlowCheckpointStore.create(
        dataSource,
        JdbcEventFlowCheckpointDialects.postgresql());

EventWorker worker = EventWorker.builder("agents")
        .clock(SystemClock.INSTANCE)
        .eventBus(InMemoryEventBus.create())
        .checkpointStore(eventStore)
        .build();
```

Event-loop checkpoint schema SQL is packaged separately under:

```text
flower/eventloop/persistence/jdbc/postgresql/schema.sql
flower/eventloop/persistence/jdbc/mysql/schema.sql
flower/eventloop/persistence/jdbc/oracle/schema.sql
flower/eventloop/persistence/jdbc/h2/schema.sql
```

## Execution Context Columns

The standard checkpoint schemas include nullable execution-context columns:

```text
tenant_id
user_id
session_id
run_id
trace_id
correlation_id
```

If you already created the checkpoint table from an older schema, add those
nullable columns before upgrading the JDBC store. Migration SQL is packaged
next to each dialect schema:

```text
flower/persistence/jdbc/postgresql/execution-context-migration.sql
flower/persistence/jdbc/mysql/execution-context-migration.sql
flower/persistence/jdbc/oracle/execution-context-migration.sql
flower/persistence/jdbc/h2/execution-context-migration.sql
```

## Recovery Boundary

Durable checkpoints keep the `ExecutionContext` with the saved flow position.
After recovery, the same logical run keeps the same `runId`, `traceId`, tenant,
and user identifiers. Flower does not regenerate a new run id during recovery.

Flower's durable mode is checkpoint/resume, not durable execution replay.
External writes and API calls should be idempotent because recovery may rebuild
a fresh Flow and re-enter a Step according to that Step's `RecoveryPolicy`.

Core `StepContext.startTimeout(...)` is runtime-only and is not stored in
durable checkpoints. Durable Flows reject it. Store long-lived deadlines in
domain state, or use event-loop await deadlines when the deadline itself must
survive restart.

## Operational Boundaries

Flower's in-memory Flow ownership is scoped to one JVM and one `Engine`.
`FlowRecoveryService` reads active checkpoints and submits rebuilt Flows; it
does not claim rows, acquire distributed locks, renew leases, or fence older
writers. If multiple application instances can recover the same store, the host
application must coordinate that recovery. `multiWriterSafe` on
`CheckpointStoreCapabilities` is advisory store metadata, not a core guarantee
that cross-process single-writer recovery is enforced.

Checkpoint `save(...)` and `delete(...)` are called synchronously on the Worker
tick thread or EventWorker loop thread. A slow store delays the progress of
other Flows on that Worker. Durable Flower is intended for low-frequency,
long-running orchestration; high-volume durable workloads need fast storage,
worker sharding, or a host-level execution strategy.

Terminal durable Flows first save a terminal tombstone and then attempt cleanup
delete. This intentionally costs an extra write on the happy path so a failed
delete cannot make a completed Flow recoverable again.

`definitionVersion` is a compatibility gate only when both the Flow definition
and the checkpoint carry a non-null value. If either side is null, Flower skips
the version check for migration compatibility.

EventWorker shutdown is cooperative. Step callbacks, effects, and `onExit`
handlers should not block the event-loop thread; if they do, shutdown and final
checkpoint cleanup can be delayed or interrupted.

Durable event-loop awaits cannot checkpoint predicate lambdas. Predicate-based
event awaits are rejected when the await is registered because the concrete
await conditions are produced at runtime by the Step.
