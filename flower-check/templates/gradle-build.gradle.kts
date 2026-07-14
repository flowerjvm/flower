// Copy this snippet into build.gradle.kts in the host Flower project.
// The host project should apply the Java plugin before this snippet.
val flowerCheckVersion = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/flowerjvm/flower")
        credentials {
            username = (findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR") ?: ""
            password = (findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN") ?: ""
        }
    }
}

val flowerCheck by configurations.creating

dependencies {
    flowerCheck("io.github.flowerjvm:flower-check:$flowerCheckVersion")
    compileOnly("io.github.flowerjvm:flower-check-annotations:$flowerCheckVersion")
}

tasks.register<JavaExec>("flowerCheck") {
    group = "verification"
    description = "Runs flower-check against Flower source usage."
    classpath = flowerCheck
    mainClass.set("io.github.flowerjvm.flower.check.cli.FlowerCheckCli")
    onlyIf {
        !providers.gradleProperty("flower.check.skip")
            .map { it.toBoolean() }
            .getOrElse(false)
    }

    val flowerCheckArgs = mutableListOf(
        "--config",
        "${rootProject.projectDir}/flower-check.config"
    )
    providers.gradleProperty("flower.check.writeBaseline").orNull?.let { baseline ->
        flowerCheckArgs.addAll(listOf("--write-baseline", baseline))
    }
    flowerCheckArgs.add("${project.projectDir}/src/main/java")
    args(flowerCheckArgs)
}

tasks.named("check") {
    dependsOn("flowerCheck")
}
