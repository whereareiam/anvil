plugins {
    alias(libs.plugins.toolkit.architecture)
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
    compileOnly(libs.paper)

    embedded(projects.anvilAgent.agent) { isTransitive = false }
}

tasks.processResources {
    expand("version" to project.version.toString())
}
