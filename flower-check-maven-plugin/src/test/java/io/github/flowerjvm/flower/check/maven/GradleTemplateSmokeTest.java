package io.github.flowerjvm.flower.check.maven;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GradleTemplateSmokeTest {

    private static final String FLOWER_GROUP_ID = "io.github.flowerjvm";
    private static final String FLOWER_VERSION = "0.1.0";

    @TempDir
    Path tempDir;

    @Test
    void kotlinDslTemplateRunsFlowerCheckFromGradleCheck() throws Exception {
        SmokeContext context = smokeContext();
        Path project = createProject(context, "kotlin-clean", Template.KOTLIN, false);

        GradleResult result = runGradle(context.gradleExecutable, context.gradleUserHome, project);

        assertThat(result.output).contains("> Task :flowerCheck");
        assertThat(result.output).contains("flower-check: no findings.");
        assertThat(result.exitCode).isEqualTo(0);
    }

    @Test
    void groovyDslTemplateFailsGradleCheckOnFinding() throws Exception {
        SmokeContext context = smokeContext();
        Path project = createProject(context, "groovy-blocking", Template.GROOVY, true);

        GradleResult result = runGradle(context.gradleExecutable, context.gradleUserHome, project);

        assertThat(result.output).contains("> Task :flowerCheck");
        assertThat(result.output).contains("FLOWER-CHECK-001");
        assertThat(result.output).contains("Thread.sleep");
        assertThat(result.exitCode).isNotEqualTo(0);
    }

    private SmokeContext smokeContext() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("flower.check.gradle.smoke"),
                "Set -Dflower.check.gradle.smoke=true to run Gradle template smoke tests.");

        Path gradle = findGradleExecutable();
        assertThat(gradle)
                .as("Set -Dflower.check.gradle.executable=/path/to/gradle or put gradle on PATH")
                .isNotNull()
                .isRegularFile();

        Path flowerRoot = flowerRoot();
        Path localRepository = tempDir.resolve("repo");
        installSmokeArtifacts(flowerRoot, localRepository);

        Path gradleUserHome = Paths.get(System.getProperty("java.io.tmpdir"), "flower-check-gradle-smoke-home");
        Files.createDirectories(gradleUserHome);
        return new SmokeContext(gradle, gradleUserHome, localRepository, flowerRoot);
    }

    private Path createProject(SmokeContext context, String name, Template template, boolean blocking)
            throws IOException {
        Path project = tempDir.resolve(name);
        Path sourceRoot = project.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);

        Files.write(project.resolve(template.settingsFile),
                Collections.singletonList("rootProject.name = \"" + name + "\""),
                StandardCharsets.UTF_8);
        Files.write(project.resolve(template.buildFile),
                buildScript(context.localRepository, template).getBytes(StandardCharsets.UTF_8));
        Files.copy(context.flowerRoot.resolve("flower-check/templates/flower-check.config"),
                project.resolve("flower-check.config"));
        Files.write(sourceRoot.resolve(blocking ? "WaitStep.java" : "GoodStep.java"),
                source(blocking).getBytes(StandardCharsets.UTF_8));

        return project;
    }

    private static String buildScript(Path localRepository, Template template) throws IOException {
        String localRepositoryUri = localRepository.toUri().toString();
        Path templatePath = flowerRoot()
                .resolve("flower-check/templates")
                .resolve(template.templateFile);
        String templateText = new String(Files.readAllBytes(templatePath), StandardCharsets.UTF_8);

        if (template == Template.KOTLIN) {
            return "plugins {\n"
                    + "    java\n"
                    + "}\n\n"
                    + "repositories {\n"
                    + "    maven { url = uri(\"" + localRepositoryUri + "\") }\n"
                    + "}\n\n"
                    + templateText;
        }

        return "plugins {\n"
                + "    id 'java'\n"
                + "}\n\n"
                + "repositories {\n"
                + "    maven { url = uri('" + localRepositoryUri + "') }\n"
                + "}\n\n"
                + templateText;
    }

    private static String source(boolean blocking) {
        String body = blocking
                ? "        Thread.sleep(1000);\n"
                : "";
        return "package demo;\n\n"
                + "class " + (blocking ? "WaitStep" : "GoodStep") + " extends Step {\n"
                + "    protected StepResult onTick(StepContext ctx) throws Exception {\n"
                + body
                + "        return StepResult.done();\n"
                + "    }\n"
                + "}\n\n"
                + "class Step {\n"
                + "}\n\n"
                + "class StepContext {\n"
                + "}\n\n"
                + "class StepResult {\n"
                + "    static StepResult done() {\n"
                + "        return new StepResult();\n"
                + "    }\n"
                + "}\n";
    }

    private static GradleResult runGradle(Path gradleExecutable, Path gradleUserHome, Path project)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<String>();
        command.add(gradleExecutable.toString());
        command.add("--no-daemon");
        command.add("--gradle-user-home");
        command.add(gradleUserHome.toString());
        command.add("check");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(project.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        String output = readFully(process.getInputStream());
        int exitCode = process.waitFor();
        return new GradleResult(exitCode, output);
    }

    private static String readFully(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void installSmokeArtifacts(Path flowerRoot, Path repository)
            throws Exception {
        String javaparserVersion = parentProperty(flowerRoot, "javaparser.version");

        installArtifact(repository,
                FLOWER_GROUP_ID,
                "flower-check",
                FLOWER_VERSION,
                flowerRoot.resolve("flower-check/target/flower-check-" + FLOWER_VERSION + ".jar"),
                pom(FLOWER_GROUP_ID, "flower-check", FLOWER_VERSION,
                        dependency("com.github.javaparser", "javaparser-core", javaparserVersion)));
        installArtifact(repository,
                FLOWER_GROUP_ID,
                "flower-check-annotations",
                FLOWER_VERSION,
                flowerRoot.resolve("flower-check-annotations/target/flower-check-annotations-" + FLOWER_VERSION + ".jar"),
                pom(FLOWER_GROUP_ID, "flower-check-annotations", FLOWER_VERSION, ""));
        installArtifact(repository,
                "com.github.javaparser",
                "javaparser-core",
                javaparserVersion,
                javaparserJar(),
                pom("com.github.javaparser", "javaparser-core", javaparserVersion, ""));
    }

    private static Path javaparserJar() throws ClassNotFoundException, URISyntaxException {
        return Paths.get(Class.forName("com.github.javaparser.JavaParser")
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    private static void installArtifact(Path repository,
                                        String groupId,
                                        String artifactId,
                                        String version,
                                        Path jar,
                                        String pom) throws IOException {
        assertThat(jar)
                .as("Expected smoke artifact jar to exist before Gradle smoke test: " + jar)
                .isRegularFile();

        Path artifactDirectory = repository.resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version);
        Files.createDirectories(artifactDirectory);
        Files.copy(jar,
                artifactDirectory.resolve(artifactId + "-" + version + ".jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.write(artifactDirectory.resolve(artifactId + "-" + version + ".pom"),
                pom.getBytes(StandardCharsets.UTF_8));
    }

    private static String pom(String groupId, String artifactId, String version, String dependencies) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>" + groupId + "</groupId>\n"
                + "    <artifactId>" + artifactId + "</artifactId>\n"
                + "    <version>" + version + "</version>\n"
                + dependencies
                + "</project>\n";
    }

    private static String dependency(String groupId, String artifactId, String version) {
        return "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>" + groupId + "</groupId>\n"
                + "            <artifactId>" + artifactId + "</artifactId>\n"
                + "            <version>" + version + "</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n";
    }

    private static String parentProperty(Path flowerRoot, String name) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(flowerRoot.resolve("pom.xml").toFile());
        return document.getElementsByTagName(name).item(0).getTextContent().trim();
    }

    private static Path flowerRoot() {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        if (Files.isDirectory(userDir.resolve("flower-check"))
                && Files.isDirectory(userDir.resolve("flower-check-maven-plugin"))) {
            return userDir;
        }

        Path parent = userDir.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("flower-check"))
                && Files.isDirectory(parent.resolve("flower-check-maven-plugin"))) {
            return parent;
        }

        throw new IllegalStateException("Cannot locate flower repository root from " + userDir);
    }

    private static Path findGradleExecutable() throws IOException {
        String configured = System.getProperty("flower.check.gradle.executable");
        if (configured != null && configured.trim().length() > 0) {
            return Paths.get(configured);
        }

        String gradleHome = System.getenv("GRADLE_HOME");
        if (gradleHome != null && gradleHome.trim().length() > 0) {
            Path fromHome = Paths.get(gradleHome).resolve("bin").resolve(isWindows() ? "gradle.bat" : "gradle");
            if (Files.isRegularFile(fromHome)) {
                return fromHome;
            }
        }

        String path = System.getenv("PATH");
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        for (String directory : path.split(java.io.File.pathSeparator)) {
            for (String name : executableNames()) {
                Path candidate = Paths.get(directory).resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static List<String> executableNames() {
        if (isWindows()) {
            List<String> names = new ArrayList<String>();
            names.add("gradle.bat");
            names.add("gradle.cmd");
            names.add("gradle.exe");
            return names;
        }
        return Collections.singletonList("gradle");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private enum Template {
        KOTLIN("settings.gradle.kts", "build.gradle.kts", "gradle-build.gradle.kts"),
        GROOVY("settings.gradle", "build.gradle", "gradle-build.gradle");

        private final String settingsFile;
        private final String buildFile;
        private final String templateFile;

        Template(String settingsFile, String buildFile, String templateFile) {
            this.settingsFile = settingsFile;
            this.buildFile = buildFile;
            this.templateFile = templateFile;
        }
    }

    private static final class SmokeContext {
        private final Path gradleExecutable;
        private final Path gradleUserHome;
        private final Path localRepository;
        private final Path flowerRoot;

        private SmokeContext(Path gradleExecutable, Path gradleUserHome, Path localRepository, Path flowerRoot) {
            this.gradleExecutable = gradleExecutable;
            this.gradleUserHome = gradleUserHome;
            this.localRepository = localRepository;
            this.flowerRoot = flowerRoot;
        }
    }

    private static final class GradleResult {
        private final int exitCode;
        private final String output;

        private GradleResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
