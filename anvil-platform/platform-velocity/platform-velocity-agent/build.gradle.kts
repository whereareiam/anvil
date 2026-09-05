plugins {
    alias(libs.plugins.toolkit.architecture)
    id("platform-agent")
}

description = "Anvil platform agent for Velocity"

architecture {
    kind = assembly
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "platform-velocity-agent"
    }
}

dependencies {
    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilAgent.agentApi)
    compileOnly(libs.velocity)

    embedded(projects.anvilAgent.agent) { isTransitive = false }

    annotationProcessor(libs.velocity)
}
