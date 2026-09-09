plugins {
    application
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Version-isolated MCProtocolLib clients and workers for Anvil"

dependencies {
    api(projects.anvilProtocol.protocolApi)

    implementation(libs.adventure.plain)
    implementation(libs.jackson.databind)
    implementation(libs.minecraft.auth)
    implementation(libs.slf4j.api)

    compileOnly(libs.mcprotocol)

    runtimeOnly(libs.mcprotocol)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(projects.anvilCapability.capabilityProtocolApi)
    testImplementation(projects.anvilCapability)
    testImplementation(projects.anvilCapability.capabilityBuiltin.default)
    testImplementation(projects.anvilLauncher)
    testImplementation(projects.anvilEnvironment.cache.cacheApi)
    testImplementation(projects.anvilEnvironment.cache)
    testImplementation(projects.anvilEnvironment.provisioning.provisioningArtifact)
    testImplementation(projects.anvilEnvironment.provisioning.provisioningArtifact.artifactApi)

    testCompileOnly(libs.mcprotocol)
}

application {
    mainClass.set("me.whereareiam.anvil.protocol.mcprotocol.worker.child.McProtocolWorkerMain")
}
