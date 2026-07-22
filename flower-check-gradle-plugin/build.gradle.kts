import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jreleaser") version "1.25.0"
}

group = "io.github.flowerjvm"
version = providers.gradleProperty("flowerVersion").orElse("0.1.2-SNAPSHOT").get()

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
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
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Flower Check Gradle Plugin")
            description.set("Runs flower-check against host application source during Gradle check.")
            url.set("https://github.com/flowerjvm/flower")
            inceptionYear.set("2026")
            licenses {
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("parkKevinSB")
                    name.set("Kevin SB Park")
                    email.set("oiltrustkr@gmail.com")
                    url.set("https://github.com/parkKevinSB")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/flowerjvm/flower.git")
                developerConnection.set("scm:git:ssh://git@github.com/flowerjvm/flower.git")
                url.set("https://github.com/flowerjvm/flower")
                tag.set("HEAD")
            }
        }
    }

    repositories {
        maven {
            name = "Staging"
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
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

jreleaser {
    configFile.set(layout.projectDirectory.file("jreleaser.yml"))
    dependsOnAssemble.set(false)
    gitRootSearch.set(true)
}
