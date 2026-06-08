package io.github.parkkevinsb.flower.spring;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerDumpEndpointAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FlowerJdbcPersistenceAutoConfiguration.class,
                    FlowerAutoConfiguration.class,
                    FlowerDumpEndpointAutoConfiguration.class));

    @Test
    void dumpEndpointIsDisabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(FlowerDumpController.class));
    }

    @Test
    void dumpEndpointRendersEngineDumpWhenEnabled() {
        runner.withPropertyValues(
                "flower.auto-start=false",
                "flower.admin.dump.enabled=true"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(FlowerDumpController.class);

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

            String json = ctx.getBean(FlowerDumpController.class).dump(Boolean.FALSE);

            assertThat(json).contains("\"engineState\":\"CREATED\"");
            assertThat(json).contains("\"currentStepId\":\"await-response\"");
            assertThat(json).contains("\"currentStepIndex\":1");
            assertThat(json).contains("\"currentStepNo\":7");
            assertThat(json).contains("\"steps\":[");
            assertThat(json).contains("\"executionContext\"");
        });
    }

    @Test
    void dumpEndpointCanPrettyPrintByDefault() {
        runner.withPropertyValues(
                "flower.admin.dump.enabled=true",
                "flower.admin.dump.pretty=true"
        ).run(ctx -> {
            String json = ctx.getBean(FlowerDumpController.class).dump(null);

            assertThat(json).contains("\n");
            assertThat(json).contains("  \"engineState\"");
        });
    }
}
