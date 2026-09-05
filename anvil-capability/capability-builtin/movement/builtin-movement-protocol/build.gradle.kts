plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "MCProtocolLib adapter for the built-in movement capability"

dependencies {
    implementation(libs.mcprotocol)

    compileOnly(projects.anvilCapability.capabilityBuiltin.movement.builtinMovementApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.session.builtinSessionApi)
    compileOnly(projects.anvilProtocol.protocolAdapterApi)
}
