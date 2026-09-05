import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
    id("bundle")
}

architecture {
    kind = assembly
}

description = "Anvil's executable scenario launcher distribution"

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}

listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.named("shadowJar"))
    }
}

dependencies {
    api(projects.anvilAgent.agentApi)
    api(projects.anvilApi)
    api(projects.anvilCapability.capabilityApi)
    api(projects.anvilPlatform.platformApi)
    api(projects.anvilProtocol.protocolAdapterApi)
    api(projects.anvilProtocol.protocolApi)

    compileOnly(projects.anvilEngine)

    embedded(projects.anvilAgent.agentCommon) { isTransitive = false }
    embedded(projects.anvilCapability.capabilityRuntime) { isTransitive = false }
    embedded(projects.anvilEngine) { isTransitive = false }
    embedded(libs.jackson.databind)
    embedded(libs.slf4j.api)
    embedded(libs.slf4j.simple)

    testImplementation(projects.anvilCapability.capabilityBuiltin.default)

    testRuntimeOnly(projects.anvilPlatform.platformBukkit.platformBukkitAgent)
    testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordAgent)
    testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordProvider)
    testRuntimeOnly(projects.anvilPlatform.platformPaper.platformPaperProvider)
    testRuntimeOnly(projects.anvilPlatform.platformSpigot.platformSpigotProvider)
    testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityAgent)
    testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityProvider)
    testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol)
}

toolkitPublish {
    artifactId.set("launcher")
    pom {
        name.set("Anvil Launcher")
    }
}

tasks.named("build") {
    dependsOn("shadowJar")
}

configurations.testRuntimeOnly {
    extendsFrom(configurations["embedded"])
}
