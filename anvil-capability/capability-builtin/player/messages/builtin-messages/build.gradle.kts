plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in messages capability"

dependencies {
    api(projects.anvilCapability.capabilityProtocolApi)
    api(projects.anvilCapability.capabilityBuiltin.player.messages.builtinMessagesApi)

    compileOnly(projects.anvilCapability.capabilityBuiltin.player.messages.builtinMessagesProtocol)
    compileOnly(libs.mcprotocol)

    embedded(projects.anvilCapability.capabilityBuiltin.player.messages.builtinMessagesProtocol) { isTransitive = false }
}
