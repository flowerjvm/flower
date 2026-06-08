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
 * Optional same-application Flower console.
 *
 * <p>Disabled by default. Enable with
 * {@code flower.admin.console.enabled=true}. It serves a small HTML page and a
 * same-origin dump API from the host Spring Boot web application.
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
@ConditionalOnProperty(prefix = "flower.admin.console", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FlowerProperties.class)
public class FlowerConsoleEndpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "flowerConsoleController")
    public FlowerConsoleController flowerConsoleController(Engine engine, FlowerProperties properties) {
        return new FlowerConsoleController(engine, properties);
    }
}
