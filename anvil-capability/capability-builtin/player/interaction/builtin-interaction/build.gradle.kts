plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in interaction capability"

dependencies {
    api(projects.anvilCapability.capabilityProtocolApi)
    api(projects.anvilCapability.capabilityBuiltin.player.interaction.builtinInteractionApi)

    compileOnly(projects.anvilCapability.capabilityBuiltin.player.interaction.builtinInteractionProtocol)
    compileOnly(libs.mcprotocol)

    embedded(projects.anvilCapability.capabilityBuiltin.player.interaction.builtinInteractionProtocol) { isTransitive = false }
}
