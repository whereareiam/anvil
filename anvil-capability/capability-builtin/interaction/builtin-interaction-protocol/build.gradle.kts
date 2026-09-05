plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "MCProtocolLib adapter for the built-in interaction capability"

dependencies {
    implementation(libs.mcprotocol)

    compileOnly(projects.anvilCapability.capabilityBuiltin.interaction.builtinInteractionApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.session.builtinSessionApi)
    compileOnly(projects.anvilProtocol.protocolAdapterApi)
}
