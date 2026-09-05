plugins {
    alias(libs.plugins.toolkit.architecture)
    id("platform-agent")
}

description = "Anvil platform agent for BungeeCord"

architecture {
    kind = assembly
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "platform-bungeecord-agent"
    }
}

dependencies {
    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilAgent.agentApi)
    compileOnly(libs.bungeecord)

    embedded(projects.anvilAgent.agent) { isTransitive = false }
}

tasks.processResources {
    expand("version" to project.version.toString())
}
