plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in server capability"

dependencies {
    api(projects.anvilCapability.capabilityBuiltin.server.builtinServerApi)

    embedded(projects.anvilCapability.capabilityBuiltin.server.builtinServerBridge) { isTransitive = false }
}
