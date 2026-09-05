plugins {
    alias(libs.plugins.toolkit.architecture)
    id("agent-java")
    id("platform-agent")
}

description = "Anvil platform agent for Paper and Spigot"

architecture {
    kind = assembly
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "platform-bukkit-agent"
    }
}

dependencies {
    compileOnly(projects.anvilAgent.agentApi)
    compileOnly(libs.paperLegacy)

    embedded(projects.anvilAgent.agent) { isTransitive = false }
}

tasks.processResources {
    expand("version" to project.version.toString())
}
