plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "MCProtocolLib adapter for the built-in messages capability"

dependencies {
    implementation(libs.adventure.plain)
    implementation(libs.mcprotocol)

    compileOnly(projects.anvilCapability.capabilityBuiltin.messages.builtinMessagesApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.session.builtinSessionApi)
    compileOnly(projects.anvilProtocol.protocolAdapterApi)
}
