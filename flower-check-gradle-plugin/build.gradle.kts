plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.flowerjvm"
version = providers.gradleProperty("flowerVersion").orElse("0.1.0").get()

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    plugins {
        create("flowerCheck") {
            id = "io.github.flowerjvm.flower-check"
            implementationClass = "io.github.flowerjvm.flower.check.gradle.FlowerCheckGradlePlugin"
            displayName = "Flower Check Gradle Plugin"
            description = "Runs flower-check against host application source during Gradle check."
        }
    }
}

dependencies {
    implementation("io.github.flowerjvm:flower-check:${project.version}")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/flowerjvm/flower")
            credentials {
                username = (findProperty("gpr.user") as String?)
                    ?: System.getenv("GITHUB_ACTOR")
                    ?: ""
                password = (findProperty("gpr.key") as String?)
                    ?: System.getenv("GITHUB_TOKEN")
                    ?: ""
            }
        }
    }
}
