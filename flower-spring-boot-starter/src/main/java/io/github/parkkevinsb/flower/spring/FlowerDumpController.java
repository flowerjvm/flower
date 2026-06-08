package io.github.parkkevinsb.flower.spring;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.observability.dump.EngineDumpJson;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only HTTP endpoint for rendering the current Flower Engine dump.
 *
 * <p>The controller does not mutate the Engine. It only calls
 * {@link Engine#dump()} when an HTTP request arrives.
 */
@RestController
public final class FlowerDumpController {

    private final Engine engine;
    private final FlowerProperties properties;

    public FlowerDumpController(Engine engine, FlowerProperties properties) {
        if (engine == null) {
            throw new IllegalArgumentException("engine must not be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.engine = engine;
        this.properties = properties;
    }

    @GetMapping(path = "${flower.admin.dump.path:/internal/flower/dump}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String dump(@RequestParam(name = "pretty", required = false) Boolean pretty) {
        boolean renderPretty = pretty != null
                ? pretty.booleanValue()
                : properties.getAdmin().getDump().isPretty();
        return renderPretty
                ? EngineDumpJson.toPrettyJson(engine.dump())
                : EngineDumpJson.toJson(engine.dump());
    }
}
