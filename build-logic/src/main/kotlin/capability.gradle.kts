import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.whereareiam.toolkit.architecture.model.ArchitectureExtension

plugins {
    id("unit")
    id("bundle")
    id("me.whereareiam.toolkit.publish.maven")
}

extensions.configure<ArchitectureExtension>("architecture") {
    kind = assembly
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}

configurations.named("runtimeElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.named("shadowJar"))
}

toolkitPublish {
    artifactId.set(project.name)

    pom {
        name.set(project.name)
        description.set(project.description ?: "Anvil capability wiring")
    }
}

tasks.named("build") {
    dependsOn("shadowJar")
}
