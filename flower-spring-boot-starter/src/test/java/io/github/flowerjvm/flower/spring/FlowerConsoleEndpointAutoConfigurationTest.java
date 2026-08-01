package io.github.flowerjvm.flower.spring;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowerConsoleEndpointAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FlowerJdbcPersistenceAutoConfiguration.class,
                    FlowerAutoConfiguration.class,
                    FlowerConsoleEndpointAutoConfiguration.class));

    @Test
    void consoleIsDisabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(FlowerConsoleController.class));
    }

    @Test
    void consoleRendersHtmlWhenEnabled() {
        runner.withPropertyValues(
                "flower.admin.console.enabled=true",
                "flower.admin.console.api-path=/internal/flower/console/dump",
                "flower.admin.console.poll-interval-ms=1500",
                "flower.admin.console.flow-graph-url=http://localhost:18790/"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(FlowerConsoleController.class);

            String html = ctx.getBean(FlowerConsoleController.class).console();

            assertThat(html).contains("Flower Console");
            assertThat(html).contains("/internal/flower/console/dump");
            assertThat(html).contains("const DEFAULT_POLL_MS = 1500;");
            assertThat(html).contains("id=\"flowGraphBtn\"");
            assertThat(html).contains("const FLOW_GRAPH_URL = \"http://localhost:18790/\";");
            assertThat(html).contains("id=\"startBtn\"");
            assertThat(html).contains("id=\"stopBtn\"");
        });
    }

    @Test
    void consoleCanHideFlowGraphButton() {
        runner.withPropertyValues(
                "flower.admin.console.enabled=true",
                "flower.admin.console.flow-graph-url="
        ).run(ctx -> {
            String html = ctx.getBean(FlowerConsoleController.class).console();

            assertThat(html).contains("const FLOW_GRAPH_URL = \"\";");
            assertThat(html).contains("el(\"flowGraphBtn\").hidden = !FLOW_GRAPH_URL;");
        });
    }

    @Test
    void consoleRejectsUnsafeFlowGraphUrlSchemes() {
        FlowerProperties.Console console = new FlowerProperties.Console();

        assertThatThrownBy(() -> console.setFlowGraphUrl("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flow-graph-url");
    }

    @Test
    void consoleDumpApiRendersCurrentEngineState() {
        runner.withPropertyValues(
                "flower.auto-start=false",
                "flower.admin.console.enabled=true"
        ).run(ctx -> {
            Engine engine = ctx.getBean(Engine.class);
            engine.attach();

            Step done = new Step() {
                @Override
                protected StepResult onTick(StepContext ctx) {
                    return StepResult.done();
                }
            };
            Step stay = new Step() {
                @Override
                protected StepResult onTick(StepContext ctx) {
                    ctx.setStepNo(7);
                    return StepResult.stay();
                }
            };

            engine.worker("main").submit(Flow.builder("document-review", "DOC-1")
                    .step("prepare", done)
                    .step("await-response", stay)
                    .step("emit", done)
                    .build());
            engine.worker("main").tickOnce();
            engine.worker("main").tickOnce();

            String json = ctx.getBean(FlowerConsoleController.class).dump(Boolean.FALSE);

            assertThat(json).contains("\"currentStepId\":\"await-response\"");
            assertThat(json).contains("\"currentStepIndex\":1");
            assertThat(json).contains("\"currentStepNo\":7");
            assertThat(json).contains("\"steps\":[");
        });
    }
}
