package io.github.flowerjvm.flower.persistence.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SQL dialect for the Flower checkpoint table.
 */
public interface JdbcCheckpointDialect {

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
