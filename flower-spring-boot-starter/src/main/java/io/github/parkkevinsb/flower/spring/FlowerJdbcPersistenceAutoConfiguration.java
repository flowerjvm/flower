package io.github.parkkevinsb.flower.spring;

import io.github.parkkevinsb.flower.core.persistence.FlowCheckpointStore;
import io.github.parkkevinsb.flower.persistence.jdbc.JdbcCheckpointDialect;
import io.github.parkkevinsb.flower.persistence.jdbc.JdbcCheckpointDialects;
import io.github.parkkevinsb.flower.persistence.jdbc.JdbcFlowCheckpointStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Optional JDBC checkpoint-store auto-configuration.
 *
 * <p>Activated only when the JDBC persistence module is on the classpath and
 * {@code flower.persistence.type=jdbc}. Schema creation remains the
 * application's responsibility.
 */
@AutoConfiguration(before = FlowerAutoConfiguration.class)
@ConditionalOnClass(name = "io.github.parkkevinsb.flower.persistence.jdbc.JdbcFlowCheckpointStore")
@ConditionalOnProperty(prefix = "flower", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FlowerProperties.class)
public class FlowerJdbcPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FlowCheckpointStore.class)
    @ConditionalOnProperty(prefix = "flower.persistence", name = "type", havingValue = "jdbc")
    public FlowCheckpointStore flowerJdbcCheckpointStore(
            FlowerProperties properties,
            DataSource dataSource
    ) {
        return JdbcFlowCheckpointStore.create(dataSource, dialect(properties));
    }

    static JdbcCheckpointDialect dialect(FlowerProperties properties) {
        FlowerProperties.JdbcDialect dialect = properties.getPersistence().getJdbc().getDialect();
        if (dialect == null) {
            throw new IllegalStateException(
                    "flower.persistence.jdbc.dialect must be set when flower.persistence.type=jdbc");
        }
        switch (dialect) {
            case POSTGRESQL:
                return JdbcCheckpointDialects.postgresql();
            case MYSQL:
                return JdbcCheckpointDialects.mysql();
            case ORACLE:
                return JdbcCheckpointDialects.oracle();
            case H2:
                return JdbcCheckpointDialects.h2();
            default:
                throw new IllegalStateException("Unsupported Flower JDBC dialect: " + dialect);
        }
    }
}
