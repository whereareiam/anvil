plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "MCProtocolLib adapter for the built-in inventory capability"

dependencies {
    implementation(libs.mcprotocol)

    compileOnly(projects.anvilCapability.capabilityBuiltin.inventory.builtinInventoryApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.session.builtinSessionApi)
    compileOnly(projects.anvilProtocol.protocolAdapterApi)
}
