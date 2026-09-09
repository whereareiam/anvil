plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in server capability"

dependencies {
    api(projects.anvilCapability.capabilityApi)
    api(projects.anvilCapability.capabilityBuiltin.player.server.builtinServerApi)

    compileOnly(projects.anvilCapability.capabilityBuiltin.player.server.builtinServerBridge)

    embedded(projects.anvilCapability.capabilityBuiltin.player.server.builtinServerBridge) { isTransitive = false }
}
