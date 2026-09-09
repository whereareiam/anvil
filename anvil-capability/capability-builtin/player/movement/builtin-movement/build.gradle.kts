plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in movement capability"

dependencies {
    api(projects.anvilCapability.capabilityProtocolApi)
    api(projects.anvilCapability.capabilityBuiltin.player.movement.builtinMovementApi)

    compileOnly(projects.anvilCapability.capabilityBuiltin.player.movement.builtinMovementProtocol)
    compileOnly(libs.mcprotocol)

    embedded(projects.anvilCapability.capabilityBuiltin.player.movement.builtinMovementProtocol) { isTransitive = false }
}
