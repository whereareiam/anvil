plugins {
    application
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Version-isolated MCProtocolLib clients and workers for Anvil"

dependencies {
    api(projects.anvilProtocol.protocolApi)

    implementation(projects.anvilProtocol.protocolAdapterApi)
    implementation(libs.adventure.plain)
    implementation(libs.jackson.databind)
    implementation(libs.minecraft.auth)
    implementation(libs.slf4j.api)

    compileOnly(libs.mcprotocol)

    runtimeOnly(libs.mcprotocol)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(projects.anvilCapability.capabilityBuiltin.default)
}

application {
    mainClass.set("me.whereareiam.anvil.protocol.mcprotocol.worker.child.McProtocolWorkerMain")
}
