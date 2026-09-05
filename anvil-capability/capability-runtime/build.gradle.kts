plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "Internal runtime for composing dependency-discovered Anvil capabilities"

dependencies {
    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilCapability.capabilityApi)
    compileOnly(projects.anvilProtocol.protocolApi)

    testImplementation(projects.anvilApi)
    testImplementation(projects.anvilCapability.capabilityApi)
    testImplementation(projects.anvilCapability.capabilityBuiltin.default)
    testImplementation(projects.anvilProtocol.protocolApi)
}
