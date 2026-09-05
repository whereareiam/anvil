plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "MCProtocol client creation, pinned library resolution, and shared worker control"

dependencies {
    api(projects.anvilProtocol.protocolApi)

    implementation(projects.anvilProtocol.protocolAdapterApi)
    implementation(libs.jackson.databind)
    implementation(libs.maven.resolver)
    implementation(libs.minecraft.auth)
    implementation(libs.slf4j.api)

    runtimeOnly(libs.slf4j.simple)

    testImplementation(projects.anvilCapability.capabilityBuiltin.default)

    testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolBinding2026)
    testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolBinding1206)
    testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolBinding1182)
    testRuntimeOnly(libs.mcprotocol)
}
