package io.github.parkkevinsb.flower.check.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateFilesTest {

    @Test
    void mavenTemplatesWireFlowerCheckIntoVerify() throws IOException {
        String plugin = templateText("maven-plugin.xml");
        String workflow = templateText("github-actions-flower-check.yml");

        assertThat(plugin).contains("flower-check-annotations");
        assertThat(plugin).contains("flower-check-maven-plugin");
        assertThat(workflow).contains("packages: read");
        assertThat(workflow).contains("mvn -B verify");
    }

    @Test
    void gradleTemplatesWireFlowerCheckIntoCheck() throws IOException {
        String pluginKotlinSettings = templateText("gradle-plugin-settings.gradle.kts");
        String pluginKotlinBuild = templateText("gradle-plugin-build.gradle.kts");
        String pluginGroovySettings = templateText("gradle-plugin-settings.gradle");
        String pluginGroovyBuild = templateText("gradle-plugin-build.gradle");
        String kotlin = templateText("gradle-build.gradle.kts");
        String groovy = templateText("gradle-build.gradle");
        String workflow = templateText("github-actions-flower-check-gradle.yml");

        assertThat(pluginKotlinSettings).contains("pluginManagement");
        assertThat(pluginKotlinBuild).contains("io.github.parkkevinsb.flower.flower-check");
        assertThat(pluginGroovySettings).contains("pluginManagement");
        assertThat(pluginGroovyBuild).contains("io.github.parkkevinsb.flower.flower-check");
        assertThat(kotlin).contains("tasks.register<JavaExec>(\"flowerCheck\")");
        assertThat(kotlin).contains("dependsOn(\"flowerCheck\")");
        assertThat(kotlin).contains("flower.check.writeBaseline");
        assertThat(groovy).contains("tasks.register('flowerCheck', JavaExec)");
        assertThat(groovy).contains("dependsOn tasks.named('flowerCheck')");
        assertThat(groovy).contains("flower.check.writeBaseline");
        assertThat(workflow).contains("cache: gradle");
        assertThat(workflow).contains("./gradlew --no-daemon check");
    }

    private static String templateText(String name) throws IOException {
        return new String(Files.readAllBytes(templatePath(name)), StandardCharsets.UTF_8);
    }

    private static Path templatePath(String name) {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path fromModule = userDir.resolve("templates").resolve(name);
        if (Files.isRegularFile(fromModule)) {
            return fromModule;
        }
        return userDir.resolve("flower-check").resolve("templates").resolve(name);
    }
}
