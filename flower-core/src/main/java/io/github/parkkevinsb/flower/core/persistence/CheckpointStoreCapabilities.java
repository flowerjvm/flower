package io.github.parkkevinsb.flower.core.persistence;

/**
 * Declares checkpoint-store capabilities.
 *
 * <p>These flags describe the store. They do not expand core's execution
 * guarantees. In particular, Flower's in-memory Flow ownership is scoped to one
 * JVM and one Engine; cross-process recovery still needs host-level locking,
 * leases, fencing, or leader election.
 */
public final class CheckpointStoreCapabilities {

    private static final CheckpointStoreCapabilities NONE =
            new CheckpointStoreCapabilities(false, false, false);
    private static final CheckpointStoreCapabilities DURABLE_ONLY =
            new CheckpointStoreCapabilities(true, false, false);
    private static final CheckpointStoreCapabilities DURABLE_QUERYABLE =
            new CheckpointStoreCapabilities(true, true, false);
    private static final CheckpointStoreCapabilities DURABLE_QUERYABLE_MULTI_WRITER =
            new CheckpointStoreCapabilities(true, true, true);

    private final boolean durable;
    private final boolean queryable;
    private final boolean multiWriterSafe;

    private CheckpointStoreCapabilities(boolean durable, boolean queryable, boolean multiWriterSafe) {
        if (!durable && (queryable || multiWriterSafe)) {
            throw new IllegalArgumentException("non-durable checkpoint stores cannot be queryable or multi-writer safe");
        }
        if (multiWriterSafe && !queryable) {
            throw new IllegalArgumentException("multi-writer safe checkpoint stores must also be queryable");
        }
        this.durable = durable;
        this.queryable = queryable;
        this.multiWriterSafe = multiWriterSafe;
    }

    public static CheckpointStoreCapabilities none() {
        return NONE;
    }

    public static CheckpointStoreCapabilities durableOnly() {
        return DURABLE_ONLY;
    }

    public static CheckpointStoreCapabilities durableQueryable() {
        return DURABLE_QUERYABLE;
    }

    public static CheckpointStoreCapabilities durableQueryableMultiWriterSafe() {
        return DURABLE_QUERYABLE_MULTI_WRITER;
    }

    public static CheckpointStoreCapabilities of(
            boolean durable,
            boolean queryable,
            boolean multiWriterSafe) {
        if (!durable && !queryable && !multiWriterSafe) {
            return NONE;
        }
        if (durable && !queryable && !multiWriterSafe) {
            return DURABLE_ONLY;
        }
        if (durable && queryable && !multiWriterSafe) {
            return DURABLE_QUERYABLE;
        }
        if (durable && queryable && multiWriterSafe) {
            return DURABLE_QUERYABLE_MULTI_WRITER;
        }
        return new CheckpointStoreCapabilities(durable, queryable, multiWriterSafe);
    }

    /**
     * True when {@code save/delete} are real durable persistence operations.
     */
    public boolean durable() {
        return durable;
    }

    /**
     * True when active checkpoints can be queried for recovery.
     */
    public boolean queryable() {
        return queryable;
    }

    /**
     * True when the store itself can participate in multi-writer coordination.
     *
     * <p>This is advisory metadata. Core recovery does not automatically claim
     * rows or enforce cross-process single-writer execution just because this
     * flag is true.
     */
    public boolean multiWriterSafe() {
        return multiWriterSafe;
    }
}
