import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.Configuration

plugins {
    id("com.gradleup.shadow")
}

val embedded: Configuration = configurations.create("embedded") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

tasks.withType<ShadowJar>().configureEach {
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
    configurations = listOf(embedded)
}
