import org.gradle.api.tasks.JavaExec
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val intellijBaselineVersion = "2026.1.1"
val intellijSinceBuild = "261"
val intellijVerificationVersions = listOf("2026.1.1", "2026.2.4", "263.7307.112")

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val jteCompatibility by sourceSets.creating

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // See https://github.com/JetBrains/intellij-platform-gradle-plugin
    intellijPlatform {
        intellijIdea(intellijBaselineVersion)

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")

        pluginVerifier()
        zipSigner()

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("gg.jte:jte-runtime:3.2.4")
    add(jteCompatibility.implementationConfigurationName, "gg.jte:jte-kotlin:3.2.4")
}

intellijPlatform {
    instrumentCode = false
    pluginConfiguration {
        id = "de.rechenwerk.kte"
        name = "KTE"
        version = "0.2.1"
    }
    projectName = "kte-intellij"
    publishing {
        token = System.getenv("ORG_GRADLE_PROJECT_intellijPublishToken")
    }
    pluginVerification {
        ides {
            intellijVerificationVersions.forEach { version ->
                create(IntelliJPlatformType.IntellijIdea, version)
            }
        }
    }
}

tasks {
    patchPluginXml {
        sinceBuild = provider { intellijSinceBuild }
        untilBuild = provider { null }
    }

    test {
        // Kotlin analysis fixtures retain IDE caches across tests; the default 2 GiB heap thrashes.
        maxHeapSize = "4g"
    }

    register<JavaExec>("checkJteKotlinCodegen") {
        group = "verification"
        description = "Checks template syntax against the real JTE Kotlin code generator in an isolated JVM."
        dependsOn(jteCompatibility.classesTaskName)
        classpath = jteCompatibility.runtimeClasspath
        mainClass = "org.jusecase.jte.intellij.compat.JteKotlinCodegenCheck"
    }

    named("check") {
        dependsOn("checkJteKotlinCodegen")
    }
}
