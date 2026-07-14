package io.github.flowerjvm.flower.spring;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.observability.dump.EngineDumpJson;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Built-in read-only Flower console page and same-origin dump API.
 */
@RestController
public final class FlowerConsoleController {

    private final Engine engine;
    private final FlowerProperties properties;

    public FlowerConsoleController(Engine engine, FlowerProperties properties) {
        if (engine == null) {
            throw new IllegalArgumentException("engine must not be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.engine = engine;
        this.properties = properties;
    }

    @GetMapping(path = "${flower.admin.console.path:/internal/flower/console}",
            produces = MediaType.TEXT_HTML_VALUE)
    public String console() {
        FlowerProperties.Console console = properties.getAdmin().getConsole();
        return FlowerConsoleHtml.render(console.getApiPath(), console.getPollIntervalMs());
    }

    @GetMapping(path = "${flower.admin.console.api-path:/internal/flower/console/dump}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String dump(@RequestParam(name = "pretty", required = false) Boolean pretty) {
        return pretty != null && pretty.booleanValue()
                ? EngineDumpJson.toPrettyJson(engine.dump())
                : EngineDumpJson.toJson(engine.dump());
    }
}
