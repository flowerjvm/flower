// Copy this snippet into build.gradle.kts in the host Flower project.
plugins {
    java
    id("io.github.flowerjvm.flower-check") version "0.1.0-SNAPSHOT"
}

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

dependencies {
    compileOnly("io.github.flowerjvm:flower-check-annotations:0.1.0-SNAPSHOT")
}

flowerCheck {
    includeTests.set(false)
}
