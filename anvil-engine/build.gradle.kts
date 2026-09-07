plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "Platform-neutral process lifecycle and runtime context for Anvil"

dependencies {
    compileOnly(projects.anvilAgent.agentApi)
    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilExecution.executionApi)
    compileOnly(projects.anvilProvisioning.provisioningApi)
    compileOnly(projects.anvilProvisioning.provisioningJava.api)
    compileOnly(projects.anvilPlatform.platformApi)
    compileOnly(projects.anvilProtocol.protocolApi)

    testImplementation(projects.anvilAgent.agentApi)
    testImplementation(projects.anvilApi)
    testImplementation(projects.anvilExecution.executionApi)
    testImplementation(projects.anvilProvisioning.provisioningApi)
    testImplementation(projects.anvilProvisioning.provisioningJava.api)
    testImplementation(projects.anvilPlatform.platformApi)
    testImplementation(projects.anvilProtocol.protocolApi)
    testImplementation(projects.anvilExecution.executionLocal)
    testImplementation(projects.anvilProvisioning.provisioningCache)
    testImplementation(projects.anvilProvisioning.provisioningJava)
}
