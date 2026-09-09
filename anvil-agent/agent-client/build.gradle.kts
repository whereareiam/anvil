plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

architecture {
    sharedApis = setOf(projects.anvilAgent.agentApi.path)
}

description = "Host-side agent connections, sessions, and player observation"

dependencies {
    compileOnly(projects.anvilAgent.agentClient.clientApi)
    compileOnly(projects.anvilApi)

    testImplementation(projects.anvilAgent.agentClient.clientApi)
    testImplementation(projects.anvilApi)

    testCompileOnly(libs.annotations)
}
