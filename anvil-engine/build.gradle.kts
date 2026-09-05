plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "Platform-neutral process lifecycle and runtime context for Anvil"

dependencies {
    implementation(libs.jackson.databind)

    compileOnly(projects.anvilAgent.agentApi)
    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilPlatform.platformApi)
    compileOnly(projects.anvilProtocol.protocolApi)

    testImplementation(projects.anvilAgent.agentApi)
    testImplementation(projects.anvilApi)
    testImplementation(projects.anvilPlatform.platformApi)
    testImplementation(projects.anvilProtocol.protocolApi)
}
