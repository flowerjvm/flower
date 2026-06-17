package io.github.parkkevinsb.flower.eventloop.persistence.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SQL dialect for the Flower event-flow checkpoint table.
 */
public interface JdbcEventFlowCheckpointDialect {

    String upsertSql();

    String deleteSql();

    String findSql();

    String findActiveSql();

    String findActiveByWorkerSql();

    default void bindBoolean(PreparedStatement ps, int index, boolean value) throws SQLException {
        ps.setBoolean(index, value);
    }

    default boolean readBoolean(ResultSet rs, String column) throws SQLException {
        return rs.getBoolean(column);
    }
}
