package io.github.parkkevinsb.flower.spring;

import io.github.parkkevinsb.flower.core.engine.Engine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Optional Spring MVC endpoint for reading {@link Engine#dump()} as JSON.
 *
 * <p>Disabled by default. Enable with
 * {@code flower.admin.dump.enabled=true}. Keep the endpoint behind application
 * authentication, a private network, or an admin gateway.
 */
@AutoConfiguration(after = FlowerAutoConfiguration.class)
@ConditionalOnClass(name = {
        "io.github.parkkevinsb.flower.core.engine.Engine",
        "io.github.parkkevinsb.flower.observability.dump.EngineDumpJson",
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.web.servlet.DispatcherServlet"
})
@ConditionalOnBean(Engine.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "flower.admin.dump", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FlowerProperties.class)
public class FlowerDumpEndpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "flowerDumpController")
    public FlowerDumpController flowerDumpController(Engine engine, FlowerProperties properties) {
        return new FlowerDumpController(engine, properties);
    }
}
