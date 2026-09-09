plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in session capability"

dependencies {
    api(projects.anvilCapability.capabilityProtocolApi)
    api(projects.anvilCapability.capabilityBuiltin.player.session.builtinSessionApi)

    compileOnly(projects.anvilCapability.capabilityBuiltin.player.session.builtinSessionProtocol)
    compileOnly(libs.mcprotocol)

    embedded(projects.anvilCapability.capabilityBuiltin.player.session.builtinSessionProtocol) { isTransitive = false }
}
