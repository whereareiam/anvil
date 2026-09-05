plugins {
    alias(libs.plugins.toolkit.architecture)
    id("platform-agent")
}

description = "Shaded common runtime for Anvil platform agents"

architecture {
    kind = assembly
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "agent"
    }
}

dependencies {
    embedded(projects.anvilAgent.agentApi) { isTransitive = false }
    embedded(projects.anvilAgent.agentCommon) { isTransitive = false }
    embedded(projects.anvilApi) { isTransitive = false }
    embedded(libs.jackson.databind)
}

tasks.named("build") {
    dependsOn(":anvil-agent:agent-common:build")
}
