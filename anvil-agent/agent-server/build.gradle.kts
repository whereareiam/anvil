plugins {
    alias(libs.plugins.toolkit.architecture)
    id("platform-agent")
}

architecture {
    sharedApis = setOf(projects.anvilAgent.agentApi.path)
}

description = "Authenticated agent endpoint and operation handlers embedded in Minecraft processes"

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "agent"
    }
}

dependencies {
    compileOnly(projects.anvilAgent.agentServer.serverApi)

    embedded(projects.anvilAgent.agentApi) { isTransitive = false }
    embedded(projects.anvilAgent.agentServer.serverApi) { isTransitive = false }
    embedded(projects.anvilApi) { isTransitive = false }
    embedded(libs.jackson.databind)

    testImplementation(projects.anvilAgent.agentClient)
    testImplementation(projects.anvilAgent.agentClient.clientApi)
    testImplementation(projects.anvilAgent.agentServer.serverApi)

    testCompileOnly(libs.annotations)
}
